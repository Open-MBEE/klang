package k.frontend

import com.microsoft.z3._
import scala.collection.mutable.{ListBuffer, Map => MMap, Set => MSet}

/**
 * UnifiedSolver - Iterative solving loop for K models
 *
 * Handles:
 * 1. CEGAR refinement for external/opaque function calls
 * 2. Object creation bounds for dynamic instantiation
 * 3. Incremental/anytime solving with pause, resume, sampling
 * 4. Optimization with soft constraints (max-SAT style)
 */
object UnifiedSolver {

  // Type aliases to avoid confusion between K Model and Z3 Model
  type KModel = k.frontend.Model
  type Z3Model = com.microsoft.z3.Model

  // ============================================================================
  // Configuration
  // ============================================================================

  /** Maximum iterations before giving up */
  var maxIterations: Int = 100

  /** Maximum object instances per class */
  var maxObjectBound: Int = 100

  /** Initial object bound for collections */
  var initialObjectBound: Int = 1

  /** Whether to use Z3 Optimize API for soft constraints */
  var useOptimize: Boolean = true

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

