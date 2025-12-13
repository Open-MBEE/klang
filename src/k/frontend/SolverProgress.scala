package k.frontend

import org.json.JSONObject
import org.json.JSONArray
import scala.collection.mutable.ListBuffer

/**
 * Progress reporting for K solver operations.
 *
 * Emits structured JSON events that the IDE can parse to show:
 * - CEGAR refinement iterations
 * - Value ranges for variables
 * - Optimization progress
 * - Constraint breakpoints
 */
object SolverProgress {

  /** Whether progress reporting is enabled */
  var enabled: Boolean = false

  /** Whether to emit JSON events (vs human-readable) */
  var jsonOutput: Boolean = false

  /** Callback for progress events (used by IDE integration) */
  var progressCallback: Option[ProgressEvent => Unit] = None

  /** History of CEGAR iterations for the current solve */
  private val cegarHistory: ListBuffer[CEGARIteration] = ListBuffer()

  /** Current variable bounds cache */
  private var variableBounds: Map[String, VariableBounds] = Map()

  /** Current optimization state */
  private var optimizationState: Option[OptimizationProgress] = None

  /** Active constraint breakpoints */
  private var breakpoints: Set[String] = Set()

  // ============================================================================
  // Progress Event Types
  // ============================================================================

  sealed trait ProgressEvent {
    def toJson: JSONObject
  }

  /** CEGAR iteration event */
  case class CEGARIteration(
    iteration: Int,
    status: String,  // "candidate", "counterexample", "verified", "refined"
    candidateSolution: Option[Map[String, String]] = None,
    counterexample: Option[String] = None,
    refinementConstraint: Option[String] = None,
    externalFunction: Option[String] = None,
    expectedValue: Option[String] = None,
    actualValue: Option[String] = None,
    timestamp: Long = System.currentTimeMillis()
  ) extends ProgressEvent {
    def toJson: JSONObject = {
      val json = new JSONObject()
      json.put("type", "cegar")
      json.put("iteration", iteration)
      json.put("status", status)
      json.put("timestamp", timestamp)

      candidateSolution.foreach { sol =>
        val solJson = new JSONObject()
        sol.foreach { case (k, v) => solJson.put(k, v) }
        json.put("candidate", solJson)
      }

      counterexample.foreach(c => json.put("counterexample", c))
      refinementConstraint.foreach(r => json.put("refinement", r))
      externalFunction.foreach(f => json.put("externalFunction", f))
      expectedValue.foreach(e => json.put("expectedValue", e))
      actualValue.foreach(a => json.put("actualValue", a))

      json
    }
  }

  /** Variable bounds event */
  case class VariableBounds(
    variableName: String,
    minValue: Option[String] = None,
    maxValue: Option[String] = None,
    exactValue: Option[String] = None,
    feasible: Boolean = true,
    varType: String = "unknown"
  ) extends ProgressEvent {
    def toJson: JSONObject = {
      val json = new JSONObject()
      json.put("type", "bounds")
      json.put("variable", variableName)
      json.put("varType", varType)
      json.put("feasible", feasible)

      minValue.foreach(m => json.put("min", m))
      maxValue.foreach(m => json.put("max", m))
      exactValue.foreach(e => json.put("exact", e))

      json
    }

    /** Format as display string */
    def toDisplayString: String = {
      if (!feasible) return "∅ (infeasible)"

      (exactValue, minValue, maxValue) match {
        case (Some(e), _, _) => s"= $e"
        case (_, Some(min), Some(max)) if min == max => s"= $min"
        case (_, Some(min), Some(max)) => s"∈ [$min, $max]"
        case (_, Some(min), None) => s"≥ $min"
        case (_, None, Some(max)) => s"≤ $max"
        case _ => "?"
      }
    }
  }

  /** Optimization progress event */
  case class OptimizationProgress(
    iteration: Int,
    objectiveName: String,
    currentValue: Option[String] = None,
    bestValue: Option[String] = None,
    lowerBound: Option[String] = None,
    upperBound: Option[String] = None,
    gap: Option[Double] = None,
    status: String = "running",  // running, optimal, timeout, infeasible
    timestamp: Long = System.currentTimeMillis()
  ) extends ProgressEvent {
    def toJson: JSONObject = {
      val json = new JSONObject()
      json.put("type", "optimization")
      json.put("iteration", iteration)
      json.put("objective", objectiveName)
      json.put("status", status)
      json.put("timestamp", timestamp)

      currentValue.foreach(v => json.put("current", v))
      bestValue.foreach(v => json.put("best", v))
      lowerBound.foreach(v => json.put("lower", v))
      upperBound.foreach(v => json.put("upper", v))
      gap.foreach(g => json.put("gap", g))

      json
    }
  }

  /** Constraint breakpoint hit event */
  case class BreakpointHit(
    constraintName: String,
    constraintExpression: String,
    line: Int,
    currentValues: Map[String, String] = Map(),
    satisfied: Option[Boolean] = None
  ) extends ProgressEvent {
    def toJson: JSONObject = {
      val json = new JSONObject()
      json.put("type", "breakpoint")
      json.put("constraint", constraintName)
      json.put("expression", constraintExpression)
      json.put("line", line)

      satisfied.foreach(s => json.put("satisfied", s))

      if (currentValues.nonEmpty) {
        val valuesJson = new JSONObject()
        currentValues.foreach { case (k, v) => valuesJson.put(k, v) }
        json.put("values", valuesJson)
      }

      json
    }
  }

  /** Call stack frame */
  case class CallFrame(
    frameType: String,  // "k-constraint", "java", "python"
    name: String,
    file: Option[String] = None,
    line: Option[Int] = None,
    details: Option[String] = None
  ) {
    def toJson: JSONObject = {
      val json = new JSONObject()
      json.put("frameType", frameType)
      json.put("name", name)
      file.foreach(f => json.put("file", f))
      line.foreach(l => json.put("line", l))
      details.foreach(d => json.put("details", d))
      json
    }
  }

  /** Unified call stack event */
  case class UnifiedCallStack(
    frames: List[CallFrame]
  ) extends ProgressEvent {
    def toJson: JSONObject = {
      val json = new JSONObject()
      json.put("type", "callstack")
      val framesArray = new JSONArray()
      frames.foreach(f => framesArray.put(f.toJson))
      json.put("frames", framesArray)
      json
    }
  }

  /** Solver state summary */
  case class SolverState(
    phase: String,  // "parsing", "typechecking", "smt-generation", "solving", "cegar", "done"
    progress: Double = 0.0,  // 0.0 to 1.0
    message: String = "",
    cegarIteration: Int = 0,
    variablesBound: Int = 0,
    constraintsProcessed: Int = 0,
    constraintsTotal: Int = 0
  ) extends ProgressEvent {
    def toJson: JSONObject = {
      val json = new JSONObject()
      json.put("type", "state")
      json.put("phase", phase)
      json.put("progress", progress)
      json.put("message", message)
      json.put("cegarIteration", cegarIteration)
      json.put("variablesBound", variablesBound)
      json.put("constraintsProcessed", constraintsProcessed)
      json.put("constraintsTotal", constraintsTotal)
      json
    }
  }

  // ============================================================================
  // Progress Reporting Methods
  // ============================================================================

  /** Emit a progress event */
  def emit(event: ProgressEvent): Unit = {
    if (!enabled) return

    // Call registered callback
    progressCallback.foreach(_(event))

    // Also print to stdout if JSON output enabled
    if (jsonOutput) {
      println(s"K_PROGRESS:${event.toJson.toString}")
    }
  }

  /** Report start of a new CEGAR iteration */
  def cegarStart(iteration: Int): Unit = {
    val event = CEGARIteration(iteration, "started")
    cegarHistory += event
    emit(event)
  }

  /** Report CEGAR candidate solution */
  def cegarCandidate(iteration: Int, solution: Map[String, String]): Unit = {
    val event = CEGARIteration(iteration, "candidate", candidateSolution = Some(solution))
    cegarHistory += event
    emit(event)
  }

  /** Report CEGAR counterexample found */
  def cegarCounterexample(
    iteration: Int,
    funcName: String,
    expected: String,
    actual: String
  ): Unit = {
    val event = CEGARIteration(
      iteration, "counterexample",
      externalFunction = Some(funcName),
      expectedValue = Some(expected),
      actualValue = Some(actual),
      counterexample = Some(s"$funcName: expected $expected but got $actual")
    )
    cegarHistory += event
    emit(event)
  }

  /** Report CEGAR refinement constraint added */
  def cegarRefinement(iteration: Int, constraint: String): Unit = {
    val event = CEGARIteration(iteration, "refined", refinementConstraint = Some(constraint))
    cegarHistory += event
    emit(event)
  }

  /** Report CEGAR verification success */
  def cegarVerified(iteration: Int): Unit = {
    val event = CEGARIteration(iteration, "verified")
    cegarHistory += event
    emit(event)
  }

  /** Update variable bounds */
  def updateBounds(varName: String, bounds: VariableBounds): Unit = {
    variableBounds += (varName -> bounds)
    emit(bounds)
  }

  /** Get current bounds for a variable */
  def getBounds(varName: String): Option[VariableBounds] = variableBounds.get(varName)

  /** Get all current variable bounds */
  def getAllBounds: Map[String, VariableBounds] = variableBounds

  /** Update optimization progress */
  def updateOptimization(progress: OptimizationProgress): Unit = {
    optimizationState = Some(progress)
    emit(progress)
  }

  /** Report breakpoint hit */
  def breakpointHit(
    constraintName: String,
    expression: String,
    line: Int,
    values: Map[String, String]
  ): Unit = {
    if (breakpoints.contains(constraintName)) {
      emit(BreakpointHit(constraintName, expression, line, values))
    }
  }

  /** Set constraint breakpoints */
  def setBreakpoints(names: Set[String]): Unit = {
    breakpoints = names
  }

  /** Add a constraint breakpoint */
  def addBreakpoint(name: String): Unit = {
    breakpoints += name
  }

  /** Remove a constraint breakpoint */
  def removeBreakpoint(name: String): Unit = {
    breakpoints -= name
  }

  /** Check if constraint has breakpoint */
  def hasBreakpoint(name: String): Boolean = breakpoints.contains(name)

  /** Report solver state */
  def reportState(state: SolverState): Unit = {
    emit(state)
  }

  /** Report unified call stack */
  def reportCallStack(frames: List[CallFrame]): Unit = {
    emit(UnifiedCallStack(frames))
  }

  /** Clear all progress state (call at start of new solve) */
  def reset(): Unit = {
    cegarHistory.clear()
    variableBounds = Map()
    optimizationState = None
  }

  /** Get CEGAR iteration history */
  def getCEGARHistory: List[CEGARIteration] = cegarHistory.toList

  /** Get current optimization state */
  def getOptimizationState: Option[OptimizationProgress] = optimizationState

  // ============================================================================
  // JSON Output for All Progress
  // ============================================================================

  /** Get full progress summary as JSON */
  def toJson: JSONObject = {
    val json = new JSONObject()

    // CEGAR history
    val cegarArray = new JSONArray()
    cegarHistory.foreach(c => cegarArray.put(c.toJson))
    json.put("cegar", cegarArray)

    // Variable bounds
    val boundsJson = new JSONObject()
    variableBounds.foreach { case (k, v) => boundsJson.put(k, v.toJson) }
    json.put("bounds", boundsJson)

    // Optimization
    optimizationState.foreach(o => json.put("optimization", o.toJson))

    // Breakpoints
    val bpArray = new JSONArray()
    breakpoints.foreach(bp => bpArray.put(bp))
    json.put("breakpoints", bpArray)

    json
  }
}

