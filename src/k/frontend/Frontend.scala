package k.frontend

import org.apache.log4j.{Level, Logger}

import scala.util.control.Breaks._
import org.antlr.runtime.tree.ParseTree
import k.frontend
import java.io._
import java.nio
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.Path

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.atn.PredictionMode
import org.json.JSONArray
import org.json.JSONObject
import k.frontend.ModelParser.ModelContext
import org.json.JSONTokener

import scala.collection.mutable.{ListBuffer => MList}
import java.nio.file._
import k.frontend.{Satisfiable, Unsatisfiable, Unknown}

object Frontend {

  var modelFileDirectory: String = null
  var classpath: Set[String] = Set()
  var timeoutValue = 30000

  var lastParsedModel: Model = null
  var lastVisitor: KScalaVisitor = null

  // Parse cache: maps (filePath, lastModified) -> parsed Model
  // Disabled by default; enable with -cache flag for batch testing
  var parseCache: scala.collection.mutable.Map[(String, Long), Model] =
    scala.collection.mutable.Map()
  var parseCacheEnabled: Boolean = false

  def clearParseCache(): Unit = {
    parseCache.clear()
  }

  /**
   * Parse annotations from K file comments.
   * Supports:
   *   // @preferred_options -dsn-pass -timeout 60000
   *   // @expected SAT|UNSAT|TIMEOUT|ERROR
   * Returns a map of annotation name -> value
   */
  def parseAnnotations(filepath: String): Map[String, String] = {
    try {
      val file = new java.io.File(filepath)
      if (!file.exists()) return Map.empty
      
      val result = scala.collection.mutable.Map[String, String]()
      val source = scala.io.Source.fromFile(file)
      try {
        val lines = source.getLines().take(50).toList // Only check first 50 lines
        for (line <- lines) {
          val trimmed = line.trim
          // Support both // and /* */ style comments and -- style comments
          if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*") || trimmed.startsWith("--")) {
            val cleanedLine = trimmed
              .stripPrefix("//")
              .stripPrefix("/*")
              .stripPrefix("--")
              .stripPrefix("*")
              .stripSuffix("*/")
              .trim
            
            if (cleanedLine.startsWith("@preferred_options")) {
              val optString = cleanedLine.stripPrefix("@preferred_options").trim
              result("preferred_options") = optString
            } else if (cleanedLine.startsWith("@expected")) {
              val expected = cleanedLine.stripPrefix("@expected").trim.toUpperCase
              result("expected") = expected
            } else if (cleanedLine.startsWith("@timeout")) {
              val timeout = cleanedLine.stripPrefix("@timeout").trim
              result("timeout") = timeout
            }
          }
        }
        result.toMap
      } finally {
        source.close()
      }
    } catch {
      case e: Exception =>
        log(s"Warning: Could not parse annotations from $filepath: ${e.getMessage}")
        Map.empty
    }
  }
  
  /**
   * Parse @preferred_options annotation from K file comments.
   * Example: // @preferred_options -dsn-pass -timeout 60000
   * Returns a list of additional command-line args to apply.
   */
  def parsePreferredOptions(filepath: String): List[String] = {
    val annotations = parseAnnotations(filepath)
    annotations.get("preferred_options") match {
      case Some(opts) =>
        val optList = opts.split("\\s+").toList.filter(_.nonEmpty)
        if (optList.nonEmpty) log(s"Found @preferred_options: $optList")
        optList
      case None => Nil
    }
  }
  
  /**
   * Parse @expected annotation from K file comments.
   * Example: // @expected SAT
   * Returns the expected result or None if not specified.
   */
  def parseExpectedResult(filepath: String): Option[String] = {
    val annotations = parseAnnotations(filepath)
    annotations.get("expected")
  }

  type OptionMap = Map[Symbol, Any]

  def log(msg: String = "") = Misc.log("main", msg)
  def logDebug(msg: String) = if (K2Z3.debug) Misc.log("main", s"DEBUG: $msg")

  def errorExit(msg: String = "") = Misc.errorExit("main", msg)

  /**
   * Run solver with auto-detection of problem properties.
   * Shared between batch-verbose mode and non-batch mode.
   * 
   * @param model The K model to solve
   * @param smtStr The SMT string representation
   * @param options The command-line options map
   * @param timeoutMs Timeout in milliseconds
   * @param printModel Whether to print the model on SAT
   * @param verbose Whether to print solver selection info
   * @return (outcome, optionalZ3Model) where outcome is SAT/UNSAT/TIMEOUT/UNKNOWN
   */
  def solveWithAutoDetection(
    model: Model, 
    smtStr: String, 
    options: OptionMap, 
    timeoutMs: Int, 
    printModel: Boolean,
    verbose: Boolean
  ): (String, Option[com.microsoft.z3.Model]) = {
    if (verbose) log("Using Auto-Detection...")
    val props = ProblemAnalyzer.analyze(model)
    val config = ProblemAnalyzer.selectConfig(props, SolveConfig.fromOptions(options))
    
    if (verbose) {
      log(s"  Detected: hasDynamicHeap=${props.hasDynamicHeap}, complexity=${props.estimatedComplexity}")
      log(s"  Selected: heapStrategy=${config.heapStrategy}, incrementalMode=${config.incrementalMode}")
    }
    
    val result = config.heapStrategy match {
      case HeapStrategy.CEGAR =>
        if (verbose) log("  → Using Heap CEGAR strategy")
        UnifiedSolver.solveWithHeapCegar(model, printModel = printModel)
        
      case HeapStrategy.Soft =>
        if (verbose) log("  → Using Soft Heap strategy")
        UnifiedSolver.solveWithSoftHeap(model, printModel = printModel)
        
      case HeapStrategy.Fixed | HeapStrategy.Auto =>
        config.incrementalMode match {
          case IncrementalMode.Scenarios =>
            if (verbose) log("  → Using DSN Scenario-based Incremental Solver")
            import k.frontend.DSNPassSolver
            val scenarioResult = DSNPassSolver.solveByScenariosIncremental(model, smtStr, timeoutMs)
            scenarioResult match {
              case Some(sr) =>
                sr.result match {
                  case Satisfiable(m, _) => UnifiedSolver.SolveResult.Sat(m)
                  case Unsatisfiable(_, _) => UnifiedSolver.SolveResult.Unsat
                  case Unknown(reason, _, timeout) => 
                    if (timeout) UnifiedSolver.SolveResult.Timeout
                    else UnifiedSolver.SolveResult.Unknown(reason)
                }
              case None => UnifiedSolver.SolveResult.Unsat
            }
          case _ =>
            if (verbose) log("  → Using UnifiedSolver (default)")
            UnifiedSolver.solve(model, smtStr, printModel = printModel, timeoutMs = Some(timeoutMs))
        }
    }
    
    result match {
      case UnifiedSolver.SolveResult.Sat(z3Model) =>
        if (verbose) log("Auto: SAT")
        ("SAT", Some(z3Model))
      case UnifiedSolver.SolveResult.Unsat =>
        if (verbose) log("Auto: UNSAT")
        ("UNSAT", None)
      case UnifiedSolver.SolveResult.Timeout =>
        if (verbose) log("Auto: TIMEOUT")
        ("TIMEOUT", None)
      case UnifiedSolver.SolveResult.Unknown(reason) =>
        if (verbose) log(s"Auto: UNKNOWN ($reason)")
        ("UNKNOWN", None)
    }
  }

  def parseArgs(map: OptionMap, list: List[String]): OptionMap = {
    def isSwitch(s: String) = (s(0) == '-')

    list match {
      case Nil => map
      case "-f" :: value :: tail =>
        parseArgs(map ++ Map('modelFile -> value), tail)
      case "-fraw" :: value :: tail =>
        parseArgs(map ++ Map('rawSMTFile -> value), tail)
      case "-instances" :: value :: tail =>
        parseArgs(map ++ Map('instances -> value.toInt), tail)
      case "-timeout" :: value :: tail =>
        timeoutValue = value.toInt
        parseArgs(map ++ Map('timeout -> value.toInt), tail)
      case "-classpath" :: value :: tail =>
        classpath = value.replace("\"", "").split(File.pathSeparator).toSet
        parseArgs(map, tail)
      case "-tests" :: tail => parseArgs(map ++ Map('tests -> true), tail)
      case "-baseline" :: tail => parseArgs(map ++ Map('baseline -> true), tail)
      case "-test" :: tail => parseArgs(map ++ Map('test -> true), tail)
      case "-v" :: tail => parseArgs(map ++ Map('verbose -> true), tail)
      case "-query" :: tail => parseArgs(map ++ Map('query -> true), tail)
      case "-stats" :: tail => parseArgs(map ++ Map('stats -> true), tail)
      case "-dot" :: tail => parseArgs(map ++ Map('dot -> true), tail)
      case "-latex" :: tail => parseArgs(map ++ Map('latex -> true), tail)
      case "-scala" :: tail => parseArgs(map ++ Map('scala -> true), tail)
      case "-json" :: tail => parseArgs(map ++ Map('printJson -> true), tail)
      case "-tc" :: tail => parseArgs(map ++ Map('tc -> true), tail)
      case "-mmsJson" :: value :: tail =>
        parseArgs(map ++ Map('mmsJson -> value), tail)
      case "-expressionToJson" :: value :: tail =>
        parseArgs(map ++ Map('expression -> value), tail)
      case "-jsonToExpression" :: value :: tail =>
        parseArgs(map ++ Map('json -> value), tail)
      case "-postnobody" :: tail => parseArgs(map ++ Map('postnobody -> true), tail)
      case "-unified" :: tail => parseArgs(map ++ Map('unified -> true), tail)
      case "-unified-scenarios" :: tail => parseArgs(map ++ Map('unifiedScenarios -> true), tail)
      case "-dsn-pass" :: tail => parseArgs(map ++ Map('dsnPass -> true), tail) // DSN_Pass.k solver (scenario-based incremental)
      case "-legacy" :: tail => parseArgs(map ++ Map('legacy -> true, 'unified -> false), tail)
      case "-heapcegar" :: tail => parseArgs(map ++ Map('heapcegar -> true), tail)
      case "-heapcegar-cvc5" :: tail => parseArgs(map ++ Map('heapcegar -> true, 'heapcegarcvc5 -> true), tail)
      case "-heapsoft" :: tail => parseArgs(map ++ Map('heapsoft -> true), tail)
      case "-incremental" :: tail => parseArgs(map ++ Map('incremental -> true), tail)
      case "-scenario-tracking" :: tail => parseArgs(map ++ Map('scenarioTracking -> true), tail)
      case "-cvc5" :: tail => parseArgs(map ++ Map('cvc5 -> true), tail)
      case "-yices" :: tail => parseArgs(map ++ Map('yices -> true), tail)
      case "-mathsat" :: tail => parseArgs(map ++ Map('mathsat -> true), tail)
      case "-minizinc" :: tail => parseArgs(map ++ Map('minizinc -> true), tail)
      case "-bae" :: tail => parseArgs(map ++ Map('bae -> true), tail)
      case "-mzn-solver" :: value :: tail => parseArgs(map ++ Map('mznSolver -> value), tail)
      case "-mzn-timeout" :: value :: tail => parseArgs(map ++ Map('mznTimeout -> value.toInt), tail)
      case "-emit-mzn" :: tail => parseArgs(map ++ Map('emitMzn -> true), tail)
      case "-batch" :: tail => parseArgs(map ++ Map('batch -> true), tail)
      case "-batch-verbose" :: tail => parseArgs(map ++ Map('batchVerbose -> true), tail)
      case "-timing" :: tail => parseArgs(map ++ Map('timing -> true), tail)
      case "-analyze" :: tail => parseArgs(map ++ Map('analyze -> true), tail)
      case "-auto" :: tail => parseArgs(map ++ Map('auto -> true), tail)
      case "-prefer-file-options" :: tail => parseArgs(map ++ Map('preferFileOptions -> true), tail)
      case "-ignore-file-options" :: tail => parseArgs(map ++ Map('ignoreFileOptions -> true), tail)
      case "-debug" :: tail =>
        K2Z3.debug = true
        UnifiedSolver.debug = true
        parseArgs(map ++ Map('debug -> true), tail)
      case "-ktc" :: tail =>
        // Use K-based type checker (experimental - spawns subprocess to solve types)
        parseArgs(map ++ Map('ktc -> true), tail)
      case "-ktc-gen" :: tail =>
        // Generate K type check program only (don't run)
        parseArgs(map ++ Map('ktcGen -> true), tail)
      case "-tc-strict" :: tail =>
        // Type checking mode: Strict (declarations required, unambiguous types)
        KTypeChecker.setMode(reqDecl = true, reqUnambiguous = true)
        parseArgs(map ++ Map('ktc -> true), tail)  // Also enable K-based TC
      case "-tc-inferred" :: tail =>
        // Type checking mode: InferredDecls (no declarations required, unambiguous types)
        KTypeChecker.setMode(reqDecl = false, reqUnambiguous = true)
        parseArgs(map ++ Map('ktc -> true), tail)  // Also enable K-based TC
      case "-tc-ambiguous" :: tail =>
        // Type checking mode: AmbiguousTypes (declarations required, ambiguous types allowed)
        KTypeChecker.setMode(reqDecl = true, reqUnambiguous = false)
        parseArgs(map ++ Map('ktc -> true), tail)  // Also enable K-based TC
      case "-tc-flexible" :: tail =>
        // Type checking mode: FullyFlexible (no declarations required, ambiguous types allowed)
        KTypeChecker.setMode(reqDecl = false, reqUnambiguous = false)
        parseArgs(map ++ Map('ktc -> true), tail)  // Also enable K-based TC
      case value :: tail if !value.startsWith("-") =>
        // Non-switch argument is a model file
        parseArgs(map ++ Map('modelFile -> value), tail)
      case option :: tail =>
        println("Unknown option " + option)
        parseArgs(map, tail)  // Continue with remaining args
    }
  }

  def getImportModels(model: Model, fullFileName: String): List[Model] = {
    var importModels = List[Model]()

    if (model != null) {
      try {
        var s: Set[String] = Set()
        if (fullFileName != null) {
          s = Set(fullFileName)
        }
        importModels = processImports(model, s)._1
        // Don't type-check here - it will be type-checked after combining with imports
        // This avoids duplicate type checking when packages are involved
      } catch {
        case TypeCheckException => Misc.errorExit("Main", "Given K did not type check.")
        case e: Throwable =>
          e.printStackTrace()
          Misc.errorExit("Main", "Exception encountered during type checking.")
      }
    }
    importModels
  }

  def combinePackages(packages: List[PackageDecl]): List[PackageDecl] = {
    packages.map(p => combinePackage(p))
  }

  def combinePackage(pkg: PackageDecl): PackageDecl = {
    // Don't recursively combine - just process imports for the package's model
    // This avoids duplicate processing when packages contain classes
    var importModels = if (pkg.model != null) {
      var s: Set[String] = Set()
      try {
        processImports(pkg.model, s)._1
      } catch {
        case _ => List[Model]()
      }
    } else {
      List[Model]()
    }
    var allDecls = importModels.flatMap { x => x.decls }
    var allAnnotations = importModels.flatMap { x => x.annotations }
    var allPackages = importModels.flatMap { x => x.packages }
    var allImports = importModels.flatMap { x => x.imports }
    var m: Model = Model(pkg.model.packageName,
      combinePackages(pkg.model.packages ++ allPackages),
      (pkg.model.imports ++ allImports).toSet.toList,
      pkg.model.annotations ++ allAnnotations,
      pkg.model.decls ++ allDecls)
    var p = new PackageDecl(pkg.name, m)
    p
  }

  def combineModel(model: Model): Model = {
    combineModel(model, null)
  }

  def combineModel(model: Model, fullFileName: String): Model = {
    var importModels = getImportModels(model, fullFileName)

    var allDecls = importModels.flatMap { x => x.decls }
    var allAnnotations = importModels.flatMap { x => x.annotations }
    var allPackages = importModels.flatMap { x => x.packages }
    var allImports = importModels.flatMap { x => x.imports }
    val combinedModel = Model(model.packageName,
      combinePackages(model.packages ++ allPackages),
      (model.imports ++ allImports).toSet.toList,
      model.annotations ++ allAnnotations,
      model.decls ++ allDecls)
    combinedModel
  }

  def scala_main(args: Array[String]): Unit = {
    // First, do a preliminary parse to find the model file and option precedence flags
    val prelimOptions = parseArgs(Map(), args.toList)
    
    // Check option precedence mode:
    // - Default: CLI wins (file options are defaults that CLI can override)
    // - -prefer-file-options: File @preferred_options win over CLI
    // - -ignore-file-options: Completely ignore @preferred_options
    val preferFileOptions = prelimOptions.getOrElse('preferFileOptions, false).asInstanceOf[Boolean]
    val ignoreFileOptions = prelimOptions.getOrElse('ignoreFileOptions, false).asInstanceOf[Boolean]
    
    // If a model file is specified, check for @preferred_options annotations
    val annotationArgs: List[String] = if (ignoreFileOptions) {
      Nil  // Ignore file options entirely
    } else {
      prelimOptions.get('modelFile) match {
        case Some(f: String) =>
          val opts = parsePreferredOptions(f)
          if (opts.nonEmpty) {
            val mode = if (preferFileOptions) "file wins" else "CLI wins"
            log(s"Found @preferred_options in $f: ${opts.mkString(" ")} (mode: $mode)")
          }
          opts
        case _ => Nil
      }
    }
    
    // Apply option precedence based on mode:
    // - Default (CLI wins): prepend annotation args so CLI args come after and override
    // - -prefer-file-options: append annotation args so they override CLI args
    val options = if (annotationArgs.nonEmpty) {
      if (preferFileOptions) {
        // File options win: append them so they override CLI
        parseArgs(Map(), args.toList ++ annotationArgs)
      } else {
        // CLI wins (default): prepend file options so CLI overrides them
        parseArgs(Map(), annotationArgs ++ args.toList)
      }
    } else {
      prelimOptions
    }
    
    var model: Model = null
    var filename: String = null
    var fullFileName: String = null
    var rawSMT: String = null
    var smtModel: String = ""

    options.get('postnobody) match {
      case Some(true) => ASTOptions.checkPostNoBody = true
      case _ =>
    }

    options.get('tests) match {
      case Some(true) => doTests(options.getOrElse('baseline, false).asInstanceOf[Boolean])
      case _ => ()
    }

    options.get('test) match {
      case Some(_) =>
        try {
          print("[main] Please enter the test case to run:")
          val testCase = scala.io.StdIn.readLine().trim
          val fileName = testCase.asInstanceOf[String]
          val testsDir = new File(new File(new File("."), "src"), "tests")
          val file = new File(testsDir, fileName)
          val result = doTest(file, true)
          val baselineFile = new File(testsDir, "baseline.json")
          val baselineObject =
            if (baselineFile.exists) {
              val json = scala.io.Source.fromFile(baselineFile).mkString
              var tokener: JSONTokener = new JSONTokener(json)
              var jsonObject: JSONObject = new JSONObject(tokener)
              jsonObject
            } else {
              new JSONObject()
            }
          if (baselineObject.has(fileName))
            compareSingleResultDetail(baselineObject.getJSONObject(fileName), result, testsDir)
          else log(s"Baseline does not contain $fileName. Cannot compare.")
        } catch {
          case TypeCheckException => errorExit("Type Checking exception.")
          case K2SMTException => errorExit("K2SMT Exception during SMT solving.")
          case K2Z3Exception => errorExit("Z3 Exception during SMT solving.")
        }
      case _ => ()
    }

    // Batch mode: process multiple files from stdin sequentially in single JVM
    // Always checks @expected annotations and baselines
    options.get('batch) match {
      case Some(true) =>
        val batchVerbose = options.get('batchVerbose).contains(true)
        K2Z3.silent = !batchVerbose  // Suppress verbose K2Z3 output unless -batch-verbose
        val showTiming = options.get('timing).contains(true)
        val saveBaseline = options.get('baseline).contains(true)
        val startTime = System.nanoTime()
        var passed = 0
        var failed = 0
        var baselineMatched = 0
        var baselineMismatched = 0
        var total = 0

        // Read file paths from stdin, one per line
        val lines = scala.io.Source.stdin.getLines().toList
        for (testFile <- lines if testFile.trim.nonEmpty) {
          total += 1
          val testStart = System.nanoTime()
          var status = "UNKNOWN"
          var extra = ""
          var outcome = "UNKNOWN"  // SAT, UNSAT, ERROR, TIMEOUT
          var typeChecked = false
          var json1Obj: AnyRef = ""  // JSONObject when available, "" otherwise
          var json2Obj: AnyRef = ""

          val file = new File(testFile.trim)
          val testName = file.getName
          val testDirName = Option(file.getParent).map(p => new File(p).getName).getOrElse(".")
          
          // Load @expected annotation, @preferred_options, and baseline
          val expectedOpt = parseExpectedResult(testFile.trim)
          val preferredOpts = if (ignoreFileOptions) Nil else parsePreferredOptions(testFile.trim)
          val baselineOpt = loadPerFileBaseline(file)
          
          // Determine if ERROR is expected (from annotation or baseline)
          val errorExpected = expectedOpt.contains("ERROR") || 
            baselineOpt.exists(b => !b.optBoolean("typeChecks", true))

          try {
            // Reset all state for each test
            var t0, t1, tReset, tParse, tCombine, tTypeCheck, tSMTGen, tSolve: Long = 0

            if (batchVerbose) {
              log(s"Processing $testFile")
            }

            t0 = System.nanoTime()
            TypeChecker.reset()
            UtilSMT.reset
            K2Z3.reset()
            t1 = System.nanoTime()
            tReset = t1 - t0

            // Apply @preferred_options for this test based on precedence mode:
            // - Default (CLI wins): file options only apply if CLI didn't set them
            // - -prefer-file-options: file options override CLI
            // - -ignore-file-options: file options are already filtered out above
            
            // Parse file's preferred timeout and solver options
            var fileTimeout: Option[Int] = None
            var fileUseDsnPass = false
            var fileUseHeapCegar = false
            var fileUseCvc5 = false
            val timeoutIdx = preferredOpts.indexOf("-timeout")
            if (timeoutIdx >= 0 && timeoutIdx + 1 < preferredOpts.length) {
              try {
                fileTimeout = Some(preferredOpts(timeoutIdx + 1).toInt)
              } catch { case _: NumberFormatException => }
            }
            if (preferredOpts.contains("-dsn-pass")) {
              fileUseDsnPass = true
            }
            if (preferredOpts.contains("-heapcegar")) {
              fileUseHeapCegar = true
            }
            if (preferredOpts.contains("-heapcegar-cvc5")) {
              fileUseHeapCegar = true
              fileUseCvc5 = true
            }
            if (preferredOpts.contains("-cvc5")) {
              fileUseCvc5 = true
            }
            
            // Determine effective timeout based on precedence
            // CLI timeout is in timeoutValue (set by -timeout flag in parseArgs)
            // The default timeoutValue is 30000
            val cliHasTimeout = options.contains('timeout) || timeoutValue != 30000
            val testTimeout = if (preferFileOptions) {
              // File wins: use file timeout if specified, otherwise CLI
              fileTimeout.getOrElse(timeoutValue)
            } else {
              // CLI wins (default): use CLI timeout if explicitly set, otherwise file
              if (cliHasTimeout) timeoutValue else fileTimeout.getOrElse(timeoutValue)
            }
            
            // Determine effective solver options based on precedence
            val cliHasDsnPass = options.getOrElse('dsnPass, false).asInstanceOf[Boolean]
            val cliHasHeapCegar = options.getOrElse('heapcegar, false).asInstanceOf[Boolean]
            val cliHasCvc5 = options.getOrElse('cvc5, false).asInstanceOf[Boolean] ||
                             options.getOrElse('heapcegarcvc5, false).asInstanceOf[Boolean]
            
            val useDsnPass = if (preferFileOptions) {
              if (fileUseDsnPass) true else cliHasDsnPass
            } else {
              if (cliHasDsnPass) true else fileUseDsnPass
            }
            
            val useHeapCegar = if (preferFileOptions) {
              if (fileUseHeapCegar) true else cliHasHeapCegar
            } else {
              if (cliHasHeapCegar) true else fileUseHeapCegar
            }
            
            val useCvc5 = if (preferFileOptions) {
              if (fileUseCvc5) true else cliHasCvc5
            } else {
              if (cliHasCvc5) true else fileUseCvc5
            }
            
            // Set CVC5 compatibility flag for SMT generation
            if (useCvc5) {
              ASTOptions.cvc5Compatible = true
            } else {
              ASTOptions.cvc5Compatible = false
            }

            if (!file.exists()) {
              status = "NOTFOUND"
              outcome = "ERROR"
            } else {
              t0 = System.nanoTime()
              val testModel = getModelFromFile(testFile.trim)
              t1 = System.nanoTime()
              tParse = t1 - t0

              if (testModel != null) {
                val testDir = if (file.getParent == null) "." else file.getParent
                classpath = Set(testDir)
                modelFileDirectory = testDir

                t0 = System.nanoTime()
                val combinedModel = combineModel(testModel, testFile.trim)
                t1 = System.nanoTime()
                tCombine = t1 - t0

                t0 = System.nanoTime()
                val tc: TypeChecker = new TypeChecker(combinedModel)
                tc.smtCheck
                t1 = System.nanoTime()
                tTypeCheck = t1 - t0
                typeChecked = true
                if (batchVerbose) {
                  log("Type checking completed. No errors found.")
                }

                // Generate json1 and json2 for baseline (same as doTest())
                ASTOptions.useJson1 = true
                json1Obj = combinedModel.toJson
                ASTOptions.useJson1 = false
                json2Obj = combinedModel.toJson

                t0 = System.nanoTime()
                val smtStr = combinedModel.toSMT
                t1 = System.nanoTime()
                tSMTGen = t1 - t0

                // Print statistics in verbose mode
                if (batchVerbose) {
                  println(UtilSMT.statistics)
                }

                t0 = System.nanoTime()
                
                if (useDsnPass) {
                  // Use DSN_Pass solver for scenario-based incremental solving
                  val solveResult = runWithTimeout(testTimeout) {
                    import k.frontend.DSNPassSolver
                    val result = DSNPassSolver.solveByScenariosIncremental(combinedModel, smtStr, testTimeout)
                    result match {
                      case Some(scenarioResult) =>
                        scenarioResult.result match {
                          case Satisfiable(model, _) => K2Z3.z3Model = model
                          case _ =>
                        }
                      case None => // All scenarios failed
                    }
                  }
                  // Determine outcome
                  if (solveResult.isEmpty) {
                    outcome = "TIMEOUT"
                  } else if (K2Z3.z3Model != null && K2Z3.z3Model.toString != "()") {
                    outcome = "SAT"
                    if (batchVerbose) K2Z3.PrintModel(combinedModel)
                  } else {
                    outcome = "UNSAT"
                  }
                } else if (useHeapCegar) {
                  // Use Heap CEGAR for dynamic object creation
                  val solveResult = runWithTimeout(testTimeout) {
                    val result = UnifiedSolver.solveWithHeapCegar(combinedModel, printModel = batchVerbose)
                    result match {
                      case UnifiedSolver.SolveResult.Sat(model) => 
                        K2Z3.z3Model = model
                        outcome = "SAT"
                      case UnifiedSolver.SolveResult.Unsat =>
                        outcome = "UNSAT"
                      case UnifiedSolver.SolveResult.Timeout =>
                        outcome = "TIMEOUT"
                      case UnifiedSolver.SolveResult.Unknown(reason) =>
                        outcome = "UNKNOWN"
                    }
                  }
                  if (solveResult.isEmpty) {
                    outcome = "TIMEOUT"
                  }
                } else if (batchVerbose) {
                  // Use shared auto-detection logic for verbose mode
                  val solveResult = runWithTimeout(testTimeout) {
                    val (resultOutcome, z3ModelOpt) = solveWithAutoDetection(
                      combinedModel, smtStr, options, testTimeout, printModel = true, verbose = true
                    )
                    outcome = resultOutcome
                    z3ModelOpt.foreach(m => K2Z3.z3Model = m)
                  }
                  if (solveResult.isEmpty) {
                    outcome = "TIMEOUT"
                  }
                } else {
                  // Non-verbose batch mode: use simple K2Z3.solveSMT (faster)
                  val solveResult = runWithTimeout(testTimeout) {
                    K2Z3.solveSMT(combinedModel, smtStr, false)
                  }
                  // Determine outcome from solve result
                  if (solveResult.isEmpty) {
                    outcome = "TIMEOUT"
                  } else if (K2Z3.z3Model != null && K2Z3.z3Model.toString != "()") {
                    outcome = "SAT"
                  } else {
                    outcome = "UNSAT"
                    // Try to get partial model for UNSAT (for debugging/baselines)
                    tryGetPartialModelForUnsat(combinedModel, smtStr)
                  }
                }
                t1 = System.nanoTime()
                tSolve = t1 - t0

                // Build current result JSON for comparison
                val resultJson = new JSONObject()
                resultJson.put("name", testName)
                resultJson.put("outcome", outcome)  // SAT, UNSAT, ERROR, TIMEOUT, UNKNOWN
                resultJson.put("typeChecks", typeChecked)
                resultJson.put("model", if (combinedModel != null) combinedModel.toString else "")
                resultJson.put("smt", smtStr)
                resultJson.put("smtModel", if (K2Z3.z3Model != null) K2Z3.z3Model.toString else "")
                resultJson.put("json1", json1Obj)
                resultJson.put("json2", json2Obj)

                // Determine pass/fail based on BOTH checks:
                // 1. @expected annotation (if present, outcome must match)
                // 2. Baseline file (if present, must match)
                // 3. Default: SAT/UNSAT = PASS if neither @expected nor baseline present
                // Fail if EITHER check fails
                
                var expectedPassed = true
                var expectedInfo = ""
                var baselinePassed = true
                var baselineInfo = ""
                
                // Check @expected if present
                expectedOpt match {
                  case Some(expected) if expected == outcome =>
                    expectedInfo = s"$outcome (expected)"
                  case Some(expected) =>
                    expectedPassed = false
                    expectedInfo = s"got $outcome, expected $expected"
                  case None =>
                    expectedInfo = outcome
                }
                
                // Check baseline if present
                baselineOpt match {
                  case Some(baseline) =>
                    val (matches, details) = compareResult(baseline, resultJson)
                    if (matches) {
                      baselineMatched += 1
                      baselineInfo = "baseline OK"
                    } else {
                      baselineMismatched += 1
                      baselinePassed = false
                      val fieldNames = List("typeChecks", "model", "json1", "json2", "smt", "smtModel")
                      val fieldResults = details.tail
                      val mismatchFields = fieldResults.zip(fieldNames)
                        .collect { case (v, f) if v == "false" || v == "???" => f }
                        .mkString(", ")
                      baselineInfo = s"baseline mismatch: $mismatchFields"
                    }
                  case None =>
                    // No baseline to check
                }
                
                // Determine overall pass/fail
                if (expectedPassed && baselinePassed) {
                  status = "PASSED"
                  passed += 1
                  // Build extra info
                  extra = if (baselineOpt.isDefined) {
                    s"$expectedInfo, $baselineInfo"
                  } else {
                    expectedInfo
                  }
                } else {
                  status = "FAILED"
                  failed += 1
                  // Show what failed
                  val failures = List(
                    if (!expectedPassed) Some(expectedInfo) else None,
                    if (!baselinePassed) Some(baselineInfo) else None
                  ).flatten.mkString("; ")
                  extra = failures
                }

                // Save baseline if requested
                if (saveBaseline) {
                  savePerFileBaseline(file, resultJson)
                }

                // Add timing breakdown if requested
                if (showTiming) {
                  extra = f"$extra reset=${tReset/1e6}%.0f,parse=${tParse/1e6}%.0f,combine=${tCombine/1e6}%.0f,tc=${tTypeCheck/1e6}%.0f,smt=${tSMTGen/1e6}%.0f,solve=${tSolve/1e6}%.0f"
                }
              }
            }
          } catch {
            case TypeCheckException =>
              outcome = "ERROR"
              // Check if ERROR was expected (via @expected or baseline)
              if (errorExpected) {
                status = "PASSED"
                extra = "ERROR (expected)"
                passed += 1
              } else {
                expectedOpt match {
                  case Some(expected) =>
                    status = "FAILED"
                    extra = s"got ERROR (type check), expected $expected"
                    failed += 1
                  case None =>
                    // No @expected - check if baseline expects error
                    if (baselineOpt.exists(b => getOutcomeFromResult(b) == "ERROR")) {
                      status = "PASSED"
                      extra = "ERROR (matches baseline)"
                      passed += 1
                      baselineMatched += 1
                    } else if (baselineOpt.isDefined) {
                      status = "FAILED"
                      extra = "ERROR (type check) - baseline expected different"
                      failed += 1
                      baselineMismatched += 1
                    } else {
                      // No @expected and no baseline - error is a failure
                      status = "FAILED"
                      extra = "ERROR (type check)"
                      failed += 1
                    }
                }
              }
            case K2SMTException =>
              outcome = "ERROR"
              if (errorExpected) {
                status = "PASSED"
                extra = "ERROR (expected)"
                passed += 1
              } else {
                expectedOpt match {
                  case Some(expected) =>
                    status = "FAILED"
                    extra = s"got ERROR (K2SMT), expected $expected"
                    failed += 1
                  case None =>
                    if (baselineOpt.exists(b => getOutcomeFromResult(b) == "ERROR")) {
                      status = "PASSED"
                      extra = "ERROR (matches baseline)"
                      passed += 1
                      baselineMatched += 1
                    } else if (baselineOpt.isDefined) {
                      status = "FAILED"
                      extra = "ERROR (K2SMT) - baseline expected different"
                      failed += 1
                      baselineMismatched += 1
                    } else {
                      status = "FAILED"
                      extra = "ERROR (K2SMT)"
                      failed += 1
                    }
                }
              }
            case K2Z3Exception =>
              outcome = "ERROR"
              if (errorExpected) {
                status = "PASSED"
                extra = "ERROR (expected)"
                passed += 1
              } else {
                expectedOpt match {
                  case Some(expected) =>
                    status = "FAILED"
                    extra = s"got ERROR (K2Z3), expected $expected"
                    failed += 1
                  case None =>
                    if (baselineOpt.exists(b => getOutcomeFromResult(b) == "ERROR")) {
                      status = "PASSED"
                      extra = "ERROR (matches baseline)"
                      passed += 1
                      baselineMatched += 1
                    } else if (baselineOpt.isDefined) {
                      status = "FAILED"
                      extra = "ERROR (K2Z3) - baseline expected different"
                      failed += 1
                      baselineMismatched += 1
                    } else {
                      status = "FAILED"
                      extra = "ERROR (K2Z3)"
                      failed += 1
                    }
                }
              }
            case e: Throwable =>
              outcome = "ERROR"
              status = "FAILED"
              extra = e.getClass.getSimpleName + ": " + Option(e.getMessage).getOrElse("").take(50)
              failed += 1
          }

          val testEnd = System.nanoTime()
          val duration = (testEnd - testStart) / 1e9

          // Output pipe-delimited: status|duration|dir|name|extra
          println(f"$status|$duration%.2f|$testDirName|$testName|$extra")
          System.out.flush()
        }

        val totalTime = (System.nanoTime() - startTime) / 1e9
        // Output summary line with baseline stats
        println(f"SUMMARY|$totalTime%.2f|$total|$passed|$failed|$baselineMatched|$baselineMismatched")
        System.out.flush()
        return

      case _ => ()
    }

    options.get('modelFile) match {
      case Some(f: String) =>
        log(s"Processing $f")
        model = getModelFromFile(f)
        filename = Paths.get(f).getFileName.toString
        fullFileName = f
        if (Paths.get(f).getParent == null)
          modelFileDirectory = new java.io.File(".").getCanonicalPath
        else
          modelFileDirectory = Paths.get(f).getParent.toString
        classpath = classpath + modelFileDirectory

        // massage classpath
        classpath = classpath.map { x =>
          // Don't prepend modelFileDirectory if x is already equal to it or is absolute
          if (!Paths.get(x).isAbsolute() && x != modelFileDirectory) 
            Paths.get(modelFileDirectory, x).toString
          else x
        }
        classpath = classpath.map {
          _.trim
        }

        println("CLASSPATH set to: " + classpath.mkString(","))

      case _ => ()
    }

    options.get('rawSMTFile) match {
      case Some(f: String) =>
        rawSMT = getRawSMTFromFile(f)
        K2Z3.debugRawModel = true
      case _ => ()
    }

    options.get('instances) match {
      case Some(i: Int) =>
        ASTOptions.numberOfInstances = i
      case _ => ()
    }

    options.get('mmsJson) match {
      case Some(file: String) => {
        model = parseMMSJsonFromFile(file)
      }
      case _ => ()
    }

    options.get('printJson) match {
      case Some(_) =>
        if (model != null) {
          // Remember old value of option
          val optionsUseJson1 = ASTOptions.useJson1
          // MMS method using toJson1
          ASTOptions.useJson1 = true
          println("JSON1: " + model.toJson)
          val modelFromJson = visitJsonObject(model.toJson).asInstanceOf[Model]
          // MMS method using toJson2
          ASTOptions.useJson1 = false
          println("JSON2: " + model.toJson)
          val modelFromJson2 = visitJsonObject2(model.toJson).asInstanceOf[Model]

          // Reset old value of option
          ASTOptions.useJson1 = optionsUseJson1
        } else
          println("Model was null!")
      case _ => ()
    }

    if (model != null && !options.contains('tc)) {

      //      //case class Model(packageName: Option[PackageDecl], imports: List[ImportDecl],
      //      //annotations: List[AnnotationDecl],
      //      //decls: List[TopDecl]) {
      //      var allDecls = importModels.flatMap { x => x.decls }
      //      var allAnnotations = importModels.flatMap { x => x.annotations }
      //      var allPackages = importModels.flatMap { x => x.packages }
      //      var allImports =  importModels.flatMap { x => x.imports }
      //      val combinedModel = Model(model.packageName,
      //        model.packages ++ allPackages,
      //        (model.imports ++ allImports).toSet.toList,
      //        model.annotations ++ allAnnotations,
      //        model.decls ++ allDecls)
      val combinedModel = combineModel(model, fullFileName)
      
      // If -analyze is specified, analyze problem properties and exit
      if (options.contains('analyze)) {
        val props = ProblemAnalyzer.analyze(combinedModel)
        println("\n" + "="*60)
        println("PROBLEM ANALYSIS")
        println("="*60)
        println(s"File: $fullFileName")
        println()
        println("Heap Properties:")
        println(s"  hasDynamicHeap: ${props.hasDynamicHeap}")
        println(s"  dynamicClasses: ${props.dynamicClasses.mkString(", ")}")
        println(s"  fixedObjectCount: ${props.fixedObjectCount}")
        println()
        println("Structure Properties:")
        println(s"  hasScenarios: ${props.hasScenarios}")
        println(s"  scenarioCount: ${props.scenarioCount}")
        println()
        println("Complexity Metrics:")
        println(s"  classCount: ${props.classCount}")
        println(s"  constraintCount: ${props.constraintCount}")
        println(s"  propertyCount: ${props.propertyCount}")
        println(s"  functionCount: ${props.functionCount}")
        println()
        println("Special Features:")
        println(s"  hasExternalFunctions: ${props.hasExternalFunctions}")
        println(s"  hasCollections: ${props.hasCollections}")
        println(s"  hasQuantifiers: ${props.hasQuantifiers}")
        println(s"  hasRecursion: ${props.hasRecursion}")
        println()
        println(s"Estimated Complexity: ${props.estimatedComplexity}")
        println()
        
        // Show recommended config
        val config = ProblemAnalyzer.selectConfig(props, SolveConfig.default)
        println("Recommended Configuration:")
        println(s"  heapStrategy: ${config.heapStrategy}")
        println(s"  incrementalMode: ${config.incrementalMode}")
        println(s"  initialTimeoutMs: ${config.initialTimeoutMs}")
        println(s"  useSoftConstraintFallback: ${config.useSoftConstraintFallback}")
        if (props.hasDynamicHeap) {
          println(s"  initialHeapBounds: ${config.initialHeapBounds}")
        }
        println("="*60)
        return
      }
      
      // If -ktc-gen is specified, generate K type check program and exit
      if (options.contains('ktcGen)) {
        log("Generating K type check program...")
        val kProgram = KTypeChecker.generateTypeCheckProgram(combinedModel)
        println(kProgram)
        return
      }
      
      // Type checking: Traditional is default, use -ktc for K-based
      if (options.contains('ktc)) {
        // K-based type checker (experimental - spawns subprocess)
        val modeName = (KTypeChecker.requireDeclarations, KTypeChecker.requireUnambiguousTypes) match {
          case (true, true) => "Strict"
          case (false, true) => "InferredDecls"
          case (true, false) => "AmbiguousTypes"
          case (false, false) => "FullyFlexible"
        }
        log(s"Using K-based type checker (mode: $modeName)...")
        val result = KTypeChecker.typeCheck(combinedModel)
        if (result.success) {
          log("Type checking completed. No errors found.")
          if (result.inferredTypes.nonEmpty) {
            logDebug(s"Inferred types: ${result.inferredTypes.map { case (k, v) => s"$k: $v" }.mkString(", ")}")
          }
        } else {
          result.errors.foreach(e => println(s"[KTypeChecker] Error: $e"))
          errorExit("Type checking failed.")
        }
      } else {
        // Traditional type checker (default)
        TypeChecker.reset()
        val tc: TypeChecker = new TypeChecker(combinedModel)
        tc.smtCheck
        log("Type checking completed. No errors found.")
      }

      // Set CVC5 compatibility flag BEFORE SMT generation if -cvc5 is specified
      val useCVC5 = options.getOrElse('cvc5, false).asInstanceOf[Boolean]
      if (useCVC5) {
        ASTOptions.cvc5Compatible = true
      }

      val beforeLen = smtModel.length
      smtModel += combinedModel.toSMT
      val afterLen = smtModel.length
      // Always write SMT model to log file for debugging
      try {
        val smtLogFile = new java.io.PrintWriter(new java.io.FileOutputStream(".tmp/k_smt_model.log", false))
        smtLogFile.println("=== SMT Model Generated (" + new java.util.Date() + ") ===")
        smtLogFile.println(smtModel)
        smtLogFile.close()
        println("[SMT model written to .tmp/k_smt_model.log]")
      } catch { case e: Throwable => println("[Failed to write SMT model: " + e) }
      println(UtilSMT.statistics)
      try {
        val useIncremental = options.getOrElse('incremental, false).asInstanceOf[Boolean]
        val useScenarioTracking = options.getOrElse('scenarioTracking, false).asInstanceOf[Boolean]
        val useLegacy = options.getOrElse('legacy, false).asInstanceOf[Boolean] // Legacy solver (old path)
        val useDsnPass = options.getOrElse('dsnPass, false).asInstanceOf[Boolean] // DSN_Pass.k solver (scenario-based incremental)
        val useUnifiedScenarios = options.getOrElse('unifiedScenarios, false).asInstanceOf[Boolean] // Scenario-based unified solver
        val useHeapCegar = options.getOrElse('heapcegar, false).asInstanceOf[Boolean]
        val useBAE = options.getOrElse('bae, false).asInstanceOf[Boolean]
        val useYices = options.getOrElse('yices, false).asInstanceOf[Boolean]
        val useMathSAT = options.getOrElse('mathsat, false).asInstanceOf[Boolean]
        val useAuto = options.getOrElse('auto, false).asInstanceOf[Boolean]
        val useUnified = options.getOrElse('unified, false).asInstanceOf[Boolean]  // Only if explicitly requested
        // useCVC5 is already defined above
        
        // Check if any specific solver/strategy was explicitly requested
        val hasExplicitStrategy = useDsnPass || useScenarioTracking || useIncremental || 
          useUnifiedScenarios || useLegacy || useHeapCegar || useBAE || useYices || useMathSAT || useCVC5 || useUnified
        
        // AUTO-DETECTION is now the DEFAULT when no explicit strategy is specified
        // Use -unified flag to force the old UnifiedSolver behavior
        if (!hasExplicitStrategy || useAuto) {
          val (resultOutcome, z3ModelOpt) = solveWithAutoDetection(
            combinedModel, smtModel, options, timeoutValue, printModel = true, verbose = true
          )
          z3ModelOpt.foreach(m => K2Z3.z3Model = m)
        // DSN_Pass.k-specific solver (runs if explicitly specified)
        } else if (useDsnPass) {
          println("[main] Using DSN_Pass Solver (scenario-based incremental)")
          import k.frontend.DSNPassSolver
          val result = DSNPassSolver.solveByScenariosIncremental(combinedModel, smtModel, timeoutValue)
          result match {
            case Some(scenarioResult) =>
              log(s"DSNPassSolver: SAT (${scenarioResult.scenario.name})")
              scenarioResult.result match {
                case Satisfiable(model, _) =>
                  K2Z3.z3Model = model
                  K2Z3.PrintModel(combinedModel)
                case _ =>
              }
            case None =>
              log("DSNPassSolver: UNSAT or all scenarios failed")
          }
        // Diagnostic tools
        } else if (useScenarioTracking) {
          println("[main] Using Scenario Tracking Diagnostic")
          import k.frontend.ScenarioTrackingDiagnostic
          val results = ScenarioTrackingDiagnostic.diagnoseWithScenarios(combinedModel, smtModel, timeoutValue)
          println("\n" + "="*70)
          println("SCENARIO TRACKING SUMMARY")
          println("="*70)
          results.foreach { status =>
            println(s"\nAfter ${status.constraintGroup}:")
            status.scenarioResults.foreach { case (scenarioName, result) =>
              val resultStr = result match {
                case Satisfiable(_, _) => "SAT"
                case Unsatisfiable(_, _) => "UNSAT"
                case Unknown(_, _, true) => "TIMEOUT"
                case Unknown(reason, _, _) => s"UNKNOWN ($reason)"
              }
              println(s"  $scenarioName: $resultStr")
            }
          }
          println("="*70)
        } else if (useIncremental) {
          println("[main] Using Incremental Solving Diagnostic")
          val results = IncrementalDiagnostic.diagnoseModel(combinedModel, smtModel, timeoutValue)
          println("\n" + "="*70)
          println("DIAGNOSTIC SUMMARY")
          println("="*70)
          results.foreach { result =>
            val status = result.result match {
              case Satisfiable(_, _) => "SAT"
              case Unsatisfiable(_, _) => "UNSAT"
              case Unknown(_, _, true) => "TIMEOUT"
              case Unknown(reason, _, _) => s"UNKNOWN ($reason)"
            }
            println(s"${result.groupName}: $status (${result.timeMs}ms)")
          }
          println("="*70)
        // UnifiedSolver (default Z3-based solver)
        } else if (useUnified) {
          log("Using UnifiedSolver")
          // Scenario tracking is disabled by default - enable only when explicitly requested
          // (It can be enabled via -unified-scenarios flag or detected automatically for disjunctive models)
          UnifiedSolver.useScenarioTracking = false
          // Note: CVC5 compatibility is already set via ASTOptions.cvc5Compatible above if -cvc5 is used
          // The unified solver uses Z3's API directly (via K2Z3 infrastructure)
          val result = UnifiedSolver.solve(combinedModel, smtModel, printModel = true, timeoutMs = Some(timeoutValue))
          result match {
            case UnifiedSolver.SolveResult.Sat(model) =>
              log("UnifiedSolver: SAT")
              // Model is already printed by UnifiedSolver when printModel=true, so we don't need to print again
              // Just set it for other potential uses
              K2Z3.z3Model = model
            case UnifiedSolver.SolveResult.Unsat =>
              log("UnifiedSolver: UNSAT")
            case UnifiedSolver.SolveResult.Timeout =>
              log("UnifiedSolver: TIMEOUT")
            case UnifiedSolver.SolveResult.Unknown(reason) =>
              log(s"UnifiedSolver: UNKNOWN ($reason)")
          }
        // External solvers (BAE, Yices, MathSAT, CVC5)
        } else if (useBAE) {
          println("[main] Using BAE Solver")
          BAESolver.debug = K2Z3.debug
          BAESolver.solveSMT(combinedModel, fullFileName, true)
        } else if (useYices) {
          println("[main] Using Yices Solver")
          if (!YicesSolver.isAvailable) {
            println("[Yices] WARNING: Yices not found. Install it or set YicesSolver.yicesPath")
            println("[Yices] Falling back to Z3...")
            val res = runWithTimeout(timeoutValue) {
              K2Z3.solveSMT(combinedModel, smtModel, true)
            }
            if (res.isEmpty) log("Timeout")
          } else {
            YicesSolver.debug = K2Z3.debug
            YicesSolver.solveSMT(combinedModel, smtModel, true)
          }
        } else if (useMathSAT) {
          println("[main] Using MathSAT Solver")
          if (!MathSATSolver.isAvailable) {
            println("[MathSAT] WARNING: MathSAT not found. Install it or set MathSATSolver.mathsatPath")
            println("[MathSAT] Falling back to Z3...")
            val res = runWithTimeout(timeoutValue) {
              K2Z3.solveSMT(combinedModel, smtModel, true)
            }
            if (res.isEmpty) log("Timeout")
          } else {
            MathSATSolver.debug = K2Z3.debug
            MathSATSolver.solveSMT(combinedModel, smtModel, true)
          }
        } else if (useCVC5) {
          println("[main] Using CVC5 Solver")
          if (!CVC5Solver.isAvailable) {
            println("[CVC5] WARNING: CVC5 not found. Install it or set CVC5Solver.cvc5Path")
            println("[CVC5] Falling back to unified solver...")
            // Fall back to unified solver when CVC5 is not available
            UnifiedSolver.useScenarioTracking = false
            val result = UnifiedSolver.solve(combinedModel, smtModel, printModel = true, timeoutMs = Some(timeoutValue))
            result match {
              case UnifiedSolver.SolveResult.Sat(model) =>
                log("UnifiedSolver: SAT")
                try {
                  K2Z3.z3Model = model
                  K2Z3.PrintModel(combinedModel)
                } catch {
                  case e: Throwable =>
                    log(s"Note: Could not print model: ${e.getMessage}")
                }
              case UnifiedSolver.SolveResult.Unsat =>
                log("UnifiedSolver: UNSAT")
              case UnifiedSolver.SolveResult.Timeout =>
                log("UnifiedSolver: TIMEOUT")
              case UnifiedSolver.SolveResult.Unknown(reason) =>
                log(s"UnifiedSolver: UNKNOWN ($reason)")
            }
          } else {
            CVC5Solver.debug = K2Z3.debug
            CVC5Solver.solveSMT(combinedModel, smtModel, true)
          }
        } else if (useLegacy) {
          // Legacy solver path (old behavior) - only used when -legacy flag is specified
          log("Using legacy solver")
          val res = runWithTimeout(timeoutValue) {
            K2Z3.solveSMT(combinedModel, smtModel, true)
          }
          if (res.isEmpty) log("Timeout")
        } else if (useHeapCegar) {
          println("[main] Using Heap CEGAR Solver")
          // CVC5 compatibility is already set via ASTOptions.cvc5Compatible above if -cvc5 or -heapcegar-cvc5 is used
          // The heap CEGAR solver uses the standard solve method which works with both Z3 and CVC5-compatible SMT
          val result = UnifiedSolver.solveWithHeapCegar(combinedModel, printModel = true)
          result match {
            case UnifiedSolver.SolveResult.Sat(model) =>
              log("HeapCEGAR: SAT")
            case UnifiedSolver.SolveResult.Unsat =>
              log("HeapCEGAR: UNSAT")
            case UnifiedSolver.SolveResult.Timeout =>
              log("HeapCEGAR: TIMEOUT")
            case UnifiedSolver.SolveResult.Unknown(reason) =>
              log(s"HeapCEGAR: UNKNOWN ($reason)")
          }
        } else if (options.getOrElse('heapsoft, false).asInstanceOf[Boolean]) {
          println("[main] Using Soft-Bounded Heap Solver (Optimize API)")
          val result = UnifiedSolver.solveWithSoftHeap(combinedModel, printModel = true)
          result match {
            case UnifiedSolver.SolveResult.Sat(model) =>
              log("HeapSoft: SAT")
            case UnifiedSolver.SolveResult.Unsat =>
              log("HeapSoft: UNSAT")
            case UnifiedSolver.SolveResult.Timeout =>
              log("HeapSoft: TIMEOUT")
            case UnifiedSolver.SolveResult.Unknown(reason) =>
              log(s"HeapSoft: UNKNOWN ($reason)")
          }
        } else if (options.getOrElse('minizinc, false).asInstanceOf[Boolean] ||
                   options.getOrElse('emitMzn, false).asInstanceOf[Boolean]) {
          // MiniZinc solver
          println("[main] Using MiniZinc Solver")

          // Translate K model to MiniZinc
          val mznResult = K2MiniZinc.translate(combinedModel)

          // Print any warnings
          if (mznResult.warnings.nonEmpty) {
            println("[MiniZinc] Warnings:")
            mznResult.warnings.foreach(w => println(s"  - $w"))
          }

          // Write MiniZinc model to file
          val mznFile = new java.io.File(".tmp/model.mzn")
          val mznWriter = new java.io.PrintWriter(mznFile)
          mznWriter.write(mznResult.mznCode)
          mznWriter.close()
          println(s"[MiniZinc] Model written to ${mznFile.getAbsolutePath}")

          // If -emit-mzn only, just output and exit
          if (options.getOrElse('emitMzn, false).asInstanceOf[Boolean]) {
            println("\n=== MiniZinc Model ===")
            println(mznResult.mznCode)
            println("======================")
          } else {
            // Actually solve with MiniZinc
            if (!MiniZincSolver.isAvailable) {
              println("[MiniZinc] WARNING: MiniZinc not found. Install it from https://www.minizinc.org/")
              println("[MiniZinc] Model saved to .tmp/model.mzn - run manually with: minizinc .tmp/model.mzn")
            } else {
              val solver = options.getOrElse('mznSolver, "gecode").asInstanceOf[String]
              val mznTimeout = options.getOrElse('mznTimeout, timeoutValue).asInstanceOf[Int]
              println(s"[MiniZinc] Solving with $solver (timeout: ${mznTimeout}ms)...")
              MiniZincSolver.verbose = K2Z3.debug
              MiniZincSolver.timeout = mznTimeout
              val result = MiniZincSolver.solve(mznResult.mznCode, solver)

              result.status match {
                case MznSat =>
                  println("[MiniZinc] SAT")
                  result.firstSolution.foreach { sol =>
                    println("Solution:")
                    println(MiniZincSolver.formatSolution(sol))
                  }
                case MznUnsat =>
                  println("[MiniZinc] UNSAT")
                case MznUnknown =>
                  println(s"[MiniZinc] UNKNOWN: ${result.error}")
                case MznError =>
                  println(s"[MiniZinc] ERROR: ${result.error}")
              }
            }
          }
        } else if (options.getOrElse('unifiedScenarios, false).asInstanceOf[Boolean]) {
          log("Using UnifiedSolver with Scenario Tracking")
          import k.frontend.UnifiedSolverWithScenarios
          val result = UnifiedSolverWithScenarios.solve(combinedModel, smtModel, printModel = true, timeoutValue)
          val shouldPrintModel = !options.getOrElse('batch, false).asInstanceOf[Boolean]
          result match {
            case UnifiedSolver.SolveResult.Sat(z3Model) =>
              log("UnifiedSolver+Scenarios: SAT")
              K2Z3.z3Model = z3Model
              // Print model using existing K2Z3 infrastructure
              if (shouldPrintModel) {
                K2Z3.PrintModel(combinedModel)
              }
            case UnifiedSolver.SolveResult.Unsat =>
              log("UnifiedSolver+Scenarios: UNSAT")
            case UnifiedSolver.SolveResult.Timeout =>
              log("UnifiedSolver+Scenarios: TIMEOUT")
            case UnifiedSolver.SolveResult.Unknown(reason) =>
              log(s"UnifiedSolver+Scenarios: UNKNOWN ($reason)")
          }
        } else {
          val res = runWithTimeout(timeoutValue) {
            K2Z3.solveSMT(combinedModel, smtModel, true)
          }
          if (res.isEmpty) log("Timeout")
        }
      } catch {
        case TypeCheckException => errorExit("Type Checking exception.")
        case K2SMTException => errorExit("K2SMT Exception during SMT solving.")
        case K2Z3Exception => errorExit("Z3 Exception during SMT solving.")
        case e: Throwable =>
          e.printStackTrace()
          errorExit("Unknown Exception during SMT solving.")
      }
    }

    if (rawSMT != null) {
      if (K2Z3.debug) {
        // Write raw SMT to log file
        try {
          val smtLogFile = new java.io.PrintWriter(new java.io.FileOutputStream(".tmp/k_smt_raw.log", false))
          smtLogFile.println("=== Raw SMT (" + new java.util.Date() + ") ===")
          smtLogFile.println(rawSMT)
          smtLogFile.close()
          println("[Raw SMT written to .tmp/k_smt_raw.log]")
        } catch { case _: Throwable => }
      }
      try {
        val res = runWithTimeout(timeoutValue) {
          K2Z3.solveSMT(null, rawSMT, false)
        }
        if (res.isEmpty) log("Timeout")

      } catch {
        case TypeCheckException => errorExit("Type Checking exception.")
        case K2SMTException => errorExit("K2SMT Exception during SMT solving.")
        case K2Z3Exception => errorExit("Z3 Exception during SMT solving.")
        case e: Throwable =>
          e.printStackTrace()
          errorExit("Unknown Exception during SMT solving.")
      }
    }

    options.get('latex) match {
      case Some(_) => if (model != null) K2Latex.convert(filename, model)
      case _ => ()
    }

    options.get('scala) match {
      case Some(_) =>
        if (model != null && fullFileName != null) {
          val file = new FileWriter(fullFileName + ".scala", false)
          val scalaProgram = model.toScala
          file.append(scalaProgram)
          file.close()
          if (K2Z3.debug) {
            println()
            println("--- Scala Program ---")
            println()
            println(scalaProgram)
            println()
            println("-----------------")
            println()
          }
        }
      case _ => ()
    }

    options.get('dot) match {
      case Some(_) => if (model != null) printClassDOT(filename, model)
      case _ => ()
    }

    options.get('stats) match {
      case Some(_) => printStats(model)
      case _ => ()
    }

    options.get('expression) match {
      case Some(expressionString: String) => {
        println(exp2Json(expressionString))
      }
      case _ => ()
    }

    options.get('query) match {
      case Some(_) => {
        /*  Removing elastic since it's not used and complicates the build.
        try {
          doElastic(model)
        } catch {
          case e: Throwable =>
            e.printStackTrace()
        }
        */
      }
      case _ => ()
    }

  }

  def getImportFileLocationFromClassPath(fileName: String): String = {
    for (d <- classpath) {
      val path = Paths.get(d, fileName)
      if (Files.exists(path)) return path.toString
    }
    return null
  }

  def processImports(model: Model, processed: Set[String]): (List[Model], Set[String]) = {
    var models = List[Model]()
    var newProcessed = processed
    if (newProcessed == null) {
      newProcessed = Set()
    }
    // Known Java package roots - imports starting with these are Java imports, not K imports
    val javaPackageRoots = Set("java", "javax", "scala", "com", "org", "gov", "edu", "net")

    for (i <- model.imports) {
      val firstPart = i.name.names.headOption.getOrElse("")

      // Skip Python imports - they are handled by PythonExternalFunctions
      if (i.isPython) {
        log(s"Skipping Python import ${i.name} (handled by PythonExternalFunctions)")
      }
      // Skip Java imports - they are handled by the TypeChecker
      else if (javaPackageRoots.contains(firstPart) || i.isJava) {
        log(s"Skipping Java import ${i.name} (handled by type checker)")
      } else {
        val iFile = getImportFileLocationFromClassPath((i.name.toPath + ".k").toString)
        if (iFile == null) {
          errorExit(s"Import ${i.name} could not be found!")
        }
        if (!newProcessed.contains(iFile)) {
          log(s"Processing import $iFile")
          val iModel = getModelFromFile(iFile)
          newProcessed += iFile
          val (importImports, iProcessed) = processImports(iModel, newProcessed)
          newProcessed = newProcessed ++ iProcessed
          // TypeChecker will be called on the fully combined model later
          models = iModel :: (models ++ importImports)
        } else {
          log(s"Skipping $iFile (already processed).")
        }
      }
    }
    return (models, newProcessed)
  }


  def getFileTree(f: File): Stream[File] =
    f #:: (if (f.isDirectory) f.listFiles().toStream.flatMap(getFileTree)
    else Stream.empty)

  def doTest(file: File, debug: Boolean): JSONObject = {
    log(s"Running test ${file.getName}")
    TypeChecker.reset
    UtilSMT.reset
    K2Z3.debug = debug
    K2Z3.silent = !debug
    ASTOptions.debug = debug
    ASTOptions.silent = !debug
    TypeChecker.silent = !debug
    TypeChecker.debug = debug

    val currentTestJsonObject = new JSONObject()

    try {
      val model = getModelFromFile(file.toString)

      if (model != null) new TypeChecker(model).smtCheck

      val (json1, json2) =
        if (model != null) {
          ASTOptions.useJson1 = true
          val json1 = model.toJson
          ASTOptions.useJson1 = false
          val json2 = model.toJson
          (json1, json2)
        } else (null, null)
      val smt =
        if (model != null) model.toSMT
        else null

      // Use shared auto-detection solving logic (same as batch mode)
      var outcome = "UNKNOWN"
      if (smt != null) {
        val res = runWithTimeout(timeoutValue) {
          val (resultOutcome, z3ModelOpt) = solveWithAutoDetection(
            model, smt, Map[Symbol, Any](), timeoutValue, printModel = debug, verbose = debug
          )
          outcome = resultOutcome
          z3ModelOpt.foreach(m => K2Z3.z3Model = m)
        }
        if (res.isEmpty) {
          outcome = "TIMEOUT"
        } else if (outcome == "UNSAT") {
          // Try to get partial model for UNSAT (for debugging/baselines)
          tryGetPartialModelForUnsat(model, smt)
        }
      }

      currentTestJsonObject.put("name", file.getName)
      currentTestJsonObject.put("outcome", outcome)
      currentTestJsonObject.put("model", model.toString)
      currentTestJsonObject.put("json1", json1)
      currentTestJsonObject.put("json2", json2)
      currentTestJsonObject.put("smt", smt)
      currentTestJsonObject.put("smtModel", if (K2Z3.z3Model != null) K2Z3.z3Model.toString else "")
      currentTestJsonObject.put("typeChecks", true)
    } catch {
      case TypeCheckException =>
        currentTestJsonObject.put("name", file.getName)
        currentTestJsonObject.put("outcome", "ERROR")
        currentTestJsonObject.put("model", "")
        currentTestJsonObject.put("json1", "")
        currentTestJsonObject.put("json2", "")
        currentTestJsonObject.put("smt", "")
        currentTestJsonObject.put("smtModel", "")
        currentTestJsonObject.put("typeChecks", false)
    }
  }

  /**
   * Try to get a partial model for UNSAT problems.
   * Uses max-SAT approach via UnifiedSolver to find the maximum number of constraints
   * that can be satisfied together. The partial model is stored in K2Z3.z3Model.
   */
  def tryGetPartialModelForUnsat(model: Model, smtStr: String): Unit = {
    try {
      // Write SMT to temp file
      val tempFile = new java.io.File(".tmp/k_debug.smt2")
      val writer = new java.io.PrintWriter(tempFile)
      writer.write(smtStr)
      writer.close()

      // Parse to get constraints
      val boolExps = K2Z3.ctx.parseSMTLIB2File(
        tempFile.getAbsolutePath, Array(), Array(), Array(), Array())
      val boolExpsList = boolExps.map(_.asInstanceOf[com.microsoft.z3.BoolExpr]).toList

      // Use max-SAT with soft constraints to find partial model
      val optimize = K2Z3.ctx.mkOptimize()
      val optParams = K2Z3.ctx.mkParams()
      optParams.add("timeout", 5000)  // 5 second timeout for partial model
      optimize.setParameters(optParams)

      // Filter out quantified constraints (Optimize doesn't support them)
      def containsQuantifier(expr: com.microsoft.z3.Expr[_]): Boolean = {
        if (expr.isQuantifier) return true
        for (i <- 0 until expr.getNumArgs) {
          if (containsQuantifier(expr.getArgs()(i))) return true
        }
        false
      }
      val nonQuantifiedExps = boolExpsList.filterNot(containsQuantifier)

      if (nonQuantifiedExps.isEmpty) return  // All constraints are quantified

      // Add non-quantified constraints as SOFT constraints
      for (expr <- nonQuantifiedExps) {
        optimize.AssertSoft(expr, 1, "partial")
      }

      val status = optimize.Check()
      if (status == com.microsoft.z3.Status.SATISFIABLE) {
        val partialModel = optimize.getModel
        if (partialModel != null) {
          K2Z3.z3Model = partialModel
        }
      }
    } catch {
      case _: Exception => // Ignore errors in partial model extraction
    }
  }

  def runWithTimeout[T](timeoutMs: Long)(f: => T): Option[T] = {
    //awaitAll(timeoutMs, future(f)).head.asInstanceOf[Option[T]]
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent._
    //import Await._
    //import scala.concurrent.Future
    import scala.concurrent.Await
    import scala.concurrent.duration._
    //import scala.concurrent.Awaitable
    import scala.concurrent.{Future, ExecutionContext}
    import ExecutionContext.Implicits.global
    Some(Await.result(Future(f), Duration.create(timeoutMs, "ms")))
  }

  /**
   * Load a per-file baseline from the baseline/ subdirectory.
   * Returns None if the baseline file doesn't exist.
   */
  def loadPerFileBaseline(kFile: File): Option[JSONObject] = {
    val baselineDir = new File(kFile.getParentFile, "baseline")
    val baselineFile = new File(baselineDir, kFile.getName + ".json")
    if (baselineFile.exists) {
      try {
        val json = scala.io.Source.fromFile(baselineFile).mkString
        val tokener = new JSONTokener(json)
        Some(new JSONObject(tokener))
      } catch {
        case e: Exception =>
          log(s"Warning: Could not load baseline for ${kFile.getName}: ${e.getMessage}")
          None
      }
    } else {
      None
    }
  }
  
  /**
   * Save a per-file baseline to the baseline/ subdirectory.
   */
  def savePerFileBaseline(kFile: File, baseline: JSONObject): Unit = {
    val baselineDir = new File(kFile.getParentFile, "baseline")
    if (!baselineDir.exists) baselineDir.mkdirs()
    val baselineFile = new File(baselineDir, kFile.getName + ".json")
    val fw = new FileWriter(baselineFile)
    fw.write(baseline.toString(2))
    fw.close()
    log(s"Baseline saved: ${baselineFile.getPath}")
  }
  
  /**
   * Get expected result from @expected annotation in K file.
   * Returns one of: "SAT", "UNSAT", "ERROR", "TIMEOUT", or None if not specified.
   */
  def getExpectedFromFile(kFile: File): Option[String] = {
    parseExpectedResult(kFile.getPath)
  }
  
  /**
   * Determine outcome category from a baseline/test result.
   * Returns: "SAT", "UNSAT", "ERROR", or "UNKNOWN"
   */
  def getOutcomeFromResult(result: JSONObject): String = {
    val typeChecks = result.optBoolean("typeChecks", false)
    val smtModel = result.optString("smtModel", "")
    
    if (!typeChecks) "ERROR"
    else if (smtModel == "()" || smtModel == "()\\n") "UNSAT"
    else if (smtModel != null && smtModel.nonEmpty) "SAT"
    else "UNKNOWN"
  }
  
  /**
   * Check consistency between @expected annotation and baseline.
   * Returns (isConsistent, message)
   */
  def checkExpectedConsistency(kFile: File, baseline: JSONObject): (Boolean, String) = {
    val expected = getExpectedFromFile(kFile)
    val baselineOutcome = getOutcomeFromResult(baseline)
    
    expected match {
      case Some(exp) if exp != baselineOutcome =>
        (false, s"@expected=$exp but baseline=$baselineOutcome")
      case Some(exp) =>
        (true, s"@expected=$exp matches baseline")
      case None =>
        (true, "no @expected annotation")
    }
  }

  def doTests(saveBaseline: Boolean): Unit = {
    // Extended header with @expected consistency column
    var resultRows: List[List[String]] = List(List("Name", "TypeChecks", "Model", "JSON1", "JSON2", "SMT", "SMTModel", "@expected"))
    val testsDir = new File(new File(new File(".").getAbsolutePath, "src"), "tests")
    var kFiles = getFileTree(testsDir).filter(_.getName.endsWith(".k"))
    
    var testsRun: Int = 0
    var testsMatched: Int = 0
    var expectedMismatches: Int = 0

    kFiles.foreach { file =>
      try {
        testsRun = testsRun + 1
        val currentTestJsonObject = doTest(file, false)

        // Load per-file baseline
        val baselineOpt = loadPerFileBaseline(file)
        
        // Check @expected consistency
        val (expectedConsistent, expectedMsg) = baselineOpt match {
          case Some(baseline) => checkExpectedConsistency(file, baseline)
          case None => 
            val expected = getExpectedFromFile(file)
            val currentOutcome = getOutcomeFromResult(currentTestJsonObject)
            expected match {
              case Some(exp) if exp != currentOutcome => 
                expectedMismatches += 1
                (false, s"@expected=$exp but got $currentOutcome")
              case Some(exp) => (true, s"@expected=$exp ✓")
              case None => (true, "-")
            }
        }
        
        baselineOpt match {
          case Some(baseline) =>
            val result = compareResult(baseline, currentTestJsonObject)
            val expectedCol = if (expectedConsistent) expectedMsg else s"⚠ $expectedMsg"
            resultRows = (result._2 :+ expectedCol) :: resultRows
            if (result._1 && expectedConsistent) testsMatched = testsMatched + 1
            if (!expectedConsistent) expectedMismatches += 1
          case None =>
            val expectedCol = if (expectedConsistent) expectedMsg else s"⚠ $expectedMsg"
            resultRows = List(file.getName + "*", "New", "test", "case", "", "", "", expectedCol) :: resultRows
        }
        
        // Save baseline if requested
        if (saveBaseline) {
          savePerFileBaseline(file, currentTestJsonObject)
        }

      } catch {
        case K2SMTException => resultRows = List(file.getName + "*", "K2SMT", "error", "", "", "", "", "-") :: resultRows
        case K2Z3Exception => resultRows = List(file.getName + "*", "K2Z3", "error", "", "", "", "", "-") :: resultRows
        case e: Throwable =>
          log("Exception: " + e.toString)
          resultRows = List(file.getName + "*", "-", "-", "-", "-", "-", "-", "-") :: resultRows
      }
    }
    
    println
    log("Results:")
    println
    println(Tabulator.format(resultRows.reverse))
    println
    println(s"\t$testsMatched/$testsRun tests matched baseline")
    if (expectedMismatches > 0) {
      println(s"\t⚠ $expectedMismatches @expected annotation mismatches")
    }
    if (saveBaseline) {
      println(s"\tBaselines saved to src/tests/baseline/")
    }
  }

  def compareSingleResultDetail(bo: JSONObject, co: JSONObject, testDir: File): Unit = {
    var resultRows: List[List[String]] = List(List("Name", "TypeChecksEq (TypeChecks)", "ModelEqual", "JSON1Equal", "JSON2Equal", "SMTEqual", "SMTModelEqual"))
    log()
    println(Tabulator.format((compareResult(bo, co)._2 :: resultRows).reverse))
    log()
    var fw = new FileWriter(new File(testDir, "baseline.smt"))
    fw.write(bo.getString("smt"))
    fw.close
    log(s"Baseline SMT stored in ${testDir.getAbsolutePath}/baseline.smt")
    fw = new FileWriter(new File(testDir, "current.smt"))
    fw.write(co.getString("smt"))
    fw.close
    log(s"Current SMT stored in ${testDir.getAbsolutePath}/current.smt")

    fw = new FileWriter(new File(testDir, "baseline.smt.model"))
    fw.write(bo.getString("smtModel"))
    fw.close
    log(s"Baseline SMT model stored in ${testDir.getAbsolutePath}/baseline.smt.model")
    if (co.has("smtModel")) {
      fw = new FileWriter(new File(testDir, "current.smt.model"))
      fw.write(co.get("smtModel").toString)
      fw.close
      log(s"Current SMT model stored in ${testDir.getAbsolutePath}/current.smt.model")
    }

  }

  /**
    * Determine if two JSONObjects are similar.
    * They must contain the same set of names which must be associated with
    * similar values.
    *
    * @param other The other JSONObject
    * @return true if they are equal
    */
  def similar(dis: JSONObject, other: Any): Boolean = try {
    if (!other.isInstanceOf[JSONObject]) return false
    val set = dis.keySet
    if (!set.equals((other.asInstanceOf[JSONObject]).keySet)) return false
    val iterator = set.iterator
    while ( {
      iterator.hasNext
    }) {
      val name = iterator.next.asInstanceOf[String]
      val valueThis = dis.get(name)
      val valueOther = other.asInstanceOf[JSONObject].get(name)
      if (!similar(valueThis, valueOther) ) return false
    }
    true
  } catch {
    case exception: Throwable =>
      false
  }

  def similar( valueThis: Any, valueOther: Any ): Boolean = try {
    if ( valueThis == null ) {
      if ( valueOther == null ) return true
      return false
    }
    if ( valueOther == null ) return false
    if (valueThis.isInstanceOf[JSONObject]) if (!similar(valueThis.asInstanceOf[JSONObject], valueOther)) return false
    else if (valueThis.isInstanceOf[JSONArray]) if (!similar(valueThis.asInstanceOf[JSONArray], valueOther)) return false
    else if (valueThis.isInstanceOf[AnyRef] && !(valueThis.asInstanceOf[AnyRef].equals(valueOther))) return false
    else if (!(valueThis == valueOther)) return false
    true
  } catch {
    case exception: Throwable =>
    false
  }

  def similar(dis: JSONArray, other: Any): Boolean = try {
    if (!other.isInstanceOf[JSONArray]) return false
    val otherArr = other.asInstanceOf[JSONArray]
    if ( dis.length() != otherArr.length() ) return false
    for (i <- 0 until dis.length()) {
      if ( !similar(dis.get(i), otherArr.get(i)) ) return false
    }
    return true
  } catch {
    case exception: Throwable =>
      false
  }

  def compareSingleResult(key: String, bo: JSONObject, co: JSONObject): String = {
    if (bo.has(key) && co.has(key) && co.get(key).toString != "") {
      if (bo.get(key).isInstanceOf[JSONObject] && co.get(key).isInstanceOf[JSONObject])
        similar(bo.getJSONObject(key), co.getJSONObject(key)).toString
      else bo.get(key).toString.equals(co.get(key).toString).toString
    } else if (bo.has(key) && !co.has(key))
      "false"
    else if (co.has(key) && !bo.has(key))
      "???"
    else "-"
  }

  // Helper to check if a comparison result indicates a mismatch
  // "true" = match, "-" = both empty (ok), "false" = mismatch, "???" = unexpected value
  def isMismatch(result: String): Boolean = result == "false" || result == "???"

  def compareResult(bo: JSONObject, co: JSONObject): (Boolean, List[String]) = {
    val typeChecksEq = compareSingleResult("typeChecks", bo, co)
    val modelEq = compareSingleResult("model", bo, co)
    val json1Eq = compareSingleResult("json1", bo, co)
    val json2Eq = compareSingleResult("json2", bo, co)
    val smtEq = compareSingleResult("smt", bo, co)
    val smtModelEq = compareSingleResult("smtModel", bo, co)
    val typeCheckString =
      if (typeChecksEq == "true") s"$typeChecksEq (${co.get("typeChecks")})"
      else s"$typeChecksEq"
    // A field is OK if it's "true" (matches) or "-" (both empty/missing)
    // A field is a mismatch if it's "false" (different values) or "???" (unexpected)
    if (isMismatch(typeChecksEq) || isMismatch(modelEq) || isMismatch(json1Eq) ||
      isMismatch(json2Eq) || isMismatch(smtEq) || isMismatch(smtModelEq))
      (false, List(bo.getString("name") + "*", s"$typeCheckString", s"$modelEq", s"$json1Eq",
        s"$json2Eq", s"$smtEq", s"$smtModelEq"))
    else
      (true, List(bo.getString("name"), s"$typeCheckString", s"$modelEq", s"$json1Eq",
        s"$json2Eq", s"$smtEq", s"$smtModelEq"))
  }

  def parseMMSJson(file: String): Model = {
    return parseMMSJsonFromFile( file )
  }
  
  def parseMMSJsonFromFile(file: String): Model = {
    val json = scala.io.Source.fromFile(file).mkString
    return parseMMSJsonFromString(json)
  }
  def parseMMSJsonFromString(json: String): Model = {
    //val json = "" + jsonString
    //println("Frontend.parseMMSJsonFromString(" + json + ")")
    var tokener: JSONTokener = new JSONTokener(json)
    var jsonObject: JSONObject = new JSONObject(tokener)
    //println("jsonObject = " + jsonObject)
    val elementsArray = jsonObject.get("elements").asInstanceOf[JSONArray]
    //println("elementsArray = " + elementsArray)
    var packageName: Option[PackageDecl] = None
    var packages: MList[PackageDecl] = MList[PackageDecl]()
    var imports: List[ImportDecl] = List()
    var annotations: Set[AnnotationDecl] = Set()
    var mdecls: List[TopDecl] = List[TopDecl]()
    var id2Decl: Map[String, TopDecl] = Map()

    // first build the classes 
    for (i <- Range(0, elementsArray.length())) {
      try {
        val obj = elementsArray.get(i).asInstanceOf[JSONObject]
        if (obj.keySet.contains("specialization")) {
          val name = if (obj.optString("name") != null && obj.optString("name").length > 0) obj.optString("name") else obj.optString("sysmlid"); 
//          if (obj.getString("name").length == 0) {
//            //println("Warning: found unnamed element in JSON: " + obj.getString("sysmlid"))
//          } else {
            val specializationObject = obj.getJSONObject("specialization")
            //specializationObject.getString("type")  match {
            //  case "Element" =>
                val entity = EntityDecl(Nil, ClassToken, None, name.replace(" ", "_"), null, Nil, Nil, Nil)
                mdecls = entity :: mdecls
                id2Decl += (obj.getString("sysmlid") -> entity)
            //  case _ => ()
            //}
//          }
        }
      } catch {
        case _: Throwable => ()
      }

    }

    // now we can process properties and constraints
    for (i <- Range(0, elementsArray.length())) {
      val obj = elementsArray.get(i).asInstanceOf[JSONObject]
      try {
        if (obj.keySet.contains("specialization")) {
          val specializationObject = obj.getJSONObject("specialization")
          if (obj.getString("name").length == 0 &&
            specializationObject.getString("type") != "Generalization") {
            //println("Warning: found unnamed element in JSON: " + obj.getString("sysmlid"))
          } else {
            specializationObject.getString("type") match {
              case "Property" =>
                val owningDecl = id2Decl(obj.getString("owner")).asInstanceOf[EntityDecl]
                val propertyType =
                  if (specializationObject.get("propertyType") == JSONObject.NULL) IntType
                  else {
                    val typeDecl = id2Decl(specializationObject.getString("propertyType")).asInstanceOf[EntityDecl]
                    IdentType(QualifiedName(List(typeDecl.ident)), List())
                  }
                val property = PropertyDecl(Nil, obj.getString("name").replace(" ", "_"), Some(propertyType), None, None, None)
                val newDecl = EntityDecl(owningDecl.annotations, owningDecl.entityToken, owningDecl.keyword, owningDecl.ident, null, owningDecl.typeParams, owningDecl.extending, property :: owningDecl.members)
                mdecls = mdecls.diff(List(owningDecl))
                mdecls = newDecl :: mdecls

                id2Decl += (obj.getString("owner") -> newDecl)

                // TODO -- This is broken -- need to wire model to package
              case "Package" =>
                var pkg =
                PackageDecl(QualifiedName(obj.getString("qualifiedName").replace("-", "_").replace(" ", "_").split("/").toList.filterNot { _.isEmpty }), null)
                packages += pkg
              case "Constraint" =>
                if (specializationObject.getJSONObject("specification").has("expressionBody")) {
                  val constraintExpressionBody = specializationObject.getJSONObject("specification").getJSONArray("expressionBody")

                  val constraintExpression = if (constraintExpressionBody.length() > 0) { constraintExpressionBody.get(0).asInstanceOf[String] } else BooleanLiteral(true).toString
                  val (ksv: KScalaVisitor, tree: ModelContext) = getVisitor(constraintExpression)
                  var m: Model = ksv.visit(tree).asInstanceOf[Model]
                  var exp: Exp = m.decls(0).asInstanceOf[ExpressionDecl].exp
                  val owningDecl = id2Decl(obj.getString("owner")).asInstanceOf[EntityDecl]
                  val constraint = ConstraintDecl(Some(obj.getString("name").replace(" ", "_")), exp)
                  val newDecl = EntityDecl(owningDecl.annotations, owningDecl.entityToken, owningDecl.keyword, owningDecl.ident, null, owningDecl.typeParams, owningDecl.extending, constraint :: owningDecl.members)
                  mdecls = mdecls.diff(List(owningDecl))
                  mdecls = newDecl :: mdecls
                  id2Decl += (obj.getString("owner") -> newDecl)
                } else {
                  log("Constraint is missing expressionBody in specification..." + specializationObject)
                }
              case "Generalization" =>
                val owningDecl = id2Decl(specializationObject.getString("source")).asInstanceOf[EntityDecl]
                val targetDecl = id2Decl(specializationObject.getString("target")).asInstanceOf[EntityDecl]
                val newDecl = EntityDecl(owningDecl.annotations, owningDecl.entityToken, owningDecl.keyword, owningDecl.ident, null, owningDecl.typeParams, IdentType(QualifiedName(List(targetDecl.ident)), List()) :: owningDecl.extending, owningDecl.members)
                mdecls = mdecls.diff(List(owningDecl))
                mdecls = newDecl :: mdecls
                id2Decl += (obj.getString("owner") -> newDecl)

              case _ => ()
            }
          }
        }
      } catch {
        //case e if e.isInstanceOf[java.util.NoSuchElementException] => log("Skipping element..." + obj)
        case _: Throwable => log("Skipping element..." + obj)
      }
    }

    val model = Model(null, packages.toList, imports, annotations, mdecls)
    println(model)
    model
  }

  def printClassDOT(filename: String, model: Model) = {
    val classFile = new FileWriter(filename + ".dot", false)
    classFile.append("digraph G { node [shape=record,fontname=Courier,fontsize=10,color=\".7 .3 1.0\"];")
    model.decls.foreach { d =>
      if (d.isInstanceOf[EntityDecl]) {
        val ed = d.asInstanceOf[EntityDecl]
        val properties =
          ed.members.filter { m => m.isInstanceOf[PropertyDecl] }
            .map(m => m.asInstanceOf[PropertyDecl].name).asInstanceOf[List[String]].mkString("|")
        val functions =
          ed.members.filter { m => m.isInstanceOf[FunDecl] }
            .map(m => m.asInstanceOf[FunDecl].ident).asInstanceOf[List[String]].mkString("|")
        val label = s"${ed.ident} | {Properties | $properties} | {Functions | $functions}"
        classFile.append(ed.ident + " [shape=record,label=\"" + label + "\"];\r\n")
        ed.members.foreach { m =>
          if (m.isInstanceOf[PropertyDecl]) {
            classFile.append(s"${ed.ident} -> ${
              m.asInstanceOf[PropertyDecl].ty.toString
                .replace("[", "")
                .replace("]", "")
                .replace("Set", "")
            };")
          }
        }
      }
    }
    classFile.append("}")
    classFile.close()
  }

  def visitJsonObject(o: Any): AnyRef = {
    val obj = o.asInstanceOf[JSONObject]
    obj.getString("type") match {
      // Expressions:
      case "ParenExp" =>
        ParenExp(visitJsonObject(obj.get("exp")).asInstanceOf[Exp])
      case "IdentExp" =>
        IdentExp(obj.get("ident").asInstanceOf[String])
      case "DotExp" =>
        val exp: Exp = visitJsonObject(obj.get("exp")).asInstanceOf[Exp]
        val ident: String = obj.get("ident").asInstanceOf[String]
        DotExp(exp, ident)
      case "IfExp" =>
        val cond: Exp = visitJsonObject(obj.get("cond")).asInstanceOf[Exp]
        val trueBranch: Exp = visitJsonObject(obj.get("trueBranch")).asInstanceOf[Exp]
        val falseBranch: Option[Exp] =
          if (obj.keySet().contains("falseBranch")) Some(visitJsonObject(obj.get("falseBranch")).asInstanceOf[Exp])
          else None
        IfExp(cond, trueBranch, falseBranch)
      case "WhileExp" =>
        val cond: Exp = visitJsonObject(obj.get("condition")).asInstanceOf[Exp]
        val body = visitJsonObject(obj.get("body")).asInstanceOf[Exp]
        WhileExp(cond, body)
      case "ForExp" =>
        val pattern: Pattern = visitJsonObject(obj.get("pattern")).asInstanceOf[Pattern]
        val exp: Exp = visitJsonObject(obj.get("exp")).asInstanceOf[Exp]
        val body = visitJsonObject(obj.get("body")).asInstanceOf[Exp]
        ForExp(pattern, exp, body)
      case "FunApplExp" =>
        val exp1: Exp = visitJsonObject(obj.get("exp1")).asInstanceOf[Exp]
        val args: List[Argument] = visitJsonArray(obj.get("args"), visitJsonObject).asInstanceOf[List[Argument]]
        FunApplExp(exp1, args)
      case "BinExp" =>
        val operator: BinaryOp =
          obj.get("op") match {
            case "Plus"    => ADD
            case "Minus"   => SUB
            case "Times"   => MUL
            case "Divide"  => DIV
            case "Modulo"  => REM
            case "LTE"     => LTE
            case "GTE"     => GTE
            case "LT"      => LT
            case "GT"      => GT
            case "EQ"      => EQ
            case "NotEQ"   => NEQ
            case "IsIn"    => ISIN
            case "NotIn"   => NOTISIN
            case "Subset"  => SUBSET
            case "Psubset" => PSUBSET
            case "Union"   => SETUNION
            case "Inter"   => SETINTER
            case "And"     => AND
            case "OR"      => OR
            case "Tuples"  => TUPLEINDEX
            case "Concat"  => LISTCONCAT
            case "Implies" => IMPL
            case "Assign"  => ASSIGN
            case x =>
              println(x + " not recognized as a BinOp.")
              null
          }
        BinExp(visitJsonObject(obj.get("exp1")).asInstanceOf[Exp],
          operator,
          visitJsonObject(obj.get("exp2")).asInstanceOf[Exp])
      case "UnaryExp" =>
        val operator: UnaryOp =
          obj.get("op") match {
            case "Neg"  => NEG
            case "Not"  => NOT
            case "Prev" => PREV
          }
        UnaryExp(operator, visitJsonObject(obj.get("exp")).asInstanceOf[Exp])
      case "QuantifiedExp" =>
        val quant = visitJsonObject(obj.get("quant")).asInstanceOf[Quantifier]
        val bindings = visitJsonArray(obj.get("bindings"), visitJsonObject).asInstanceOf[List[RngBinding]]
        val exp = visitJsonObject(obj.get("exp")).asInstanceOf[Exp]
        QuantifiedExp(quant, bindings, exp)
      case "TupleExp" =>
        TupleExp(visitJsonArray(obj.get("exps"), visitJsonObject).asInstanceOf[List[Exp]])
      case "CollectionEnumExp" =>
        CollectionEnumExp(Misc.getCollectionKind(obj.getString("kind")),
          visitJsonArray(obj.get("exps"), visitJsonObject).asInstanceOf[List[Exp]])
      case "CollectionRangeExp" =>
        CollectionRangeExp(Misc.getCollectionKind(obj.getString("kind")),
          visitJsonObject(obj.get("exp1")).asInstanceOf[Exp],
          visitJsonObject(obj.get("exp2")).asInstanceOf[Exp])
      case "CollectionComprExp" =>
        var kind = Misc.getCollectionKind(obj.getString("kind"))
        var exp1 = visitJsonObject(obj.get("exp1")).asInstanceOf[Exp]
        var bindings = visitJsonArray(obj.get("bindings"), visitJsonObject).asInstanceOf[List[RngBinding]]
        var exp2 = visitJsonObject(obj.get("exp2")).asInstanceOf[Exp]
        CollectionComprExp(kind, exp1, bindings, exp2)
      case "LambdaExp" =>
        var pat = visitJsonObject(obj.get("pat")).asInstanceOf[Pattern]
        var exp = visitJsonObject(obj.get("exp")).asInstanceOf[Exp]
        LambdaExp(pat, exp)
      case "AssertExp" =>
        AssertExp(visitJsonObject(obj.get("exp")).asInstanceOf[Exp])
      case "StarExp" =>
        StarExp
      case "ResultExp" =>
        ResultExp
      case "QualifiedName" =>
        QualifiedName(visitJsonArray(obj.get("names").asInstanceOf[JSONArray], (x => x.asInstanceOf[String])).asInstanceOf[List[String]])
      case "IdentType" =>
        val ident = visitJsonObject(obj.get("ident")).asInstanceOf[QualifiedName]
        val args = visitJsonArray(obj.get("args").asInstanceOf[JSONArray], visitJsonObject).asInstanceOf[List[Type]]
        IdentType(ident, args)
      case "PositionalArgument" =>
        PositionalArgument(visitJsonObject(obj.get("exp")).asInstanceOf[Exp])
      case "NamedArgument" =>
        val ident: String = obj.getString("ident")
        val exp: Exp = visitJsonObject(obj.get("exp")).asInstanceOf[Exp]
        NamedArgument(ident, exp)
      case "CartesianType" =>
        CartesianType(visitJsonArray(obj.get("types"), visitJsonObject).asInstanceOf[List[Type]])
      case "IdentPattern" =>
        IdentPattern(obj.get("ident").asInstanceOf[String])
      case "ProductPattern" =>
        ProductPattern(visitJsonArray(obj.get("patterns"), visitJsonObject).asInstanceOf[List[Pattern]])
      case "RngBinding" =>
        val patterns = visitJsonArray(obj.get("patterns"), visitJsonObject).asInstanceOf[List[Pattern]]
        val collection = visitJsonObject(obj.get("collection")).asInstanceOf[Collection]
        RngBinding(patterns, collection)
      case "ExpCollection" =>
        ExpCollection(visitJsonObject(obj.get("exp")).asInstanceOf[Exp])
      case "TypeCollection" =>
        TypeCollection(visitJsonObject(obj.get("ty")).asInstanceOf[Type])
      case "LiteralInteger" =>
        IntegerLiteral(obj.getLong("i"))
      case "LiteralFloatingPoint" =>
        //RealLiteral(java.lang.Float.parseFloat(obj.get("f").toString)) // was: asInstanceOf[String]
        val bd = new java.math.BigDecimal(obj.get("f").toString).setScale(8, java.math.BigDecimal.ROUND_DOWN)
        RealLiteral(bd)
      case "LiteralCharacter" =>
        CharacterLiteral(obj.get("c").asInstanceOf[Char])
      case "LiteralBoolean" =>
        BooleanLiteral(obj.getBoolean("b"))
      // TODO
      // case "LiteralDate" =>
      //   DateLiteral(obj.getDate("t"))
      // TODO
      // case "LiteralDuration" =>
      //   DateLiteral(obj.getDuration("d"))
      case "LiteralBoolean" =>
        BooleanLiteral(obj.getBoolean("b"))
      case "StringLiteral" =>
        StringLiteral(obj.getString("string"))
      case "ElementValue" =>
        IdentExp(obj.get("element").asInstanceOf[String])
      // Top level:
      case "Model" =>
        var packageName: Option[String] =
          if (obj.keySet().contains("packageName")) Some(obj.get("packageName")).asInstanceOf[Option[String]]
          else None
        var packages = visitJsonArray(obj.get("packages"), visitJsonObject).asInstanceOf[List[PackageDecl]]
        var annotations = visitJsonArray(obj.get("annotations"), visitJsonObject).asInstanceOf[Set[AnnotationDecl]]
        var imports = visitJsonArray(obj.get("imports"), visitJsonObject).asInstanceOf[List[ImportDecl]]
        var decls = visitJsonArray(obj.get("decls"), visitJsonObject).asInstanceOf[List[TopDecl]]
        Model(packageName, packages, imports, annotations, decls)
      case "AnnotationDecl" =>
        AnnotationDecl(obj.getString("name"), visitJsonObject(obj.get("ty")).asInstanceOf[Type])
      case "Annotation" =>
        Annotation(obj.getString("name"), visitJsonObject(obj.get("exp")).asInstanceOf[Exp])
      case "PackageDecl" =>
        var name: QualifiedName = visitJsonObject(obj.get("name")).asInstanceOf[QualifiedName]
        var model: Model = visitJsonObject(obj.get("model")).asInstanceOf[Model]
        PackageDecl(name, model)
      case "ImportDecl" =>
        var name: QualifiedName = visitJsonObject(obj.get("name")).asInstanceOf[QualifiedName]
        var star: Boolean =
          if (obj.get("star").equals("true")) true
          else false
        ImportDecl(name, star)
      case "EntityDecl" =>
        var annotations = visitJsonArray(obj.get("annotations"), visitJsonObject).asInstanceOf[List[Annotation]]
        var entityToken =
          if (obj.get("entityToken").equals("class")) ClassToken
          else if (obj.get("entityToken").equals("assoc")) AssocToken
          else IdentifierToken(obj.getString("entityToken"))
        var keyword =
          if (obj.keySet().contains("keyword")) Some(obj.getString("keyword"))
          else None
        var ident: String = obj.get("ident").toString()
        var typeParams: List[TypeParam] =
          if (obj.keySet.contains("typeParams"))
            visitJsonArray(obj.get("typeParams"), visitJsonObject).asInstanceOf[List[TypeParam]]
          else Nil
        var extending: List[Type] =
          visitJsonArray(obj.get("extending"), visitJsonObject).asInstanceOf[List[Type]]
        var members: List[MemberDecl] =
          visitJsonArray(obj.get("members"), visitJsonObject).asInstanceOf[List[MemberDecl]]
        EntityDecl(annotations, entityToken, keyword, ident, null, typeParams, extending, members)
      case "TypeParam" =>
        var ident: String = obj.get("ident").toString()
        var bound: Option[TypeBound] =
          if (obj.keySet().contains("bound")) Some(visitJsonObject(obj.get("bound")).asInstanceOf[TypeBound])
          else None
        TypeParam(ident, bound)
      case "TypeBound" => // should this be here?
        visitJsonArray(obj.get("types"), visitJsonObject).asInstanceOf[List[Type]]
      case "TypeDecl" =>
        var ident: String = obj.get("ident").toString()
        var typeParams: List[TypeParam] = visitJsonArray(obj.get("typeParam"), visitJsonObject).asInstanceOf[List[TypeParam]]
        var ty =
          if (obj.keySet().contains("ty")) Some(visitJsonObject(obj.get("ty")).asInstanceOf[Type])
          else None
        TypeDecl(ident, typeParams, ty)
      case "PropertyDecl" =>
        var modifiers =
          visitJsonArray(obj.get("modifiers"), getModifier).asInstanceOf[List[PropertyModifier]]
        var name = obj.getString("name")
        var ty: Option[Type] = 
          if (obj.keySet.contains("ty")) Some(visitJsonObject(obj.get("ty")).asInstanceOf[Type])
          else None
        var multiplicity =
          if (obj.keySet.contains("multiplicity")) Some(visitJsonObject(obj.get("multiplicity")).asInstanceOf[Multiplicity])
          else None
        var assignment =
          if (obj.keySet.contains("assignment")) Some(obj.getBoolean("assignment"))
          else None
        var expr: Option[Exp] =
          if (obj.keySet().contains("expr")) Some(visitJsonObject(obj.get("expr")).asInstanceOf[Exp])
          else None
        PropertyDecl(modifiers, name, ty, multiplicity, assignment, expr)
      case "FunDecl" =>
        var ident: String = obj.get("ident").toString()
        var typeParams = visitJsonArray(obj.get("typeParams"), visitJsonObject).asInstanceOf[List[TypeParam]]
        var params = visitJsonArray(obj.get("params"), visitJsonObject).asInstanceOf[List[Param]]
        var ty =
          if (obj.keySet().contains("ty")) Some(visitJsonObject(obj.get("ty")).asInstanceOf[Type])
          else None
        var spec = visitJsonArray(obj.get("spec"), visitJsonObject).asInstanceOf[List[FunSpec]]
        var body: List[MemberDecl] = visitJsonArray(obj.get("body"), visitJsonObject).asInstanceOf[List[MemberDecl]]
        FunDecl(ident, typeParams, params, ty, spec, body)
      case "FunSpec" =>
        FunSpec(obj.getBoolean("pre"), visitJsonObject(obj.get("exp")).asInstanceOf[Exp])
      case "ConstraintDecl" =>
        var name: Option[String] =
          if (obj.keySet().contains("name")) Some(obj.get("name").toString())
          else None
        var exp = visitJsonObject(obj.get("exp")).asInstanceOf[Exp]
        ConstraintDecl(name, exp)
      case "ExpressionDecl" =>
        var exp = visitJsonObject(obj.get("exp")).asInstanceOf[Exp]
        ExpressionDecl(exp)
      case "Multiplicity" =>
        var exp1 = visitJsonObject(obj.get("exp1")).asInstanceOf[Exp]
        val exp2: Option[Exp] =
          if (obj.keySet().contains("falseBranch")) Some(visitJsonObject(obj.get("exp2")).asInstanceOf[Exp])
          else None
        Multiplicity(exp1, exp2)
      case "Param" =>
        Param(obj.getString("name"), visitJsonObject(obj.get("ty")).asInstanceOf[Type])
      case "Quantifier" =>
        obj.getString("element") match {
          case "Forall" => Forall
          case "Exists" => Exists
        }
      case "TypeCastCheckExp" =>
        TypeCastCheckExp(obj.getBoolean("cast"), visitJsonObject(obj.get("exp")).asInstanceOf[Exp], visitJsonObject(obj.get("ty")).asInstanceOf[Type])
      case "BlockExp" =>
        BlockExp(visitJsonArray(obj.getJSONArray("body"), visitJsonObject).asInstanceOf[List[MemberDecl]])
      case "BoolType"   => BoolType
      case "IntType"    => IntType
      case "RealType"   => RealType
      case "StringType" => StringType
      case "UnitType"   => UnitType
      case "CharType"   => CharType
      case "TimeType"   => TimeType
      case "DurationType"   => DurationType
      case key @ _ =>
        println("Unknown keys encountered in JSON string!: " + key).asInstanceOf[Nothing]
        //System.exit(-1).asInstanceOf[Nothing]
    }
  }

  def visitJsonArray(o: Any, f: Any => AnyRef): List[AnyRef] = {
    val obj = o.asInstanceOf[JSONArray]
    var res: List[AnyRef] = Nil
    for (i <- Range(0, obj.length())) {
      val element = obj.get(i)
      res = res ++ List(f(element))
    }
    res
  }

  def getModifier(po: Any): AnyRef = {
    po.asInstanceOf[String] match {
      case "part"    => Part
      case "var"     => Var
      case "val"     => Val
      case "source"  => Source
      case "target"  => Target
      case "unique"  => Unique
      case "ordered" => Ordered
    }
  }

  // Assuming that the input to this is an expression in JSON string format
  def json2exp(expressionString: String): String = {
    var tokener: JSONTokener = new JSONTokener(expressionString)
    var jsonObject: JSONObject = new JSONObject(tokener)
    //var element: JSONArray = jsonObject.get("elements").asInstanceOf[JSONArray]
    //var specialization: JSONObject = element.get(0).asInstanceOf[JSONObject]
    //var exp: Exp = visitJsonObject(specialization.get("specialization").asInstanceOf[JSONObject]).asInstanceOf[Exp]
    var exp = visitJsonObject(jsonObject)
    exp.toString()
  }

  def getRngBindingList(o: JSONObject): List[RngBinding] = {
    visitJsonArray(o.get("bindings").asInstanceOf[JSONArray], getRngBinding).asInstanceOf[List[RngBinding]]
  }

  def getPattern(po: Any): AnyRef = {
    val p = po.asInstanceOf[JSONObject]
    val operand = p.get("operand").asInstanceOf[JSONArray]
    operand.get(0).asInstanceOf[JSONObject].get("type") match {
      case "IdentPattern"   => IdentPattern(operand.getString(1))
      case "ProductPattern" => ProductPattern(getPatternList(p.get("operand").asInstanceOf[JSONArray].get(1).asInstanceOf[JSONArray]))
    }
  }

  def getPatternList(pl: JSONArray): List[Pattern] = {
    visitJsonArray(pl, getPattern).asInstanceOf[List[Pattern]]
  }

  def getCollection(o: JSONObject): Collection = {
    val operand = o.getJSONArray("operand")
    operand.getJSONObject(0).get("type") match {
      case "ExpCollection" =>
        ExpCollection(visitJsonObject2(operand.get(1)).asInstanceOf[Exp])
      case "TypeCollection" =>
        TypeCollection(visitJsonObject2(operand.get(1)).asInstanceOf[Type])
    }
  }

  def getRngBinding(o: Any): AnyRef = {
    val obj = o.asInstanceOf[JSONObject].getJSONArray("operand")
    val patternList: MList[Pattern] = MList()
    for (i <- Range(2, obj.length)) {
      patternList += getPattern(obj.getJSONObject(i)).asInstanceOf[Pattern]
    }
    val collection = getCollection(obj.get(1).asInstanceOf[JSONObject])
    RngBinding(patternList.toList, collection)
  }

  
  
  def visitJsonObject2(o: Any): AnyRef = {
    visitJsonObject2(o, false)
  }

  def visitJsonObject2(o: Any, pullOperandType: Boolean): AnyRef = {
    val obj = o.asInstanceOf[JSONObject]
    var spec = obj.optJSONObject("specialization")
    if ( spec != null ) {
      return visitJsonObject2(spec, pullOperandType)
    }
    // i is the index to the first argument
    val i = if (pullOperandType) 1 else 2
    val operand: JSONArray = if (!pullOperandType) null else obj.get("operand").asInstanceOf[JSONArray]
    val theType = if (!pullOperandType) obj.optString("type") 
          else {
            operand.getJSONObject(0).optString("element")
          }
    val opIsElementValue = 
      if (!pullOperandType) false 
      else {
        var kind = operand.get(0).asInstanceOf[JSONObject].get("type").asInstanceOf[String]
        kind match { 
          case "ElementValue" => true
          case _ => false
        }
      }
    theType match {
      case "Expression" =>
        visitJsonObject2(o, true)
//        val operand: JSONArray = obj.get("operand").asInstanceOf[JSONArray]
//        val kind = operand.get(0).asInstanceOf[JSONObject].get("type").asInstanceOf[String]
//        kind match {
          case "BlockExp" =>
            val memberDecls: MList[MemberDecl] = MList()
            for (j <- Range(i, operand.length())) {
              memberDecls += visitJsonObject2(operand.getJSONObject(j)).asInstanceOf[MemberDecl]
            }
            BlockExp(memberDecls.toList)
          case "ParenExp" =>
            ParenExp(visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp])
          case "DotExp" =>
            val exp = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val ident = operand.getString(i+1)
            DotExp(exp, ident)
          case "StarExp" | "Star" => StarExp
          case "FunApplExp" =>
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val args =
              if (operand.length > 2) {
                val argsList: MList[Argument] = MList()
                for (j <- Range(i+1, operand.length())) {
                  argsList += visitJsonObject2(operand.get(j)).asInstanceOf[Argument]
                }
                argsList.toList
              } else Nil
            FunApplExp(exp1, args)
          case "WhileExp" | "While" =>
            val cond: Exp = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val body = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            WhileExp(cond, body)
          case "ForExp" | "For" =>
            val pattern: Pattern = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Pattern]
            val cond: Exp = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            val body = visitJsonObject2(operand.getJSONObject(i+2)).asInstanceOf[Exp]
            ForExp(pattern, cond, body)
          case "IfExp" | "If" | "Conditional" =>
            val cond = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val trueBranch = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            val falseBranch =
              if (operand.length() == i+3)
                Some(visitJsonObject2(operand.getJSONObject(i+2)).asInstanceOf[Exp])
              else None
            IfExp(cond, trueBranch, falseBranch)
          case "QuantifiedExp" =>
            val quantifier = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Quantifier]
            val bindings = getRngBindingList(operand.getJSONObject(i+1)).asInstanceOf[List[RngBinding]]
            val exp = visitJsonObject2(operand.getJSONObject(i+2)).asInstanceOf[Exp]
            QuantifiedExp(quantifier, bindings, exp)
          case "CollectionComprExp" =>
            var kind = Misc.getCollectionKind(operand.getString(i))
            var exp1 = visitJsonObject2(operand.get(i+1)).asInstanceOf[Exp]
            var exp2 = visitJsonObject2(operand.get(i+2)).asInstanceOf[Exp]
            val bindings: MList[RngBinding] = MList()
            for (j <- Range(i+3, operand.length())) {
              bindings += visitJsonObject2(operand.get(j)).asInstanceOf[RngBinding]
            }
            CollectionComprExp(kind, exp1, bindings.toList, exp2)
          case "LambdaExp" | "Lambda" =>
            val pat = getPattern(operand.getJSONObject(i)).asInstanceOf[Pattern]
            val exp = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            LambdaExp(pat, exp)
          case "ResultExp" | "Result" => ResultExp
          case "TypeCastCheckExp" | "TypeCastCheck" =>
            TypeCastCheckExp(operand.getBoolean(i),
              visitJsonObject2(operand.get(i+1)).asInstanceOf[Exp],
              visitJsonObject2(operand.get(i+2)).asInstanceOf[Type])
          case "TupleExp" | "Tuple" =>
            val expList: MList[Exp] = MList()
            for (j <- Range(i, operand.length)) {
              expList += (visitJsonObject2(operand.get(j)).asInstanceOf[Exp])
            }
            TupleExp(expList.toList)
          case "Forall" => Forall
          case "Exists" => Exists
          case "IntegerLiteral" | "LiteralInteger" =>
            var jo : JSONObject = obj
            if ( operand != null ) jo = operand.get(i).asInstanceOf[JSONObject]
            IntegerLiteral(jo.getLong("integer"))
          case "RealLiteral" | "LiteralReal" => // was FloatingPointLiteral
            //RealLiteral(java.lang.Float.parseFloat(operand.get(1).toString)) // was: operand.getString(1)
            val bd = new java.math.BigDecimal(obj.get("double").toString).setScale(8, java.math.BigDecimal.ROUND_DOWN)
            //println(bd.formatted("%f"))
            RealLiteral(bd)
          case "CharacterLiteral" | "LiteralCharacter" =>
            CharacterLiteral(obj.get("char").asInstanceOf[Char])
          case "BooleanLiteral" | "LiteralBoolean" =>
            BooleanLiteral(obj.getBoolean("boolean"))
          case "StringLiteral" | "LiteralString" =>
            StringLiteral(obj.getString("string"))
          // TODO
          //case "DateLiteral" | "LiteralDate" =>
          //  DateLiteral(obj.getDate("date"))
          //case "DurationLiteral" | "LiteralDuration" =>
          //  DurationLiteral(obj.getDuration("duration"))
          case "BoolType"   => BoolType
          case "IntType"    => IntType
          case "RealType"   => RealType
          case "StringType" => StringType
          case "UnitType"   => UnitType
          case "CharType"   => CharType
          case "TimeType"   => TimeType
          case "DurationType"   => DurationType
          case "IdentType" =>
            IdentType(visitJsonObject2(operand.get(i)).asInstanceOf[QualifiedName],
              null) // missing type parameters I think (null)
          case "Multiplicity" =>
            var exp1 = visitJsonObject2(operand.get(i)).asInstanceOf[Exp]
            var exp2 =
              if (operand.length() == i+1)
                Some(visitJsonObject2(operand.get(i)).asInstanceOf[Exp])
              else
                None
            Multiplicity(exp1, exp2)
          case "Plus" | "Add"    => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, ADD, exp2)
          case "Sub" | "Minus"   => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, SUB, exp2)
          case "Times" | "Multiply"  => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, MUL, exp2)
          case "Divide"  => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, DIV, exp2)
          case "Modulo"  => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, REM, exp2)
          case "LTE" | "LessEquals"    => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, LTE, exp2)
          case "GTE" | "GreaterEquals"    => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, GTE, exp2)
          case "LT" | "Less"     => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, LT, exp2)
          case "GT" | "Greater"     => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, GT, exp2)
          case "EQ" | "Equals"     => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, EQ, exp2)
          case "NotEQ" | "NEQ" | "NoteEquals" => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, NEQ, exp2)
          case "IsIn"   => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, ISIN, exp2)
          case "NotIn"   => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, NOTISIN, exp2)
          case "Subset"  => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, SUBSET, exp2)
          case "Psubset" => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, PSUBSET, exp2)
          case "Union"   => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, SETUNION, exp2)
          case "Inter"   => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, SETINTER, exp2)
          case "And"     => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, AND, exp2)
          case "Or"      => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, OR, exp2)
          case "Tuples"  => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, TUPLEINDEX, exp2)
          case "Concat"  => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, LISTCONCAT, exp2)
          case "Assign"  => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, ASSIGN, exp2)
          case "Implies" => 
            val exp1 = visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            BinExp(exp1, IMPL, exp2)
          case "BinExp" =>
            val operator: BinaryOp =
              operand.getString(i) match {
                case "Plus"    => ADD
                case "Minus"   => SUB
                case "Times"   => MUL
                case "Divide"  => DIV
                case "Modulo"  => REM
                case "LTE"     => LTE
                case "GTE"     => GTE
                case "LT"      => LT
                case "GT"      => GT
                case "EQ"      => EQ
                case "NotEQ"   => NEQ
                case "IsIn"    => ISIN
                case "NotIn"   => NOTISIN
                case "Subset"  => SUBSET
                case "Psubset" => PSUBSET
                case "Union"   => SETUNION
                case "Inter"   => SETINTER
                case "And"     => AND
                case "Or"      => OR
                case "Tuples"  => TUPLEINDEX
                case "Concat"  => LISTCONCAT
                case "Assign"  => ASSIGN
                case "Implies" => IMPL
                case _ =>
                  println(operand.get(0).asInstanceOf[JSONObject].get("element"))
                  null
              }
            val exp1 = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            val exp2 = visitJsonObject2(operand.getJSONObject(i+2)).asInstanceOf[Exp]
            BinExp(exp1, operator, exp2)
          case "Neg" | "Negative" | "NEG" => 
            UnaryExp(NEG, visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp])
          case "Not"  => 
            UnaryExp(NOT, visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp])
          case "Prev" => 
            UnaryExp(PREV, visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp])
          case "UnaryExp" =>
            val operator: UnaryOp =
              operand.getString(i) match {
                case "Neg"  => NEG
                case "Not"  => NOT
                case "Prev" => PREV
                case op @ _ =>
                  println("unknown operator " + op).asInstanceOf[Nothing]
                  //System.exit(-1).asInstanceOf[Nothing]
              }
            UnaryExp(operator, visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp])
          case "PositionalArgument" =>
            PositionalArgument(visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp])
          case "NamedArgument" =>
            val ident = operand.getString(i)
            val exp = visitJsonObject2(operand.getJSONObject(i+1)).asInstanceOf[Exp]
            NamedArgument(ident, exp)
          case "RngBinding" =>
            val patterns: MList[Pattern] = MList()
            for (j <- Range(i+1, operand.length())) {
              patterns += visitJsonObject2(operand.get(j)).asInstanceOf[Pattern]
            }
            val collection = visitJsonObject2(operand.get(i)).asInstanceOf[Collection]
            RngBinding(patterns.toList, collection)
          case "ExpCollection" =>
            ExpCollection(visitJsonObject2(operand.get(i)).asInstanceOf[Exp])
          case "IdentPattern" =>
            IdentPattern(operand.get(i).asInstanceOf[String])

      case "IdentExp" | "ElementValue" =>
        IdentExp(obj.get("element").asInstanceOf[String])
      // Non-Expr, similar to regular JSON
      case "Model" =>
        var packageName: Option[String] =
          if (obj.keySet().contains("packageName")) Some(obj.get("packageName")).asInstanceOf[Option[String]]
          else None
        var packages = visitJsonArray(obj.get("packages"), visitJsonObject).asInstanceOf[List[PackageDecl]]
        var annotations = visitJsonArray(obj.get("annotations"), visitJsonObject2).asInstanceOf[Set[AnnotationDecl]]
        var imports = visitJsonArray(obj.get("imports"), visitJsonObject2).asInstanceOf[List[ImportDecl]]
        var decls = visitJsonArray(obj.get("decls"), visitJsonObject2).asInstanceOf[List[TopDecl]]
        Model(packageName, packages, imports, annotations, decls)
      case "AnnotationDecl" =>
        AnnotationDecl(obj.getString("name"), visitJsonObject2(obj.get("ty")).asInstanceOf[Type])
      case "Annotation" =>
        Annotation(obj.getString("name"), visitJsonObject2(obj.get("exp")).asInstanceOf[Exp])
      case "PackageDecl" =>
        var name: QualifiedName = visitJsonObject2(obj.get("name")).asInstanceOf[QualifiedName]
        var model: Model = visitJsonObject(obj.get("model")).asInstanceOf[Model]
        PackageDecl(name, model)
      case "ImportDecl" =>
        var name: QualifiedName = visitJsonObject2(obj.get("name")).asInstanceOf[QualifiedName]
        var star: Boolean =
          if (obj.get("star").equals("true")) true
          else false
        ImportDecl(name, star)
      case "EntityDecl" =>
        var annotations = visitJsonArray(obj.get("annotations"), visitJsonObject2).asInstanceOf[List[Annotation]]
        var entityToken =
          if (obj.get("entityToken").equals("class")) ClassToken
          else if (obj.get("entityToken").equals("assoc")) AssocToken
          else IdentifierToken(obj.getString("entityToken"))
        var keyword =
          if (obj.keySet().contains("keyword")) Some(obj.getString("keyword"))
          else None
        var ident: String = obj.get("ident").toString()
        var typeParams: List[TypeParam] =
          if (obj.keySet.contains("typeParams"))
            visitJsonArray(obj.get("typeParams"), visitJsonObject2).asInstanceOf[List[TypeParam]]
          else Nil
        var extending: List[Type] =
          visitJsonArray(obj.get("extending"), visitJsonObject2).asInstanceOf[List[Type]]
        var members: List[MemberDecl] =
          visitJsonArray(obj.get("members"), visitJsonObject2).asInstanceOf[List[MemberDecl]]
        EntityDecl(annotations, entityToken, keyword, ident, null, typeParams, extending, members)
      case "TypeParam" =>
        var ident: String = obj.get("ident").toString()
        var bound: Option[TypeBound] =
          if (obj.keySet().contains("bound")) Some(visitJsonObject2(obj.get("bound")).asInstanceOf[TypeBound])
          else None
        TypeParam(ident, bound)
      case "TypeBound" =>
        visitJsonArray(obj.get("types"), visitJsonObject2).asInstanceOf[List[Type]]
      case "TypeDecl" =>
        var ident: String = obj.get("ident").toString()
        var typeParams: List[TypeParam] = visitJsonArray(obj.get("typeParam"), visitJsonObject2).asInstanceOf[List[TypeParam]]
        var ty =
          if (obj.keySet().contains("ty")) Some(visitJsonObject2(obj.get("ty")).asInstanceOf[Type])
          else None
        TypeDecl(ident, typeParams, ty)
      case "PropertyDecl" =>
        var modifiers =
          visitJsonArray(obj.get("modifiers"), getModifier).asInstanceOf[List[PropertyModifier]]
        var name = obj.getString("name")
        var ty: Option[Type] = 
          if (obj.keySet.contains("ty")) Some(visitJsonObject2(obj.get("ty")).asInstanceOf[Type])
          else None
        var multiplicity =
          if (obj.keySet.contains("multiplicity")) Some(visitJsonObject2(obj.get("multiplicity")).asInstanceOf[Multiplicity])
          else None
        var assignment =
          if (obj.keySet.contains("assignment")) Some(obj.getBoolean("assignment"))
          else None
        var expr: Option[Exp] =
          if (obj.keySet().contains("expr")) Some(visitJsonObject2(obj.get("expr")).asInstanceOf[Exp])
          else None
        PropertyDecl(modifiers, name, ty, multiplicity, assignment, expr)
      case "FunDecl" =>
        var ident: String = obj.get("ident").toString()
        var typeParams = visitJsonArray(obj.get("typeParams"), visitJsonObject2).asInstanceOf[List[TypeParam]]
        var params = visitJsonArray(obj.get("params"), visitJsonObject2).asInstanceOf[List[Param]]
        var ty =
          if (obj.keySet().contains("ty")) Some(visitJsonObject2(obj.get("ty")).asInstanceOf[Type])
          else None
        var spec = visitJsonArray(obj.get("spec"), visitJsonObject2).asInstanceOf[List[FunSpec]]
        var body: List[MemberDecl] = visitJsonArray(obj.get("body"), visitJsonObject2).asInstanceOf[List[MemberDecl]]
        FunDecl(ident, typeParams, params, ty, spec, body)
      case "FunSpec" =>
        FunSpec(obj.getBoolean("pre"), visitJsonObject2(obj.get("exp")).asInstanceOf[Exp])
      case "ConstraintDecl" =>
        var name: Option[String] =
          if (obj.keySet().contains("name")) Some(obj.get("name").toString())
          else None
        var exp = visitJsonObject2(obj.get("exp")).asInstanceOf[Exp]
        ConstraintDecl(name, exp)
      case "ExpressionDecl" =>
        var exp = visitJsonObject2(obj.get("exp")).asInstanceOf[Exp]
        ExpressionDecl(exp)
      case "Multiplicity" =>
        var exp1 = visitJsonObject2(obj.get("exp1")).asInstanceOf[Exp]
        val exp2: Option[Exp] =
          if (obj.keySet().contains("falseBranch")) Some(visitJsonObject2(obj.get("exp2")).asInstanceOf[Exp])
          else None
        Multiplicity(exp1, exp2)
      case "Param" =>
        Param(obj.getString("name"), visitJsonObject2(obj.get("ty")).asInstanceOf[Type])
      case "Quantifier" =>
        obj.getString("element") match {
          case "Forall" => Forall
          case "Exists" => Exists
        }
      case "TypeCastCheckExp" =>
        TypeCastCheckExp(obj.getBoolean("cast"), visitJsonObject2(obj.get("exp")).asInstanceOf[Exp], visitJsonObject2(obj.get("ty")).asInstanceOf[Type])
      case "QualifiedName" =>
        QualifiedName(visitJsonArray(obj.get("names").asInstanceOf[JSONArray], (x => x.asInstanceOf[String])).asInstanceOf[List[String]])
      case key @ _ =>
        if (opIsElementValue) {//} || (key match { case "ElementValue" => true case _ => false })) {
          // Assume that this is a reference to some Operation, either a FunctionAppl or Constructor
          //val exp1 = visitJsonObject2(key).asInstanceOf[Exp]
          val exp1 = IdentExp(key)//visitJsonObject2(operand.getJSONObject(i)).asInstanceOf[Exp]
          val args =
            if (operand.length > 1) {
              val argsList: MList[Argument] = MList()
              for (j <- Range(i, operand.length())) {
                argsList += PositionalArgument(visitJsonObject2(operand.get(j)).asInstanceOf[Exp])
              }
              argsList.toList
            } else Nil
          FunApplExp(exp1, args)
        } else {
          println("Unknown keys encountered in JSON string! (2) : " + key)//.asInstanceOf[Nothing]
          return null
          //System.exit(-1).asInstanceOf[Nothing]
        }
//      case key @ _ =>
//        println("Unknown keys encountered in JSON string! (2) : " + key)
//        System.exit(-1).asInstanceOf[Nothing]
    }
  }

  // Assuming that the input to this is an expression in JSON string format
  def json2exp2(expressionString: String): String = {
    var tokener: JSONTokener = new JSONTokener(expressionString)
    var jsonObject: JSONObject = new JSONObject(tokener)
    var element: JSONArray = jsonObject.get("elements").asInstanceOf[JSONArray]
    var specialization: JSONObject = element.get(0).asInstanceOf[JSONObject]
    var exp: Exp = visitJsonObject2(specialization.get("specialization").asInstanceOf[JSONObject]).asInstanceOf[Exp]
    if ( exp == null ) return null;
    return exp.toString();
  }

  def getVisitor(contents: String): (KScalaVisitor, ModelContext) = {

    var input: ANTLRInputStream = new ANTLRInputStream(contents)
    var lexer: ModelLexer = new ModelLexer(input)
    var tokens: CommonTokenStream = new CommonTokenStream(lexer)
    var parser: ModelParser = new ModelParser(tokens)
    parser.setBuildParseTree(true)

    // Use SLL prediction mode for faster parsing (falls back to LL if needed)
    parser.getInterpreter.setPredictionMode(PredictionMode.SLL)

    var tree = parser.model()
    var treeString = tree.toStringTree(parser);
    println("PARSE TREE:\n" + treeString)
    var ksv: KScalaVisitor = new KScalaVisitor()
    lastVisitor = ksv
    (ksv, tree)
  }

  def getModelFromFile(f: String): Model = {
    val path: Path = Paths.get(f)
    if (!Files.exists(path)) {
      errorExit(s"Given path does not exist: $f")
    }

    // Check cache if enabled
    if (parseCacheEnabled) {
      val lastModified = Files.getLastModifiedTime(path).toMillis
      val cacheKey = (f, lastModified)
      parseCache.get(cacheKey) match {
        case Some(cachedModel) =>
          lastParsedModel = cachedModel
          return cachedModel
        case None => // Continue to parse
      }
    }

    var bytes: Array[Byte] = Files.readAllBytes(path)
    var fileContents: String = new String(bytes, "UTF-8")
    val (ksv: KScalaVisitor, tree: ModelContext) = getVisitor(fileContents)
    lastParsedModel = ksv.visit(tree).asInstanceOf[Model]

    // Store in cache if enabled
    if (parseCacheEnabled) {
      val lastModified = Files.getLastModifiedTime(path).toMillis
      val cacheKey = (f, lastModified)
      parseCache.put(cacheKey, lastParsedModel)
    }

    lastParsedModel
  }

  /**
   * Read an SMT formula directly from a file.
   * Useful for making SMT experiments without rise4fun.com
   */
  def getRawSMTFromFile(f: String): String = {
    var path: Path = Paths.get(f)
    var bytes: Array[Byte] = Files.readAllBytes(path)
    var fileContents: String = new String(bytes, "UTF-8")
    fileContents
  }


  def getLastDeclDict(f: String) : Map[MemberDecl, Tuple2[Int, Int]] = {
    if (lastVisitor == null) {
      return getDeclDict(f)
    }
    return lastVisitor.declToPosition
  }
  def getLastDeclDict() : Map[MemberDecl, Tuple2[Int, Int]] = {
    if (lastVisitor == null) {
      return null
    }
    return lastVisitor.declToPosition
  }
  def getDeclDict(f: String) : Map[MemberDecl, Tuple2[Int, Int]] = {
    val (ksv: KScalaVisitor, tree: ModelContext) = getVisitor(f)
    var m: Model = ksv.visit(tree).asInstanceOf[Model]
    lastParsedModel = m
    ksv.declToPosition
  }

  def getModelFromString(f: String): Model = {
    val (ksv: KScalaVisitor, tree: ModelContext) = getVisitor(f)
    var m: Model = ksv.visit(tree).asInstanceOf[Model]
    lastParsedModel = m
    m
  }

  def exp2Json(expressionString: String): String = {
    val (ksv: KScalaVisitor, tree: ModelContext) = getVisitor(expressionString)
    var m: Model = ksv.visit(tree).asInstanceOf[Model]
    lastParsedModel = m

    var exp: Exp = m.decls(0).asInstanceOf[ExpressionDecl].exp
    val array = new JSONArray()
    val operand = new JSONArray()
    val root = new JSONObject()
    ASTOptions.useJson1 = true
    exp.toJson.toString(4)
  }

  def exp2Json2(expressionString: String): String = {
    val (ksv: KScalaVisitor, tree: ModelContext) = getVisitor(expressionString)
    var m: Model = ksv.visit(tree).asInstanceOf[Model]
    lastParsedModel = m

    var exp: Exp = m.decls(0).asInstanceOf[ExpressionDecl].exp
    val array = new JSONArray()
    val operand = new JSONArray()
    val root = new JSONObject()

    ASTOptions.useJson1 = false

    var elements = exp.toJson
    var specialization = new JSONObject()
    specialization = new JSONObject()
    specialization.put("specialization", elements)
    array.put(specialization)
    var res: JSONObject = root.put("elements", array)
    res.toString(4)
  }

  def exp2KExp(expressionString: String): Exp = {
    val (ksv: KScalaVisitor, tree: ModelContext) = getVisitor(expressionString)
    var m: Model = ksv.visit(tree).asInstanceOf[Model]
    lastParsedModel = m

    if (m == null || m.decls == null || m.decls.length == 0) {
      val mm = StringLiteral("")//Model(None,List(), List(), Set(), List())
      mm
    } else {
      var exp: Exp = m.decls(0).asInstanceOf[ExpressionDecl].exp
      exp
    }
  }


  def exp2KExpList(expressionString: String): List[Exp] = {
    val (ksv: KScalaVisitor, tree: ModelContext) = getVisitor(expressionString)
    var m: Model = ksv.visit(tree).asInstanceOf[Model]
    lastParsedModel = m
    m.decls.map(x => x.asInstanceOf[ExpressionDecl].exp)
  }
  
  def getEntitiesFromString(expressionString: String): List[EntityDecl] = {
    val (ksv: KScalaVisitor, tree: ModelContext) = getVisitor(expressionString)
    var m: Model = ksv.visit(tree).asInstanceOf[Model]
    lastParsedModel = m
    m.decls.map(x => x.asInstanceOf[EntityDecl])
  }
  
  def getEntitiesFromModel(m: Model): List[EntityDecl] = {
    if (m == null || m.decls == null || m.decls.length == 0) {
      List[EntityDecl]()
    } else {
      for (x <- m.decls if x.getClass == classOf[EntityDecl]) yield x.asInstanceOf[EntityDecl]
    }
  }
  
  def getTopLevelProperties(m: Model): List[PropertyDecl] = {
    if (m == null || m.decls == null || m.decls.length == 0) {
      List[PropertyDecl]()
    } else {
      for (x <- m.decls if x.getClass == classOf[PropertyDecl]) yield x.asInstanceOf[PropertyDecl]
    }
  }
  
  def getTopLevelConstraints(m: Model): List[ConstraintDecl] = {
    if (m == null || m.decls == null || m.decls.length == 0) {
      List[ConstraintDecl]()
    } else {
      for (x <- m.decls if x.getClass == classOf[ConstraintDecl]) yield x.asInstanceOf[ConstraintDecl]
    }
  }
  
  def getTopLevelFunctions(m: Model): List[FunDecl] = {
    if (m == null || m.decls == null || m.decls.length == 0) {
      List[FunDecl]()
    } else {
      for (x <- m.decls if x.getClass == classOf[FunDecl]) yield x.asInstanceOf[FunDecl]
    }
  }
  
  def getTopLevelExpressions(m: Model): List[ExpressionDecl] = {
    if (m == null || m.decls == null || m.decls.length == 0) {
      List[ExpressionDecl]()
    } else {
      for (x <- m.decls if x.getClass == classOf[ExpressionDecl]) yield x.asInstanceOf[ExpressionDecl]
    }
  }

  def getDeclCount(m: Model, d: Class[_]): Int = {
    if (m == null || m.decls == null || m.decls.length == 0) {
      0
    } else {
      m.decls.count(decl => (d == decl.getClass))
    }
  }

  def printStats(m: Model): Unit = {
    println("Imports: " + m.imports.size)
    println("Entities: " + getDeclCount(m, classOf[EntityDecl]))
    println("Properties: " + getDeclCount(m, classOf[PropertyDecl]))
    println("Functions: " + getDeclCount(m, classOf[FunDecl]))
    println("Types: " + getDeclCount(m, classOf[TypeDecl]))
    println("Expressions: " + getDeclCount(m, classOf[ExpressionDecl]))
    println("Constraints: " + getDeclCount(m, classOf[ConstraintDecl]))
  }

  def analyze(m: Model): Unit = {

  }
}

