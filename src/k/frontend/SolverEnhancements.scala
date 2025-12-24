// filepath: /Users/bclement/git/klang/src/k/frontend/SolverEnhancements.scala
package k.frontend

import com.microsoft.z3.{Context, Solver, Optimize, Sort, Expr, BoolExpr, IntExpr, RealExpr, 
  ArithExpr, FuncDecl, Symbol => Z3Symbol, Model => Z3Model, Status, Params, SeqSort}
import scala.jdk.CollectionConverters._
import scala.collection.mutable.{ListBuffer, HashMap => MMap, Stack}

/**
 * Advanced Z3 Solver Enhancements for K Language
 *
 * Features:
 * - Incremental solving with push/pop scopes
 * - Anytime best-effort solutions with timeouts
 * - Optimization objectives (minimize/maximize)
 * - Enhanced unsat core reporting
 * - Native sequence theory support (via SMT-LIB)
 * - Regular expression support (via SMT-LIB)
 * - Opaque function support (uninterpreted + axiom learning)
 */

// ============================================================================
// Solver Configuration
// ============================================================================

/**
 * Configuration for solver behavior
 */
case class SolverConfig(
  timeout: Option[Long] = None,           // Timeout in milliseconds
  produceUnsatCores: Boolean = true,      // Enable unsat core extraction
  incrementalMode: Boolean = false,       // Enable incremental solving
  bestEffort: Boolean = false,            // Return partial results on timeout
  maxIterations: Int = 1000,              // Max iterations for CEGAR loops
  verbosity: Int = 0                      // Debug verbosity level
)

object SolverConfig {
  val default: SolverConfig = SolverConfig()
  
  def withTimeout(ms: Long): SolverConfig = default.copy(timeout = Some(ms))
  def bestEffort(ms: Long): SolverConfig = default.copy(timeout = Some(ms), bestEffort = true)
}

// ============================================================================
// Solver Result Types
// ============================================================================

/**
 * Enhanced solver result with more information
 */
sealed trait SolverResult {
  def isSat: Boolean
  def isUnsat: Boolean
  def isUnknown: Boolean
  def model: Option[Z3Model]
}

case class Satisfiable(z3Model: Z3Model, isPartial: Boolean = false) extends SolverResult {
  def isSat = true
  def isUnsat = false
  def isUnknown = false
  def model = Some(z3Model)
}

case class Unsatisfiable(
  unsatCore: List[String],           // Names of conflicting constraints
  conflictExplanation: String        // Human-readable explanation
) extends SolverResult {
  def isSat = false
  def isUnsat = true
  def isUnknown = false
  def model = None
}

case class Unknown(
  reason: String,
  partialModel: Option[Z3Model] = None,  // Best-effort partial result
  timeoutOccurred: Boolean = false
) extends SolverResult {
  def isSat = false
  def isUnsat = false
  def isUnknown = true
  def model = partialModel
}

// ============================================================================
// Optimization Objectives
// ============================================================================

sealed trait OptimizationKind
case object Minimize extends OptimizationKind { override def toString = "minimize" }
case object Maximize extends OptimizationKind { override def toString = "maximize" }

/**
 * An optimization objective
 */
case class OptimizationObjective(
  kind: OptimizationKind,
  expression: String,  // SMT expression to optimize
  weight: Option[Int] = None,  // For multi-objective optimization
  id: Option[String] = None    // Optional identifier
)

// ============================================================================
// Incremental Solving Session
// ============================================================================

/**
 * Manages an incremental solving session with push/pop scopes
 */
class IncrementalSession(ctx: Context, config: SolverConfig = SolverConfig.default) {
  private val solver: Solver = ctx.mkSolver()
  private val scopeStack: Stack[String] = Stack()
  private var assertionLabels: MMap[String, BoolExpr] = MMap()
  private var assertionCount: Int = 0
  
  // Configure solver
  {
    val params = ctx.mkParams()
    if (config.produceUnsatCores) params.add("unsat_core", true)
    config.timeout.foreach(ms => params.add("timeout", ms.toInt))
    solver.setParameters(params)
  }
  
  /**
   * Push a new scope onto the assertion stack
   */
  def push(scopeName: String = ""): Unit = {
    solver.push()
    scopeStack.push(scopeName)
  }
  
  /**
   * Pop the most recent scope
   */
  def pop(): Option[String] = {
    if (scopeStack.nonEmpty) {
      solver.pop()
      Some(scopeStack.pop())
    } else None
  }
  
  /**
   * Pop multiple scopes
   */
  def pop(n: Int): Unit = {
    solver.pop(n)
    (1 to n).foreach(_ => if (scopeStack.nonEmpty) scopeStack.pop())
  }
  
  /**
   * Current scope depth
   */
  def scopeDepth: Int = scopeStack.size
  
  /**
   * Add an assertion with optional label for unsat core tracking
   */
  def assert(expr: BoolExpr, label: Option[String] = None): String = {
    val actualLabel = label.getOrElse {
      assertionCount += 1
      s"_assertion_$assertionCount"
    }
    
    val labelConst = ctx.mkBoolConst(actualLabel)
    assertionLabels(actualLabel) = expr
    solver.assertAndTrack(expr, labelConst)
    actualLabel
  }
  
  /**
   * Add a soft constraint (for optimization)
   */
  def assertSoft(expr: BoolExpr, weight: Int = 1, id: String = ""): Unit = {
    // Soft constraints are handled differently - store for later use with Optimize
    // For now, just add as hard constraint
    solver.add(expr)
  }
  
  /**
   * Check satisfiability
   */
  def check(): SolverResult = {
    val status = solver.check()
    status match {
      case Status.SATISFIABLE =>
        Satisfiable(solver.getModel)
      case Status.UNSATISFIABLE =>
        val core = extractUnsatCore()
        Unsatisfiable(core, formatUnsatExplanation(core))
      case Status.UNKNOWN =>
        Unknown(solver.getReasonUnknown)
    }
  }
  
  /**
   * Check satisfiability with assumptions
   */
  def checkAssuming(assumptions: BoolExpr*): SolverResult = {
    val status = solver.check(assumptions: _*)
    status match {
      case Status.SATISFIABLE =>
        Satisfiable(solver.getModel)
      case Status.UNSATISFIABLE =>
        val core = extractUnsatCore()
        Unsatisfiable(core, formatUnsatExplanation(core))
      case Status.UNKNOWN =>
        Unknown(solver.getReasonUnknown)
    }
  }
  
  /**
   * Get the underlying Z3 solver (for model extraction)
   */
  def getSolver: Solver = solver
  
  /**
   * Get current model if satisfiable
   */
  def getModel: Option[Z3Model] = {
    try {
      val status = solver.check()
      if (status == Status.SATISFIABLE) {
        Some(solver.getModel)
      } else {
        None
      }
    } catch {
      case _: Throwable => None
    }
  }
  
  /**
   * Extract unsat core as list of assertion labels
   */
  private def extractUnsatCore(): List[String] = {
    try {
      solver.getUnsatCore.map(_.toString).toList
    } catch {
      case _: Throwable => List()
    }
  }
  
  /**
   * Format human-readable unsat explanation
   */
  private def formatUnsatExplanation(core: List[String]): String = {
    if (core.isEmpty) {
      "The constraints are unsatisfiable (no detailed core available)"
    } else {
      val coreDescriptions = core.map { label =>
        UtilSMT.constraintMessageMap.getOrElse(label, label)
      }
      s"Conflicting constraints:\n${coreDescriptions.map(c => s"  - $c").mkString("\n")}"
    }
  }
  
  /**
   * Reset the session
   */
  def reset(): Unit = {
    solver.reset()
    scopeStack.clear()
    assertionLabels.clear()
    assertionCount = 0
  }
}

// ============================================================================
// Anytime/Best-Effort Solver
// ============================================================================

/**
 * Solver that returns best-effort results with timeout support
 */
class AnytimeSolver(ctx: Context, config: SolverConfig = SolverConfig.default) {
  private val optimize: Optimize = ctx.mkOptimize()
  private var objectives: ListBuffer[OptimizationObjective] = ListBuffer()
  
  // Configure optimizer
  {
    val params = ctx.mkParams()
    config.timeout.foreach(ms => params.add("timeout", ms.toInt))
    optimize.setParameters(params)
  }
  
  /**
   * Add a hard constraint
   */
  def assert(expr: BoolExpr): Unit = {
    optimize.Add(expr)
  }
  
  /**
   * Add a soft constraint with weight
   */
  def assertSoft(expr: BoolExpr, weight: Int = 1, id: String = "soft"): Unit = {
    optimize.AssertSoft(expr, weight, id)
  }
  
  /**
   * Add minimization objective
   */
  def minimize(expr: ArithExpr[_]): Unit = {
    optimize.MkMinimize(expr)
  }
  
  /**
   * Add maximization objective
   */
  def maximize(expr: ArithExpr[_]): Unit = {
    optimize.MkMaximize(expr)
  }
  
  /**
   * Solve with best-effort behavior
   * Returns partial results if timeout occurs
   */
  def solve(): SolverResult = {
    val status = optimize.Check()
    status match {
      case Status.SATISFIABLE =>
        Satisfiable(optimize.getModel)
      case Status.UNSATISFIABLE =>
        // Try to get explanation
        Unsatisfiable(List(), "Optimization problem is unsatisfiable")
      case Status.UNKNOWN =>
        // Try to get partial model if available
        val partialModel = try {
          Some(optimize.getModel)
        } catch {
          case _: Throwable => None
        }
        Unknown(
          optimize.getReasonUnknown,
          partialModel,
          timeoutOccurred = optimize.getReasonUnknown.toLowerCase.contains("timeout")
        )
    }
  }
  
  /**
   * Reset the optimizer
   */
  def reset(): Unit = {
    // Note: Z3 Optimize doesn't have a reset method, need to recreate
    objectives.clear()
  }
}

// ============================================================================
// Opaque Function Support
// ============================================================================

/**
 * Represents an opaque (black-box) function that can be called externally
 */
case class OpaqueFunction(
  name: String,
  argTypes: List[Sort],
  returnType: Sort,
  implementation: Option[Any => Any] = None  // JVM function if available
)

/**
 * Manager for opaque functions with axiom learning
 */
class OpaqueFunctionManager(ctx: Context) {
  private val functions: MMap[String, OpaqueFunction] = MMap()
  private val learnedAxioms: MMap[String, ListBuffer[BoolExpr]] = MMap()
  private val functionDecls: MMap[String, FuncDecl[_ <: Sort]] = MMap()
  
  /**
   * Register an opaque function
   */
  def registerFunction(func: OpaqueFunction): FuncDecl[_ <: Sort] = {
    functions(func.name) = func
    learnedAxioms(func.name) = ListBuffer()
    
    val decl = ctx.mkFuncDecl(func.name, func.argTypes.toArray, func.returnType)
    functionDecls(func.name) = decl
    decl
  }
  
  /**
   * Get function declaration for use in constraints
   */
  def getDecl(name: String): Option[FuncDecl[_ <: Sort]] = functionDecls.get(name)
  
  /**
   * Learn an axiom from concrete execution
   * Given concrete inputs and output, add equality axiom
   */
  def learnAxiom(name: String, args: Array[Expr[_ <: Sort]], result: Expr[_ <: Sort]): Option[BoolExpr] = {
    functionDecls.get(name).map { decl =>
      val application = ctx.mkApp(decl, args: _*)
      val axiom = ctx.mkEq(application, result)
      learnedAxioms(name) += axiom
      axiom
    }
  }
  
  /**
   * Get all learned axioms for a function
   */
  def getAxioms(name: String): List[BoolExpr] = {
    learnedAxioms.getOrElse(name, ListBuffer()).toList
  }
  
  /**
   * Get all learned axioms
   */
  def getAllAxioms: List[BoolExpr] = {
    learnedAxioms.values.flatMap(_.toList).toList
  }
  
  /**
   * Invoke opaque function with concrete arguments (if implementation available)
   */
  def invoke(name: String, args: Any*): Option[Any] = {
    for {
      func <- functions.get(name)
      impl <- func.implementation
    } yield {
      impl(args)
    }
  }
}

// ============================================================================
// Regular Expression Support (SMT-LIB string generation)
// ============================================================================

/**
 * Helper for building SMT-LIB regular expression strings
 * Note: These generate SMT-LIB text, not Z3 API objects
 */
object RegexSupport {

  /**
   * Create membership constraint: (str.in_re str (str.to_re pattern))
   */
  def mkMatchesSMT(strSMT: String, patternSMT: String): String = {
    s"(str.in_re $strSMT (str.to_re $patternSMT))"
  }

  /**
   * Concatenate regexes in SMT-LIB
   */
  def mkConcatSMT(regexes: String*): String = {
    if (regexes.length == 1) regexes.head
    else s"(re.++ ${regexes.mkString(" ")})"
  }

  /**
   * Union of regexes in SMT-LIB
   */
  def mkUnionSMT(regexes: String*): String = {
    s"(re.union ${regexes.mkString(" ")})"
  }

  /**
   * Kleene star in SMT-LIB
   */
  def mkStarSMT(regex: String): String = s"(re.* $regex)"

  /**
   * Kleene plus in SMT-LIB
   */
  def mkPlusSMT(regex: String): String = s"(re.+ $regex)"

  /**
   * Optional in SMT-LIB
   */
  def mkOptionSMT(regex: String): String = s"(re.opt $regex)"

  /**
   * Character range in SMT-LIB
   */
  def mkRangeSMT(lo: String, hi: String): String = s"(re.range $lo $hi)"

  /**
   * Common patterns as SMT-LIB
   */
  def digitSMT: String = "(re.range \"0\" \"9\")"
  def lowerSMT: String = "(re.range \"a\" \"z\")"
  def upperSMT: String = "(re.range \"A\" \"Z\")"
  def alphaSMT: String = s"(re.union $lowerSMT $upperSMT)"
  def alphaNumSMT: String = s"(re.union $alphaSMT $digitSMT)"
  def anySMT: String = "re.allchar"
}

// ============================================================================
// Sequence Theory Support (SMT-LIB string generation)
// ============================================================================

/**
 * Helper for Z3 sequence operations via SMT-LIB text generation
 */
object SeqSupport {

  /**
   * Create empty sequence SMT
   */
  def mkEmptySMT(sortSMT: String): String = s"(as seq.empty (Seq $sortSMT))"

  /**
   * Create unit sequence SMT
   */
  def mkUnitSMT(elementSMT: String): String = s"(seq.unit $elementSMT)"

  /**
   * Concatenate sequences SMT
   */
  def mkConcatSMT(seqs: String*): String = {
    if (seqs.length == 1) seqs.head
    else s"(seq.++ ${seqs.mkString(" ")})"
  }

  /**
   * Sequence length SMT
   */
  def mkLengthSMT(seqSMT: String): String = s"(seq.len $seqSMT)"

  /**
   * Get element at index SMT
   */
  def mkAtSMT(seqSMT: String, indexSMT: String): String = s"(seq.nth $seqSMT $indexSMT)"

  /**
   * Extract subsequence SMT
   */
  def mkExtractSMT(seqSMT: String, offsetSMT: String, lengthSMT: String): String =
    s"(seq.extract $seqSMT $offsetSMT $lengthSMT)"

  /**
   * Check if subsequence is contained SMT
   */
  def mkContainsSMT(seqSMT: String, subseqSMT: String): String =
    s"(seq.contains $seqSMT $subseqSMT)"

  /**
   * Check prefix SMT
   */
  def mkPrefixOfSMT(prefixSMT: String, seqSMT: String): String =
    s"(seq.prefixof $prefixSMT $seqSMT)"

  /**
   * Check suffix SMT
   */
  def mkSuffixOfSMT(suffixSMT: String, seqSMT: String): String =
    s"(seq.suffixof $suffixSMT $seqSMT)"

  /**
   * Find index of subsequence SMT
   */
  def mkIndexOfSMT(seqSMT: String, subseqSMT: String, offsetSMT: String): String =
    s"(seq.indexof $seqSMT $subseqSMT $offsetSMT)"

  /**
   * Replace first occurrence SMT
   */
  def mkReplaceSMT(seqSMT: String, srcSMT: String, dstSMT: String): String =
    s"(seq.replace $seqSMT $srcSMT $dstSMT)"
}

// ============================================================================
// CEGAR (Counter-Example Guided Abstraction Refinement) Loop
// ============================================================================

/**
 * CEGAR loop for refining opaque function behavior
 */
class CEGARSolver(
  ctx: Context, 
  opaqueFunctions: OpaqueFunctionManager,
  config: SolverConfig = SolverConfig.default
) {
  private val session = new IncrementalSession(ctx, config)
  
  /**
   * Solve with CEGAR refinement for opaque functions
   * 
   * @param constraints Initial constraints
   * @param opaqueCallExtractor Function to extract opaque function calls from model
   * @return Solver result after refinement
   */
  def solveWithRefinement(
    constraints: List[BoolExpr],
    opaqueCallExtractor: Z3Model => List[(String, Array[Any], Any)]
  ): SolverResult = {
    
    // Add initial constraints
    constraints.foreach(c => session.assert(c))
    
    // Add any previously learned axioms
    opaqueFunctions.getAllAxioms.foreach(a => session.assert(a))
    
    var iterations = 0
    var result: SolverResult = null
    
    while (iterations < config.maxIterations) {
      iterations += 1
      
      result = session.check()
      
      result match {
        case Satisfiable(model, _) =>
          // Extract opaque function calls from model
          val calls = opaqueCallExtractor(model)
          
          if (calls.isEmpty) {
            // No opaque calls to validate - we're done
            return result
          }
          
          // Validate each opaque call against actual implementation
          var foundSpurious = false
          for ((funcName, args, modelResult) <- calls) {
            opaqueFunctions.invoke(funcName, args: _*) match {
              case Some(actualResult) if actualResult != modelResult =>
                // Spurious counterexample - add refinement axiom
                // This requires converting actualResult back to Z3 Expr
                // For now, just log and continue
                foundSpurious = true
                // Would add: opaqueFunctions.learnAxiom(funcName, argsExpr, actualResultExpr)
                
              case _ => // Result matches or no implementation available
            }
          }
          
          if (!foundSpurious) {
            // All opaque calls validated
            return result
          }
          // Otherwise continue loop with new axioms
          
        case Unsatisfiable(_, _) =>
          return result
          
        case Unknown(_, _, _) =>
          return result
      }
    }
    
    // Max iterations reached
    Unknown(s"CEGAR loop did not converge after $iterations iterations", 
            result.model, timeoutOccurred = false)
  }
}

// ============================================================================
// Enhanced K2Z3 Integration
// ============================================================================

/**
 * Extended K2Z3 interface with advanced features
 */
object K2Z3Enhanced {
  
  /**
   * Create an incremental session for step-by-step solving
   */
  def createIncrementalSession(config: SolverConfig = SolverConfig.default): IncrementalSession = {
    val ctx = new Context(Map("model" -> "true").asJava)
    new IncrementalSession(ctx, config)
  }
  
  /**
   * Create an anytime solver for optimization with timeouts
   */
  def createAnytimeSolver(config: SolverConfig = SolverConfig.default): AnytimeSolver = {
    val ctx = new Context(Map("model" -> "true").asJava)
    new AnytimeSolver(ctx, config)
  }
  
  /**
   * Solve with timeout and best-effort result
   */
  def solveWithTimeout(smtModel: String, timeoutMs: Long): SolverResult = {
    val ctx = new Context(Map("model" -> "true").asJava)
    val config = SolverConfig.bestEffort(timeoutMs)
    val solver = new AnytimeSolver(ctx, config)
    
    // Parse and add constraints
    try {
      val tempFile = new java.io.File(".tmp/k_timeout_solve.smt2")
      val writer = new java.io.PrintWriter(tempFile)
      writer.write(smtModel)
      writer.close()
      
      val boolExps = ctx.parseSMTLIB2File(tempFile.getAbsolutePath, Array(), Array(), Array(), Array())
      boolExps.foreach(solver.assert)
      
      tempFile.delete()
      solver.solve()
    } catch {
      case e: Throwable =>
        Unknown(s"Parse error: ${e.getMessage}")
    }
  }
  
  /**
   * Solve with optimization objectives
   */
  def solveWithOptimization(
    smtModel: String, 
    objectives: List[OptimizationObjective],
    timeoutMs: Option[Long] = None
  ): SolverResult = {
    val ctx = new Context(Map("model" -> "true").asJava)
    val config = timeoutMs.map(SolverConfig.withTimeout).getOrElse(SolverConfig.default)
    val solver = new AnytimeSolver(ctx, config)
    
    try {
      val tempFile = new java.io.File(".tmp/k_opt_solve.smt2")
      val writer = new java.io.PrintWriter(tempFile)
      writer.write(smtModel)
      writer.close()
      
      val boolExps = ctx.parseSMTLIB2File(tempFile.getAbsolutePath, Array(), Array(), Array(), Array())
      boolExps.foreach(solver.assert)
      
      // Note: Objectives would need to be parsed/converted to ArithExpr
      // This is a simplified version
      
      tempFile.delete()
      solver.solve()
    } catch {
      case e: Throwable =>
        Unknown(s"Parse error: ${e.getMessage}")
    }
  }
}
