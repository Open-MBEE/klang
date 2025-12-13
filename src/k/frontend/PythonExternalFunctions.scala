package k.frontend

import scala.util.{Try, Success, Failure}
import scala.collection.mutable.{Map => MMap}
import scala.sys.process._
import java.io.{File, PrintWriter}

/**
 * PythonExternalFunctions - Support for calling external Python functions from K
 *
 * This module enables K models to import and call Python functions using CEGAR-style
 * (Counter-Example Guided Abstraction Refinement) solving.
 *
 * Syntax:
 *   - import python math.sqrt     -- Import specific function
 *   - import python numpy         -- Import module
 *   - import python mymodule.*    -- Import all from module
 *
 * Usage in K:
 *   import python math
 *
 *   class MyClass {
 *     x : Real
 *     req math.sqrt(x) = 5.0
 *   }
 *
 * The approach:
 * 1. During SMT generation, Python calls are treated as uninterpreted functions
 * 2. After Z3 finds a candidate solution, we call the actual Python function via subprocess
 * 3. If the result mismatches, we add a refinement constraint and re-solve
 * 4. Repeat until consistent or max iterations reached
 *
 * @author K Language Team
 */
object PythonExternalFunctions {

  /** Whether to log Python function calls (follows K2Z3.debug) */
  def logCalls: Boolean = K2Z3.debug

  /** Path to Python interpreter - can be overridden */
  var pythonPath: String = detectPython()

  /** Cache of evaluated function calls: (qualifiedName, args) -> result */
  private val evaluationCache: MMap[(String, List[Any]), Any] = MMap()

  /** Import map: simple name -> (module, function) */
  private var pythonImportMap: Map[String, (String, Option[String])] = Map()

  /** Temporary directory for Python bridge scripts */
  private lazy val tempDir: File = {
    val dir = new File(System.getProperty("user.dir"), ".tmp/python_bridge")
    dir.mkdirs()
    dir
  }

  /**
   * Detect Python interpreter path
   */
  private def detectPython(): String = {
    // Try common Python paths
    val candidates = List("python3", "python", "/usr/bin/python3", "/usr/local/bin/python3")

    for (candidate <- candidates) {
      try {
        val result = Seq(candidate, "--version").!!
        if (result.nonEmpty) return candidate
      } catch {
        case _: Exception => // continue
      }
    }

    // Default fallback
    "python3"
  }

  /**
   * Check if a qualified name refers to a Python import
   */
  def isPythonReference(qualifiedName: String): Boolean = {
    val parts = qualifiedName.split("\\.")
    if (parts.isEmpty) return false

    // Check if first part matches a registered Python import
    pythonImportMap.contains(parts.head)
  }

  /**
   * Register a Python import declaration
   *
   * Examples:
   *   - "math" -> module math, no specific function
   *   - "math.sqrt" -> module math, function sqrt
   *   - "numpy.linalg.det" -> module numpy.linalg, function det
   */
  def registerImport(qualifiedName: String, star: Boolean): Unit = {
    val parts = qualifiedName.split("\\.").toList

    if (star) {
      // import python numpy.* - import all from module
      val moduleName = parts.mkString(".")
      pythonImportMap += (parts.last -> (moduleName, None))
      if (logCalls) println(s"[PythonExternalFunctions] Registered wildcard import: $moduleName.*")
    } else if (parts.length == 1) {
      // import python math - import entire module
      pythonImportMap += (parts.head -> (parts.head, None))
      if (logCalls) println(s"[PythonExternalFunctions] Registered module import: ${parts.head}")
    } else {
      // import python math.sqrt - import specific function
      val moduleName = parts.dropRight(1).mkString(".")
      val funcName = parts.last
      pythonImportMap += (funcName -> (moduleName, Some(funcName)))
      if (logCalls) println(s"[PythonExternalFunctions] Registered function import: $funcName from $moduleName")
    }
  }

  /**
   * Resolve a qualified name for Python execution
   *
   * Returns (moduleName, functionName)
   */
  def resolveQualifiedName(qualifiedName: String): Option[(String, String)] = {
    val parts = qualifiedName.split("\\.").toList
    if (parts.isEmpty) return None

    pythonImportMap.get(parts.head) match {
      case Some((moduleName, Some(funcName))) if parts.length == 1 =>
        // Direct function call: sqrt(x) -> math.sqrt
        Some((moduleName, funcName))
      case Some((moduleName, None)) if parts.length >= 2 =>
        // Module.function call: math.sqrt(x)
        Some((moduleName, parts.tail.mkString(".")))
      case Some((moduleName, _)) if parts.length >= 2 =>
        // Nested call: numpy.linalg.det
        Some((moduleName, parts.tail.mkString(".")))
      case _ =>
        // Try direct interpretation as module.function
        if (parts.length >= 2) {
          Some((parts.dropRight(1).mkString("."), parts.last))
        } else {
          None
        }
    }
  }

  /**
   * Evaluate a Python function call with concrete arguments
   *
   * Uses subprocess to call Python and parse the result.
   */
  def evaluatePythonCall(moduleName: String, funcName: String, args: List[Any]): Try[Any] = Try {
    // Check cache first
    val cacheKey = (s"$moduleName.$funcName", args)
    evaluationCache.get(cacheKey) match {
      case Some(result) => return Success(result)
      case None => // continue
    }

    // Generate Python code to evaluate the function
    val pythonCode = generatePythonCode(moduleName, funcName, args)

    if (logCalls) {
      println(s"[PythonExternalFunctions] Executing: $moduleName.$funcName(${args.mkString(", ")})")
    }

    // Write to temp file and execute
    val scriptFile = new File(tempDir, s"eval_${System.currentTimeMillis()}.py")
    try {
      val writer = new PrintWriter(scriptFile)
      writer.write(pythonCode)
      writer.close()

      // Execute Python and capture output
      val output = Seq(pythonPath, scriptFile.getAbsolutePath).!!.trim

      // Parse the result
      val result = parseResult(output)

      // Cache the result
      evaluationCache += (cacheKey -> result)

      if (logCalls) {
        println(s"[PythonExternalFunctions] Result: $result")
      }

      result
    } finally {
      scriptFile.delete()
    }
  }

  /**
   * Generate Python code to evaluate a function call and print the result
   * Supports debugging via debugpy when K_PYTHON_DEBUG environment variable is set
   */
  private def generatePythonCode(moduleName: String, funcName: String, args: List[Any]): String = {
    val argsStr = args.map(argToPython).mkString(", ")

    // Handle nested module.function names
    val (importStmt, callExpr) = if (funcName.contains(".")) {
      // e.g., numpy.linalg.det -> import numpy.linalg; numpy.linalg.det(...)
      val fullModule = s"$moduleName.${funcName.split("\\.").dropRight(1).mkString(".")}"
      val actualFunc = funcName.split("\\.").last
      (s"import $fullModule", s"$fullModule.$actualFunc($argsStr)")
    } else {
      (s"import $moduleName", s"$moduleName.$funcName($argsStr)")
    }

    // Check if debugging is enabled via environment variable
    val debugCode = if (sys.env.getOrElse("K_PYTHON_DEBUG", "0") == "1") {
      val debugPort = sys.env.getOrElse("K_PYTHON_DEBUG_PORT", "5678")
      s"""
         |import os
         |# Enable debugpy if K_PYTHON_DEBUG is set
         |if os.environ.get('K_PYTHON_DEBUG', '0') == '1':
         |    try:
         |        import debugpy
         |        debug_port = int(os.environ.get('K_PYTHON_DEBUG_PORT', $debugPort))
         |        if not debugpy.is_client_connected():
         |            debugpy.listen(('0.0.0.0', debug_port))
         |            print(f"[K Python Debug] Listening on port {debug_port}", file=sys.stderr)
         |    except ImportError:
         |        print("[K Python Debug] debugpy not installed, skipping debug setup", file=sys.stderr)
         |    except Exception as e:
         |        print(f"[K Python Debug] Could not start debugpy: {e}", file=sys.stderr)
         |""".stripMargin
    } else {
      ""
    }

    s"""$importStmt
       |import json
       |import sys
       |$debugCode
       |try:
       |    result = $callExpr
       |    # Output in a parseable format
       |    if isinstance(result, bool):
       |        print("BOOL:" + str(result))
       |    elif isinstance(result, int):
       |        print("INT:" + str(result))
       |    elif isinstance(result, float):
       |        print("FLOAT:" + str(result))
       |    elif isinstance(result, str):
       |        print("STR:" + json.dumps(result))
       |    elif result is None:
       |        print("NONE:")
       |    else:
       |        # Try to convert to string representation
       |        print("OBJ:" + str(result))
       |except Exception as e:
       |    print("ERROR:" + str(e), file=sys.stderr)
       |    sys.exit(1)
       |""".stripMargin
  }

  /**
   * Convert a Scala/Java value to Python literal
   */
  private def argToPython(value: Any): String = value match {
    case i: Int => i.toString
    case l: Long => l.toString
    case d: Double =>
      if (d == d.toLong) s"${d.toLong}.0" else d.toString
    case f: Float => f.toString
    case b: Boolean => if (b) "True" else "False"
    case s: String => s""""$s""""
    case bi: BigInt => bi.toString
    case bd: BigDecimal => bd.toString
    case null => "None"
    case list: List[_] => s"[${list.map(argToPython).mkString(", ")}]"
    case other => other.toString
  }

  /**
   * Parse Python output back to Scala value
   */
  private def parseResult(output: String): Any = {
    val trimmed = output.trim
    if (trimmed.startsWith("BOOL:")) {
      trimmed.substring(5) == "True"
    } else if (trimmed.startsWith("INT:")) {
      trimmed.substring(4).toLong
    } else if (trimmed.startsWith("FLOAT:")) {
      trimmed.substring(6).toDouble
    } else if (trimmed.startsWith("STR:")) {
      // Parse JSON string
      val jsonStr = trimmed.substring(4)
      jsonStr.drop(1).dropRight(1)  // Remove quotes
    } else if (trimmed.startsWith("NONE:")) {
      null
    } else if (trimmed.startsWith("OBJ:")) {
      trimmed.substring(4)  // Return as string
    } else {
      // Try to parse as number
      try {
        trimmed.toDouble
      } catch {
        case _: NumberFormatException => trimmed
      }
    }
  }

  /**
   * Try to evaluate a Python call, returning either a concrete result or None
   */
  def tryEvaluate(qualifiedName: String, args: List[Any]): Option[Any] = {
    resolveQualifiedName(qualifiedName) match {
      case Some((moduleName, funcName)) =>
        evaluatePythonCall(moduleName, funcName, args) match {
          case Success(result) => Some(result)
          case Failure(e) =>
            if (logCalls) println(s"[PythonExternalFunctions] Call failed: $e")
            None
        }
      case None =>
        if (logCalls) println(s"[PythonExternalFunctions] Could not resolve: $qualifiedName")
        None
    }
  }

  /**
   * Check if all arguments are concrete (not symbolic)
   */
  def areArgsConcrete(args: List[Any]): Boolean = {
    args.forall {
      case _: Int | _: Long | _: Double | _: Float | _: String | _: Boolean | null => true
      case _: java.lang.Number => true
      case list: List[_] => list.forall(areArgsConcrete)
      case _ => false
    }
  }

  private def areArgsConcrete(item: Any): Boolean = {
    item match {
      case _: Int | _: Long | _: Double | _: Float | _: String | _: Boolean | null => true
      case _: java.lang.Number => true
      case _ => false
    }
  }

  /**
   * Convert a Scala/Java value to SMT-LIB2 format
   */
  def anyToSMT(value: Any): String = value match {
    case i: Int => i.toString
    case l: Long => l.toString
    case d: Double =>
      if (d == d.toLong) s"${d.toLong}.0"
      else d.toString
    case f: Float => f.toString
    case b: Boolean => b.toString
    case s: String => s""""$s""""
    case bi: BigInt => bi.toString
    case bd: BigDecimal => bd.toString
    case null => "nil"  // SMT nil
    case other => other.toString
  }

  /**
   * Clear all state (for new model)
   */
  def reset(): Unit = {
    evaluationCache.clear()
    pythonImportMap = Map()
  }

  /**
   * Get all registered Python imports
   */
  def getImports: Map[String, (String, Option[String])] = pythonImportMap

  /**
   * Check if Python is available
   */
  def isPythonAvailable: Boolean = {
    try {
      val result = Seq(pythonPath, "--version").!!
      result.nonEmpty
    } catch {
      case _: Exception => false
    }
  }

  /**
   * Get Python version
   */
  def getPythonVersion: Option[String] = {
    try {
      Some(Seq(pythonPath, "--version").!!.trim)
    } catch {
      case _: Exception => None
    }
  }
}

