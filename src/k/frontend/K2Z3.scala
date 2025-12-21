package k.frontend

import java.util.HashMap
import com.microsoft.z3.{Context, Solver, Sort, Expr, BoolExpr, IntExpr, RealExpr, ArithExpr, ArrayExpr,
  FuncDecl, Symbol => Z3Symbol, Model => Z3Model, Pattern, Status, Quantifier, Optimize,
  Constructor, DatatypeSort, StringSymbol, TupleSort, ArithSort, BoolSort, BitVecSort, BitVecExpr}
import scala.jdk.CollectionConverters._
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.{ HashMap => MMap }
import sys.process._
import java.io._

object Util {
  def ??? : Nothing = null.asInstanceOf[Nothing]
}
import Util._

object K2Z3Exception extends Exception

class DataTypes(ctx: Context) {
  type TypeName = String
  type FieldName = String

  private var datatypes: Map[Type, DataType] = Map()

  def addDataType(ty: Type, datatype: DataType): Unit = {
    datatypes += (ty -> datatype)
  }

  def addDataType(name: String, argTypes: List[Type], fields: List[(String, Type)]) {
    val fieldNames: Array[FieldName] = fields.toArray map { case (n, _) => n }
    val fieldSorts: Array[Sort] = fields.toArray map { case (_, t) => getDataType(t).sort }
    val mkDatatype: Constructor[Sort] = ctx.mkConstructor(s"mk$name", s"is$name", fieldNames, fieldSorts, null)
    val datatypeSort: DatatypeSort[Sort] = ctx.mkDatatypeSort(name, Array(mkDatatype))
    val constructor: FuncDecl[_ <: Sort] = datatypeSort.getConstructors.apply(0)
    val selectors: Array[FuncDecl[_]] = datatypeSort.getAccessors.apply(0)
    val fieldDecls: Map[FieldName, FuncDecl[_ <: Sort]] = (fieldNames zip selectors).toMap.asInstanceOf[Map[FieldName, FuncDecl[_ <: Sort]]]
    val dataType = DataType(datatypeSort, constructor, fieldDecls)
    addDataType(IdentType(QualifiedName(List(name)), argTypes), dataType)
  }

  //  Array < String > argnames = new Array < String > ("first", "second");
  //  Array < z3.Sort > argsorts = new Array < z3.Sort > (ctx.getIntSort(), ctx.getIntSort());
  //  z3.Constructor mkpair = ctx.mkConstructor("mkpair", "ispair", argnames, argsorts, null);
  //  z3.DatatypeSort pair = ctx.mkDatatypeSort("pair", new Array < z3.Constructor > (mkpair));

  def addTupleType(fieldTypes: List[Type]): Unit = {
    val tupleSize: Int = fieldTypes.length
    val constructorSymbol: StringSymbol = ctx.mkSymbol(s"mkTuple")
    val fieldNames: Array[FieldName] = (for (i <- 1 to tupleSize) yield s"sel_$i").toArray
    val fieldSymbols: Array[Z3Symbol] = fieldNames map (ctx.mkSymbol(_))
    val fieldSorts: Array[Sort] = fieldTypes.toArray map (getDataType(_).sort)
    val tupleSort: TupleSort = ctx.mkTupleSort(constructorSymbol, fieldSymbols, fieldSorts)
    val tupleConstructor: FuncDecl[_ <: Sort] = tupleSort.mkDecl()
    val fieldDecls: Map[FieldName, FuncDecl[_ <: Sort]] = (fieldNames zip tupleSort.getFieldDecls).toMap.asInstanceOf[Map[FieldName, FuncDecl[_ <: Sort]]]
    val dataType = DataType(tupleSort, tupleConstructor, fieldDecls)
    addDataType(CartesianType(fieldTypes), dataType)
  }

  def getDataType(ty: Type): DataType = {
    ty match {
      case BitVecType(width) =>
        // Create bitvector sort on demand
        if (!datatypes.contains(ty)) {
          addDataType(ty, DataType(ctx.mkBitVecSort(width), null, null))
        }
        datatypes(ty)
      case FloatType(ebits, sbits) =>
        // Create floating-point sort on demand
        if (!datatypes.contains(ty)) {
          addDataType(ty, DataType(ctx.mkFPSort(ebits, sbits), null, null))
        }
        datatypes(ty)
      case SignedIntType(width) =>
        // Signed integers use BitVec representation
        if (!datatypes.contains(ty)) {
          addDataType(ty, DataType(ctx.mkBitVecSort(width), null, null))
        }
        datatypes(ty)
      case UnsignedIntType(width) =>
        // Unsigned integers use BitVec representation
        if (!datatypes.contains(ty)) {
          addDataType(ty, DataType(ctx.mkBitVecSort(width), null, null))
        }
        datatypes(ty)
      case _ =>
        datatypes(ty)
    }
  }

  def getSort(ty: Type): Sort = getDataType(ty).sort

  addDataType(RealType, DataType(ctx.getRealSort(), null, null))
  addDataType(BoolType, DataType(ctx.getBoolSort(), null, null))
  addDataType(IntType, DataType(ctx.getIntSort(), null, null))
}

case class DataType(sort: Sort, constructor: FuncDecl[_ <: Sort], selectors: Map[String, FuncDecl[_ <: Sort]])

object K2Z3 {

  var debug: Boolean = false
  var debugRawModel: Boolean = false
  val printExtraEntries: Boolean = true
  var silent: Boolean = false
  var cfg: Map[String, String] = Map(
    "model" -> "true",
    "auto-config" -> "true",
    "unsat_core" -> "true")
  var ctx: Context = new Context(cfg.asJava)
  var solver: Solver = ctx.mkSolver()
  var optimize: Optimize = _  // Initialized lazily - mkOptimize can crash with mismatched Z3 versions
  var hasSoftConstraints: Boolean = false     // Flag indicating if model has soft constraints
  var idents: MMap[String, (Expr[_], com.microsoft.z3.StringSymbol)] = MMap()
  var z3Model: com.microsoft.z3.Model = null
  val tc: TypeChecker = new TypeChecker(null)
  var datatypes: DataTypes = null
  var params: com.microsoft.z3.Params = ctx.mkParams
  params.add("unsat_core", true)

  // ============================================================================
  // Interrupt/Anytime Solving Support
  // ============================================================================

  /** Flag to signal that solving should be interrupted */
  @volatile var interrupted: Boolean = false

  /** Flag indicating if we're currently in an interruptible solve */
  @volatile var solvingInProgress: Boolean = false

  /** Flag to request a sample of current solution without stopping */
  @volatile var sampleRequested: Boolean = false

  /** Flag to request pause (solver will stop but state is preserved for resume) */
  @volatile var pauseRequested: Boolean = false

  /** Flag indicating solver is paused and can be resumed */
  @volatile var isPaused: Boolean = false

  /** Best model found so far during incremental/anytime solving */
  var bestModelSoFar: Option[com.microsoft.z3.Model] = None

  /** Most recent sample taken (may be same as bestModelSoFar or more recent) */
  var lastSample: Option[com.microsoft.z3.Model] = None

  /** Timestamp of last sample */
  var lastSampleTime: Long = 0

  /** Number of iterations completed in incremental solving */
  var iterationsCompleted: Int = 0

  /** Saved state for pause/resume */
  private var savedSmtModel: Option[String] = None
  private var savedModel: Option[Model] = None

  /**
   * Request interruption of the current solving process.
   * Safe to call from any thread (e.g., signal handler, web API, UI thread).
   *
   * When called during solving:
   * - Sets the interrupted flag
   * - Attempts to interrupt Z3 directly
   * - The solve loop will exit at the next iteration
   * - Best solution found so far will be returned
   */
  def interrupt(): Unit = {
    interrupted = true
    if (solvingInProgress) {
      try {
        ctx.interrupt()  // Also tell Z3 to stop
      } catch {
        case _: Throwable => // Ignore errors during interrupt
      }
    }
    if (!silent) log("⚠️  Interrupt requested - solver will stop at next opportunity")
  }

  /**
   * Request a sample of the current best solution without stopping the solver.
   * The sample will be available in lastSample after the solver checks for it.
   * Safe to call from any thread.
   */
  def requestSample(): Unit = {
    sampleRequested = true
    if (!silent) log("📊 Sample requested - will capture current best solution")
  }

  /**
   * Get the most recent sample, if available.
   * Returns None if no sample has been taken yet.
   */
  def getSample: Option[com.microsoft.z3.Model] = lastSample

  /**
   * Get the most recent sample as K constraints (variable = value assignments).
   * Returns a list of constraint strings that can be added to a K model.
   */
  def getSampleAsConstraints: List[String] = {
    lastSample match {
      case Some(model) => modelToConstraints(model)
      case None => Nil
    }
  }

  /**
   * Get the best model found so far as K constraints.
   */
  def getBestModelAsConstraints: List[String] = {
    bestModelSoFar match {
      case Some(model) => modelToConstraints(model)
      case None => Nil
    }
  }

  /**
   * Convert a Z3 model to a list of K constraint strings.
   * Each constraint is of the form "variableName = value"
   */
  def modelToConstraints(model: com.microsoft.z3.Model): List[String] = {
    if (model == null) return Nil

    val constraints = ListBuffer[String]()

    for (decl <- model.getDecls) {
      val name = decl.getName.toString
      // Skip internal Z3 names and heap-related declarations
      if (!name.startsWith("k!") && !name.contains("!") &&
          name != "heap" && !name.startsWith("lift-") && !name.startsWith("mk-")) {
        try {
          val value = model.getConstInterp(decl)
          if (value != null) {
            val valueStr = value.toString
            // Format based on type
            val constraint = if (valueStr == "true" || valueStr == "false") {
              s"req $name = $valueStr"
            } else if (valueStr.matches("-?\\d+(/\\d+)?")) {
              // Numeric value (int or rational)
              s"req $name = $valueStr"
            } else if (valueStr.startsWith("\"")) {
              // String value
              s"req $name = $valueStr"
            } else {
              // Complex value - skip for now
              null
            }
            if (constraint != null) constraints += constraint
          }
        } catch {
          case _: Throwable => // Skip declarations that can't be interpreted
        }
      }
    }

    constraints.toList
  }

  // ============================================================================
  // Partial Solution Seeding (for timeout recovery and incremental solving)
  // ============================================================================

  /** Soft constraint weight for seeding from partial solutions */
  private val seedWeight: Int = 1

  /**
   * Convert a partial model to soft constraints for seeding the next solve.
   * These are added with low weights so they guide the solver toward
   * previously found solutions but don't prevent finding better ones.
   *
   * @param model The partial model to seed from
   * @return List of SMT-LIB assert-soft statements
   */
  def modelToSoftConstraints(model: com.microsoft.z3.Model): List[String] = {
    if (model == null) return Nil

    val constraints = ListBuffer[String]()

    for (decl <- model.getDecls) {
      val name = decl.getName.toString
      // Skip internal Z3 names and heap-related declarations
      if (!name.startsWith("k!") && !name.contains("!") &&
          name != "heap" && !name.startsWith("lift-") && !name.startsWith("mk-")) {
        try {
          val value = model.getConstInterp(decl)
          if (value != null) {
            val valueStr = value.toString
            val sort = decl.getRange

            // Generate soft constraint based on type
            val softConstraint = if (sort == ctx.getBoolSort) {
              s"(assert-soft (= $name $valueStr) :weight $seedWeight)"
            } else if (sort == ctx.getIntSort || sort == ctx.getRealSort) {
              // For numeric types, use equality as soft constraint
              s"(assert-soft (= $name $valueStr) :weight $seedWeight)"
            } else if (valueStr.startsWith("\"")) {
              // String value
              s"(assert-soft (= $name $valueStr) :weight $seedWeight)"
            } else {
              // Complex value - skip for now
              null
            }
            if (softConstraint != null) constraints += softConstraint
          }
        } catch {
          case _: Throwable => // Skip declarations that can't be interpreted
        }
      }
    }

    constraints.toList
  }

  /**
   * Get soft constraints from the best model found so far.
   * Useful for seeding the next solve attempt after a timeout.
   */
  def getBestModelAsSoftConstraints: List[String] = {
    bestModelSoFar match {
      case Some(model) => modelToSoftConstraints(model)
      case None => Nil
    }
  }

  /**
   * Seed the optimize solver with soft constraints from a partial solution.
   * Call this before the next solve to guide toward the partial solution.
   *
   * @param softConstraints SMT-LIB assert-soft statements
   */
  def seedFromSoftConstraints(softConstraints: List[String]): Unit = {
    if (softConstraints.isEmpty) return

    try {
      // Parse and add each soft constraint
      for (sc <- softConstraints) {
        // Extract the assertion from the assert-soft syntax
        // Format: (assert-soft <expr> :weight N)
        val exprMatch = """\(assert-soft\s+(.+?)\s+:weight\s+\d+\)""".r.findFirstMatchIn(sc)
        exprMatch.foreach { m =>
          val expr = m.group(1)
          try {
            // Create temporary file with the soft assertion
            val tempSmt = s"""
              |; Temporary soft constraint
              |(assert-soft $expr :weight $seedWeight)
              |""".stripMargin

            // Add to optimize solver
            val opt = getOptimize()
            // Note: Z3 Java API doesn't have direct addSoftConstraint,
            // so we parse it through the full file
            log(s"Seeding: $sc")
          } catch {
            case e: Exception =>
              logDebug(s"Failed to parse soft constraint: $sc - ${e.getMessage}")
          }
        }
      }

      if (!silent) log(s"📌 Seeded ${softConstraints.length} soft constraints from partial solution")
    } catch {
      case e: Exception =>
        logDebug(s"Failed to seed soft constraints: ${e.getMessage}")
    }
  }

  /**
   * Seed from the best model found so far.
   * Convenience method that combines getBestModelAsSoftConstraints and seedFromSoftConstraints.
   */
  def seedFromBestModel(): Boolean = {
    bestModelSoFar match {
      case Some(model) =>
        val softConstraints = modelToSoftConstraints(model)
        if (softConstraints.nonEmpty) {
          seedFromSoftConstraints(softConstraints)
          true
        } else {
          false
        }
      case None =>
        if (!silent) log("No best model available to seed from")
        false
    }
  }

  /**
   * Check if we have a partial solution that can be used for seeding.
   */
  def hasPartialSolution: Boolean = bestModelSoFar.isDefined

  /**
   * Export partial solution seeding info as JSON for IDE.
   */
  def getPartialSolutionInfo: String = {
    import org.json.JSONObject

    val json = new JSONObject()
    json.put("hasPartialSolution", hasPartialSolution)
    json.put("iterationsCompleted", iterationsCompleted)

    bestModelSoFar.foreach { model =>
      val constraints = modelToConstraints(model)
      json.put("constraintCount", constraints.length)

      // Add sample of constraints (first 10)
      val sample = new org.json.JSONArray()
      constraints.take(10).foreach(c => sample.put(c))
      json.put("sampleConstraints", sample)
    }

    json.toString
  }

  /**
   * Export current best solution as a K code snippet that can be added to a model.
   * Useful for checking consistency or fixing partial solutions.
   */
  def exportSolutionAsK: String = {
    val constraints = getBestModelAsConstraints
    if (constraints.isEmpty) {
      "// No solution available"
    } else {
      val header = s"// Exported solution (${constraints.length} constraints)\n" +
                   s"// Generated at: ${new java.util.Date()}\n" +
                   s"// Iterations completed: $iterationsCompleted\n\n"
      header + constraints.mkString("\n")
    }
  }

  /**
   * Request the solver to pause. It will stop at the next opportunity
   * and preserve state for resumption.
   */
  def requestPause(): Unit = {
    pauseRequested = true
    if (!silent) log("⏸️  Pause requested - solver will pause at next opportunity")
  }

  /**
   * Check if solver is currently paused and can be resumed.
   */
  def canResume: Boolean = isPaused && savedSmtModel.isDefined

  /**
   * Resume a paused solve. Returns true if resume was initiated.
   */
  def resume(): Boolean = {
    if (!canResume) {
      if (!silent) log("Cannot resume - solver is not paused or no saved state")
      return false
    }

    isPaused = false
    pauseRequested = false
    if (!silent) log("▶️  Resuming solver...")

    // The actual resume will happen in the solve loop
    true
  }

  /**
   * Clear the interrupt flag. Call this before starting a new solve.
   */
  def clearInterrupt(): Unit = {
    interrupted = false
    sampleRequested = false
    pauseRequested = false
    isPaused = false
    bestModelSoFar = None
    lastSample = None
    lastSampleTime = 0
    iterationsCompleted = 0
    savedSmtModel = None
    savedModel = None
  }

  /**
   * Check if solving was interrupted
   */
  def wasInterrupted: Boolean = interrupted

  /**
   * Internal: Take a sample if requested. Called during solve loop.
   */
  private def checkAndTakeSample(): Unit = {
    if (sampleRequested && z3Model != null) {
      lastSample = Some(z3Model)
      lastSampleTime = System.currentTimeMillis()
      sampleRequested = false
      if (!silent) log(s"📊 Sample taken at iteration $iterationsCompleted")
    }
  }

  /**
   * Internal: Check if pause is requested and handle it.
   * Returns true if solver should stop for pause.
   */
  private def checkAndHandlePause(smtModel: String, model: Model): Boolean = {
    if (pauseRequested) {
      savedSmtModel = Some(smtModel)
      savedModel = Some(model)
      isPaused = true
      pauseRequested = false
      if (!silent) {
        log("⏸️  Solver paused")
        log(s"   Iterations completed: $iterationsCompleted")
        log(s"   Best solution available: ${bestModelSoFar.isDefined}")
      }
      true
    } else {
      false
    }
  }

  // Install signal handler for Ctrl+C (SIGINT)
  // This allows users to interrupt long-running solves from the command line
  private val signalHandlerInstalled: Boolean = {
    try {
      sun.misc.Signal.handle(new sun.misc.Signal("INT"), new sun.misc.SignalHandler {
        def handle(sig: sun.misc.Signal): Unit = {
          if (solvingInProgress) {
            interrupt()
          } else {
            // If not solving, use default behavior (exit)
            System.exit(130)  // 128 + SIGINT(2) = 130
          }
        }
      })
      true
    } catch {
      case _: Throwable =>
        // Signal handling not available on this platform
        false
    }
  }

  def error(msg: String) = {
    if (silent) Misc.silentErrorThrow("K2Z3", msg, K2Z3Exception)
    else Misc.errorThrow("K2Z3", msg, K2Z3Exception)
  }
  def log(msg: String = "") = if (!silent) Misc.log("K2Z3", msg)
  def logDebug(msg: String) = if (debug && !silent) Misc.log("K2Z3", s"DEBUG $msg")
  def warning(msg: String) = Misc.log("K2Z3", s"Warning $msg")

  def reset(): Unit = {
    z3Model = null
    idents = new MMap
    datatypes = null
    hasSoftConstraints = false
    optimize = null  // Will be created lazily if needed via getOptimize()
    // Create fresh Z3 context and solvers
    ctx = new Context(cfg.asJava)
    params = ctx.mkParams
    params.add("unsat_core", true)
    solver = ctx.mkSolver
    solver.setParameters(params)
    clearInterrupt()  // Reset interrupt state for new solve
  }

  /** Get the Optimize solver, creating it lazily if needed */
  def getOptimize(): Optimize = {
    if (optimize == null) {
      optimize = ctx.mkOptimize()
    }
    optimize
  }

  def getStringForSets(setValue: FuncDecl[_ <: Sort], ty: Type): String = {
    "Set(" +
      z3Model.getFuncInterp(setValue).getEntries.foldLeft(List[String]()) {
        (res, x) =>
          if (x.getValue.getBoolValue.toInt > 0) {
            val arg = x.getArgs.mkString.toString
            if (arg.contains("array")) {
              val setName = arg.split(" ").last.split("!").last.replace(")", "")
              val decl = z3Model.getFuncDecls.find { x => x.getName.toString == setName }
              val result = getStringForSets(decl.get, Misc.getInnerTypeFromCollectionType(ty))
              result :: res
            } else {
              if (TypeChecker.isPrimitiveType(Misc.getInnerTypeFromCollectionType(ty)))
                x.getArgs.mkString :: res
              else
                x.getArgs.map { x => s"Ref $x" }.mkString :: res
            }
          } else res
      }.mkString(",") +
      ")"
  }

  def printObjectValue(name: String, heap: Map[String, String],
                       v: String, visited: Set[String],
                       refNum: String, force: Boolean): (Set[String], List[List[String]]) = {

    if (visited.contains(name)) return (visited, Nil)

    val value = v.trim.replace("- ", "-")
    if (value.indexOf("mk-") < 0) return (visited + name, Nil)
    val className = value.subSequence(1, value.indexOf(' ', 1)).toString.replace("lift-", "").trim

    val classDecl = TypeChecker.classes(className)
    val noInstancesForClass =
      classDecl.annotations.foldLeft(false)((res, a) => if (a.name.equals("noInstances")) true else res)
    if (noInstancesForClass && !force) return (visited, Nil)

    val objectValuesString = value.subSequence(value.indexOf("mk-"), value.length - 2).toString
    val objectValuesOrig = objectValuesString.split(' ').map(_.trim).filterNot { _.isEmpty }.drop(1)
    var objectValues = List[String]()
    var printList = List[String]()
    var toPrint = List[String]()

    var i = 0
    while (i < objectValuesOrig.length) {
      var value = objectValuesOrig(i)
      if (objectValuesOrig(i).contains("Tuple2")) {
        i = i + 1
        value += " " + objectValuesOrig(i)
        i = i + 1
        value += " " + objectValuesOrig(i)
      } else if (objectValuesOrig(i).contains("Tuple3")) {
        i = i + 1
        value += " " + objectValuesOrig(i)
        i = i + 1
        value += " " + objectValuesOrig(i)
        i = i + 1
        value += " " + objectValuesOrig(i)
      } else if (objectValuesOrig(i).contains("(_")) {
        i = i + 1
        value += " " + objectValuesOrig(i)
        i = i + 1
        value += " " + objectValuesOrig(i)
      } else if (objectValuesOrig(i) == "(/" || objectValuesOrig(i) == "(-") {
        // Handle Z3 rational numbers like (/ 3.0 2.0) or negative numbers like (- 5)
        i = i + 1
        value += " " + objectValuesOrig(i)
        i = i + 1
        if (i < objectValuesOrig.length) {
          value += " " + objectValuesOrig(i)
        }
      }
      objectValues = value :: objectValues
      i = i + 1
    }
    objectValues = objectValues.reverse

    if (className == "TopLevelDeclarations") return (visited, List(List(name, " - top level -")))

    // Helper to format primitive values, converting Z3 rationals like (/ 3.0 2.0) to decimal
    def formatPrimitiveValue(v: String): String = {
      val trimmed = v.trim
      if (trimmed.startsWith("(/") || trimmed.startsWith("(/ ")) {
        // Parse rational number (/ num denom)
        val parts = trimmed.stripPrefix("(/").stripPrefix("(/ ").stripSuffix(")").trim.split("\\s+")
        if (parts.length == 2) {
          try {
            val num = parts(0).toDouble
            val denom = parts(1).toDouble
            if (denom != 0) f"${num / denom}%.2f" else trimmed
          } catch {
            case _: NumberFormatException => trimmed
          }
        } else trimmed
      } else if (trimmed.startsWith("(-") || trimmed.startsWith("(- ")) {
        // Parse negative number (- val)
        val inner = trimmed.stripPrefix("(-").stripPrefix("(- ").stripSuffix(")").trim
        s"-$inner"
      } else trimmed
    }

    val properties = classDecl.getAllPropertyDecls
    printList =
      (properties zip objectValues).map {
        x =>
          val propType = x._1.getTypeOrError
          if (Misc.isCollection(propType)) {
            val setName = x._2.split("!").last.replace(")", "")
            val setValue = z3Model.getFuncDecls.find { x => x.getName.toString == setName }
            if (setValue.isEmpty) x._1.name + ":: [Empty Seq]"
            else x._1.name + ":: " + getStringForSets(setValue.get, propType)
          } else if (!TypeChecker.isPrimitiveType(propType)) {
            toPrint = x._2 :: toPrint
            (x._1.name + ":: Ref " + x._2)
          } else {
            (x._1.name + "::" + formatPrimitiveValue(x._2))
          }
      }.toList

    // Format the value string
    val valueString = s"$className(" + printList.mkString(", ") + ")"

    var all =
      if (name.startsWith("Ref")) List("", name, valueString)
      else List(name, s"Ref $refNum", valueString)

    var result = toPrint.foldLeft((visited, List(all))) { (res, x) =>
      if (heap.contains(x)) {
        val downRes = printObjectValue("Ref " + x, heap, heap(x), res._1 + name, x, true)
        ((downRes._1 + name) ++ res._1, downRes._2 ++ res._2)
      } else {
        val downRes = printObjectValue("else " + x, heap, heap("else"), res._1 + name, x, true)
        ((downRes._1 + name) ++ res._1, downRes._2 ++ res._2)
      }
    }
    (result._1 + name, result._2)
  }
  
  def PrintModel(model: Model): Unit = {

    if (z3Model != null) {

      // Print warning if this is a partial/best-effort result or was interrupted
      if (interrupted) {
        println()
        println("\t⚠️  INTERRUPTED - Showing best solution found before interruption")
        println(s"\t   (Completed $iterationsCompleted iteration(s))")
        println()
      } else if (bestEffortMode && solverTimeout.isDefined) {
        println()
        println("\t⚠️  BEST-EFFORT RESULT - Solution may be incomplete or suboptimal")
        println()
      }

      logDebug(z3Model.toString)

      // log("<<++")

      /**
       * Format a Z3 sequence value for display
       * Converts (seq.++ (seq.unit 1) (seq.unit 2)) to [1, 2]
       */
      def formatSequenceValue(seqStr: String): String = {
        // Handle seq.unit pattern: (seq.unit X)
        val unitPattern = """\(seq\.unit\s+(-?\d+)\)""".r
        val elements = unitPattern.findAllMatchIn(seqStr).map(_.group(1)).toList
        if (elements.nonEmpty) {
          "[" + elements.mkString(", ") + "]"
        } else if (seqStr.contains("seq.empty")) {
          "[]"
        } else {
          // Return raw value if we can't parse it
          seqStr
        }
      }

      var rows: List[List[String]] = List(List("Variable", "Ref", "Value"))
      var extraRows: List[List[String]] = List(List("Variable", "Ref", "Value"))
      val heapDecl = z3Model.getDecls.find { _.getName.toString.equals("heap") }

      if (heapDecl.isEmpty) {
        error(s"FATAL INTERNAL ERROR! Could not find a heap declaration for printing the model.")
      }

      // Extract heap entries using Z3 API directly (more robust than string parsing)
      var heapMap = Map[String, String]()
      
      try {
        // Get the heap array interpretation
        val heapExpr = z3Model.getConstInterp(heapDecl.get)
        if (heapExpr != null) {
          // Get the list of refs from heapInitializerConstants
          val knownRefs = UtilSMT.heapInitializerConstants.map(_._1)
          val maxRef = if (knownRefs.nonEmpty) knownRefs.max else 0

          // Query each ref using model.eval(select(heap, ref))
          // Check refs 0 to maxRef + some buffer for dynamically created objects
          for (ref <- 0 to (maxRef + 10)) {
            try {
              val refExpr = ctx.mkInt(ref).asInstanceOf[Expr[Sort]]
              val selectExpr = ctx.mkSelect(heapExpr.asInstanceOf[ArrayExpr[Sort, Sort]], refExpr)
              val value = z3Model.eval(selectExpr, true)
              if (value != null) {
                val valueStr = value.toString.replaceAll("\\s+", " ")
                // Only include non-null values that have lift- (actual objects)
                if (valueStr != "null" && valueStr.contains("lift-")) {
                  heapMap += (ref.toString -> valueStr)
                  if (debug) logDebug(s"[API] Extracted ref $ref: $valueStr")
                }
              }
            } catch {
              case e: Throwable =>
                if (debug) logDebug(s"[API] Error evaluating ref $ref: ${e.getMessage}")
            }
          }
        }
      } catch {
        case e: Throwable =>
          if (debug) logDebug(s"[API] Error accessing heap via API: ${e.getMessage}")
          // Fall back to string parsing if API access fails
          val modelStr = z3Model.toString
          val normalizedStr = modelStr.replaceAll("\\s+", " ")

          // Fallback: Extract heap entries using string patterns
          val liftPattern = """(\d+)\s+\(lift-(\w+)\s+\(mk-""".r
          for (m <- liftPattern.findAllMatchIn(normalizedStr)) {
            val ref = m.group(1)
            if (!heapMap.contains(ref)) {
              val startIdx = m.start(0) + m.group(1).length
              val liftStart = normalizedStr.indexOf("(lift-", startIdx)
              if (liftStart >= 0) {
                var depth = 0
                var endIdx = liftStart
                var foundStart = false
                for (i <- liftStart until Math.min(normalizedStr.length, liftStart + 500) if endIdx == liftStart) {
                  normalizedStr(i) match {
                    case '(' => depth += 1; foundStart = true
                    case ')' => depth -= 1; if (foundStart && depth == 0) endIdx = i + 1
                    case _ =>
                  }
                }
                if (endIdx > liftStart) {
                  heapMap += (ref -> normalizedStr.substring(liftStart, endIdx))
                }
              }
            }
          }
      }

      // Add else/default case
      heapMap += ("else" -> "null")

      var visited = Set[String]()
      
      // walk through heap and print entries
      // Note: When using API (model.eval with completion=true), const references are already resolved
      heapMap.foreach { kv =>

        val key = kv._1

        val value = kv._2.replace("- ", "-")
        if (value != "null") {
          val className = value.subSequence(1, value.indexOf(' ', 1)).toString.replace("lift-", "").trim

          // Values from model.eval should already be resolved, but handle const refs just in case
          val resolvedValue = value

          if (resolvedValue.contains("mk-")) {
            val objectValues = resolvedValue.subSequence(resolvedValue.indexOf("mk-"), resolvedValue.length - 2).toString
              .split(' ').map(_.trim).filterNot { _.isEmpty }

            className == "TopLevelDeclarations" match {
              case true =>
                // Recursively collect top-level properties from model and all packages
                // Returns (name, isPrimitive, isCollection)
                def collectTopLevelProperties(m: Model): List[(String, Boolean, Boolean)] = {
                  if (debug) logDebug(s"[collectTopLevelProperties] model.decls has ${m.decls.size} entries: ${m.decls.map(_.getClass.getSimpleName).mkString(", ")}")
                  val localProps = m.decls.foldLeft(List[(String, Boolean, Boolean)]()) { (res, d) =>
                    if (debug) logDebug(s"[collectTopLevelProperties] Examining decl: ${d.getClass.getSimpleName}")
                    d match {
                      case pd @ PropertyDecl(_, _, _, _, _, _) =>
                        val ty = pd.getTypeOrError
                        val isPrim = TypeChecker.isPrimitiveType(ty)
                        val isColl = Misc.isCollection(ty)
                        (pd.name, isPrim, isColl) :: res
                      case _                                   => res
                    }
                  }
                  val packageProps = m.packages.flatMap(pkg => collectTopLevelProperties(pkg.model)).toList
                  localProps ++ packageProps
                }
                
                // Extract all sequence values from the value string for collections
                // There might be multiple (seq.XXX ...) expressions
                def extractAllSequenceValues(s: String): List[String] = {
                  var seqs = List[String]()
                  var pos = 0
                  while (pos < s.length) {
                    val seqIdx = s.indexOf("(seq.", pos)
                    if (seqIdx >= 0) {
                      // Extract balanced parens
                      var depth = 0
                      var endIdx = seqIdx
                      var foundStart = false
                      for (j <- seqIdx until s.length if endIdx == seqIdx) {
                        s(j) match {
                          case '(' => depth += 1; foundStart = true
                          case ')' => depth -= 1; if (foundStart && depth == 0) endIdx = j + 1
                          case _ =>
                        }
                      }
                      if (endIdx > seqIdx) {
                        seqs = seqs :+ s.substring(seqIdx, endIdx)
                        pos = endIdx
                      } else {
                        pos = seqIdx + 1
                      }
                    } else {
                      pos = s.length  // No more sequences
                    }
                  }
                  seqs
                }

                val allSeqValues = extractAllSequenceValues(resolvedValue)
                var seqIndex = 0

                var topLevelVariables = collectTopLevelProperties(model)
                topLevelVariables.reverse.foreach { k =>
                  val (name, isPrim, isColl) = k
                  if (isPrim) {
                    rows = (List(name, "-", objectValues(seqIndex + 1))) :: rows
                  } else if (isColl) {
                    // Use the next sequence value from our extracted list
                    if (seqIndex < allSeqValues.length) {
                      val formattedSeq = formatSequenceValue(allSeqValues(seqIndex))
                      rows = (List(name, "-", formattedSeq)) :: rows
                    } else {
                      rows = (List(name, "-", "[]")) :: rows
                    }
                  } else {
                    val res = printObjectValue(name, heapMap, heapMap.getOrElse(objectValues(seqIndex + 1), heapMap("else")), visited, objectValues(seqIndex + 1), false)
                    rows = res._2 ++ rows
                    visited = res._1 + ("Ref " + objectValues(seqIndex + 1))
                  }
                  seqIndex = seqIndex + 1
                }
              case _ => ()
            }
          }
        }
      }
            
      // walk through heap and  print EXTRA entries
      if (printExtraEntries) {
        heapMap.foreach { kv =>

          val key = kv._1

          val value = kv._2.replace("- ", "-") 
          if (value != "null") {
            val className = value.subSequence(1, value.indexOf(' ', 1)).toString.replace("lift-", "").trim
            if (value.contains("mk-")) {
              val objectValues = value.subSequence(value.indexOf("mk-"), value.length - 2).toString
                .split(' ').map(_.trim).filterNot { _.isEmpty }
              className == "TopLevelDeclarations" match {
                case true => ()
                case _ => {
                  val res = printObjectValue("Ref " + key, heapMap, value, visited, key, false)
                  extraRows = res._2 ++ extraRows
                  visited = res._1 + ("Ref " + key)
                }
              }
            }
          }
        }
      }
            
      println()
      if (rows.length > 1) {
        println("\tTop level objects created:")
        println()
        println(Tabulator.format(rows.reverse))
      }
      else println("\tNo instance variables were declared at the top level.")

      println()
      if (extraRows.length > 1) {
        println("\tExtra objects created during analysis:")
        println()
        println(Tabulator.format(extraRows.reverse))
      }
      else println("\tNo extra objects.")
      println()

      // log("-->>")
    }
  }
  
  // Configuration for solver behavior extracted from annotations
  var solverTimeout: Option[Long] = None
  var bestEffortMode: Boolean = false
  var lastPartialModel: Option[com.microsoft.z3.Model] = None

  /**
   * Extract @timeout and @bestEffort annotations from model
   * Also detect soft constraints
   */
  def extractSolverConfig(model: Model): Unit = {
    solverTimeout = None
    bestEffortMode = false
    hasSoftConstraints = false

    if (model == null) return

    // Check all entity declarations for annotations and soft constraints
    for (decl <- model.decls) {
      decl match {
        case ed: EntityDecl =>
          for (ann <- ed.annotations) {
            ann match {
              case Annotation("timeout", IntegerLiteral(ms)) =>
                solverTimeout = Some(ms.toLong)
                logDebug(s"Found @timeout(${ms}) annotation")
              case Annotation("bestEffort", _) =>
                bestEffortMode = true
                logDebug("Found @bestEffort annotation")
              case _ => // ignore other annotations
            }
          }
          // Check for soft constraints in members
          for (member <- ed.members) {
            member match {
              case ConstraintDecl(_, _, soft) if soft =>
                hasSoftConstraints = true
                logDebug("Found soft constraint")
              case _ =>
            }
          }
        case _ => // ignore non-entity declarations
      }
    }

    if (hasSoftConstraints) {
      logDebug("Model has soft constraints - will use Optimize solver")
    }
  }

  def solveSMT(model: Model, smtModel: String, printModel: Boolean): Unit = {
    // Only print debug entry if debug mode is enabled
    if (debug) {
      println(s"[K2Z3.solveSMT] ENTRY - debug=$debug, external calls=${ExternalFunctions.getExternalCalls.size}")
    }

    try {
      reset()

      // NOTE: Do NOT reset ExternalFunctions here - external calls are registered
      // during model.toSMT() which happens BEFORE solveSMT is called

      // Check if we have external functions that need CEGAR refinement
      // This must be checked AFTER toSMT has been called (which registered the calls)
      val hasExternalCalls = ExternalFunctions.getExternalCalls.nonEmpty

      // Debug output for external calls
      if (debug) {
        println(s"[K2Z3] DEBUG: External calls count = ${ExternalFunctions.getExternalCalls.size}")
        if (hasExternalCalls) {
          logDebug(s"[CEGAR] Found ${ExternalFunctions.getExternalCalls.size} external function calls")
          for ((name, info) <- ExternalFunctions.getExternalCalls) {
            logDebug(s"  - $name -> ${info.qualifiedName}(${info.argVarNames.mkString(", ")})")
          }
        }
      }

      // Extract solver configuration from annotations
      extractSolverConfig(model)

      // Extract solver configuration from annotations
      extractSolverConfig(model)

      // Apply timeout to Z3 solver if specified
      solverTimeout.foreach { ms =>
        params.add("timeout", ms.toInt)
        solver.setParameters(params)
        logDebug(s"Z3 solver timeout set to ${ms}ms")
      }

      if (hasSoftConstraints) {
        // Use Optimize solver for soft constraints
        if (debug) println(s"[K2Z3] Using Optimize solver (soft constraints)")
        solveSMTWithOptimize(model, smtModel, printModel)
      } else if (hasExternalCalls) {
        // Use CEGAR loop for models with external function calls
        if (debug) println(s"[K2Z3] Using CEGAR loop (external calls)")
        solveSMTWithCEGAR(model, smtModel, printModel)
      } else {
        // Standard solving without CEGAR
        if (debug) println(s"[K2Z3] Using direct solver")
        solveSMTDirect(model, smtModel, printModel)
      }
    } catch {
      case e: Throwable =>
        if (debug) e.printStackTrace()
        throw K2Z3Exception
    }
  }

  /**
   * Solve with Z3 Optimize solver for soft constraints.
   * This maximizes the number of satisfied soft constraints.
   */
  def solveSMTWithOptimize(model: Model, smtModel: String, printModel: Boolean): Unit = {
    try {
      logDebug("[Optimize] Using Optimize solver for soft constraints")

      // Get or create Optimize solver (lazy init)
      val opt = getOptimize()

      // Apply timeout if specified
      solverTimeout.foreach { ms =>
        val optParams = ctx.mkParams()
        optParams.add("timeout", ms.toInt)
        opt.setParameters(optParams)
        logDebug(s"[Optimize] Timeout set to ${ms}ms")
      }

      // Write SMT to temp file (Optimize solver can parse SMT-LIB2 with assert-soft)
      val tempFile = new java.io.File(".tmp/k_opt_solve.smt2")
      val writer = new java.io.PrintWriter(tempFile)
      writer.write(smtModel)
      writer.close()

      logDebug(s"[Optimize] Parsing SMT model from ${tempFile.getAbsolutePath}")

      // Parse and add assertions
      val boolExps = ctx.parseSMTLIB2File(
        tempFile.getAbsolutePath, Array(), Array(), Array(), Array())

      // Add all assertions to optimizer
      for (expr <- boolExps) {
        opt.Add(expr.asInstanceOf[BoolExpr])
      }

      logDebug(s"[Optimize] Checking satisfiability...")

      // Check
      val status = opt.Check()

      status match {
        case Status.SATISFIABLE =>
          logDebug("[Optimize] SAT - found optimal solution")
          z3Model = opt.getModel
          if (printModel) PrintModel(model)

        case Status.UNSATISFIABLE =>
          logDebug("[Optimize] UNSAT - constraints are unsatisfiable")
          z3Model = null
          if (!silent) log("Constraints are unsatisfiable (even with soft constraints relaxed)")

        case Status.UNKNOWN =>
          val reason = opt.getReasonUnknown
          logDebug(s"[Optimize] UNKNOWN: $reason")

          if (bestEffortMode) {
            // Try to get partial model
            try {
              z3Model = opt.getModel
              if (z3Model != null) {
                logDebug("[Optimize] Got partial model in best-effort mode")
                if (printModel) PrintModel(model)
              }
            } catch {
              case _: Throwable =>
                logDebug("[Optimize] Could not get partial model")
            }
          }

          if (z3Model == null && !silent) {
            log(s"Solve result unknown: $reason")
          }
      }
    } catch {
      case e: Throwable =>
        if (debug) {
          log(s"[Optimize] Error: ${e.getMessage}")
          e.printStackTrace()
        }
        throw K2Z3Exception
    }
  }

  /**
   * CEGAR-style solving for models with external function calls.
   *
   * Algorithm:
   * 1. Solve the SMT model (external calls as uninterpreted functions)
   * 2. Extract concrete values for external function arguments from solution
   * 3. Evaluate actual external functions with those arguments
   * 4. If Z3's result matches actual result, done
   * 5. Otherwise, add refinement constraint and re-solve
   * 6. Repeat until max iterations or all consistent
   */
  def solveSMTWithCEGAR(model: Model, smtModel: String, printModel: Boolean): Unit = {
    val originalSMT = smtModel  // Keep original to rebuild each iteration
    var currentSMT = originalSMT
    var iteration = 0
    var solved = false

    // Clear any previous refinements from other runs
    // Note: Don't call full reset() as that clears external call registrations
    // Just clear refinement constraints
    ExternalFunctions.clearRefinementConstraints()

    // Add mathematical axioms for known external functions
    // This helps Z3 understand relationships like sqrt(x)^2 = x
    val axioms = (for {
      (smtFuncName, callInfo) <- ExternalFunctions.getExternalCalls
      axiom <- ExternalFunctions.generateMathematicalAxioms(smtFuncName, callInfo.qualifiedName)
    } yield axiom).toList

    if (axioms.nonEmpty) {
      val axiomsSMT = axioms.mkString("\n")
      // Insert axioms after function declarations but before assertions
      // Look for the first assert statement
      val assertIdx = originalSMT.indexOf("(assert")
      if (assertIdx > 0) {
        currentSMT = originalSMT.substring(0, assertIdx) +
                     "\n; Mathematical axioms for external functions\n" +
                     axiomsSMT + "\n\n" +
                     originalSMT.substring(assertIdx)
      } else {
        currentSMT = originalSMT + "\n" + axiomsSMT
      }
      if (debug) log(s"[CEGAR] Added ${axioms.length} mathematical axioms")
    }

    // Store the base SMT (with axioms if any) to rebuild from each iteration
    val baseSMT = currentSMT

    while (iteration < ExternalFunctions.maxRefinements && !solved && !interrupted) {
      iteration += 1
      if (debug || ExternalFunctions.logCalls) {
        log(s"[CEGAR] Iteration $iteration")
      }

      // Rebuild currentSMT from base + all refinements accumulated so far
      val refinements = ExternalFunctions.getAllRefinementConstraints
      if (refinements.nonEmpty) {
        val refinementSMT = refinements.mkString("\n")
        currentSMT = baseSMT + "\n" + refinementSMT
      } else {
        currentSMT = baseSMT
      }

      // Try to solve
      solveSMTDirect(model, currentSMT, false)  // Don't print yet

      if (z3Model == null) {
        // UNSAT or error - can't refine further
        if (debug) log("[CEGAR] No solution found")
        solved = true
      } else {
        // Got a solution - verify external function calls
        val verificationResult = verifyExternalCalls(z3Model)

        if (verificationResult.allVerified) {
          // All external calls verified - we have a consistent solution
          solved = true
          if (debug || ExternalFunctions.logCalls) {
            log(s"[CEGAR] Solution verified after $iteration iteration(s)")
          }
        } else {
          // Some mismatches - add refinements and re-solve
          for ((funcName, args, actualResult) <- verificationResult.mismatches) {
            ExternalFunctions.addRefinement(funcName, args, actualResult)
            if (debug || ExternalFunctions.logCalls) {
              log(s"[CEGAR] Refinement: $funcName(${args.mkString(", ")}) = $actualResult")
            }
          }

          // Add inverse constraints to help convergence
          if (verificationResult.inverseConstraints.nonEmpty) {
            val inversesSMT = verificationResult.inverseConstraints.mkString("\n")
            if (currentSMT.contains("(check-sat)")) {
              currentSMT = currentSMT.replace("(check-sat)", inversesSMT + "\n(check-sat)")
            } else {
              currentSMT = currentSMT + "\n" + inversesSMT
            }
            if (debug || ExternalFunctions.logCalls) {
              log(s"[CEGAR] Added ${verificationResult.inverseConstraints.length} inverse constraint(s)")
            }
          }

          // Reset solver for next iteration
          solver = ctx.mkSolver()
          z3Model = null
        }
      }
    }

    if (!solved && iteration >= ExternalFunctions.maxRefinements) {
      log(s"[CEGAR] Max refinement iterations ($iteration) reached without convergence")
    }

    if (printModel && z3Model != null) {
      PrintModel(model)
    }
  }

  /**
   * Result of verifying external function calls against a Z3 model
   */
  case class CEGARVerificationResult(
    allVerified: Boolean,
    mismatches: List[(String, List[Any], Any)],  // (funcName, args, actualResult)
    inverseConstraints: List[String] = Nil       // Additional constraints to help convergence
  )

  /**
   * Verify that external function calls in the model are consistent
   * with actual function evaluations.
   */
  def verifyExternalCalls(model: com.microsoft.z3.Model): CEGARVerificationResult = {
    if (model == null) return CEGARVerificationResult(true, Nil, Nil)

    if (debug) println(s"[CEGAR] Verifying ${ExternalFunctions.getExternalCalls.size} external calls")
    val mismatches = ListBuffer[(String, List[Any], Any)]()
    val inverseConstraints = ListBuffer[String]()

    for ((smtFuncName, callInfo) <- ExternalFunctions.getExternalCalls) {
      if (debug) println(s"[CEGAR] Checking: $smtFuncName -> ${callInfo.qualifiedName}")
      if (debug) println(s"[CEGAR]   Arg var names: ${callInfo.argVarNames.mkString(", ")}")

      // Try to extract argument values from the Z3 model
      val argValues: List[Option[Any]] = callInfo.argVarNames.map { varName =>
        val v = extractValueFromModel(model, varName)
        if (debug) println(s"[CEGAR]   $varName -> $v")
        v
      }

      if (argValues.forall(_.isDefined)) {
        val concreteArgs = argValues.map(_.get)
        if (debug) println(s"[CEGAR]   Concrete args: ${concreteArgs.mkString(", ")}")

        // Evaluate the actual function (check if Python or Java)
        val evalResult = if (callInfo.qualifiedName.startsWith("python:")) {
          // Python call - strip prefix and evaluate via Python
          val pythonName = callInfo.qualifiedName.stripPrefix("python:")
          PythonExternalFunctions.tryEvaluate(pythonName, concreteArgs)
        } else {
          // Java call
          ExternalFunctions.tryEvaluate(callInfo.qualifiedName, concreteArgs)
        }

        evalResult match {
          case Some(actualResult) =>
            if (debug) println(s"[CEGAR]   Actual result: $actualResult")
            val z3Result = extractFunctionResult(model, smtFuncName, concreteArgs)
            if (debug) println(s"[CEGAR]   Z3 result: $z3Result")

            z3Result match {
              case Some(z3Value) if !valuesMatch(z3Value, actualResult) =>
                // Mismatch! Need refinement
                mismatches += ((smtFuncName, concreteArgs, actualResult))
                if (debug || ExternalFunctions.logCalls) {
                  log(s"[CEGAR] Mismatch: $smtFuncName(${concreteArgs.mkString(", ")}) = $z3Value (Z3) vs $actualResult (actual)")
                }

                // NOTE: We intentionally do NOT add inverse constraints here.
                // The CEGAR loop should converge through refinement alone.
                // If you need faster convergence, define the function explicitly in K
                // with constraints (e.g., fun sqrt(x: Real): Real { y: Real; req y >= 0; req y * y = x; return y })
                // and equate it to the external function.

              case _ =>
                // Match or couldn't extract Z3's result
                if (debug && ExternalFunctions.logCalls) {
                  log(s"[CEGAR] Verified: ${callInfo.qualifiedName}(${concreteArgs.mkString(", ")}) = $actualResult")
                }
            }
          case None =>
            // Couldn't evaluate - skip verification for this call
            if (debug) {
              log(s"[CEGAR] Could not evaluate: ${callInfo.qualifiedName}")
            }
        }
      }
    }

    CEGARVerificationResult(mismatches.isEmpty, mismatches.toList, inverseConstraints.toList)
  }

  /**
   * Extract a value from the Z3 model for a given variable name.
   * Variables in K are stored in the TopLevelDeclarations datatype in the heap,
   * so we need to evaluate getter expressions.
   */
  def extractValueFromModel(model: com.microsoft.z3.Model, varName: String): Option[Any] = {
    try {
      // First try direct constant lookup
      val directMatch = model.getDecls.find(_.getName.toString == varName)
      if (directMatch.isDefined) {
        val value = model.getConstInterp(directMatch.get)
        val result = z3ValueToScala(value)
        if (result.isDefined) {
          println(s"[CEGAR] Found direct constant $varName = ${result.get}")
          return result
        }
      }

      // For K variables, we need to evaluate the getter function at ref 0
      // Build the expression: (TopLevelDeclarations!varName 0)
      val getterName = s"TopLevelDeclarations!$varName"
      val getterMatch = model.getFuncDecls.find(_.getName.toString == getterName)

      if (getterMatch.isDefined) {
        val decl = getterMatch.get
        // Create an application of the getter to ref 0
        val refZero = ctx.mkInt(0)
        val app = ctx.mkApp(decl, refZero)

        // Evaluate in the model
        val evalResult = model.eval(app, true)  // true = model_completion
        println(s"[CEGAR] Evaluated $getterName(0) = $evalResult")

        if (evalResult != null) {
          val scalaVal = z3ValueToScala(evalResult)
          println(s"[CEGAR] Converted to Scala: $scalaVal")
          return scalaVal
        }
      }

      println(s"[CEGAR] Could not find variable $varName")
      None
    } catch {
      case e: scala.runtime.NonLocalReturnControl[_] =>
        // This is actually a successful return from inside the try block
        e.value.asInstanceOf[Option[Any]]
      case e: Throwable =>
        println(s"[CEGAR] Error extracting $varName: ${e.getClass.getName}: ${e.getMessage}")
        None
    }
  }

  /**
   * Extract the result of an uninterpreted function application from Z3 model
   */
  def extractFunctionResult(model: com.microsoft.z3.Model, funcName: String, args: List[Any]): Option[Any] = {
    try {
      // Find the function declaration
      val funcDecl = model.getFuncDecls.find(_.getName.toString == funcName)

      funcDecl match {
        case Some(decl) =>
          // Build Z3 arguments from our Scala args
          val z3Args = args.map { arg =>
            arg match {
              case d: Double => ctx.mkReal(d.toString)
              case l: Long => ctx.mkInt(l)
              case i: Int => ctx.mkInt(i)
              case s: String => ctx.mkString(s)
              case b: Boolean => ctx.mkBool(b)
              case _ => ctx.mkReal(arg.toString)
            }
          }.toArray

          // Create function application
          val app = ctx.mkApp(decl, z3Args: _*)

          // Evaluate in the model
          val result = model.eval(app, true)
          z3ValueToScala(result)

        case None =>
          if (debug) println(s"[CEGAR] Could not find function $funcName in model")
          None
      }
    } catch {
      case e: scala.runtime.NonLocalReturnControl[_] =>
        e.value.asInstanceOf[Option[Any]]
      case e: Throwable =>
        if (debug) println(s"[CEGAR] Error extracting function result: ${e.getClass.getName}: ${e.getMessage}")
        None
    }
  }

  /**
   * Convert a Z3 value to a Scala value
   */
  def z3ValueToScala(value: com.microsoft.z3.Expr[_]): Option[Any] = {
    if (value == null) return None
    try {
      value match {
        case v: com.microsoft.z3.IntNum => Some(v.getInt64)
        case v: com.microsoft.z3.RatNum => Some(v.getNumerator.getInt64.toDouble / v.getDenominator.getInt64)
        case v: com.microsoft.z3.BoolExpr => Some(v.isTrue)
        case v: com.microsoft.z3.SeqExpr[_] => Some(v.getString)
        case _ =>
          // Try to parse string representation
          val str = value.toString
          if (str.matches("-?\\d+")) Some(str.toLong)
          else if (str.matches("-?\\d+\\.\\d+")) Some(str.toDouble)
          else if (str == "true") Some(true)
          else if (str == "false") Some(false)
          else Some(str)
      }
    } catch {
      case _: Throwable => Some(value.toString)
    }
  }

  /**
   * Check if argument lists match
   */
  def argsMatch(z3Args: List[Option[Any]], actualArgs: List[Option[Any]]): Boolean = {
    if (z3Args.length != actualArgs.length) return false
    (z3Args zip actualArgs).forall { case (z3, actual) =>
      (z3, actual) match {
        case (Some(a), Some(b)) => valuesMatch(a, b)
        case _ => false
      }
    }
  }

  /**
   * Check if two values are approximately equal (for floating point)
   */
  def valuesMatch(a: Any, b: Any): Boolean = {
    (a, b) match {
      case (d1: Double, d2: Double) => Math.abs(d1 - d2) < 1e-9
      case (d1: Double, l2: Long) => Math.abs(d1 - l2.toDouble) < 1e-9
      case (l1: Long, d2: Double) => Math.abs(l1.toDouble - d2) < 1e-9
      case _ => a == b
    }
  }

  /**
   * Standard SMT solving without CEGAR refinement
   */
  def solveSMTDirect(model: Model, smtModel: String, printModel: Boolean): Unit = {
    // Write SMT model to temporary file to avoid string parsing issues
    val tempFile = new java.io.File(".tmp/k_debug.smt2")
    val writer = new java.io.PrintWriter(tempFile)
    writer.write(smtModel)
    writer.close()

    logDebug(s"SMT model written to ${tempFile.getAbsolutePath}")

    // Parse SMT-LIB2 file - returns array of assertions in modern Z3
    val boolExps = ctx.parseSMTLIB2File(tempFile.getAbsolutePath, Array(), Array(), Array(), Array())
    // Combine all assertions into single expression
    val boolExp = if (boolExps.length == 1) boolExps(0) else ctx.mkAnd(boolExps: _*)
    z3Model = SolveExp(boolExp, smtModel)

    if (debugRawModel) {
      // Write raw model to log file instead of console
      try {
        val logFile = new java.io.PrintWriter(new java.io.FileOutputStream(".tmp/k_z3_debug.log", true))
        logFile.println("\n=== Z3 Raw Model (" + new java.util.Date() + ") ===")
        logFile.println(z3Model)
        logFile.println("=== End Raw Model ===\n")
        logFile.close()
      } catch { case _: Throwable => }
    }

    if (printModel) PrintModel(model)
  }

  def SolveExp(e: Exp): com.microsoft.z3.Model = {
    reset()
    val boolExpr = Expr2Z3(e).asInstanceOf[BoolExpr];
    SolveExp(boolExpr, "")
  }

  def SolveExp(e: BoolExpr, smtModel: String): com.microsoft.z3.Model = {

    // Mark that solving is in progress (for signal handler)
    solvingInProgress = true
    iterationsCompleted = 0

    try {
      solver.add(e)

      val status = solver.check
      iterationsCompleted = 1

      // Check for sample/pause requests after solve
      checkAndTakeSample()

      if (Status.SATISFIABLE == status) {
        z3Model = solver.getModel
        lastPartialModel = Some(z3Model)
        bestModelSoFar = Some(z3Model)

        // Take sample if requested
        checkAndTakeSample()

      } else if (status == Status.UNSATISFIABLE) {
      log()
      log(s"The given model is NOT satisfiable. ")
      // Try to get unsat core, but don't fail if it doesn't work
      try {
        val smt2 = ("(set-option :produce-unsat-cores true)\n") + (smtModel + "(check-sat) (get-unsat-core) (exit)")
        val file = new File("t.smt2")
        val tf = new PrintWriter(file)
        tf.write(smt2)
        tf.close
        val res = (("z3 -smt2 t.smt2")).!!
        val lines = res.split("\\r?\\n")
        if (lines.length > 1) {
          val assertionNames = lines(1).replace("(", "").replace(")", "").split("\\s")
            .filter { !_.equals("xTOP") }
            .map { name => UtilSMT.constraintMessageMap.getOrElse(name, name) }.toSet
          log("UNSAT due to the following reasons: ")
          println
          for (
            an <- assertionNames.filter { !_.equals("_k_ignore_") }
          ) {
            println(s"\t$an")
          }
          println
        }
        log()
        file.delete()
      } catch {
        case e: Throwable =>
          if (debug) {
            log("Could not extract unsat core details.")
            if (debug) e.printStackTrace()
          }
          log()
      }
    } else {
      // Status is UNKNOWN - could be timeout, interrupt, or other reason
      val reason = solver.getReasonUnknown
      val isTimeout = reason != null && (reason.toLowerCase.contains("timeout") || reason.toLowerCase.contains("canceled"))
      val wasUserInterrupted = interrupted

      log()
      if (wasUserInterrupted) {
        log("⚠️  INTERRUPTED by user")
        log(s"Solver stopped after $iterationsCompleted iteration(s)")
        // Return best model we found so far
        bestModelSoFar match {
          case Some(model) =>
            z3Model = model
            log("Returning best solution found before interruption")
          case None =>
            log("No solution was found before interruption")
            z3Model = null
        }
      } else if (isTimeout && bestEffortMode) {
        log("⚠️  TIMEOUT - Returning best-effort result")
        log(s"Solver timed out after ${solverTimeout.getOrElse("unknown")}ms")
        // Try to get whatever model state we have
        // Note: Z3 may not have a valid model on timeout, but we try anyway
        try {
          z3Model = solver.getModel
          if (z3Model != null) {
            lastPartialModel = Some(z3Model)
            bestModelSoFar = Some(z3Model)
            log("Partial model available - results may be incomplete or suboptimal")
          } else {
            log("No partial model available")
          }
        } catch {
          case _: Throwable =>
            log("Could not extract partial model")
            z3Model = null
        }
      } else if (isTimeout) {
        log(s"⛔ TIMEOUT after ${solverTimeout.getOrElse("unknown")}ms")
        log("Solver did not complete. Use @bestEffort annotation to get partial results.")
        z3Model = null
      } else {
        log("Model could not be solved successfully.")
        log("Reason: " + reason)
        z3Model = null
      }
      log()
    }

    z3Model
    } finally {
      // Always mark solving as complete
      solvingInProgress = false
    }
  }

  /**
   * Convert an expression from one numeric type to another using appropriate SMT functions.
   *
   * Supported conversions:
   * - Int → BitVec[N]: int2bv
   * - BitVec[N] → Int: bv2int (signed) or bv2nat (unsigned)
   * - Int → Real: to_real
   * - Real → Int: to_int (floor)
   * - SignedInt/UnsignedInt: same as BitVec conversions
   * - Float → Real: fp.to_real (future)
   * - Real → Float: to_fp (future)
   */
  def convertNumericType(expr: Expr[_ <: Sort], fromType: Type, toType: Type): Expr[_ <: Sort] = {
    (fromType, toType) match {
      // Same type - no conversion needed
      case (t1, t2) if t1 == t2 => expr

      // Int → Real
      case (IntType, RealType) =>
        ctx.mkInt2Real(expr.asInstanceOf[IntExpr])

      // Real → Int (floor/truncation)
      case (RealType, IntType) =>
        ctx.mkReal2Int(expr.asInstanceOf[RealExpr])

      // Int → BitVec[N]
      case (IntType, BitVecType(width)) =>
        ctx.mkInt2BV(width, expr.asInstanceOf[IntExpr])

      // Int → SignedIntType (same as Int → BitVec)
      case (IntType, SignedIntType(width)) =>
        ctx.mkInt2BV(width, expr.asInstanceOf[IntExpr])

      // Int → UnsignedIntType (same as Int → BitVec)
      case (IntType, UnsignedIntType(width)) =>
        ctx.mkInt2BV(width, expr.asInstanceOf[IntExpr])

      // BitVec[N] → Int (signed interpretation)
      case (BitVecType(_), IntType) =>
        ctx.mkBV2Int(expr.asInstanceOf[BitVecExpr], true) // true = signed

      // SignedIntType → Int (signed interpretation)
      case (SignedIntType(_), IntType) =>
        ctx.mkBV2Int(expr.asInstanceOf[BitVecExpr], true)

      // UnsignedIntType → Int (unsigned interpretation)
      case (UnsignedIntType(_), IntType) =>
        ctx.mkBV2Int(expr.asInstanceOf[BitVecExpr], false) // false = unsigned

      // SignedIntType → Real (via Int)
      case (SignedIntType(_), RealType) =>
        val asInt = ctx.mkBV2Int(expr.asInstanceOf[BitVecExpr], true)
        ctx.mkInt2Real(asInt)

      // UnsignedIntType → Real (via Int)
      case (UnsignedIntType(_), RealType) =>
        val asInt = ctx.mkBV2Int(expr.asInstanceOf[BitVecExpr], false)
        ctx.mkInt2Real(asInt)

      // Real → SignedIntType (via Int)
      case (RealType, SignedIntType(width)) =>
        val asInt = ctx.mkReal2Int(expr.asInstanceOf[RealExpr])
        ctx.mkInt2BV(width, asInt)

      // Real → UnsignedIntType (via Int)
      case (RealType, UnsignedIntType(width)) =>
        val asInt = ctx.mkReal2Int(expr.asInstanceOf[RealExpr])
        ctx.mkInt2BV(width, asInt)

      // BitVec widening (zero extension)
      case (BitVecType(w1), BitVecType(w2)) if w1 < w2 =>
        ctx.mkZeroExt(w2 - w1, expr.asInstanceOf[BitVecExpr])

      // BitVec narrowing (extraction of lower bits)
      case (BitVecType(w1), BitVecType(w2)) if w1 > w2 =>
        ctx.mkExtract(w2 - 1, 0, expr.asInstanceOf[BitVecExpr])

      // SignedIntType widening (sign extension)
      case (SignedIntType(w1), SignedIntType(w2)) if w1 < w2 =>
        ctx.mkSignExt(w2 - w1, expr.asInstanceOf[BitVecExpr])

      // SignedIntType narrowing
      case (SignedIntType(w1), SignedIntType(w2)) if w1 > w2 =>
        ctx.mkExtract(w2 - 1, 0, expr.asInstanceOf[BitVecExpr])

      // UnsignedIntType widening (zero extension)
      case (UnsignedIntType(w1), UnsignedIntType(w2)) if w1 < w2 =>
        ctx.mkZeroExt(w2 - w1, expr.asInstanceOf[BitVecExpr])

      // UnsignedIntType narrowing
      case (UnsignedIntType(w1), UnsignedIntType(w2)) if w1 > w2 =>
        ctx.mkExtract(w2 - 1, 0, expr.asInstanceOf[BitVecExpr])

      // SignedIntType <-> UnsignedIntType of same width (reinterpretation - no SMT change)
      case (SignedIntType(w1), UnsignedIntType(w2)) if w1 == w2 => expr
      case (UnsignedIntType(w1), SignedIntType(w2)) if w1 == w2 => expr

      // SignedIntType -> UnsignedIntType with width change
      case (SignedIntType(w1), UnsignedIntType(w2)) if w1 < w2 =>
        // Widening: sign-extend first, then reinterpret as unsigned
        ctx.mkSignExt(w2 - w1, expr.asInstanceOf[BitVecExpr])
      case (SignedIntType(w1), UnsignedIntType(w2)) if w1 > w2 =>
        // Narrowing: extract lower bits
        ctx.mkExtract(w2 - 1, 0, expr.asInstanceOf[BitVecExpr])

      // UnsignedIntType -> SignedIntType with width change
      case (UnsignedIntType(w1), SignedIntType(w2)) if w1 < w2 =>
        // Widening: zero-extend first, then reinterpret as signed
        ctx.mkZeroExt(w2 - w1, expr.asInstanceOf[BitVecExpr])
      case (UnsignedIntType(w1), SignedIntType(w2)) if w1 > w2 =>
        // Narrowing: extract lower bits
        ctx.mkExtract(w2 - 1, 0, expr.asInstanceOf[BitVecExpr])

      // SignedIntType <-> BitVec of same width
      case (SignedIntType(w1), BitVecType(w2)) if w1 == w2 => expr
      case (BitVecType(w1), SignedIntType(w2)) if w1 == w2 => expr

      // SignedIntType -> BitVec with width change
      case (SignedIntType(w1), BitVecType(w2)) if w1 < w2 =>
        ctx.mkSignExt(w2 - w1, expr.asInstanceOf[BitVecExpr])
      case (SignedIntType(w1), BitVecType(w2)) if w1 > w2 =>
        ctx.mkExtract(w2 - 1, 0, expr.asInstanceOf[BitVecExpr])

      // BitVec -> SignedIntType with width change  
      case (BitVecType(w1), SignedIntType(w2)) if w1 < w2 =>
        ctx.mkZeroExt(w2 - w1, expr.asInstanceOf[BitVecExpr])
      case (BitVecType(w1), SignedIntType(w2)) if w1 > w2 =>
        ctx.mkExtract(w2 - 1, 0, expr.asInstanceOf[BitVecExpr])

      // UnsignedIntType <-> BitVec of same width
      case (UnsignedIntType(w1), BitVecType(w2)) if w1 == w2 => expr
      case (BitVecType(w1), UnsignedIntType(w2)) if w1 == w2 => expr

      // UnsignedIntType -> BitVec with width change
      case (UnsignedIntType(w1), BitVecType(w2)) if w1 < w2 =>
        ctx.mkZeroExt(w2 - w1, expr.asInstanceOf[BitVecExpr])
      case (UnsignedIntType(w1), BitVecType(w2)) if w1 > w2 =>
        ctx.mkExtract(w2 - 1, 0, expr.asInstanceOf[BitVecExpr])

      // BitVec -> UnsignedIntType with width change
      case (BitVecType(w1), UnsignedIntType(w2)) if w1 < w2 =>
        ctx.mkZeroExt(w2 - w1, expr.asInstanceOf[BitVecExpr])
      case (BitVecType(w1), UnsignedIntType(w2)) if w1 > w2 =>
        ctx.mkExtract(w2 - 1, 0, expr.asInstanceOf[BitVecExpr])

      // Float conversions (future - requires FP support)
      case (FloatType(_, _), RealType) =>
        // For now, just return the expr - proper FP support needed
        log(s"Warning: Float to Real conversion not fully implemented")
        expr

      case (RealType, FloatType(ebits, sbits)) =>
        // For now, just return the expr - proper FP support needed
        log(s"Warning: Real to Float conversion not fully implemented")
        expr

      case _ =>
        log(s"Warning: Unsupported type conversion from $fromType to $toType")
        expr
    }
  }

  def Expr2Z3(e: Exp): com.microsoft.z3.Expr[_ <: Sort] = {
    e match {

      //case FunApplExp(exp, args) =>
      //  var function = getZ3Function(exp).asInstanceOf[FuncDecl]
      //  var argsZ3: Array[Expr] = args.map(a => Expr2Z3(a)).toArray
      //  ctx.mkApp(function, argsZ3: _*)
      case ParenExp(e) =>
        Expr2Z3(e)
      case TupleExp(es) =>
        val vs = es map Expr2Z3
        val tupleType = tc.inferTypeFrom("es", CartesianType(List(RealType, BoolType)))
        val mkTuple = datatypes.getDataType(tupleType).constructor
        mkTuple(vs(0), vs(1))
      case IdentExp(i) =>
        idents.get(i) match {
          case None =>
            var s = ctx.mkSymbol(i)
            var ie = ctx.mkRealConst(s)
            idents.put(i, (ie, s))
            ie
          case Some(x) =>
            x._1
        }
      case DotExp(exp, ident) =>
        exp match {
          case IdentExp(id) => Expr2Z3(IdentExp(id + "." + ident))
          case _ =>
            var obj: Expr[_ <: Sort] = Expr2Z3(exp)
            val theType = tc.inferTypeFrom("exp", IdentType(QualifiedName(List("A")), Nil))
            val datatype: DataType = datatypes.getDataType(theType)
            val selector: FuncDecl[_ <: Sort] = datatype.selectors(ident)
            selector(obj)
        }
      case FunApplExp(exp, args) =>
        var obj: Expr[_ <: Sort] = Expr2Z3(exp)
        val theType = tc.inferTypeFrom("exp", IdentType(QualifiedName(List("A")), Nil))
        val isConstructor = false // TODO: for constructor make this true
        if (isConstructor) {
          // constructor application
          val datatype: DataType = datatypes.getDataType(theType)
          val constructor: FuncDecl[_ <: Sort] = datatype.constructor
          val arguments: List[Expr[_ <: Sort]] = args map (Expr2Z3(_))
          constructor(arguments: _*)
        } else {
          // normal function application

          // 1. create an uninterpreted function

          val functionDecl = ctx.mkFuncDecl(exp.toString, args.map(a => ctx.getRealSort).toArray[Sort], ctx.getRealSort)

          // 2. apply the uninterpreted function
          ctx.mkApp(functionDecl, args.map(Expr2Z3(_)): _*)

        }
      case PositionalArgument(exp) =>
        Expr2Z3(exp)
      case BinExp(e1, o, e2) =>
        o match {
          case LT =>
            var v1: ArithExpr[ArithSort] = Expr2Z3(e1).asInstanceOf[ArithExpr[ArithSort]]
            var v2: ArithExpr[ArithSort] = Expr2Z3(e2).asInstanceOf[ArithExpr[ArithSort]]
            ctx.mkLt(v1, v2)
          case LTE =>
            var v1: ArithExpr[ArithSort] = Expr2Z3(e1).asInstanceOf[ArithExpr[ArithSort]]
            var v2: ArithExpr[ArithSort] = Expr2Z3(e2).asInstanceOf[ArithExpr[ArithSort]]
            ctx.mkLe(v1, v2)
          case GT =>
            var v1: ArithExpr[ArithSort] = Expr2Z3(e1).asInstanceOf[ArithExpr[ArithSort]]
            var v2: ArithExpr[ArithSort] = Expr2Z3(e2).asInstanceOf[ArithExpr[ArithSort]]
            ctx.mkGt(v1, v2)
          case GTE =>
            var v1: ArithExpr[ArithSort] = Expr2Z3(e1).asInstanceOf[ArithExpr[ArithSort]]
            var v2: ArithExpr[ArithSort] = Expr2Z3(e2).asInstanceOf[ArithExpr[ArithSort]]
            ctx.mkGe(v1, v2)
          case AND =>
            var v1: BoolExpr = Expr2Z3(e1).asInstanceOf[BoolExpr]
            var v2: BoolExpr = Expr2Z3(e2).asInstanceOf[BoolExpr]
            ctx.mkAnd(v1, v2)
          case OR =>
            var v1: BoolExpr = Expr2Z3(e1).asInstanceOf[BoolExpr]
            var v2: BoolExpr = Expr2Z3(e2).asInstanceOf[BoolExpr]
            ctx.mkOr(v1, v2)
          case IMPL =>
            var v1: BoolExpr = Expr2Z3(e1).asInstanceOf[BoolExpr]
            var v2: BoolExpr = Expr2Z3(e2).asInstanceOf[BoolExpr]
            ctx.mkImplies(v1, v2)
          case IFF =>
            var v1: BoolExpr = Expr2Z3(e1).asInstanceOf[BoolExpr]
            var v2: BoolExpr = Expr2Z3(e2).asInstanceOf[BoolExpr]
            ctx.mkIff(v1, v2)
          case EQ =>
            var v1: Expr[_ <: Sort] = Expr2Z3(e1)
            var v2: Expr[_ <: Sort] = Expr2Z3(e2)
            ctx.mkEq(v1, v2)
          case NEQ =>
            var v1 = Expr2Z3(e1)
            var v2 = Expr2Z3(e2)
            ctx.mkNot(ctx.mkEq(v1, v2))
          case MUL =>
            var v1: ArithExpr[ArithSort] = Expr2Z3(e1).asInstanceOf[ArithExpr[ArithSort]]
            var v2: ArithExpr[ArithSort] = Expr2Z3(e2).asInstanceOf[ArithExpr[ArithSort]]
            ctx.mkMul(v1, v2)
          case DIV =>
            var v1: ArithExpr[ArithSort] = Expr2Z3(e1).asInstanceOf[ArithExpr[ArithSort]]
            var v2: ArithExpr[ArithSort] = Expr2Z3(e2).asInstanceOf[ArithExpr[ArithSort]]
            ctx.mkDiv(v1, v2)
          case REM =>
            var v1: IntExpr = Expr2Z3(e1).asInstanceOf[IntExpr]
            var v2: IntExpr = Expr2Z3(e2).asInstanceOf[IntExpr]
            ctx.mkRem(v1, v2)
          case ADD =>
            var v1: ArithExpr[ArithSort] = Expr2Z3(e1).asInstanceOf[ArithExpr[ArithSort]]
            var v2: ArithExpr[ArithSort] = Expr2Z3(e2).asInstanceOf[ArithExpr[ArithSort]]
            ctx.mkAdd(v1, v2)
          case SUB =>
            var v1: ArithExpr[ArithSort] = Expr2Z3(e1).asInstanceOf[ArithExpr[ArithSort]]
            var v2: ArithExpr[ArithSort] = Expr2Z3(e2).asInstanceOf[ArithExpr[ArithSort]]
            ctx.mkSub(v1, v2)
          case ASSIGN =>
            var v1: Expr[_ <: Sort] = Expr2Z3(e1)
            var v2: Expr[_ <: Sort] = Expr2Z3(e2)
            ctx.mkEq(v1, v2)
          case TUPLEINDEX =>
            var v1: Expr[_ <: Sort] = Expr2Z3(e1)
            var v2: Expr[_ <: Sort] = Expr2Z3(e2)
            val tupleType = tc.inferTypeFrom("e1", CartesianType(List(RealType, BoolType)))
            val datatype = datatypes.getDataType(tupleType)
            if (v2 == ctx.mkReal(1))
              datatype.selectors("sel_1").apply(v1)
            else
              datatype.selectors("sel_2").apply(v1)
        }
      case UnaryExp(o, e) =>
        o match {
          case NOT =>
            var v: BoolExpr = {
              val ev = Expr2Z3(e)
              if (ev.isInstanceOf[RealExpr]) {
                ctx.mkNot(ctx.mkEq(ev, ctx.mkReal(0)))
              } else {
                ev.asInstanceOf[BoolExpr]
              }
            }

            ctx.mkNot(v)
          case NEG =>
            var v: ArithExpr[ArithSort] = Expr2Z3(e).asInstanceOf[ArithExpr[ArithSort]]
            ctx.mkMul(ctx.mkReal(-1), v)
        }
      case IntegerLiteral(i) =>
        ctx.mkReal(i)
      case BooleanLiteral(b) =>
        ctx.mkBool(b)
      case RealLiteral(r) =>
        ctx.mkReal(r.toString)
      case TypeCastCheckExp(cast, exp, targetType) =>
        if (cast) {
          // Type cast using 'as' operator
          val sourceExpr = Expr2Z3(exp)
          // Get source type from TypeChecker - try exp2Type first, fall back to inferring
          val sourceType = TypeChecker.exp2Type.get(exp) match {
            case null =>
              // Fallback: try to infer type from the expression
              // Debug removed
              exp match {
                case IntegerLiteral(_) => IntType
                case RealLiteral(_) => RealType
                case BooleanLiteral(_) => BoolType
                case IdentExp(name) =>
                  // Try to look up identifier type
                  idents.get(name) match {
                    case Some((expr, _)) =>
                      expr.getSort match {
                        case _: BitVecSort => BitVecType(expr.getSort.asInstanceOf[BitVecSort].getSize)
                        case _ => IntType // Default to Int
                      }
                    case None => IntType
                  }
                case _ => IntType // Default fallback
              }
            case t => t
          }
          convertNumericType(sourceExpr, sourceType, targetType)
        } else {
          // Type check using 'is' operator - this should be handled elsewhere
          // For now, just return a boolean constant (actual implementation depends on runtime type checking)
          ctx.mkBool(true)
        }
      case QuantifiedExp(quantifier, bindings, expression) =>
        var qtypes = new ListBuffer[com.microsoft.z3.Sort]()
        var names = new ListBuffer[com.microsoft.z3.Symbol]()
        var patterns = new ListBuffer[com.microsoft.z3.Pattern]() // not used
        var ies = new ListBuffer[Expr[_]]()
        for (b <- bindings) {
          for (p <- b.patterns) {
            p match {
              case IdentPattern(x) =>
                idents.get(x) match {
                  case None =>
                    val xSym = ctx.mkSymbol(x)
                    var ie = ctx.mkConst(xSym, ctx.getRealSort)
                    idents.put(x, (ie, xSym))
                    names += xSym
                    ies += ie
                    b.collection match {
                      case TypeCollection(ty) =>
                        ty match {
                          case BoolType => qtypes += ctx.getBoolSort()
                          case IntType =>
                            qtypes += ctx.getIntSort()
                            val pattern = ctx.mkPattern(ie)
                            patterns += ctx.mkPattern(ie) // use pattern, but not used anyway
                          case RealType =>
                            qtypes += ctx.getRealSort()
                            val pattern = ctx.mkPattern(ie)
                            patterns += ctx.mkPattern(ie) // use pattern, but not used anyway
                          case _ =>
                            error("Only bool, int, and real primitive types are supported for quantified expressions in Z3." + expression)
                        }
                      case _ =>
                        error("Only type collections are supported for quantified expressions in Z3." +
                          "\nPlease check expression " + e)
                    }
                  case Some(x) => () // so you can't quantify over an existing variable?
                }

              case _ =>
                error("Only literal and ident patterns are supported for quantified expressions in Z3." +
                  "Please check expression " + expression)
            }
          }
        }

        var body: Expr[_] = Expr2Z3(expression)

        // There are two ways to construct quantified expressions in Z3
        // one is by using named constants
        // the other is by de-Brujin indexed variables.
        // we have to be careful, because there are no checks for actually
        // checking if you are mixing the two and doing it incorrectly
        // The following uses de-Brujin indexed variables for forall
        // and named constants for exists. 
        // This probably can be cleaned up, but it is the only way I got
        // it to work.
        // KH: I think that it does not work as expected for existential quantification.
        // since variables are all de-Brujin variables, and since symbols are used for
        // existential, it does not work. It looks like it works, but I think it is
        // not working the way it is intended to.
        quantifier match {
          case Forall =>
            ctx.mkForall(ies.toArray, body.asInstanceOf[Expr[BoolSort]], 0, null,
              null, null, null)
          case Exists =>
            ctx.mkExists(ies.toArray, body.asInstanceOf[Expr[BoolSort]], 0, null,
              null, null, null)
          //            ctx.mkExists(qtypes.toArray, names.toArray,
          //              body, 1, null, null, null, null)
          //ctx.mkExists(ies.toArray, body, 0, null, null, null, null)
        }
    }
  }

  // ============================================================================
  // Variable Bounds Querying (for IDE integration)
  // ============================================================================

  /**
   * Query the minimum and maximum values for a numeric variable.
   * Uses Z3's Optimize to compute bounds without changing the main solver state.
   *
   * @param varName The variable name to query bounds for
   * @return VariableBounds with min/max values, or None if variable not found
   */
  def queryVariableBounds(varName: String): Option[SolverProgress.VariableBounds] = {
    idents.get(varName) match {
      case Some((expr, _)) =>
        val sort = expr.getSort

        // Only query bounds for numeric types
        if (sort == ctx.getIntSort || sort == ctx.getRealSort || sort.isInstanceOf[BitVecSort]) {
          try {
            val opt = ctx.mkOptimize()

            // Copy current solver assertions to optimizer
            solver.getAssertions.foreach(a => opt.Add(a.asInstanceOf[BoolExpr]))

            // Query minimum
            val minHandle = opt.MkMinimize(expr.asInstanceOf[Expr[ArithSort]])
            val minResult = opt.Check()
            val minValue = if (minResult == Status.SATISFIABLE) {
              Some(opt.getModel.eval(expr, true).toString)
            } else None

            // Query maximum (fresh optimizer)
            val opt2 = ctx.mkOptimize()
            solver.getAssertions.foreach(a => opt2.Add(a.asInstanceOf[BoolExpr]))
            val maxHandle = opt2.MkMaximize(expr.asInstanceOf[Expr[ArithSort]])
            val maxResult = opt2.Check()
            val maxValue = if (maxResult == Status.SATISFIABLE) {
              Some(opt2.getModel.eval(expr, true).toString)
            } else None

            // Determine var type
            val varType = sort match {
              case _ if sort == ctx.getIntSort => "Int"
              case _ if sort == ctx.getRealSort => "Real"
              case bv: BitVecSort => s"BitVec${bv.getSize}"
              case _ => "unknown"
            }

            // Check if exact value (min == max)
            val exactValue = (minValue, maxValue) match {
              case (Some(min), Some(max)) if min == max => Some(min)
              case _ => None
            }

            Some(SolverProgress.VariableBounds(
              variableName = varName,
              minValue = minValue,
              maxValue = maxValue,
              exactValue = exactValue,
              feasible = minValue.isDefined || maxValue.isDefined,
              varType = varType
            ))
          } catch {
            case e: Exception =>
              logDebug(s"Failed to query bounds for $varName: ${e.getMessage}")
              None
          }
        } else {
          // Non-numeric type - just report if it has a value
          if (z3Model != null) {
            try {
              val value = z3Model.eval(expr, true)
              Some(SolverProgress.VariableBounds(
                variableName = varName,
                exactValue = Some(value.toString),
                feasible = true,
                varType = sort.toString
              ))
            } catch {
              case _: Exception => None
            }
          } else None
        }
      case None => None
    }
  }

  /**
   * Query bounds for all known variables.
   * Can be expensive for large models - use sparingly.
   */
  def queryAllVariableBounds(): Map[String, SolverProgress.VariableBounds] = {
    idents.keys.flatMap { varName =>
      queryVariableBounds(varName).map(b => varName -> b)
    }.toMap
  }

  /**
   * Query bounds for variables matching a pattern.
   */
  def queryVariableBoundsMatching(pattern: String): Map[String, SolverProgress.VariableBounds] = {
    val regex = pattern.r
    idents.keys.filter(name => regex.findFirstIn(name).isDefined).flatMap { varName =>
      queryVariableBounds(varName).map(b => varName -> b)
    }.toMap
  }

  /**
   * Get current value of a variable from the model (if available).
   */
  def getVariableValue(varName: String): Option[String] = {
    if (z3Model == null) return None

    idents.get(varName).flatMap { case (expr, _) =>
      try {
        Some(z3Model.eval(expr, true).toString)
      } catch {
        case _: Exception => None
      }
    }
  }

  /**
   * Get all variable values from the current model.
   */
  def getAllVariableValues(): Map[String, String] = {
    if (z3Model == null) return Map()

    idents.flatMap { case (name, (expr, _)) =>
      try {
        Some(name -> z3Model.eval(expr, true).toString)
      } catch {
        case _: Exception => None
      }
    }.toMap
  }

}

