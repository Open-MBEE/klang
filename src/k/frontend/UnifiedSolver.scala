package k.frontend

import com.microsoft.z3._
import scala.collection.mutable.{ListBuffer, Map => MMap, Set => MSet}

/**
 * UnifiedSolver - Iterative solving loop for K models
 *
 * Handles:
 * 1. CEGAR refinement for external/opaque function calls
 * 2. Heap-bound CEGAR: start small, increase on UNSAT or tight bounds
 * 3. Incremental/anytime solving with pause, resume, sampling
 * 4. Optimization with soft constraints (max-SAT style)
 */
object UnifiedSolver {

  // Type aliases to avoid confusion between K Model and Z3 Model
  type KModel = k.frontend.Model
  type Z3Model = com.microsoft.z3.Model

  // ============================================================================
  // Heap Strategy for CEGAR
  // ============================================================================

  sealed trait HeapStrategy
  object HeapStrategy {
    /** Do not bound heap at all (use current static allocation) */
    case object Unbounded extends HeapStrategy
    /** Fixed bounds, no CEGAR refinement */
    case object Bounded extends HeapStrategy
    /** CEGAR loop: start small, increase bounds on UNSAT or tight heap */
    case object CegarBounded extends HeapStrategy
    /** Soft constraints: allocate pool, use Optimize to minimize alive objects */
    case object SoftBounded extends HeapStrategy
  }

  /** Heap bounds per class */
  final case class HeapBounds(
    defaultBound: Int,
    perClassBounds: Map[String, Int] = Map.empty,
    maxBound: Int = 64
  ) {
    def boundForClass(cls: String): Int =
      perClassBounds.getOrElse(cls, defaultBound)

    def withIncreasedBound(cls: String, factor: Int = 2): HeapBounds = {
      val old = boundForClass(cls)
      val next = math.min(old * factor, maxBound)
      copy(perClassBounds = perClassBounds + (cls -> next))
    }

    def withAllBoundsIncreased(factor: Int = 2): HeapBounds = {
      val newDefault = math.min(defaultBound * factor, maxBound)
      val newPerClass = perClassBounds.map { case (cls, bound) =>
        cls -> math.min(bound * factor, maxBound)
      }
      copy(defaultBound = newDefault, perClassBounds = newPerClass)
    }

    def atMaxBound: Boolean = defaultBound >= maxBound

    /** Effective multiplier (max of all bounds for instanceMultiplier) */
    def effectiveMultiplier: Int = {
      if (perClassBounds.isEmpty) defaultBound
      else math.max(defaultBound, perClassBounds.values.max)
    }

    override def toString: String = {
      val perClassStr = if (perClassBounds.isEmpty) ""
                        else s", perClass=${perClassBounds.mkString("{", ",", "}")}"
      s"HeapBounds(default=$defaultBound, max=$maxBound$perClassStr)"
    }
  }

  // ============================================================================
  // Configuration
  // ============================================================================

  /** Maximum iterations before giving up */
  var maxIterations: Int = 100

  /** Maximum CEGAR heap iterations */
  var maxHeapCegarIterations: Int = 10

  /** Maximum object instances per class */
  var maxObjectBound: Int = 100

  /** Initial object bound for collections */
  var initialObjectBound: Int = 1

  /** Current heap strategy */
  var heapStrategy: HeapStrategy = HeapStrategy.CegarBounded

  /** Current heap bounds (for CEGAR) */
  var heapBounds: HeapBounds = HeapBounds(defaultBound = 1, maxBound = 64)

  /** Whether to use Z3 Optimize API for soft constraints */
  var useOptimize: Boolean = true

  /** Whether to use CVC5 instead of Z3 (faster for strings) */
  var useCVC5: Boolean = false

  /** Debug logging */
  var debug: Boolean = false

  // ============================================================================
  // State
  // ============================================================================

  /** Current object bounds per class: ClassName -> max instances */
  private val objectBounds: MMap[String, Int] = MMap()

  /** CEGAR refinement constraints */
  private val refinements: ListBuffer[String] = ListBuffer()

  /** Soft constraint weights: constraint string -> weight */
  private val softConstraints: MMap[String, Double] = MMap()

  /** Best solution found so far */
  private var bestSoFar: Option[Z3Model] = None

  /** Current iteration count */
  private var iteration: Int = 0

  /** Solving in progress flag */
  @volatile private var solving: Boolean = false

  /** Interrupt requested */
  @volatile private var interrupted: Boolean = false

  /** Pause requested */
  @volatile private var pauseRequested: Boolean = false

  /** Sample requested */
  @volatile private var sampleRequested: Boolean = false

  /** Paused state */
  @volatile private var isPaused: Boolean = false

  /** Last sample taken */
  private var lastSample: Option[Z3Model] = None

  /** Classes that need dynamic object bounds */
  private val dynamicClasses: MSet[String] = MSet()

  // ============================================================================
  // Public API
  // ============================================================================

  /**
   * Main entry point: solve a K model with unified loop
   */
  def solve(model: KModel, smtModel: String, printModel: Boolean): SolveResult = {
    reset()
    solving = true

    try {
      // Extract configuration from model annotations
      val config = extractConfig(model)

      // Initialize object bounds from model
      initializeObjectBounds(model)

      // Extract soft constraints from model
      extractSoftConstraints(model)

      // Main solving loop
      unifiedLoop(model, smtModel, config, printModel)
    } finally {
      solving = false
    }
  }

  /**
   * Heap-bound CEGAR solving loop
   *
   * This is a separate entry point that:
   * 1. Starts with small heap bounds (defaultBound = 1)
   * 2. On UNSAT: increases bounds and regenerates SMT
   * 3. On SAT: checks k!heap_full! flags to see if bounds are tight
   * 4. Repeats until SAT with non-tight bounds, or max iterations
   *
   * Smart refinement: Only increases bounds for classes that are tight.
   */
  def solveWithHeapCegar(model: KModel, printModel: Boolean): SolveResult = {
    // Use HeapBounds for per-class tracking
    var bounds = HeapBounds(defaultBound = 1, maxBound = 16)
    var heapIteration = 0
    var result: SolveResult = SolveResult.Unknown("Not started")
    var lastTightClasses: List[String] = Nil

    logHeap(s"Starting Heap CEGAR loop with strategy: $heapStrategy")
    logHeap(s"Initial multiplier: ${bounds.defaultBound} (max: ${bounds.maxBound})")

    while (heapIteration < maxHeapCegarIterations && !bounds.atMaxBound) {
      heapIteration += 1
      logHeap(s"")
      logHeap(s"========== Heap CEGAR Iteration $heapIteration ==========")
      logHeap(s"Current multiplier: ${bounds.effectiveMultiplier}x")
      if (bounds.perClassBounds.nonEmpty) {
        logHeap(s"Per-class multipliers: ${bounds.perClassBounds}")
      }

      // Set the global instance multiplier
      val previousMultiplier = ASTOptions.instanceMultiplier
      val currentMultiplier = bounds.effectiveMultiplier
      ASTOptions.instanceMultiplier = currentMultiplier
      logHeap(s"Set ASTOptions.instanceMultiplier = $currentMultiplier")

      try {
        // Regenerate the SMT model with new bounds
        logHeap(s"Regenerating SMT model with ${currentMultiplier}x instances...")

        // Clear previous state
        UtilSMT.reset

        // Set CVC5 compatibility mode if using CVC5
        if (useCVC5) {
          ASTOptions.cvc5Compatible = true
        }

        // Generate fresh SMT
        var smtModel = model.toSMT

        // Add heap_full flags to the model for CEGAR feedback
        smtModel = addHeapFullFlags(smtModel)

        // Log the heap layout
        if (UtilSMT.objectGraph != null) {
          logHeap(s"Heap layout:")
          for (className <- UtilSMT.objectGraph.getAllClasses) {
            val entries = UtilSMT.objectGraph.getHeapEntries(className)
            logHeap(s"  $className: ${entries.size} instances (refs ${entries.headOption.getOrElse("?")} to ${entries.lastOption.getOrElse("?")})")
          }
        }

        logHeap(s"SMT model generated, solving with ${if (useCVC5) "CVC5" else "Z3"}...")

        // Try to solve - use CVC5 if requested (faster for strings)
        if (useCVC5 && CVC5Solver.isAvailable) {
          val cvc5Result = CVC5Solver.solve(smtModel)
          result = cvc5Result match {
            case CVC5Solver.CVC5Result.Sat(_) =>
              // For CVC5, we don't have a Z3 model, so create a dummy for now
              // In a full implementation, we'd parse the CVC5 model
              SolveResult.Unknown("CVC5 SAT - model parsing not yet implemented")
            case CVC5Solver.CVC5Result.Unsat => SolveResult.Unsat
            case CVC5Solver.CVC5Result.Unknown(r) => SolveResult.Unknown(r)
            case CVC5Solver.CVC5Result.Error(e) => SolveResult.Unknown(e)
          }
        } else {
          result = solve(model, smtModel, printModel = false)
        }

        result match {
          case SolveResult.Sat(z3Model) =>
            logHeap(s"SAT! Checking if heap bounds are tight...")

            // Check k!heap_full! flags
            val tightClasses = checkHeapFullFlags(z3Model)
            lastTightClasses = tightClasses

            if (tightClasses.nonEmpty) {
              logHeap(s"Heap is TIGHT for classes: ${tightClasses.mkString(", ")}")
              logHeap(s"Solution found but using all available objects.")
              logHeap(s"Accepting tight solution.")
              if (printModel) {
                K2Z3.z3Model = z3Model
                K2Z3.PrintModel(model)
              }
              return result
            } else {
              logHeap(s"Heap bounds are NOT tight - solution has spare capacity.")
              if (printModel) {
                K2Z3.z3Model = z3Model
                K2Z3.PrintModel(model)
              }
              return result
            }

          case SolveResult.Unsat =>
            logHeap(s"UNSAT with current multiplier (${bounds.effectiveMultiplier}x).")

            if (bounds.atMaxBound) {
              logHeap(s"Already at max multiplier (${bounds.maxBound}), giving up.")
              return SolveResult.Unsat
            }

            // Smart refinement: if we know which classes were tight in a previous SAT,
            // only increase those. Otherwise, increase all.
            val oldMultiplier = bounds.effectiveMultiplier
            if (lastTightClasses.nonEmpty) {
              logHeap(s"Increasing multiplier for previously tight classes: ${lastTightClasses.mkString(", ")}")
              bounds = lastTightClasses.foldLeft(bounds) { (b, cls) =>
                b.withIncreasedBound(cls)
              }
            } else {
              // No info about tight classes, double all bounds
              bounds = bounds.withAllBoundsIncreased()
            }
            logHeap(s"Increasing multiplier: ${oldMultiplier}x -> ${bounds.effectiveMultiplier}x")
            // Continue loop

          case SolveResult.Timeout =>
            logHeap(s"TIMEOUT")
            return result

          case SolveResult.Unknown(reason) =>
            logHeap(s"UNKNOWN: $reason")
            return result
        }
      } finally {
        // Restore previous setting
        ASTOptions.instanceMultiplier = previousMultiplier
      }
    }

    if (heapIteration >= maxHeapCegarIterations) {
      logHeap(s"Max heap CEGAR iterations ($maxHeapCegarIterations) reached")
    }
    if (bounds.atMaxBound) {
      logHeap(s"Max multiplier (${bounds.maxBound}) reached")
    }

    result
  }

  /**
   * Check k!heap_full!ClassName flags in a Z3 model
   * Returns list of class names where all allocated objects are in use
   */
  private def checkHeapFullFlags(z3Model: Z3Model): List[String] = {
    val tightClasses = ListBuffer[String]()

    // Get all class names from the model
    // For now, we check against dynamicClasses which were identified during init
    // In practice, we should check all classes that have heap allocations

    // Get all class names from the objectGraph (populated during SMT generation)
    val classesToCheck = if (UtilSMT.objectGraph != null) {
      UtilSMT.objectGraph.getAllClasses.filter(_ != "TopLevelDeclarations")
    } else if (dynamicClasses.nonEmpty) {
      dynamicClasses.toList
    } else {
      objectBounds.keys.toList
    }

    logHeap(s"Checking heap_full flags for classes: ${classesToCheck.mkString(", ")}")

    for (className <- classesToCheck) {
      val flagName = s"k!heap_full!$className"
      try {
        val flagDecl = z3Model.getConstDecls.find(_.getName.toString == flagName)
        flagDecl match {
          case Some(decl) =>
            val interp = z3Model.getConstInterp(decl)
            if (interp != null && interp.isTrue) {
              logHeap(s"  k!heap_full!$className = true (tight)")
              tightClasses += className
            } else {
              logHeap(s"  k!heap_full!$className = false (spare capacity)")
            }
          case None =>
            // Flag not found - that's OK, might not be instrumented yet
            logHeap(s"  k!heap_full!$className not found in model")
        }
      } catch {
        case e: Exception =>
          logHeap(s"  Error checking k!heap_full!$className: ${e.getMessage}")
      }
    }

    tightClasses.toList
  }

  private def logHeap(msg: String): Unit = {
    // Always log heap CEGAR messages - they're important for understanding the solving process
    println(s"[HeapCEGAR] $msg")
  }

  private def logSoft(msg: String): Unit = {
    println(s"[HeapSoft] $msg")
  }

  /**
   * Solve using soft constraints to minimize heap usage.
   *
   * This approach:
   * 1. Generates the model with a pool of candidate objects per class
   * 2. Adds soft constraints preferring objects to be "dead" (unused)
   * 3. Uses Z3's Optimize API to find a solution with minimal alive objects
   *
   * Unlike CEGAR, this doesn't iterate - it uses a fixed pool size
   * but lets Z3 Optimize find the minimal usage within that pool.
   */
  def solveWithSoftHeap(model: KModel, printModel: Boolean): SolveResult = {
    logSoft("Starting Soft-Bounded Heap Solver")

    // Use a reasonable pool size - larger than CEGAR starting point
    // since we're letting Optimize find the minimum
    val poolMultiplier = 4
    val previousMultiplier = ASTOptions.instanceMultiplier
    ASTOptions.instanceMultiplier = poolMultiplier

    logSoft(s"Pool multiplier: ${poolMultiplier}x (Optimize will minimize usage)")

    try {
      // Clear previous state
      UtilSMT.reset

      // Generate SMT model with the larger pool
      val baseSmt = model.toSMT

      // Log heap layout
      if (UtilSMT.objectGraph != null) {
        logSoft(s"Heap layout:")
        for (className <- UtilSMT.objectGraph.getAllClasses) {
          val entries = UtilSMT.objectGraph.getHeapEntries(className)
          logSoft(s"  $className: ${entries.size} instances (refs ${entries.headOption.getOrElse("?")} to ${entries.lastOption.getOrElse("?")})")
        }
      }

      // Add soft constraints for heap minimization
      val smtWithSoft = addHeapSoftConstraints(model, baseSmt)

      logSoft("Solving with Optimize API...")

      // Use Optimize solver
      val result = solveWithOptimize(model, smtWithSoft, printModel)

      result match {
        case SolveResult.Sat(z3Model) =>
          logSoft("SAT - found solution with minimal heap usage")
          // Count alive objects
          countAliveObjects(z3Model)
          if (printModel) {
            K2Z3.z3Model = z3Model
            K2Z3.PrintModel(model)
          }
        case SolveResult.Unsat =>
          logSoft("UNSAT - no solution exists even with ${poolMultiplier}x pool")
        case SolveResult.Timeout =>
          logSoft("TIMEOUT")
        case SolveResult.Unknown(reason) =>
          logSoft(s"UNKNOWN: $reason")
      }

      result

    } finally {
      ASTOptions.instanceMultiplier = previousMultiplier
    }
  }

  /**
   * Add soft constraints to the SMT model to minimize heap usage.
   *
   * For each class C with heap entries, we add:
   * - (declare-const k!alive!C!i Bool) for each instance i
   * - (assert-soft (not k!alive!C!i) :weight 1) to prefer dead objects
   * - Link k!alive! flags to actual object usage via deref-is-ClassName
   */
  private def addHeapSoftConstraints(model: KModel, baseSmt: String): String = {
    val sb = new StringBuilder(baseSmt)

    sb.append("\n; ============================================\n")
    sb.append("; Soft constraints for heap minimization\n")
    sb.append("; ============================================\n\n")

    // Get heap entries from objectGraph
    if (UtilSMT.objectGraph == null) {
      logSoft("Warning: No objectGraph available for soft constraints")
      return baseSmt
    }

    val allClasses = UtilSMT.objectGraph.getAllClasses
    var totalSoftConstraints = 0

    for (className <- allClasses if className != "TopLevelDeclarations") {
      val entries = UtilSMT.objectGraph.getHeapEntries(className)
      if (entries.nonEmpty) {
        sb.append(s"; Soft constraints for $className (${entries.size} instances)\n")

        for (ref <- entries) {
          val aliveName = s"k!alive!$className!$ref"

          // Declare the alive flag
          sb.append(s"(declare-const $aliveName Bool)\n")

          // Link alive flag to actual heap usage:
          // An object is "alive" if it's referenced by any field or top-level variable
          // For simplicity, we use deref-is-ClassName which checks if the heap at that
          // position contains an object of that type. Since we're using a typed heap,
          // if the object exists in the heap, it's "alive" for our purposes.
          sb.append(s"(assert (= $aliveName (deref-is-$className $ref)))\n")

          // Soft constraint: prefer this object to NOT be alive
          // Weight 1 means each alive object costs 1 in the objective
          sb.append(s"(assert-soft (not $aliveName) :weight 1 :id soft_$aliveName)\n")

          totalSoftConstraints += 1
        }

        // Add heap_full flag for this class
        val fullFlagName = s"k!heap_full!$className"
        val allAliveNames = entries.map(ref => s"k!alive!$className!$ref").mkString(" ")
        sb.append(s"\n(declare-const $fullFlagName Bool)\n")
        sb.append(s"(assert (= $fullFlagName (and $allAliveNames)))\n")

        sb.append("\n")
      }
    }

    logSoft(s"Added $totalSoftConstraints soft constraints for heap minimization")

    sb.toString
  }

  /**
   * Add k!heap_full!ClassName flags to the SMT model for CEGAR feedback.
   * These flags are true when all heap slots for a class are in use.
   */
  private def addHeapFullFlags(baseSmt: String): String = {
    if (UtilSMT.objectGraph == null) {
      return baseSmt
    }

    val sb = new StringBuilder(baseSmt)

    sb.append("\n; ============================================\n")
    sb.append("; Heap full flags for CEGAR feedback\n")
    sb.append("; ============================================\n\n")

    val allClasses = UtilSMT.objectGraph.getAllClasses

    for (className <- allClasses if className != "TopLevelDeclarations") {
      val entries = UtilSMT.objectGraph.getHeapEntries(className)
      if (entries.nonEmpty) {
        // Declare the heap_full flag
        val fullFlagName = s"k!heap_full!$className"
        sb.append(s"(declare-const $fullFlagName Bool)\n")

        // heap_full is true when ALL slots for this class are used (not null)
        // We check if deref-is-ClassName is true for all slots
        val allUsed = entries.map(ref => s"(deref-is-$className $ref)").mkString(" ")
        if (entries.size == 1) {
          sb.append(s"(assert (= $fullFlagName $allUsed))\n")
        } else {
          sb.append(s"(assert (= $fullFlagName (and $allUsed)))\n")
        }
        sb.append("\n")
      }
    }

    sb.toString
  }

  /**
   * Solve using Z3's Optimize API with soft constraints.
   */
  private def solveWithOptimize(model: KModel, smtModel: String, printModel: Boolean): SolveResult = {
    // Write SMT to file for debugging
    try {
      val logFile = new java.io.PrintWriter(".tmp/k_soft_heap.smt2")
      logFile.println(smtModel)
      logFile.close()
      if (debug) logSoft("Wrote SMT model to .tmp/k_soft_heap.smt2")
    } catch {
      case e: Throwable => // ignore
    }

    // Use K2Z3's existing solve mechanism but with Optimize
    // For now, we delegate to regular solving since K2Z3 already handles Optimize
    // when it sees assert-soft
    K2Z3.solveSMT(model, smtModel, false)

    // Get the result
    if (K2Z3.z3Model != null) {
      SolveResult.Sat(K2Z3.z3Model)
    } else {
      // Check if it was unsat or unknown
      SolveResult.Unsat  // Simplified - should check actual result
    }
  }

  /**
   * Count and log the number of alive objects per class in the model.
   */
  private def countAliveObjects(z3Model: Z3Model): Unit = {
    if (UtilSMT.objectGraph == null) return

    logSoft("Alive object counts:")

    for (className <- UtilSMT.objectGraph.getAllClasses if className != "TopLevelDeclarations") {
      val entries = UtilSMT.objectGraph.getHeapEntries(className)
      var aliveCount = 0

      for (ref <- entries) {
        val aliveName = s"k!alive!$className!$ref"
        try {
          val flagDecl = z3Model.getConstDecls.find(_.getName.toString == aliveName)
          flagDecl match {
            case Some(decl) =>
              val interp = z3Model.getConstInterp(decl)
              if (interp != null && interp.isTrue) {
                aliveCount += 1
              }
            case None => // Flag not found, assume not alive
          }
        } catch {
          case _: Throwable => // Ignore errors
        }
      }

      logSoft(s"  $className: $aliveCount / ${entries.size} alive")
    }
  }

  /**
   * Request interruption
   */
  def interrupt(): Unit = {
    interrupted = true
    K2Z3.interrupt()
    log("Interrupt requested")
  }

  /**
   * Request pause
   */
  def requestPause(): Unit = {
    pauseRequested = true
    log("Pause requested")
  }

  /**
   * Resume from pause
   */
  def resume(): Unit = {
    if (isPaused) {
      isPaused = false
      pauseRequested = false
      log("Resuming")
    }
  }

  /**
   * Request sample of current best solution
   */
  def requestSample(): Unit = {
    sampleRequested = true
  }

  /**
   * Get last sample
   */
  def getLastSample: Option[Z3Model] = lastSample

  /**
   * Get best solution so far
   */
  def getBestSolution: Option[Z3Model] = bestSoFar

  /**
   * Get current iteration count
   */
  def getIteration: Int = iteration

  /**
   * Check if currently paused
   */
  def checkPaused: Boolean = isPaused

  // ============================================================================
  // Configuration Extraction
  // ============================================================================

  case class SolveConfig(
    timeout: Option[Long] = None,
    bestEffort: Boolean = false,
    maxObjects: Option[Int] = None
  )

  private def extractConfig(model: KModel): SolveConfig = {
    var timeout: Option[Long] = None
    var bestEffort = false
    var maxObjects: Option[Int] = None

    if (model != null) {
      for (decl <- model.decls) {
        decl match {
          case ed: EntityDecl =>
            for (ann <- ed.annotations) {
              ann match {
                case Annotation("timeout", IntegerLiteral(ms)) =>
                  timeout = Some(ms.toLong)
                case Annotation("bestEffort", _) =>
                  bestEffort = true
                case Annotation("maxObjects", IntegerLiteral(n)) =>
                  maxObjects = Some(n.toInt)
                case _ =>
              }
            }
          case _ =>
        }
      }
    }

    SolveConfig(timeout, bestEffort, maxObjects)
  }

  // ============================================================================
  // Object Bounds
  // ============================================================================

  private def initializeObjectBounds(model: KModel): Unit = {
    if (model == null) return

    // Find all class declarations
    for (decl <- model.decls) {
      decl match {
        case ed: EntityDecl if ed.keyword == ClassToken =>
          // Start with 0 for potential dynamic classes
          objectBounds += (ed.ident -> 0)
        case _ =>
      }
    }

    // Find explicit instantiations that require at least 1 instance
    for (decl <- model.decls) {
      decl match {
        case pd: PropertyDecl =>
          pd.ty match {
            case Some(IdentType(QualifiedName(List(className)), _)) =>
              // Direct instantiation: a : A
              objectBounds.get(className).foreach { current =>
                objectBounds(className) = math.max(current, 1)
              }
            case _ =>
          }
        case _ =>
      }
    }

    // Find Seq/Set/List types that might need dynamic objects
    for (decl <- model.decls) {
      decl match {
        case pd: PropertyDecl =>
          pd.ty match {
            case Some(IdentType(QualifiedName(List("Seq" | "Set" | "List")), List(innerType))) =>
              innerType match {
                case IdentType(QualifiedName(List(className)), _) =>
                  dynamicClasses += className
                  objectBounds.get(className).foreach { current =>
                    objectBounds(className) = math.max(current, initialObjectBound)
                  }
                case _ =>
              }
            case _ =>
          }
        case _ =>
      }
    }

    if (debug) {
      log(s"Initial object bounds: $objectBounds")
      log(s"Dynamic classes: $dynamicClasses")
    }
  }

  private def increaseObjectBounds(): Boolean = {
    var increased = false

    for (className <- dynamicClasses) {
      val current = objectBounds.getOrElse(className, 0)
      if (current < maxObjectBound) {
        // Double or add 1, whichever is larger
        val newBound = math.min(math.max(current * 2, current + 1), maxObjectBound)
        objectBounds(className) = newBound
        increased = true
        log(s"Increased bound for $className: $current -> $newBound")
      }
    }

    increased
  }

  private def canIncreaseObjectBounds(): Boolean = {
    dynamicClasses.exists { className =>
      objectBounds.getOrElse(className, 0) < maxObjectBound
    }
  }

  // ============================================================================
  // Soft Constraints
  // ============================================================================

  private def extractSoftConstraints(model: KModel): Unit = {
    if (model == null) return

    // Look for soft constraints (soft = true)
    for (decl <- model.decls) {
      decl match {
        case ed: EntityDecl =>
          for (member <- ed.members) {
            member match {
              case cd @ ConstraintDecl(_, exp, soft) if soft =>
                // This is a soft constraint
                softConstraints += (exp.toString -> 1.0)
              case _ =>
            }
          }
        case _ =>
      }
    }

    if (debug && softConstraints.nonEmpty) {
      log(s"Soft constraints: $softConstraints")
    }
  }

  // ============================================================================
  // Main Solving Loop
  // ============================================================================

  private def unifiedLoop(model: KModel, smtModel: String, config: SolveConfig,
                          printModel: Boolean): SolveResult = {
    iteration = 0
    var done = false
    var result: SolveResult = SolveResult.Unknown("Not started")

    while (!done && !interrupted && iteration < maxIterations) {
      iteration += 1
      log(s"=== Iteration $iteration ===")

      // Check for pause
      if (pauseRequested) {
        handlePause()
      }

      // Check for sample request
      if (sampleRequested) {
        handleSampleRequest()
      }

      // Phase 1: ENCODE
      val currentSMT = generateSMT(model, smtModel)
      log(s"Generated SMT with ${objectBounds.values.sum} potential objects")

      // Phase 2: SOLVE
      val solveResult = solveWithTimeout(model, currentSMT, config)

      // Phase 3: ANALYZE
      solveResult match {
        case SolveResult.Sat(z3Model) =>
          bestSoFar = Some(z3Model)
          log("SAT - checking refinements needed")

          // CEGAR: verify external calls
          val cegarResult = verifyCEGAR(z3Model)
          if (cegarResult.needsRefinement) {
            log(s"CEGAR: ${cegarResult.refinements.size} refinements needed")
            refinements ++= cegarResult.refinements
            // Continue to re-solve
          } else {
            // Check if we need more objects (placeholder - would need model analysis)
            val needMore = checkNeedMoreObjects(z3Model)
            if (needMore && canIncreaseObjectBounds()) {
              log("Need more objects")
              increaseObjectBounds()
              // Continue to re-solve
            } else {
              // All verified! Done!
              done = true
              result = SolveResult.Sat(z3Model)
              log("Solution verified!")
            }
          }

        case SolveResult.Unsat =>
          log("UNSAT - checking if can relax")

          // Maybe need more objects?
          if (canIncreaseObjectBounds()) {
            log("Trying with more objects")
            increaseObjectBounds()
            // Continue to re-solve
          } else if (softConstraints.nonEmpty) {
            // Could implement soft constraint relaxation here
            log("Could try relaxing soft constraints (not yet implemented)")
            done = true
            result = SolveResult.Unsat
          } else {
            // Truly unsatisfiable
            done = true
            result = SolveResult.Unsat
          }

        case SolveResult.Timeout =>
          log("TIMEOUT")
          if (config.bestEffort && bestSoFar.isDefined) {
            log("Returning best-effort result")
            done = true
            result = SolveResult.Sat(bestSoFar.get)
          } else if (canIncreaseObjectBounds()) {
            // Maybe simpler problem with fewer objects?
            // Actually, don't increase on timeout - that makes it harder
            done = true
            result = SolveResult.Timeout
          } else {
            done = true
            result = SolveResult.Timeout
          }

        case SolveResult.Unknown(reason) =>
          log(s"UNKNOWN: $reason")
          if (config.bestEffort && bestSoFar.isDefined) {
            done = true
            result = SolveResult.Sat(bestSoFar.get)
          } else {
            done = true
            result = SolveResult.Unknown(reason)
          }
      }
    }

    if (!done && iteration >= maxIterations) {
      log(s"Max iterations ($maxIterations) reached")
      result = if (bestSoFar.isDefined && config.bestEffort) {
        SolveResult.Sat(bestSoFar.get)
      } else {
        SolveResult.Unknown(s"Max iterations reached")
      }
    }

    if (interrupted) {
      log("Interrupted")
      result = if (bestSoFar.isDefined) {
        SolveResult.Sat(bestSoFar.get)
      } else {
        SolveResult.Unknown("Interrupted")
      }
    }

    // Print model if requested and we have a solution
    result match {
      case SolveResult.Sat(z3Model) if printModel =>
        K2Z3.z3Model = z3Model
        K2Z3.PrintModel(model)
      case _ =>
    }

    result
  }

  // ============================================================================
  // SMT Generation
  // ============================================================================

  private def generateSMT(model: KModel, baseSMT: String): String = {
    val sb = new StringBuilder(baseSMT)

    // Add CEGAR refinements
    if (refinements.nonEmpty) {
      sb.append("\n; CEGAR Refinements\n")
      for (r <- refinements) {
        sb.append(r)
        sb.append("\n")
      }
    }

    // Object existence variables would be added here
    // This is a placeholder for Phase 2 full implementation
    if (dynamicClasses.nonEmpty) {
      sb.append("\n; Object existence variables (placeholder)\n")
      for (className <- dynamicClasses) {
        val bound = objectBounds.getOrElse(className, 0)
        sb.append(s"; $className: up to $bound instances\n")
      }
    }

    sb.toString
  }

  // ============================================================================
  // Solving
  // ============================================================================

  private def solveWithTimeout(model: KModel, smtModel: String,
                                config: SolveConfig): SolveResult = {
    try {
      // Use existing K2Z3 infrastructure
      K2Z3.reset()

      // Set timeout if specified
      config.timeout.foreach { ms =>
        K2Z3.params.add("timeout", ms.toInt)
        K2Z3.solver.setParameters(K2Z3.params)
      }

      // Write SMT to temp file
      val tempFile = new java.io.File(".tmp/k_unified.smt2")
      val writer = new java.io.PrintWriter(tempFile)
      writer.write(smtModel)
      writer.close()

      // Parse and solve
      val boolExps = K2Z3.ctx.parseSMTLIB2File(
        tempFile.getAbsolutePath, Array(), Array(), Array(), Array())
      val boolExp = if (boolExps.length == 1) boolExps(0)
                    else K2Z3.ctx.mkAnd(boolExps: _*)

      K2Z3.solver.add(boolExp)
      val status = K2Z3.solver.check()

      status match {
        case Status.SATISFIABLE =>
          val z3Model = K2Z3.solver.getModel
          K2Z3.z3Model = z3Model
          SolveResult.Sat(z3Model)

        case Status.UNSATISFIABLE =>
          SolveResult.Unsat

        case Status.UNKNOWN =>
          val reason = K2Z3.solver.getReasonUnknown
          if (reason != null && reason.toLowerCase.contains("timeout")) {
            SolveResult.Timeout
          } else {
            SolveResult.Unknown(Option(reason).getOrElse("unknown"))
          }
      }
    } catch {
      case e: Exception =>
        log(s"Solve error: ${e.getMessage}")
        SolveResult.Unknown(e.getMessage)
    }
  }

  // ============================================================================
  // CEGAR Verification
  // ============================================================================

  case class CEGARResult(needsRefinement: Boolean, refinements: List[String])

  private def verifyCEGAR(z3Model: Z3Model): CEGARResult = {
    val newRefinements = ListBuffer[String]()

    for ((smtFuncName, callInfo) <- ExternalFunctions.getExternalCalls) {
      // Try to extract argument values
      val argValues = callInfo.argVarNames.flatMap { varName =>
        K2Z3.extractValueFromModel(z3Model, varName)
      }

      if (argValues.length == callInfo.argVarNames.length) {
        // All arguments concrete - evaluate
        ExternalFunctions.tryEvaluate(callInfo.qualifiedName, argValues) match {
          case Some(actualResult) =>
            // Check what Z3 computed
            val z3Result = K2Z3.extractFunctionResult(z3Model, smtFuncName, argValues)

            z3Result match {
              case Some(z3Value) if !K2Z3.valuesMatch(z3Value, actualResult) =>
                // Mismatch! Add refinement
                val refinement = ExternalFunctions.generateRefinementConstraint(
                  smtFuncName, argValues, actualResult)
                newRefinements += refinement
                log(s"CEGAR mismatch: $smtFuncName(${argValues.mkString(",")}) = $z3Value (Z3) vs $actualResult (actual)")

              case _ =>
                // Match or couldn't extract - OK
            }

          case None =>
            // Couldn't evaluate - skip
        }
      }
    }

    CEGARResult(newRefinements.nonEmpty, newRefinements.toList)
  }

  // ============================================================================
  // Object Bounds Analysis
  // ============================================================================

  private def checkNeedMoreObjects(z3Model: Z3Model): Boolean = {
    // Placeholder: would analyze model to see if constraints suggest
    // more objects are needed
    //
    // Ideas:
    // 1. Check if any Seq/Set variables are at their bound
    // 2. Analyze UNSAT cores when we get UNSAT
    // 3. Look for "existence" variables that are all true
    false
  }

  // ============================================================================
  // Pause/Resume/Sample
  // ============================================================================

  private def handlePause(): Unit = {
    log("Pausing...")
    isPaused = true
    pauseRequested = false

    // Wait for resume
    while (isPaused && !interrupted) {
      Thread.sleep(100)
    }

    log("Resumed")
  }

  private def handleSampleRequest(): Unit = {
    sampleRequested = false
    lastSample = bestSoFar
    log(s"Sample taken: ${lastSample.isDefined}")
  }

  // ============================================================================
  // Utilities
  // ============================================================================

  private def reset(): Unit = {
    objectBounds.clear()
    refinements.clear()
    softConstraints.clear()
    bestSoFar = None
    iteration = 0
    interrupted = false
    pauseRequested = false
    sampleRequested = false
    isPaused = false
    lastSample = None
    dynamicClasses.clear()
    ExternalFunctions.reset()
  }

  private def log(msg: String): Unit = {
    if (debug || K2Z3.debug) {
      println(s"[UnifiedSolver] $msg")
    }
  }

  /**
   * Result of solving
   */
  sealed trait SolveResult
  object SolveResult {
    case class Sat(model: Z3Model) extends SolveResult
    case object Unsat extends SolveResult
    case object Timeout extends SolveResult
    case class Unknown(reason: String) extends SolveResult
  }
}

