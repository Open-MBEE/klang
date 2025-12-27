package k.frontend

import java.io.{File, PrintWriter}
import scala.sys.process._
import scala.collection.mutable.ListBuffer

/**
 * MathSATSolver - Integration with MathSAT 5 SMT solver
 *
 * MathSAT 5 is particularly good for real arithmetic and
 * optimization problems.
 */
object MathSATSolver {

  /** Path to MathSAT binary */
  var mathsatPath: String = {
    // Try to find mathsat in various locations
    val candidates = List(
      "mathsat",  // In PATH
      "/usr/local/bin/mathsat",
      "/opt/homebrew/bin/mathsat",
      "/usr/bin/mathsat"
    )

    candidates.find { path =>
      try {
        val file = new File(path)
        file.exists() && file.canExecute()
      } catch {
        case _: Exception =>
          // Try running it
          try {
            val result = s"$path --version".!!
            result.contains("MathSAT")
          } catch {
            case _: Exception => false
          }
      }
    }.getOrElse("mathsat")
  }

  /** Debug logging */
  var debug: Boolean = false

  /** Result of MathSAT solving */
  sealed trait MathSATResult
  object MathSATResult {
    case class Sat(model: Map[String, String]) extends MathSATResult
    case object Unsat extends MathSATResult
    case class Unknown(reason: String) extends MathSATResult
    case class Error(message: String) extends MathSATResult
  }

  /**
   * Solve an SMT-LIB2 model using MathSAT
   *
   * @param smtModel The SMT-LIB2 model string
   * @param timeout Optional timeout in seconds
   * @return MathSATResult
   */
  def solve(smtModel: String, timeout: Option[Int] = None): MathSATResult = {
    // Write SMT to temp file
    val tempFile = new File(".tmp/k_mathsat_model.smt2")
    tempFile.getParentFile.mkdirs()

    // Filter out any non-SMT header lines
    val cleanedModel = smtModel.split("\n")
      .dropWhile(line => !line.trim.startsWith("(") && !line.trim.startsWith(";"))
      .mkString("\n")

    val writer = new PrintWriter(tempFile)
    writer.write(cleanedModel)
    // Ensure we have check-sat before get-model
    if (!cleanedModel.contains("(check-sat)")) {
      writer.write("\n(check-sat)\n")
    }
    // Ensure we request a model
    if (!cleanedModel.contains("(get-model)")) {
      writer.write("\n(get-model)\n")
    }
    writer.close()

    if (debug) {
      println(s"[MathSAT] Wrote SMT model to ${tempFile.getAbsolutePath}")
    }

    try {
      // Build command
      val cmd = ListBuffer(mathsatPath)
      timeout.foreach(t => cmd += s"-timeout=${t * 1000}")  // MathSAT uses milliseconds
      cmd += tempFile.getAbsolutePath

      if (debug) {
        println(s"[MathSAT] Running: ${cmd.mkString(" ")}")
      }

      // Run MathSAT and capture output
      val stdout = new StringBuilder
      val stderr = new StringBuilder
      val process = Process(cmd.toSeq)
      val exitCode = process ! ProcessLogger(
        line => stdout.append(line).append("\n"),
        line => stderr.append(line).append("\n")
      )

      val output = stdout.toString + stderr.toString

      if (debug) {
        println(s"[MathSAT] Exit code: $exitCode")
        println(s"[MathSAT] Output:\n$output")
      }

      // Parse result
      parseResult(output)

    } catch {
      case e: RuntimeException if e.getMessage != null && e.getMessage.contains("Nonzero exit") =>
        // MathSAT returns non-zero for unsat
        MathSATResult.Unsat
      case e: Exception =>
        MathSATResult.Error(s"Failed to run MathSAT: ${e.getMessage}")
    }
  }

  /**
   * Solve using MathSAT and print results similar to K2Z3
   */
  def solveSMT(model: Model, smtModel: String, printModel: Boolean): Unit = {
    println("[MathSAT]")

    val result = solve(smtModel)

    result match {
      case MathSATResult.Sat(modelMap) =>
        println("[MathSAT] The given model is satisfiable.")
        if (printModel) {
          println("\n[MathSAT] Model values:")
          modelMap.foreach { case (name, value) =>
            println(s"  $name = $value")
          }
        }

      case MathSATResult.Unsat =>
        println("[MathSAT] The given model is NOT satisfiable.")

      case MathSATResult.Unknown(reason) =>
        println(s"[MathSAT] Unknown: $reason")

      case MathSATResult.Error(message) =>
        println(s"[MathSAT] Error: $message")
    }

    println("[MathSAT]")
  }

  /**
   * Parse MathSAT output
   */
  private def parseResult(output: String): MathSATResult = {
    val lines = output.trim.split("\n").map(_.trim)

    if (lines.isEmpty) {
      return MathSATResult.Error("Empty output from MathSAT")
    }

    // MathSAT outputs sat/unsat/unknown
    val satLine = lines.find(_.toLowerCase == "sat")
    val unsatLine = lines.find(_.toLowerCase == "unsat")
    val unknownLine = lines.find(_.toLowerCase == "unknown")

    if (satLine.isDefined) {
      // Parse model
      val modelMap = parseModel(output)
      MathSATResult.Sat(modelMap)
    } else if (unsatLine.isDefined) {
      MathSATResult.Unsat
    } else if (unknownLine.isDefined) {
      MathSATResult.Unknown("solver returned unknown")
    } else {
      MathSATResult.Error(s"Unexpected output: ${lines.headOption.getOrElse("empty")}")
    }
  }

  /**
   * Parse model from MathSAT output
   */
  private def parseModel(output: String): Map[String, String] = {
    val model = scala.collection.mutable.Map[String, String]()

    // MathSAT outputs models in SMT-LIB2 format
    val lines = output.split("\n")
    var currentDef = ""

    for (line <- lines) {
      val trimmed = line.trim
      if (trimmed.startsWith("(define-fun")) {
        currentDef = trimmed
        if (trimmed.count(_ == '(') == trimmed.count(_ == ')')) {
          extractDefineFun(trimmed).foreach { case (name, value) =>
            model += (name -> value)
          }
          currentDef = ""
        }
      } else if (currentDef.nonEmpty) {
        currentDef += " " + trimmed
        if (currentDef.count(_ == '(') == currentDef.count(_ == ')')) {
          extractDefineFun(currentDef).foreach { case (name, value) =>
            model += (name -> value)
          }
          currentDef = ""
        }
      }
    }

    model.toMap
  }

  /**
   * Extract name and value from a define-fun expression
   */
  private def extractDefineFun(expr: String): Option[(String, String)] = {
    val pattern = """\(define-fun\s+(\S+)\s+\([^)]*\)\s+\S+\s+(.+)\)$""".r
    expr match {
      case pattern(name, value) => Some((name, value.trim))
      case _ => None
    }
  }

  /**
   * Check if MathSAT is available
   */
  def isAvailable: Boolean = {
    try {
      val result = s"$mathsatPath --version".!!
      result.contains("MathSAT")
    } catch {
      case _: Exception => false
    }
  }
}

