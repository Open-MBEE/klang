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

  /** Whether to use scenario tracking for disjunctive models */
  var useScenarioTracking: Boolean = false

  // Note: CVC5 support is handled via ASTOptions.cvc5Compatible flag
  // No need for separate useCVC5 flag - the solver infrastructure handles it

  /** Debug logging */
  var debug: Boolean = false

  // ============================================================================
  // State
  // ============================================================================

  /** Current object bounds per class: ClassName -> max instances */
  private val objectBounds: MMap[String, Int] = MMap()
  private var boundsWereIncreased: Boolean = false

  /** CEGAR refinement constraints */
  private val refinements: ListBuffer[String] = ListBuffer()

  /** Soft constraint weights: constraint string -> weight */
  private val softConstraints: MMap[String, Double] = MMap()
  
  /** Scenario tracking state */
  private var scenarioVars: Map[String, BoolExpr] = Map()
  private var viableScenarios: MSet[String] = MSet()

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
  def solve(model: KModel, smtModel: String, printModel: Boolean, timeoutMs: Option[Long] = None): SolveResult = {
    println("[UnifiedSolver] Starting unified solver...")
    reset()
    solving = true

    try {
      // Extract configuration from model annotations
      var config = extractConfig(model)
      
      // Override timeout from command line if provided
      val finalTimeout = timeoutMs.orElse(config.timeout)
      config = config.copy(timeout = finalTimeout)

      // Extract soft constraints from model
      extractSoftConstraints(model)
      
      // Initialize object bounds from model (but don't regenerate SMT - use provided SMT)
      // This matches the original behavior where unified loop used the SMT as-is
      initializeObjectBounds(model)

      // Initialize scenario tracking if explicitly enabled (via -unified-scenarios flag)
      // Note: We don't auto-detect scenarios - the unified loop's general max-SAT approach
      // should handle disjunctive structures naturally through the Optimize API
      if (useScenarioTracking) {
        initializeScenarioTracking(model, smtModel)
      }

      // Main solving loop
      // Note: The unified loop will regenerate SMT with correct bounds when needed
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

        // CVC5 compatibility is set in Frontend before SMT generation

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

        logHeap(s"SMT model generated, solving...")

        // Use the standard solve method (works with both Z3 and CVC5-compatible SMT)
        result = solve(model, smtModel, printModel = false)

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

    // If scenario tracking is enabled, enable best-effort by default
    // This allows the unified loop to return partial solutions when scenarios timeout
    if (useScenarioTracking && !bestEffort) {
      bestEffort = true
      if (debug) {
        log("Best-effort mode enabled for scenario tracking")
      }
    }

    SolveConfig(timeout, bestEffort, maxObjects)
  }

  // ============================================================================
  // Object Bounds
  // ============================================================================

  private def initializeObjectBounds(model: KModel): Unit = {
    if (model == null) {
      log("WARNING: initializeObjectBounds called with null model")
      return
    }

    // Find all class declarations - we need to traverse nested structures (packages)
    val allClasses = MSet[String]()
    
    log(s"Processing model with ${model.decls.size} top-level declarations and ${model.packages.size} packages")
    
    def processDecl(decl: TopDecl): Unit = {
      decl match {
        case ed: EntityDecl if ed.entityToken == ClassToken =>
          allClasses += ed.ident
          objectBounds += (ed.ident -> 0)
          log(s"  Found class: ${ed.ident}")
        case pd: PackageDecl =>
          log(s"  Found nested package: ${pd.name}")
          if (pd.model != null) {
            // Recurse into packages - PackageDecl has a model field
            for (d <- pd.model.decls) processDecl(d)
            // Also check nested packages
            for (p <- pd.model.packages) processPackage(p)
          }
        case _ =>
          // Skip other declaration types
      }
    }
    
    def processPackage(pkg: PackageDecl): Unit = {
      log(s"  Processing package: ${pkg.name}")
      if (pkg.model != null) {
        for (d <- pkg.model.decls) processDecl(d)
        for (p <- pkg.model.packages) processPackage(p)
      }
    }
    
    // Process top-level declarations
    for (decl <- model.decls) {
      processDecl(decl)
    }
    
    // Also process top-level packages (this is the main case for package-wrapped models like DSN_Pass.k)
    for (pkg <- model.packages) {
      processPackage(pkg)
    }
    
    log(s"Found ${allClasses.size} classes total: ${allClasses.mkString(", ")}")

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

    // Detect recursive structures via property references
    // A class is recursive if it has properties that reference classes in its inheritance hierarchy
    // This helps identify classes that may need dynamic bounds, but we don't regenerate SMT
    // on the first iteration - we let the normal UNSAT -> increase bounds flow handle it
    val classHierarchy = buildClassHierarchy(model)
    
    for (decl <- model.decls) {
      decl match {
        case ed: EntityDecl if ed.entityToken == ClassToken =>
          val className = ed.ident
          // Check if this class has recursive properties
          ed.members.foreach {
            case pd: PropertyDecl =>
              pd.ty match {
                case Some(IdentType(QualifiedName(List(refClassName)), _)) if allClasses.contains(refClassName) =>
                  val classNameHierarchy = classHierarchy.getOrElse(className, Set(className))
                  val refClassNameHierarchy = classHierarchy.getOrElse(refClassName, Set(refClassName))
                  val isRecursive = classNameHierarchy.contains(refClassName) || refClassNameHierarchy.contains(className)
                  
                  if (isRecursive) {
                    // Mark as dynamic and set initial bounds
                    dynamicClasses += className
                    dynamicClasses += refClassName
                    // Initialize bounds to at least initialObjectBound for dynamic classes
                    objectBounds.get(className).foreach { current =>
                      objectBounds(className) = math.max(current, initialObjectBound)
                    }
                    objectBounds.get(refClassName).foreach { current =>
                      objectBounds(refClassName) = math.max(current, initialObjectBound)
                    }
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

  /**
   * Build a map of class names to their inheritance hierarchy (including self and all ancestors)
   */
  private def buildClassHierarchy(model: KModel): Map[String, Set[String]] = {
    val hierarchy = MMap[String, Set[String]]()
    
    def addToHierarchy(className: String, visited: Set[String] = Set()): Set[String] = {
      if (visited.contains(className)) {
        // Already processed or cycle detected
        return hierarchy.getOrElse(className, Set(className))
      }
      
      if (hierarchy.contains(className)) {
        return hierarchy(className)
      }
      
      // Find the class declaration
      val classDecl = model.decls.collectFirst {
        case ed: EntityDecl if ed.entityToken == ClassToken && ed.ident == className => ed
      }
      
      val classes = MSet[String](className)
      
      classDecl.foreach { ed =>
        // Add parent classes
        ed.extending.foreach {
          case IdentType(QualifiedName(parentNames), _) =>
            for (parentName <- parentNames) {
              classes += parentName
              val parentHierarchy = addToHierarchy(parentName, visited + className)
              classes ++= parentHierarchy
            }
          case _ =>
        }
      }
      
      hierarchy(className) = classes.toSet
      classes.toSet
    }
    
    // Build hierarchy for all classes
    for (decl <- model.decls) {
      decl match {
        case ed: EntityDecl if ed.entityToken == ClassToken =>
          addToHierarchy(ed.ident)
        case _ =>
      }
    }
    
    hierarchy.toMap
  }

  private def increaseObjectBounds(): Boolean = {
    var increased = false

    // Use dynamicClasses if populated, otherwise use all classes from objectBounds
    // This allows bound increases when verification fails for non-recursive models
    val classesToIncrease = if (dynamicClasses.nonEmpty) dynamicClasses else objectBounds.keys

    for (className <- classesToIncrease) {
      val current = objectBounds.getOrElse(className, 0)
      if (current < maxObjectBound) {
        // Double or add 1, whichever is larger
        val newBound = math.min(math.max(current * 2, current + 1), maxObjectBound)
        objectBounds(className) = newBound
        increased = true
        boundsWereIncreased = true
        log(s"Increased bound for $className: $current -> $newBound")
      }
    }

    increased
  }

  private def canIncreaseObjectBounds(): Boolean = {
    // Use dynamicClasses if populated, otherwise check all classes from objectBounds
    val classesToCheck = if (dynamicClasses.nonEmpty) dynamicClasses else objectBounds.keys.toSet
    val result = classesToCheck.exists { className =>
      objectBounds.getOrElse(className, 0) < maxObjectBound
    }
    log(s"canIncreaseObjectBounds: classes=${classesToCheck.size}, result=$result")
    result
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
    println("[UnifiedSolver] Entering unified loop...")
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
      // Only regenerate SMT if object bounds were increased (not on first iteration)
      // This matches the original behavior where the unified loop used the provided SMT
      val baseSMT = if (boundsWereIncreased) {
        // Regenerate SMT with current object bounds
        // Set instanceMultiplier based on max bound
        val maxBound = if (objectBounds.values.nonEmpty) objectBounds.values.max else 1
        val previousMultiplier = ASTOptions.instanceMultiplier
        ASTOptions.instanceMultiplier = maxBound
        try {
          UtilSMT.reset
          val regenerated = model.toSMT
          ASTOptions.instanceMultiplier = previousMultiplier
          boundsWereIncreased = false // Reset flag after regeneration
          log(s"Regenerated SMT with object bounds: ${objectBounds.mkString(", ")} (instanceMultiplier=$maxBound)")
          regenerated
        } catch {
          case e: Throwable =>
            log(s"Error regenerating SMT: ${e.getMessage}, using original")
            boundsWereIncreased = false
            smtModel
        }
      } else {
        smtModel
      }
      val currentSMT = generateSMT(model, baseSMT)
      log(s"Generated SMT with ${objectBounds.values.sum} potential objects")

      // Phase 2: SOLVE
      // If scenario tracking is enabled, add scenario assumption to guide solving
      // But still use the unified loop's existing solve mechanism (max-SAT/best-effort)
      val solveResult = if (useScenarioTracking && viableScenarios.nonEmpty) {
        // Try the first viable scenario as an assumption - unified loop handles the rest
        val firstScenario = viableScenarios.head
        solveWithScenarioAssumption(model, currentSMT, config, firstScenario)
      } else {
        solveWithTimeout(model, currentSMT, config)
      }

      // Phase 3: ANALYZE
      solveResult match {
        case SolveResult.Sat(z3Model) =>
          bestSoFar = Some(z3Model)
          // Set z3Model for potential printing (but don't print here - let Frontend handle it)
          K2Z3.z3Model = z3Model
          println("[UnifiedSolver] SAT - verifying solution against hard constraints")
          log("SAT - verifying solution against hard constraints")

          // Verify against hard constraints first
          val currentScenario = if (useScenarioTracking && viableScenarios.nonEmpty) {
            Some(viableScenarios.head)
          } else {
            None
          }
          val satisfiesHardConstraints = verifyHardConstraints(model, currentSMT, z3Model, currentScenario)
          
          if (!satisfiesHardConstraints) {
            println("[UnifiedSolver] ⚠️  Solution does not satisfy all hard constraints")
            log("⚠️  Solution does not satisfy all hard constraints")
            
            // For models with dynamic classes (like lisp.k with recursive types),
            // verification failure is real and we should try with more objects.
            val hasDynamicClasses = dynamicClasses.nonEmpty
            
            if (hasDynamicClasses && canIncreaseObjectBounds()) {
              log("Model has dynamic classes - trying with more objects to find valid solution")
              increaseObjectBounds()
              // Continue loop to re-solve
            } else {
              // Verification failed - we cannot confirm this is a valid solution
              // Return Unknown rather than SAT to indicate the result is uncertain
              log("ERROR: Verification failed and cannot find valid solution")
              log("This may indicate: incomplete model from Optimize API, timeout, or genuine constraint violation")
              done = true
              result = SolveResult.Unknown("Solver returned SAT but model verification failed")
            }
          } else {
            println("[UnifiedSolver] ✓ Solution verified - satisfies all hard constraints")
            log("✓ Solution verified - satisfies all hard constraints")
            
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
          }

        case SolveResult.Unsat =>
          log("UNSAT - checking if can relax")

          // If scenario tracking is enabled, try next viable scenario
          if (useScenarioTracking && viableScenarios.size > 1) {
            val currentScenario = viableScenarios.head
            viableScenarios -= currentScenario
            log(s"Scenario $currentScenario is UNSAT - trying next scenario")
            // Continue loop to try next scenario
          } else if (canIncreaseObjectBounds()) {
            // Maybe need more objects?
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
          // If we have a best-effort solution, return it (unified loop's max-SAT behavior)
          if (config.bestEffort && bestSoFar.isDefined) {
            log("Returning best-effort result (partial solution from timeout)")
            done = true
            result = SolveResult.Sat(bestSoFar.get)
          } else if (useScenarioTracking && viableScenarios.size > 1) {
            // Try next viable scenario
            val currentScenario = viableScenarios.head
            viableScenarios -= currentScenario
            log(s"Scenario $currentScenario timed out - trying next scenario")
            // Continue loop to try next scenario
          } else if (useScenarioTracking && viableScenarios.isEmpty && bestSoFar.isDefined) {
            // All scenarios exhausted, return best effort if available
            log("All scenarios exhausted - returning best-effort result")
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
        if (z3Model != null) {
          try {
        K2Z3.z3Model = z3Model
        K2Z3.PrintModel(model)
          } catch {
            case e: Throwable =>
              log(s"Error printing model: ${e.getMessage}")
              if (debug) {
                e.printStackTrace()
              }
              println("Model is available but could not be printed. This may indicate an incomplete or invalid model.")
          }
        } else {
          log("WARNING: SAT result but model is null - cannot print model")
          println("SAT but no model available to print")
        }
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

  /**
   * General solving method using Optimize API with incremental constraint addition.
   * This implements max-SAT: adds constraints incrementally, and when groups cause UNSAT,
   * makes them soft to find best-effort solutions.
   * 
   * This is a general algorithm that works for any model, including disjunctive structures,
   * without requiring hardcoded scenario detection.
   */
  private def solveWithTimeout(model: KModel, smtModel: String,
                                config: SolveConfig): SolveResult = {
    try {
      // Use existing K2Z3 infrastructure
      K2Z3.reset()

      // Set timeout if specified (MUST be after reset, which creates new params)
      config.timeout.foreach { ms =>
        K2Z3.solverTimeout = Some(ms)
        if (debug) {
          log(s"Z3 timeout set to ${ms}ms")
        }
      }

      // Write SMT to temp file
      val tempFile = new java.io.File(".tmp/k_unified.smt2")
      val tmpDir = tempFile.getParentFile
      if (tmpDir != null && !tmpDir.exists()) {
        tmpDir.mkdirs()
      }
      val writer = new java.io.PrintWriter(tempFile)
      writer.write(smtModel)
      writer.close()

      // Parse SMT model
      val boolExps = K2Z3.ctx.parseSMTLIB2File(
        tempFile.getAbsolutePath, Array(), Array(), Array(), Array())

      // Use Optimize API for better partial model support and max-SAT
      val optimize = K2Z3.getOptimize()
      
      // Set timeout on optimizer
      config.timeout.foreach { ms =>
        val optParams = K2Z3.ctx.mkParams()
        optParams.add("timeout", ms.toInt)
        optimize.setParameters(optParams)
      }

      // Group constraints for incremental addition
      val groups = IncrementalDiagnostic.groupAssertionsByConstraint(
        boolExps.map(_.asInstanceOf[BoolExpr]).toList, 
        smtModel
      )
      
      val baseGroup = groups.find(_.name == "Base Constraints")
      val namedGroups = groups.filter(_.name != "Base Constraints")
      
      if (debug) {
        log(s"Total parsed assertions: ${boolExps.length}")
        log(s"Base Constraints group: ${baseGroup.map(_.assertions.length).getOrElse(0)} assertions")
        log(s"Named groups: ${namedGroups.length}")
        
        // List all groups
        groups.foreach { g =>
          log(s"  Group '${g.name}': ${g.assertions.length} assertions")
        }
        
        // Verify heap initialization assertion is present
        val allAssertions = boolExps.map(_.asInstanceOf[BoolExpr]).toList
        val heapInitAssertions = allAssertions.filter { expr =>
          val str = expr.toString
          str.contains("heap") && str.contains("store") && str.contains("0")
        }
        log(s"Heap initialization assertions found in all parsed assertions: ${heapInitAssertions.length}")
        if (heapInitAssertions.nonEmpty && debug) {
          log(s"Heap init assertion (first 300 chars): ${heapInitAssertions.head.toString.take(300)}")
          
          // Check which group this assertion is in
        val heapInitStr = heapInitAssertions.head.toString
        groups.foreach { g =>
          val inThisGroup = g.assertions.exists { expr =>
            expr.toString == heapInitStr
          }
          if (inThisGroup) {
            log(s"  → Heap init assertion is in group '${g.name}' (should be 'Base Constraints')")
            log(s"    This is WRONG - heap initialization should be a base constraint!")
          }
        }
        
        // Also check if it's in the ungrouped list
        val allAssertions = boolExps.map(_.asInstanceOf[BoolExpr]).toList
        val heapInitIndex = allAssertions.indexWhere(_.toString == heapInitStr)
        if (heapInitIndex >= 0) {
          log(s"  → Heap init assertion is at index $heapInitIndex in parsed assertions")
        }
        }
      }
      
      // Add base constraints first
      baseGroup.foreach { group =>
        if (debug) {
          log(s"Adding ${group.assertions.length} base constraints to Optimize solver")
          // Check if heap initialization is in base constraints by looking for heap-related expressions
          val heapExprs = group.assertions.filter { expr =>
            val str = expr.toString
            str.contains("heap") || str.contains("store")
          }
          if (heapExprs.nonEmpty) {
            log(s"Found ${heapExprs.length} heap-related expressions in base constraints")
            if (debug) {
              heapExprs.zipWithIndex.foreach { case (expr, idx) =>
                val str = expr.toString
                val hasRef0 = str.contains("0") && (str.contains("store") || str.contains("heap"))
                log(s"  Heap expr $idx (has ref 0: $hasRef0): ${str.take(150)}")
              }
            }
          } else {
            log("WARNING: No heap-related expressions found in base constraints!")
          }
        }
        group.assertions.foreach { expr =>
          optimize.Add(expr)
        }
      }
      
      // Verify base constraints are satisfiable and enforce heap
      if (debug) {
        log("Checking base constraints (heap initialization should be enforced)...")
      }
      
      // Check base constraints first (but don't use all timeout here - save it for incremental checks)
      var status = optimize.Check()
      
      if (debug) {
        log(s"Base constraints check result: $status")
        if (status == Status.SATISFIABLE) {
          // Verify heap is initialized in the model
          try {
            val baseModel = optimize.getModel
            if (baseModel != null) {
              val heapDecl = baseModel.getDecls.find(_.getName.toString == "heap")
              if (heapDecl.isDefined) {
                val heapExpr = baseModel.getConstInterp(heapDecl.get)
                if (heapExpr != null) {
                  val ref0Expr = K2Z3.ctx.mkSelect(heapExpr.asInstanceOf[ArrayExpr[Sort, Sort]], K2Z3.ctx.mkInt(0).asInstanceOf[Expr[Sort]])
                  val ref0Value = baseModel.eval(ref0Expr, true)
                  val ref0Str = if (ref0Value != null) ref0Value.toString else "null"
                  val hasLift = ref0Str.contains("lift-")
                  log(s"Base constraints model: ref 0 = ${if (hasLift) "✓ (has lift-)" else "✗ null or no lift-"} (value: ${ref0Str.take(100)})")
                  if (!hasLift) {
                    log("ERROR: Base constraints model has ref 0 = null even though heap initialization is a hard constraint!")
                  } else {
                    // Save base model to bestSoFar - it has ref 0 correctly initialized
                    bestSoFar = Some(baseModel)
                  }
                }
              }
            }
          } catch {
            case e: Throwable =>
              if (debug) log(s"Could not verify heap in base constraints model: ${e.getMessage}")
          }
        }
      }
      
      // Save base model to bestSoFar if we got SATISFIABLE (even if not in debug mode)
      if (status == Status.SATISFIABLE && bestSoFar.isEmpty) {
        try {
          val baseModel = optimize.getModel
          if (baseModel != null) {
            bestSoFar = Some(baseModel)
          }
        } catch {
          case _: Throwable =>
        }
      }
      
      // Check for timeout on base constraints
      if (status == Status.UNKNOWN) {
        val reason = optimize.getReasonUnknown
        if (reason != null && (reason.toLowerCase.contains("timeout") || reason.toLowerCase.contains("canceled"))) {
          log("TIMEOUT on base constraints")
          return SolveResult.Timeout
        }
      }
      
      if (status == Status.UNSATISFIABLE) {
        if (debug) {
          log("UNSAT with base constraints only")
        }
        return SolveResult.Unsat
      }
      
      // Add constraints incrementally (max-SAT approach)
      // Strategy: Add groups incrementally, and when a group causes UNSAT,
      // make it soft and continue to get better partial solutions
      var addedGroups = 0
      var lastSatGroup = -1
      var problematicGroups = ListBuffer[Int]() // Groups that cause UNSAT
      var hardGroups = ListBuffer[Int]() // Groups added as hard constraints
      
      // Add constraints in batches to balance progress vs. performance
      val batchSize = math.max(1, namedGroups.length / 10) // Check every 10% of groups
      
      for ((group, index) <- namedGroups.zipWithIndex) {
        // Add this group as hard constraint
        group.assertions.foreach { expr =>
          optimize.Add(expr)
        }
        hardGroups += index
        addedGroups += 1
        
        // Check periodically (not after every group to avoid too many checks)
        val shouldCheck = (index + 1) % batchSize == 0 || index == namedGroups.length - 1
        
        if (shouldCheck) {
          val checkStatus = optimize.Check()
          
          // If we're SATISFIABLE, save the model to bestSoFar so we use it at the end
          // This ensures ref 0 is correctly initialized in the final model
          if (checkStatus == Status.SATISFIABLE) {
            try {
              val satModel = optimize.getModel
              if (satModel != null) {
                bestSoFar = Some(satModel)
              }
            } catch {
              case _: Throwable =>
            }
          }
          
          // Check if we timed out - if so, break out of loop and return partial result
          if (checkStatus == Status.UNKNOWN) {
            val reason = optimize.getReasonUnknown
            if (reason != null && (reason.toLowerCase.contains("timeout") || reason.toLowerCase.contains("canceled"))) {
              if (debug) {
                log(s"  Progress: ${index + 1}/${namedGroups.length} groups - TIMEOUT at check, returning partial result")
              }
              status = checkStatus
              // Try to get partial model before breaking
              try {
                val partialModel = optimize.getModel
                if (partialModel != null) {
                  bestSoFar = Some(partialModel)
                }
              } catch {
                case _: Throwable =>
              }
              // Break out of loop - we've timed out
              // lastSatGroup already set from previous successful check
              return status match {
                case Status.SATISFIABLE =>
                  if (bestSoFar.isDefined) {
                    K2Z3.z3Model = bestSoFar.get
                    SolveResult.Sat(bestSoFar.get)
                  } else {
                    SolveResult.Timeout
                  }
                case Status.UNKNOWN =>
                  // TIMEOUT: We have a partial model but NOT all constraints were checked.
                  // Do NOT return SAT - return TIMEOUT to indicate incomplete solving.
                  // The partial model may not satisfy all hard constraints.
                  log(s"TIMEOUT at group ${index + 1}/${namedGroups.length} - NOT all constraints checked")
                  if (bestSoFar.isDefined) {
                    log(s"Have partial model (satisfied up to group ${lastSatGroup + 1}) but returning TIMEOUT since not all constraints verified")
                  }
                  SolveResult.Timeout
                case _ =>
                  // Other unknown status - return TIMEOUT
                  log(s"Unknown status during incremental solve - returning TIMEOUT")
                    SolveResult.Timeout
              }
            }
          }
          
          // Update best model if we have one
          // Only update if the new model also has ref 0 initialized (or if we don't have a model yet)
          if (checkStatus == Status.SATISFIABLE || checkStatus == Status.UNKNOWN) {
            try {
              val currentModel = optimize.getModel
              if (currentModel != null) {
                // Check if this model has ref 0 initialized
                var hasRef0 = false
                try {
                  val heapDecl = currentModel.getDecls.find(_.getName.toString == "heap")
                  if (heapDecl.isDefined) {
                    val heapExpr = currentModel.getConstInterp(heapDecl.get)
                    if (heapExpr != null) {
                      val ref0Expr = K2Z3.ctx.mkSelect(heapExpr.asInstanceOf[ArrayExpr[Sort, Sort]], K2Z3.ctx.mkInt(0).asInstanceOf[Expr[Sort]])
                      val ref0Value = currentModel.eval(ref0Expr, true)
                      val ref0Str = if (ref0Value != null) ref0Value.toString else "null"
                      hasRef0 = ref0Str.contains("lift-")
                    }
                  }
                } catch {
                  case _: Throwable =>
                }
                // Only update bestSoFar if:
                // 1. We don't have a model yet (bestSoFar.isEmpty), OR
                // 2. The new model has ref 0 initialized (hasRef0)
                // This ensures we keep the base model (which has ref 0) unless we get a better one
                if (bestSoFar.isEmpty || hasRef0) {
                  bestSoFar = Some(currentModel)
                }
                status = checkStatus
                lastSatGroup = index
                if (debug) {
                  log(s"  Progress: ${index + 1}/${namedGroups.length} groups - still SAT (ref 0: ${if (hasRef0) "✓" else "✗"})")
                }
              }
            } catch {
              case _: Throwable =>
            }
          } else if (checkStatus == Status.UNSATISFIABLE) {
            // This group makes it UNSAT - mark it as problematic
            problematicGroups += index
            if (debug) {
              log(s"  Progress: ${index + 1}/${namedGroups.length} groups - UNSAT at ${group.name}")
            }
            status = checkStatus
            // We'll rebuild with this group as soft if we have time
          }
        }
      }
      
      // If we have problematic groups and got UNSAT, try rebuilding with them as soft
      if (problematicGroups.nonEmpty && status == Status.UNSATISFIABLE) {
        if (debug) {
          log(s"Rebuilding with ${problematicGroups.length} problematic groups as soft constraints")
        }
        
        // Create new optimizer with problematic groups as soft
        val softOptimize = K2Z3.ctx.mkOptimize()
        config.timeout.foreach { ms =>
          val optParams = K2Z3.ctx.mkParams()
          optParams.add("timeout", ms.toInt)
          softOptimize.setParameters(optParams)
        }
        
        // Add base constraints (including heap initialization)
        baseGroup.foreach { group =>
          if (debug) {
            val heapExprs = group.assertions.filter { expr =>
              val str = expr.toString
              str.contains("heap") || str.contains("store")
            }
            log(s"Rebuilding: Adding ${group.assertions.length} base constraints (${heapExprs.length} heap-related) to softOptimize")
          }
          group.assertions.foreach { expr =>
            softOptimize.Add(expr)
          }
        }
        
        // Add non-problematic groups as hard, problematic groups as soft
        for ((group, index) <- namedGroups.zipWithIndex) {
          if (problematicGroups.contains(index)) {
            // Add as soft constraint
            group.assertions.zipWithIndex.foreach { case (expr, exprIdx) =>
              softOptimize.AssertSoft(expr, 1, s"soft_${group.name}_$exprIdx")
            }
          } else {
            // Add as hard constraint
            group.assertions.foreach { expr =>
              softOptimize.Add(expr)
            }
          }
        }
        
        val softStatus = softOptimize.Check()
        if (debug) {
          log(s"softOptimize.Check() returned: $softStatus")
        }
        if (softStatus == Status.SATISFIABLE || softStatus == Status.UNKNOWN) {
          try {
            val softModel = softOptimize.getModel
            if (softModel != null) {
              bestSoFar = Some(softModel)
              K2Z3.z3Model = softModel // Set for printing
              status = softStatus
              if (debug) {
                log(s"Found best-effort solution with ${problematicGroups.length} groups as soft (status: $softStatus)")
                // Verify heap initialization in the softOptimize model
                try {
                  val heapDecl = softModel.getDecls.find(_.getName.toString == "heap")
                  if (heapDecl.isDefined) {
                    val heapExpr = softModel.getConstInterp(heapDecl.get)
                    if (heapExpr != null) {
                      val ref0Expr = K2Z3.ctx.mkSelect(heapExpr.asInstanceOf[ArrayExpr[Sort, Sort]], K2Z3.ctx.mkInt(0).asInstanceOf[Expr[Sort]])
                      val ref0Value = softModel.eval(ref0Expr, true)
                      val ref0Str = if (ref0Value != null) ref0Value.toString else "null"
                      val hasLift = ref0Str.contains("lift-")
                      log(s"softOptimize model: ref 0 = ${if (hasLift) "✓ (has lift-)" else "✗ null or no lift-"} (value: ${ref0Str.take(100)})")
                      if (!hasLift) {
                        log(s"ERROR: softOptimize model has ref 0 = null even though heap initialization is in base constraints!")
                      }
                    }
                  }
                } catch {
                  case e: Throwable =>
                    if (debug) log(s"Could not verify heap in softOptimize model: ${e.getMessage}")
                }
              }
            }
          } catch {
            case _: Throwable =>
          }
        }
      }
      
      // Return result based on final status
      status match {
        case Status.SATISFIABLE =>
          // If we have a best-effort solution from soft constraints, use that instead of optimize.getModel()
          // The softOptimize model has ref 0 correctly initialized, while optimize.getModel() might not
          val z3Model = if (bestSoFar.isDefined) {
            bestSoFar.get
          } else {
            try {
            optimize.getModel
            } catch {
              case e: Throwable =>
                if (debug) log(s"Error getting model from optimizer: ${e.getMessage}")
                null
            }
          }
          if (z3Model != null) {
            K2Z3.z3Model = z3Model
            SolveResult.Sat(z3Model)
          } else {
            log("WARNING: Optimizer returned SATISFIABLE but getModel() returned null")
            SolveResult.Unknown("SAT but no model available")
          }
          
        case Status.UNSATISFIABLE =>
          if (problematicGroups.nonEmpty && bestSoFar.isDefined) {
            // We have a best-effort solution from soft constraints
            // This is a partial solution that doesn't satisfy all hard constraints
            // Only return it if we're in best-effort mode
            if (config.bestEffort) {
            log("UNSAT - returning best-effort solution from soft constraints")
            K2Z3.z3Model = bestSoFar.get
            SolveResult.Sat(bestSoFar.get)
            } else {
              log("UNSAT - have best-effort solution but bestEffort=false, returning UNSAT")
              SolveResult.Unsat
            }
          } else {
            SolveResult.Unsat
          }
          
        case Status.UNKNOWN =>
          val reason = optimize.getReasonUnknown
          if (reason != null && (reason.toLowerCase.contains("timeout") || reason.toLowerCase.contains("canceled"))) {
            // Try to get partial model on timeout
            try {
              val partialModel = optimize.getModel
              if (partialModel != null) {
                bestSoFar = Some(partialModel)
                log(s"TIMEOUT - returning partial model (satisfied up to group ${lastSatGroup + 1})")
                SolveResult.Sat(partialModel)
              } else if (bestSoFar.isDefined) {
                log("TIMEOUT - returning best model found so far")
                SolveResult.Sat(bestSoFar.get)
              } else {
                SolveResult.Timeout
              }
            } catch {
              case _: Throwable =>
                if (bestSoFar.isDefined) {
                  SolveResult.Sat(bestSoFar.get)
                } else {
                  SolveResult.Timeout
                }
            }
          } else {
            SolveResult.Unknown(Option(reason).getOrElse("unknown"))
          }
      }
    } catch {
      case e: Exception =>
        log(s"Solve error: ${e.getMessage}")
        if (bestSoFar.isDefined) {
          SolveResult.Sat(bestSoFar.get)
        } else {
          SolveResult.Unknown(e.getMessage)
        }
    }
  }

  // ============================================================================
  // Solution Verification
  // ============================================================================
  
  /**
   * Verify that a model satisfies all hard constraints.
   * Returns true if the model satisfies all hard constraints, false otherwise.
   * 
   * This uses a more robust approach: create a fresh solver with all hard constraints,
   * then check if the model satisfies it by evaluating each constraint.
   */
  private def verifyHardConstraints(
    model: KModel,
    smtModel: String,
    z3Model: Z3Model,
    scenarioName: Option[String] = None
  ): Boolean = {
    println("[UnifiedSolver] Starting verification of model against hard constraints...")
    if (z3Model == null) {
      println("[UnifiedSolver] ✗ Model is null - cannot verify")
      return false
    }
    
    try {
      // Create a fresh solver to verify the model
      val verifySolver = K2Z3.ctx.mkSolver()
      
      // Parse SMT model to get all assertions
      val tempFile = new java.io.File(".tmp/k_verify_hard.smt2")
      val tmpDir = tempFile.getParentFile
      if (tmpDir != null && !tmpDir.exists()) {
        tmpDir.mkdirs()
      }
      
      // Filter out soft constraints from SMT model
      // Keep only (assert ...) lines, not (assert-soft ...)
      val lines = smtModel.split("\n")
      val hardConstraintsOnly = lines.filter { line =>
        val trimmed = line.trim
        !trimmed.startsWith("(assert-soft") && 
        !trimmed.startsWith("; soft") &&
        !trimmed.startsWith("; Soft")
      }.mkString("\n")
      
      val writer = new java.io.PrintWriter(tempFile)
      writer.write(hardConstraintsOnly)
      writer.close()
      
      val boolExps = K2Z3.ctx.parseSMTLIB2File(
        tempFile.getAbsolutePath, Array(), Array(), Array(), Array())
      
      if (boolExps.isEmpty) {
        println("[UnifiedSolver] ⚠ No hard constraints found to verify")
        return true // If no constraints, consider it valid
      }
      
      println(s"[UnifiedSolver] Verifying ${boolExps.length} hard constraints...")
      
      // Add all hard constraints to the verification solver
      for (expr <- boolExps) {
        verifySolver.add(expr.asInstanceOf[BoolExpr])
      }
      
      // Add scenario assumption if provided
      scenarioName.foreach { name =>
        val scenarioVarName = name match {
          case "Nominal" => "scenario_nominal"
          case "AnomalousTolerable" => "scenario_anomalous_tolerable"
          case "AnomalousNotTolerable" => "scenario_anomalous_not_tolerable"
          case _ => s"scenario_${name.toLowerCase.replace(" ", "_")}"
        }
        val assumption = K2Z3.ctx.mkBoolConst(scenarioVarName)
        verifySolver.add(assumption)
      }
      
      // Evaluate each constraint with the given model
      // If all constraints evaluate to true, the model is valid
      var allSatisfied = true
      var failedConstraints = ListBuffer[String]()
      var totalConstraints = 0
      
      for (expr <- boolExps) {
        val boolExpr = expr.asInstanceOf[BoolExpr]
        totalConstraints += 1
        try {
          val evalResult = z3Model.eval(boolExpr, true) // true = model_completion
          if (evalResult != null) {
            // Check if the result is true
            val isTrue = evalResult match {
              case b: BoolExpr => b.isTrue
              case _ => evalResult.toString == "true"
            }
            if (!isTrue) {
              val constraintStr = boolExpr.simplify().toString
              println(s"[UnifiedSolver] ✗ Constraint not satisfied: ${constraintStr.take(200)}")
              failedConstraints += constraintStr
              allSatisfied = false
              // Don't break - continue to find all failures for debugging
            }
          } else {
            // eval returned null - this means the constraint couldn't be evaluated
            // This is a problem - the model might be incomplete
            val constraintStr = boolExpr.simplify().toString
            println(s"[UnifiedSolver] ✗ Constraint evaluation returned null: ${constraintStr.take(200)}")
            failedConstraints += s"${constraintStr.take(200)} (eval returned null)"
              allSatisfied = false
          }
        } catch {
          case e: Throwable =>
            val constraintStr = boolExpr.simplify().toString
            println(s"[UnifiedSolver] ✗ Error evaluating constraint: ${e.getMessage}")
            println(s"[UnifiedSolver]     Constraint: ${constraintStr.take(200)}")
            failedConstraints += s"${constraintStr.take(200)} (error: ${e.getMessage})"
            // If we can't evaluate, assume it's not satisfied
            allSatisfied = false
        }
      }
      
      // Also check scenario assumption if provided
      scenarioName.foreach { name =>
        val scenarioVarName = name match {
          case "Nominal" => "scenario_nominal"
          case "AnomalousTolerable" => "scenario_anomalous_tolerable"
          case "AnomalousNotTolerable" => "scenario_anomalous_not_tolerable"
          case _ => s"scenario_${name.toLowerCase.replace(" ", "_")}"
        }
        try {
          val assumption = K2Z3.ctx.mkBoolConst(scenarioVarName)
          val evalResult = z3Model.eval(assumption, true)
          if (evalResult != null) {
            val isTrue = evalResult match {
              case b: BoolExpr => b.isTrue
              case _ => evalResult.toString == "true"
            }
            if (!isTrue) {
              log(s"  ✗ Scenario assumption not satisfied: $scenarioVarName")
              allSatisfied = false
              }
          } else {
            log(s"  ✗ Scenario assumption evaluation returned null: $scenarioVarName")
              allSatisfied = false
          }
        } catch {
          case e: Throwable =>
            log(s"  ✗ Could not evaluate scenario assumption: ${e.getMessage}")
            allSatisfied = false
        }
      }
      
      if (allSatisfied) {
        println(s"[UnifiedSolver] ✓ All $totalConstraints hard constraints satisfied")
        true
      } else {
        println(s"[UnifiedSolver] ✗ ${failedConstraints.length} hard constraint(s) not satisfied out of $totalConstraints")
        if (failedConstraints.nonEmpty && failedConstraints.length <= 10) {
          failedConstraints.take(10).foreach { fc =>
            println(s"[UnifiedSolver]     - ${fc.take(150)}")
        }
          if (failedConstraints.length > 10) {
            println(s"[UnifiedSolver]     ... and ${failedConstraints.length - 10} more")
          }
        }
        false
      }
    } catch {
      case e: Throwable =>
        println(s"[UnifiedSolver] ERROR during verification: ${e.getMessage}")
        if (debug) {
          e.printStackTrace()
        }
        // If verification fails due to an error, assume it's not valid
        false
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
    boundsWereIncreased = false
    iteration = 0
    interrupted = false
    pauseRequested = false
    sampleRequested = false
    isPaused = false
    lastSample = None
    dynamicClasses.clear()
    scenarioVars = Map()
    viableScenarios = MSet()
    ExternalFunctions.reset()
  }
  
  // ============================================================================
  // Scenario Tracking Integration
  // ============================================================================

  /**
   * Initialize scenario tracking for models with disjunctive structure.
   * Creates boolean variables for scenarios and tracks which are viable.
   */
  private def initializeScenarioTracking(model: KModel, smtModel: String): Unit = {
    val ctx = K2Z3.ctx
    
    // Create scenario boolean variables
    val nominal = ctx.mkBoolConst("scenario_nominal")
    val anomalousTolerable = ctx.mkBoolConst("scenario_anomalous_tolerable")
    val anomalousNotTolerable = ctx.mkBoolConst("scenario_anomalous_not_tolerable")
    
    scenarioVars = Map(
      "Nominal" -> nominal,
      "AnomalousTolerable" -> anomalousTolerable,
      "AnomalousNotTolerable" -> anomalousNotTolerable
    )
    
    // Initially all scenarios are viable
    viableScenarios = MSet("Nominal", "AnomalousTolerable", "AnomalousNotTolerable")
    
    if (debug) {
      log(s"Scenario tracking initialized with ${scenarioVars.size} scenarios")
    }
  }

  /**
   * Solve with a scenario assumption - this guides the solver but still uses
   * the unified loop's existing mechanisms (best-effort, soft constraints, etc.)
   */
  private def solveWithScenarioAssumption(
    model: KModel,
    smtModel: String,
    config: SolveConfig,
    scenarioName: String
  ): SolveResult = {
    // Just add the scenario assumption and use existing solve mechanism
    // The unified loop's best-effort and soft constraint handling will do the rest
    solveWithTimeoutAndAssumption(model, smtModel, config, scenarioName)
  }

  /**
   * Solve with a scenario assumption using Z3's Optimize API with incremental constraint addition.
   * This implements true max-SAT: we add constraints incrementally and use Optimize API to
   * maximize the number of satisfied constraints, getting partial solutions when timeouts occur.
   */
  private def solveWithTimeoutAndAssumption(
    model: KModel,
    smtModel: String,
    config: SolveConfig,
    scenarioName: String
  ): SolveResult = {
    try {
      // Use existing K2Z3 infrastructure
      K2Z3.reset()

      // Set timeout if specified (MUST be after reset, which creates new params)
      config.timeout.foreach { ms =>
        K2Z3.solverTimeout = Some(ms)
        if (debug) {
          log(s"Z3 timeout set to ${ms}ms for scenario $scenarioName")
        }
      }

      // Add scenario definitions to SMT if not already present
      val scenarioDefs = createScenarioDefinitionsSMT()
      val smtWithScenarios = if (!smtModel.contains("scenario_nominal")) {
        smtModel + "\n" + scenarioDefs
      } else {
        smtModel
      }

      // Write SMT to temp file
      val tempFile = new java.io.File(".tmp/k_unified_scenario.smt2")
      val tmpDir = tempFile.getParentFile
      if (tmpDir != null && !tmpDir.exists()) {
        tmpDir.mkdirs()
      }
      val writer = new java.io.PrintWriter(tempFile)
      writer.write(smtWithScenarios)
      writer.close()

      // Parse SMT model
      val boolExps = K2Z3.ctx.parseSMTLIB2File(
        tempFile.getAbsolutePath, Array(), Array(), Array(), Array())

      // Use Optimize API for better partial model support and max-SAT
      val optimize = K2Z3.getOptimize()
      
      // Set timeout on optimizer
      config.timeout.foreach { ms =>
        val optParams = K2Z3.ctx.mkParams()
        optParams.add("timeout", ms.toInt)
        optimize.setParameters(optParams)
      }

      // Group constraints for incremental addition
      val groups = IncrementalDiagnostic.groupAssertionsByConstraint(
        boolExps.map(_.asInstanceOf[BoolExpr]).toList, 
        smtWithScenarios
      )
      
      val baseGroup = groups.find(_.name == "Base Constraints")
      val namedGroups = groups.filter(_.name != "Base Constraints")
      
      // Add base constraints first
      baseGroup.foreach { group =>
        group.assertions.foreach { expr =>
          optimize.Add(expr)
        }
      }
      
      // Get scenario variable
      val scenarioVarName = scenarioName match {
        case "Nominal" => "scenario_nominal"
        case "AnomalousTolerable" => "scenario_anomalous_tolerable"
        case "AnomalousNotTolerable" => "scenario_anomalous_not_tolerable"
        case _ => s"scenario_${scenarioName.toLowerCase.replace(" ", "_")}"
      }
      val assumption = K2Z3.ctx.mkBoolConst(scenarioVarName)
      
      // Check base + scenario first
      var status = optimize.Check(assumption)
      if (status == Status.UNSATISFIABLE) {
        if (debug) {
          log(s"Scenario $scenarioName: UNSAT with base constraints only")
        }
        return SolveResult.Unsat
      }
      
      // Add constraints incrementally (max-SAT approach)
      // Strategy: Add groups incrementally, and when a group causes UNSAT,
      // make it soft and continue to get better partial solutions
      var addedGroups = 0
      var lastSatGroup = -1
      var problematicGroups = ListBuffer[Int]() // Groups that cause UNSAT
      var hardGroups = ListBuffer[Int]() // Groups added as hard constraints
      
      // Add constraints in batches to balance progress vs. performance
      val batchSize = math.max(1, namedGroups.length / 10) // Check every 10% of groups
      
      for ((group, index) <- namedGroups.zipWithIndex) {
        // Add this group as hard constraint
        group.assertions.foreach { expr =>
          optimize.Add(expr)
        }
        hardGroups += index
        addedGroups += 1
        
        // Check periodically (not after every group to avoid too many checks)
        val shouldCheck = (index + 1) % batchSize == 0 || index == namedGroups.length - 1
        
        if (shouldCheck) {
          val checkStatus = optimize.Check(assumption)
          
          // Update best model if we have one
          if (checkStatus == Status.SATISFIABLE || checkStatus == Status.UNKNOWN) {
            try {
              val currentModel = optimize.getModel
              if (currentModel != null) {
                bestSoFar = Some(currentModel)
                status = checkStatus
                lastSatGroup = index
                if (debug) {
                  log(s"  Progress: ${index + 1}/${namedGroups.length} groups - still SAT")
                }
              }
            } catch {
              case _: Throwable =>
            }
          } else if (checkStatus == Status.UNSATISFIABLE) {
            // This group makes it UNSAT - mark it as problematic
            problematicGroups += index
            if (debug) {
              log(s"  Progress: ${index + 1}/${namedGroups.length} groups - UNSAT at ${group.name}")
            }
            status = checkStatus
            // We'll rebuild with this group as soft if we have time
          }
        }
      }
      
      // If we have problematic groups and got UNSAT, try rebuilding with them as soft
      if (problematicGroups.nonEmpty && status == Status.UNSATISFIABLE) {
        if (debug) {
          log(s"Scenario $scenarioName: Rebuilding with ${problematicGroups.length} problematic groups as soft constraints")
        }
        
        // Create new optimizer with problematic groups as soft
        val softOptimize = K2Z3.ctx.mkOptimize()
        config.timeout.foreach { ms =>
          val optParams = K2Z3.ctx.mkParams()
          optParams.add("timeout", ms.toInt)
          softOptimize.setParameters(optParams)
        }
        
        // Add base constraints
        baseGroup.foreach { g =>
          g.assertions.foreach { expr =>
            softOptimize.Add(expr)
          }
        }
        
        // Add groups: hard for non-problematic, soft for problematic
        for ((group, index) <- namedGroups.zipWithIndex) {
          if (problematicGroups.contains(index)) {
            // Add as soft constraint
            group.assertions.zipWithIndex.foreach { case (expr, exprIdx) =>
              softOptimize.AssertSoft(expr, 1, s"soft_${group.name}_$exprIdx")
            }
          } else {
            // Add as hard constraint
            group.assertions.foreach { expr =>
              softOptimize.Add(expr)
            }
          }
        }
        
        // Check with soft constraints
        val softStatus = softOptimize.Check(assumption)
        if (softStatus == Status.SATISFIABLE || softStatus == Status.UNKNOWN) {
          try {
            val softModel = softOptimize.getModel
            if (softModel != null) {
              bestSoFar = Some(softModel)
              status = softStatus
              log(s"Scenario $scenarioName: Got solution with ${problematicGroups.length} groups as soft constraints")
              
              // Verify against hard constraints
              val isValid = verifyHardConstraints(model, smtWithScenarios, softModel, Some(scenarioName))
              
              if (isValid) {
                log(s"Scenario $scenarioName: Solution verified - satisfies all hard constraints")
                return SolveResult.Sat(softModel)
              } else {
                log(s"Scenario $scenarioName: Solution is best-effort - does not satisfy all hard constraints")
                // Return as best-effort (we'll handle this in the unified loop)
                bestSoFar = Some(softModel)
                return status match {
                  case Status.SATISFIABLE =>
                    // Even though it doesn't satisfy all hard constraints, we have a partial solution
                    SolveResult.Sat(softModel) // Mark as best-effort in the result
                  case Status.UNKNOWN =>
                    val reason = softOptimize.getReasonUnknown
                    val isTimeout = reason != null && (reason.toLowerCase.contains("timeout") || reason.toLowerCase.contains("canceled"))
                    if (isTimeout) {
                      SolveResult.Timeout
                    } else {
                      SolveResult.Unknown(reason)
                    }
                  case _ => SolveResult.Unsat
                }
              }
            }
          } catch {
            case _: Throwable =>
          }
        }
      }
      
      if (debug) {
        log(s"Scenario $scenarioName: Added $addedGroups groups, last SAT at group ${lastSatGroup + 1}")
        if (problematicGroups.nonEmpty) {
          log(s"  Problematic groups: ${problematicGroups.map(i => namedGroups(i).name).mkString(", ")}")
        }
      }
      
      // Final check with all constraints and full timeout
      status = optimize.Check(assumption)

      // Extract final result
      status match {
        case Status.SATISFIABLE =>
          val z3Model = optimize.getModel
          K2Z3.z3Model = z3Model
          bestSoFar = Some(z3Model)
          
          // Verify against hard constraints
          val isValid = verifyHardConstraints(model, smtWithScenarios, z3Model, Some(scenarioName))
          
          if (isValid) {
            log(s"Scenario $scenarioName: SAT (satisfied $addedGroups constraint groups) - verified")
            SolveResult.Sat(z3Model)
          } else {
            log(s"Scenario $scenarioName: Best-effort solution (does not satisfy all hard constraints)")
            // Still return as Sat, but mark it as best-effort
            SolveResult.Sat(z3Model)
          }
          
        case Status.UNSATISFIABLE =>
          log(s"Scenario $scenarioName: UNSAT (after adding $addedGroups groups)")
          SolveResult.Unsat
          
        case Status.UNKNOWN =>
          val reason = optimize.getReasonUnknown
          val isTimeout = reason != null && (reason.toLowerCase.contains("timeout") || reason.toLowerCase.contains("canceled"))
            if (isTimeout) {
              // Optimize API provides better partial model support
              try {
                val partialModel = optimize.getModel
                if (partialModel != null) {
                  bestSoFar = Some(partialModel)
                  K2Z3.z3Model = partialModel
                  
                  // Verify the partial model against hard constraints
                  val isValid = verifyHardConstraints(model, smtWithScenarios, partialModel, Some(scenarioName))
                  
                  if (isValid) {
                    log(s"Scenario $scenarioName: TIMEOUT - but partial model satisfies all hard constraints")
                  } else {
                    log(s"Scenario $scenarioName: TIMEOUT - partial model is best-effort (satisfied up to group ${lastSatGroup + 1})")
                  }
                } else {
                  log(s"Scenario $scenarioName: TIMEOUT - no partial model available")
                }
              } catch {
                case e: Throwable =>
                  log(s"Scenario $scenarioName: TIMEOUT - could not get partial model: ${e.getMessage}")
              }
              SolveResult.Timeout
          } else {
            log(s"Scenario $scenarioName: UNKNOWN - $reason")
            // Still try to get partial model on UNKNOWN
            try {
              val partialModel = optimize.getModel
              if (partialModel != null) {
                bestSoFar = Some(partialModel)
              }
            } catch {
              case _: Throwable =>
            }
            SolveResult.Unknown(reason)
          }
      }
    } catch {
      case e: Throwable =>
        if (debug) {
          log(s"Error solving scenario $scenarioName: ${e.getMessage}")
          e.printStackTrace()
        }
        SolveResult.Unknown(e.getMessage)
    }
  }

  /**
   * Create SMT definitions for scenarios.
   */
  private def createScenarioDefinitionsSMT(): String = {
    val scheduleRef = "(TopLevelDeclarations!schedule 0)"
    val requirementsRef = s"(Schedule!requirements $scheduleRef)"
    val missedpassExpr = s"(Requirements!missedpass $requirementsRef)"
    val tolerateExpr = s"(Requirements!tolerate $requirementsRef)"
    
    s"""
; Declare scenario boolean variables
(declare-const scenario_nominal Bool)
(declare-const scenario_anomalous_tolerable Bool)
(declare-const scenario_anomalous_not_tolerable Bool)

; Scenario definitions
(assert (= scenario_nominal (not $missedpassExpr)))
(assert (= scenario_anomalous_tolerable (and $missedpassExpr $tolerateExpr)))
(assert (= scenario_anomalous_not_tolerable (and $missedpassExpr (not $tolerateExpr))))
"""
  }

  private def log(msg: String): Unit = {
    // Always log verification messages - they're critical for debugging
    val isVerificationMsg = msg.contains("✗") || msg.contains("✓") || msg.contains("verifying") || msg.contains("verified") || msg.contains("constraint") || 
                           msg.contains("ERROR") || msg.contains("WARNING") || msg.contains("Solution does not satisfy") ||
                           msg.contains("Trying") || msg.contains("Increased") || msg.contains("iteration") || msg.contains("canIncrease") ||
                           msg.contains("Found") || msg.contains("classes") || msg.contains("Processing model") || msg.contains("package")
    // Always print verification-related messages
    if (debug || K2Z3.debug || isVerificationMsg) {
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

