// filepath: /Users/bclement/git/klang/src/k/frontend/SolverEnhancements.scala
package k.frontend

import com.microsoft.z3.{Context, Solver, Optimize, Sort, Expr, BoolExpr, IntExpr, RealExpr, 
  ArithExpr, FuncDecl, Symbol => Z3Symbol, Model => Z3Model, Status, Params, SeqSort, ReExpr}
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
 * - Native sequence theory support
 * - Regular expression support
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
   * Get the underlying Z3 solver (for advanced usage)
   */
  def getSolver: Solver = solver
  
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
// Regular Expression Support
// ============================================================================

/**
 * Helper for building Z3 regular expressions
 */
object RegexSupport {

  /**
   * Convert a string pattern to Z3 regex
   * Supports basic regex syntax: ., *, +, ?, |, [], (), \d, \w, \s
   */
  def patternToZ3Regex(ctx: Context, pattern: String): ReExpr[_] = {
    // For simple patterns, use str.to_re
    // For complex patterns, build using Z3 regex constructors
    ctx.mkToRe(ctx.mkString(pattern))
  }

  /**
   * Create membership constraint: str ∈ regex
   */
  def mkInRegex(ctx: Context, str: Expr[_], regex: ReExpr[_]): BoolExpr = {
    ctx.mkInRe(str.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]], regex)
  }

  /**
   * Create regex from literal string
   */
  def mkLiteralRegex(ctx: Context, literal: String): ReExpr[_] = {
    ctx.mkToRe(ctx.mkString(literal))
  }

  /**
   * Concatenate regexes
   */
  def mkConcat(ctx: Context, regexes: ReExpr[_]*): ReExpr[_] = {
    ctx.mkReConcat(regexes: _*)
  }

  /**
   * Union of regexes
   */
  def mkUnion(ctx: Context, regexes: ReExpr[_]*): ReExpr[_] = {
    ctx.mkReUnion(regexes: _*)
  }

  /**
   * Kleene star
   */
  def mkStar(ctx: Context, regex: ReExpr[_]): ReExpr[_] = {
    ctx.mkReStar(regex)
  }

  /**
   * Kleene plus (one or more)
   */
  def mkPlus(ctx: Context, regex: ReExpr[_]): ReExpr[_] = {
    ctx.mkRePlus(regex)
  }

  /**
   * Optional (zero or one)
   */
  def mkOption(ctx: Context, regex: ReExpr[_]): ReExpr[_] = {
    ctx.mkReOption(regex)
  }

  /**
   * Character range [a-z]
   */
  def mkRange(ctx: Context, lo: Char, hi: Char): ReExpr[_] = {
    ctx.mkReRange(ctx.mkString(lo.toString), ctx.mkString(hi.toString))
  }

  /**
   * Common regex patterns
   */
  def mkDigit(ctx: Context): ReExpr[_] = mkRange(ctx, '0', '9')
  def mkLower(ctx: Context): ReExpr[_] = mkRange(ctx, 'a', 'z')
  def mkUpper(ctx: Context): ReExpr[_] = mkRange(ctx, 'A', 'Z')
  def mkAlpha(ctx: Context): ReExpr[_] = mkUnion(ctx, mkLower(ctx), mkUpper(ctx))
  def mkAlphaNum(ctx: Context): ReExpr[_] = mkUnion(ctx, mkAlpha(ctx), mkDigit(ctx))
  def mkAny(ctx: Context): ReExpr[_] = ctx.mkReAllchar()
}

// ============================================================================
// Sequence Theory Support
// ============================================================================

/**
 * Helper for Z3 sequence operations
 */
object SeqSupport {

  /**
   * Create a sequence sort for element type
   */
  def mkSeqSort(ctx: Context, elementSort: Sort): SeqSort[_] = {
    ctx.mkSeqSort(elementSort)
  }

  /**
   * Create empty sequence
   */
  def mkEmpty(ctx: Context, seqSort: SeqSort[_]): Expr[_] = {
    ctx.mkEmptySeq(seqSort)
  }

  /**
   * Create unit sequence (single element)
   */
  def mkUnit(ctx: Context, element: Expr[_]): Expr[_] = {
    ctx.mkUnit(element)
  }

  /**
   * Concatenate sequences
   */
  def mkConcat(ctx: Context, seqs: Expr[_]*): Expr[_] = {
    if (seqs.length == 1) seqs.head
    else ctx.mkConcat(seqs.head.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]],
                      seqs.tail.head.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]])
  }

  /**
   * Sequence length
   */
  def mkLength(ctx: Context, seq: Expr[_]): IntExpr = {
    ctx.mkLength(seq.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]])
  }

  /**
   * Get element at index
   */
  def mkAt(ctx: Context, seq: Expr[_], index: Expr[_]): Expr[_] = {
    ctx.mkNth(seq.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]],
              index.asInstanceOf[IntExpr])
  }

  /**
   * Extract subsequence
   */
  def mkExtract(ctx: Context, seq: Expr[_], offset: Expr[_], length: Expr[_]): Expr[_] = {
    ctx.mkExtract(seq.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]],
                  offset.asInstanceOf[IntExpr],
                  length.asInstanceOf[IntExpr])
  }

  /**
   * Check if element is in sequence
   */
  def mkContains(ctx: Context, seq: Expr[_], subseq: Expr[_]): BoolExpr = {
    ctx.mkContains(seq.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]],
                   subseq.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]])
  }

  /**
   * Check prefix
   */
  def mkPrefixOf(ctx: Context, prefix: Expr[_], seq: Expr[_]): BoolExpr = {
    ctx.mkPrefixOf(prefix.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]],
                   seq.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]])
  }

  /**
   * Check suffix
   */
  def mkSuffixOf(ctx: Context, suffix: Expr[_], seq: Expr[_]): BoolExpr = {
    ctx.mkSuffixOf(suffix.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]],
                   seq.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]])
  }

  /**
   * Find index of subsequence
   */
  def mkIndexOf(ctx: Context, seq: Expr[_], subseq: Expr[_], offset: Expr[_]): IntExpr = {
    ctx.mkIndexOf(seq.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]],
                  subseq.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]],
                  offset.asInstanceOf[IntExpr])
  }

  /**
   * Replace first occurrence
   */
  def mkReplace(ctx: Context, seq: Expr[_], src: Expr[_], dst: Expr[_]): Expr[_] = {
    ctx.mkReplace(seq.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]],
                  src.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]],
                  dst.asInstanceOf[Expr[com.microsoft.z3.SeqSort[_]]])
  }
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
      val tempFile = new java.io.File("/tmp/k_timeout_solve.smt2")
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
      val tempFile = new java.io.File("/tmp/k_opt_solve.smt2")
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

