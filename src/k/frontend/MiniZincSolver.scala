package k.frontend

import scala.sys.process._
import java.io._
import scala.collection.mutable.ListBuffer

/**
 * MiniZinc solver wrapper.
 *
 * Invokes the `minizinc` command-line tool to solve MiniZinc models.
 */
object MiniZincSolver {

  // Path to minizinc executable (auto-detected or can be set)
  var minizincPath: String = detectMiniZinc()

  // Default solver backend
  var defaultSolver: String = "gecode"  // Options: gecode, chuffed, or-tools, etc.

  // Timeout in milliseconds (0 = no timeout)
  var timeout: Long = 0  // Disabled by default - causes issues with some solvers

  // Whether to show solver output
  var verbose: Boolean = false

  /**
   * Detect MiniZinc installation.
   */
  private def detectMiniZinc(): String = {
    // Try common locations
    val candidates = List(
      "minizinc",                           // In PATH
      "/usr/local/bin/minizinc",            // Homebrew on macOS
      "/opt/homebrew/bin/minizinc",         // Homebrew on Apple Silicon
      "/usr/bin/minizinc",                  // Linux
      System.getProperty("user.home") + "/MiniZincIDE/bin/minizinc"  // IDE installation
    )

    for (path <- candidates) {
      try {
        val result = s"$path --version".!!
        if (result.contains("MiniZinc")) {
          return path
        }
      } catch {
        case _: Exception => // Try next
      }
    }

    "minizinc"  // Default, hope it's in PATH
  }

  /**
   * Check if MiniZinc is available.
   */
  def isAvailable: Boolean = {
    try {
      val result = s"$minizincPath --version".!!
      result.contains("MiniZinc")
    } catch {
      case _: Exception => false
    }
  }

  /**
   * Get available solvers.
   */
  def availableSolvers: List[String] = {
    try {
      val result = s"$minizincPath --solvers".!!
      // Parse solver list from output
      result.split("\n").filter(_.trim.nonEmpty).map(_.trim.split(" ").head).toList
    } catch {
      case _: Exception => List()
    }
  }

  /**
   * Solve a MiniZinc model.
   *
   * @param mznCode The MiniZinc code to solve
   * @param solver The solver to use (default: gecode)
   * @param allSolutions Whether to find all solutions
   * @return SolveResult with status and solution(s)
   */
  def solve(mznCode: String, solver: String = defaultSolver, allSolutions: Boolean = false): MznSolveResult = {
    // Write MiniZinc code to temp file
    val tmpDir = new File(System.getProperty("user.dir"), ".tmp")
    tmpDir.mkdirs()
    val mznFile = new File(tmpDir, s"model_${System.currentTimeMillis()}.mzn")

    try {
      val writer = new PrintWriter(mznFile)
      writer.write(mznCode)
      writer.close()

      // Build command
      val cmdParts = ListBuffer[String](
        minizincPath,
        "--solver", solver
      )

      if (timeout > 0) {
        cmdParts ++= List("--time-limit", (timeout / 1000).toString)
      }

      if (allSolutions) {
        cmdParts += "-a"  // All solutions
      }

      cmdParts += mznFile.getAbsolutePath

      if (verbose) {
        println(s"[MiniZinc] Running: ${cmdParts.mkString(" ")}")
      }

      // Run solver
      val stdout = new StringBuilder
      val stderr = new StringBuilder

      val process = Process(cmdParts.toSeq)
      val exitCode = process ! ProcessLogger(
        line => stdout.append(line + "\n"),
        line => stderr.append(line + "\n")
      )

      if (verbose) {
        println(s"[MiniZinc] Exit code: $exitCode")
        println(s"[MiniZinc] Output:\n${stdout.toString()}")
        if (stderr.nonEmpty) {
          println(s"[MiniZinc] Stderr:\n${stderr.toString()}")
        }
      }

      // Parse result
      val output = stdout.toString()

      if (output.contains("=====UNSATISFIABLE=====")) {
        MznSolveResult(MznUnsat, Nil, stderr.toString())
      } else if (output.contains("=====UNKNOWN=====")) {
        MznSolveResult(MznUnknown, Nil, stderr.toString())
      } else if (output.contains("=====ERROR=====") || exitCode != 0) {
        MznSolveResult(MznError, Nil, stderr.toString())
      } else {
        // Parse solutions
        val solutions = parseSolutions(output)
        if (solutions.isEmpty) {
          MznSolveResult(MznUnknown, Nil, "No solution found")
        } else {
          MznSolveResult(MznSat, solutions, "")
        }
      }

    } catch {
      case e: Exception =>
        MznSolveResult(MznError, Nil, e.getMessage)
    } finally {
      // Clean up temp file
      if (!verbose) {
        mznFile.delete()
      }
    }
  }

  /**
   * Parse solutions from MiniZinc output.
   */
  private def parseSolutions(output: String): List[Map[String, String]] = {
    val solutions = ListBuffer[Map[String, String]]()

    // MiniZinc output has solutions separated by ----------
    val lines = output.split("\n")
    var currentSolution = Map[String, String]()

    for (line <- lines) {
      val trimmed = line.trim

      if (trimmed == "----------" || trimmed.startsWith("=====")) {
        // Solution separator
        if (currentSolution.nonEmpty) {
          solutions += currentSolution
          currentSolution = Map()
        }
      } else if (trimmed.nonEmpty && !trimmed.startsWith("%")) {
        // Parse variable assignment - handle various formats:
        // "x = 5"
        // "s = (len=5) chars: [104, 101, ...]"
        val eqIdx = trimmed.indexOf(" = ")
        if (eqIdx > 0) {
          val name = trimmed.substring(0, eqIdx).trim
          val value = trimmed.substring(eqIdx + 3).trim.stripSuffix(";")
          currentSolution = currentSolution + (name -> value)
        }
      }
    }

    // Add last solution if any
    if (currentSolution.nonEmpty) {
      solutions += currentSolution
    }

    solutions.toList
  }

  /**
   * Parse a JSON solution object.
   */
  private def parseJsonSolution(json: String): Map[String, String] = {
    // Simple JSON parsing for flat objects
    val result = scala.collection.mutable.Map[String, String]()

    // Remove braces and split by comma
    val content = json.stripPrefix("{").stripSuffix("}").trim
    if (content.isEmpty) return Map()

    // Split on commas that are not inside brackets
    var depth = 0
    var current = new StringBuilder()
    val pairs = ListBuffer[String]()

    for (c <- content) {
      c match {
        case '[' | '{' => depth += 1; current += c
        case ']' | '}' => depth -= 1; current += c
        case ',' if depth == 0 =>
          pairs += current.toString().trim
          current = new StringBuilder()
        case _ => current += c
      }
    }
    if (current.nonEmpty) {
      pairs += current.toString().trim
    }

    // Parse each pair
    for (pair <- pairs) {
      val colonIdx = pair.indexOf(':')
      if (colonIdx > 0) {
        val key = pair.substring(0, colonIdx).trim.stripPrefix("\"").stripSuffix("\"")
        val value = pair.substring(colonIdx + 1).trim
        result(key) = value
      }
    }

    result.toMap
  }

  /**
   * Pretty-print a solution.
   */
  def formatSolution(solution: Map[String, String]): String = {
    solution.toList.sortBy(_._1).map { case (k, v) =>
      // Try to decode string char arrays from format: (len=N) chars: [c1, c2, ...]
      val decodedValue = if (v.contains("chars:") && v.contains("[")) {
        try {
          val arrayPart = v.substring(v.indexOf("["))
          val nums = arrayPart.stripPrefix("[").stripSuffix("]").split(",").map(_.trim.toInt)
          val chars = nums.takeWhile(_ > 0).map(_.toChar).mkString
          s"\"$chars\""
        } catch {
          case _: Exception => v
        }
      } else {
        v
      }
      s"  $k = $decodedValue"
    }.mkString("\n")
  }
}

/**
 * MiniZinc solve result status.
 */
sealed trait MznStatus
case object MznSat extends MznStatus
case object MznUnsat extends MznStatus
case object MznUnknown extends MznStatus
case object MznError extends MznStatus

/**
 * Result of solving a MiniZinc model.
 */
case class MznSolveResult(
  status: MznStatus,
  solutions: List[Map[String, String]],
  error: String
) {
  def isSat: Boolean = status == MznSat
  def isUnsat: Boolean = status == MznUnsat

  def firstSolution: Option[Map[String, String]] = solutions.headOption

  override def toString: String = status match {
    case MznSat =>
      val solStr = solutions.headOption.map(MiniZincSolver.formatSolution).getOrElse("(no values)")
      s"SAT\n$solStr"
    case MznUnsat => "UNSAT"
    case MznUnknown => s"UNKNOWN${if (error.nonEmpty) s": $error" else ""}"
    case MznError => s"ERROR: $error"
  }
}

