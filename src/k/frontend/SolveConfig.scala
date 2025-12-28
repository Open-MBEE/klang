package k.frontend

import scala.concurrent.duration._

/**
 * Solver backend selection - which SMT solver to use.
 * This is orthogonal to the solving strategy.
 */
sealed trait SolverBackend
object SolverBackend {
  case object Z3 extends SolverBackend
  case object CVC5 extends SolverBackend
  case object Yices extends SolverBackend
  case object MathSAT extends SolverBackend
  case object BAE extends SolverBackend
  case object Auto extends SolverBackend  // Let the system choose
}

/**
 * Heap strategy - how to handle dynamic object creation.
 */
sealed trait HeapStrategy
object HeapStrategy {
  case object Fixed extends HeapStrategy      // All objects pre-declared, fixed bounds
  case object CEGAR extends HeapStrategy      // Start small, increase on UNSAT
  case object Soft extends HeapStrategy       // Use soft constraints for heap bounds
  case object Auto extends HeapStrategy       // Detect from problem structure
}

/**
 * Incremental mode - how to add constraints to the solver.
 */
sealed trait IncrementalMode
object IncrementalMode {
  case object None extends IncrementalMode        // Submit entire problem at once
  case object Assumptions extends IncrementalMode // Use assumption-based checking
  case object PushPop extends IncrementalMode     // Use push/pop scopes
  case object Scenarios extends IncrementalMode   // Explore disjunctions via scenarios
  case object Auto extends IncrementalMode        // Detect from problem structure
}

/**
 * Configuration for the unified solving loop.
 * All options are orthogonal - any combination is valid.
 */
case class SolveConfig(
  // Solver backend
  solver: SolverBackend = SolverBackend.Z3,
  
  // Timeout strategy
  initialTimeoutMs: Int = 500,        // Start with short timeout
  maxTimeoutMs: Int = 30000,          // User-specified max
  timeoutGrowthFactor: Double = 2.0,  // Multiply timeout on each retry
  
  // Heap strategy
  heapStrategy: HeapStrategy = HeapStrategy.Auto,
  initialHeapBounds: Map[String, Int] = Map.empty,  // Class name -> initial count
  maxHeapBounds: Map[String, Int] = Map.empty,      // Class name -> max count
  heapGrowthFactor: Double = 2.0,                   // Multiply bounds on UNSAT
  
  // Incremental strategy
  incrementalMode: IncrementalMode = IncrementalMode.Auto,
  
  // Soft constraint options
  useSoftConstraintFallback: Boolean = true,  // Try soft constraints on UNSAT/TIMEOUT
  
  // Best-effort mode
  bestEffort: Boolean = false,  // Return partial solutions if available
  
  // Verification
  verifyModels: Boolean = true,  // Verify SAT models against hard constraints
  
  // Debug/output
  printModel: Boolean = true,
  debug: Boolean = false
) {
  
  /**
   * Create a copy with increased timeout.
   */
  def withIncreasedTimeout: SolveConfig = {
    val newTimeout = math.min((initialTimeoutMs * timeoutGrowthFactor).toInt, maxTimeoutMs)
    copy(initialTimeoutMs = newTimeout)
  }
  
  /**
   * Create a copy with increased heap bounds.
   */
  def withIncreasedHeapBounds(classes: Set[String]): SolveConfig = {
    val newBounds = classes.foldLeft(initialHeapBounds) { (bounds, cls) =>
      val current = bounds.getOrElse(cls, 1)
      val max = maxHeapBounds.getOrElse(cls, 100)
      val newCount = math.min((current * heapGrowthFactor).toInt, max)
      bounds + (cls -> newCount)
    }
    copy(initialHeapBounds = newBounds)
  }
  
  /**
   * Check if we can still increase timeout.
   */
  def canIncreaseTimeout: Boolean = initialTimeoutMs < maxTimeoutMs
  
  /**
   * Check if we can still increase heap bounds for any class.
   */
  def canIncreaseHeapBounds(classes: Set[String]): Boolean = {
    classes.exists { cls =>
      val current = initialHeapBounds.getOrElse(cls, 1)
      val max = maxHeapBounds.getOrElse(cls, 100)
      current < max
    }
  }
  
  /**
   * Get remaining time budget.
   */
  def remainingBudget(elapsedMs: Long): Int = {
    math.max(0, maxTimeoutMs - elapsedMs.toInt)
  }
}

object SolveConfig {
  /**
   * Default configuration - auto-detect everything.
   */
  val default: SolveConfig = SolveConfig()
  
  /**
   * Configuration for simple problems (no dynamic heap, no scenarios).
   */
  val simple: SolveConfig = SolveConfig(
    heapStrategy = HeapStrategy.Fixed,
    incrementalMode = IncrementalMode.None,
    initialTimeoutMs = 5000
  )
  
  /**
   * Configuration for problems with dynamic heap (like lisp.k).
   */
  val dynamicHeap: SolveConfig = SolveConfig(
    heapStrategy = HeapStrategy.CEGAR,
    incrementalMode = IncrementalMode.None,
    initialTimeoutMs = 1000,
    initialHeapBounds = Map.empty,  // Start with 1 per class
    maxHeapBounds = Map.empty       // Allow up to 100 per class
  )
  
  /**
   * Configuration for problems with scenarios (like DSN_Pass.k).
   */
  val scenarioBased: SolveConfig = SolveConfig(
    heapStrategy = HeapStrategy.Fixed,
    incrementalMode = IncrementalMode.Scenarios,
    useSoftConstraintFallback = true,
    initialTimeoutMs = 5000,
    maxTimeoutMs = 60000
  )
  
  /**
   * Create config from command-line options map.
   */
  def fromOptions(options: Map[Symbol, Any]): SolveConfig = {
    var config = default
    
    // Solver backend
    if (options.getOrElse('cvc5, false).asInstanceOf[Boolean]) {
      config = config.copy(solver = SolverBackend.CVC5)
    } else if (options.getOrElse('yices, false).asInstanceOf[Boolean]) {
      config = config.copy(solver = SolverBackend.Yices)
    } else if (options.getOrElse('mathsat, false).asInstanceOf[Boolean]) {
      config = config.copy(solver = SolverBackend.MathSAT)
    } else if (options.getOrElse('bae, false).asInstanceOf[Boolean]) {
      config = config.copy(solver = SolverBackend.BAE)
    }
    
    // Heap strategy
    if (options.getOrElse('heapcegar, false).asInstanceOf[Boolean]) {
      config = config.copy(heapStrategy = HeapStrategy.CEGAR)
    } else if (options.getOrElse('heapsoft, false).asInstanceOf[Boolean]) {
      config = config.copy(heapStrategy = HeapStrategy.Soft)
    }
    
    // Incremental mode
    if (options.getOrElse('dsnPass, false).asInstanceOf[Boolean]) {
      config = config.copy(incrementalMode = IncrementalMode.Scenarios)
    } else if (options.getOrElse('incremental, false).asInstanceOf[Boolean]) {
      config = config.copy(incrementalMode = IncrementalMode.PushPop)
    }
    
    // Timeout
    options.get('timeout) match {
      case Some(t: Int) => config = config.copy(maxTimeoutMs = t)
      case _ =>
    }
    
    // Debug
    if (options.getOrElse('debug, false).asInstanceOf[Boolean]) {
      config = config.copy(debug = true)
    }
    
    config
  }
}

