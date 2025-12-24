package k.frontend

import java.io.{File, PrintWriter}
import scala.sys.process._
import scala.collection.mutable.ListBuffer

/**
 * YicesSolver - Integration with Yices 2 SMT solver
 *
 * Yices 2 is often faster than Z3 for certain problem classes,
 * especially those involving real arithmetic and linear constraints.
 */
object YicesSolver {

  /** Path to Yices binary */
  var yicesPath: String = {
    // Try to find yices-smt2 in various locations
    val candidates = List(
      "yices-smt2",  // In PATH
      "yices",  // Alternative name
      "/usr/local/bin/yices-smt2",
      "/opt/homebrew/bin/yices-smt2",
      "/usr/bin/yices-smt2"
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
            result.contains("Yices")
          } catch {
            case _: Exception => false
          }
      }
    }.getOrElse("yices-smt2")
  }

  /** Debug logging */
  var debug: Boolean = false

  /** Result of Yices solving */
  sealed trait YicesResult
  object YicesResult {
    case class Sat(model: Map[String, String]) extends YicesResult
    case object Unsat extends YicesResult
    case class Unknown(reason: String) extends YicesResult
    case class Error(message: String) extends YicesResult
  }

  /**
   * Solve an SMT-LIB2 model using Yices
   *
   * @param smtModel The SMT-LIB2 model string
   * @param timeout Optional timeout in seconds
   * @return YicesResult
   */
  def solve(smtModel: String, timeout: Option[Int] = None): YicesResult = {
    // Write SMT to temp file
    val tempFile = new File(".tmp/k_yices_model.smt2")
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
      println(s"[Yices] Wrote SMT model to ${tempFile.getAbsolutePath}")
    }

    try {
      // Build command
      val cmd = ListBuffer(yicesPath, "--mode=interactive")
      timeout.foreach(t => cmd += s"--timeout=${t}")
      cmd += tempFile.getAbsolutePath

      if (debug) {
        println(s"[Yices] Running: ${cmd.mkString(" ")}")
      }

      // Run Yices and capture output
      val stdout = new StringBuilder
      val stderr = new StringBuilder
      val process = Process(cmd.toSeq)
      val exitCode = process ! ProcessLogger(
        line => stdout.append(line).append("\n"),
        line => stderr.append(line).append("\n")
      )

      val output = stdout.toString + stderr.toString

      if (debug) {
        println(s"[Yices] Exit code: $exitCode")
        println(s"[Yices] Output:\n$output")
      }

      // Parse result
      parseResult(output)

    } catch {
      case e: RuntimeException if e.getMessage != null && e.getMessage.contains("Nonzero exit") =>
        // Yices returns non-zero for unsat
        YicesResult.Unsat
      case e: Exception =>
        YicesResult.Error(s"Failed to run Yices: ${e.getMessage}")
    }
  }

  /**
   * Solve using Yices and print results similar to K2Z3
   */
  def solveSMT(model: Model, smtModel: String, printModel: Boolean): Unit = {
    println("[Yices]")

    val result = solve(smtModel)

    result match {
      case YicesResult.Sat(modelMap) =>
        println("[Yices] The given model is satisfiable.")
        if (printModel) {
          println("\n[Yices] Model values:")
          modelMap.foreach { case (name, value) =>
            println(s"  $name = $value")
          }
        }

      case YicesResult.Unsat =>
        println("[Yices] The given model is NOT satisfiable.")

      case YicesResult.Unknown(reason) =>
        println(s"[Yices] Unknown: $reason")

      case YicesResult.Error(message) =>
        println(s"[Yices] Error: $message")
    }

    println("[Yices]")
  }

  /**
   * Parse Yices output
   */
  private def parseResult(output: String): YicesResult = {
    val lines = output.trim.split("\n").map(_.trim)

    if (lines.isEmpty) {
      return YicesResult.Error("Empty output from Yices")
    }

    // Yices outputs sat/unsat/unknown
    val satLine = lines.find(_.toLowerCase == "sat")
    val unsatLine = lines.find(_.toLowerCase == "unsat")
    val unknownLine = lines.find(_.toLowerCase == "unknown")

    if (satLine.isDefined) {
      // Parse model
      val modelMap = parseModel(output)
      YicesResult.Sat(modelMap)
    } else if (unsatLine.isDefined) {
      YicesResult.Unsat
    } else if (unknownLine.isDefined) {
      YicesResult.Unknown("solver returned unknown")
    } else {
      YicesResult.Error(s"Unexpected output: ${lines.headOption.getOrElse("empty")}")
    }
  }

  /**
   * Parse model from Yices output
   */
  private def parseModel(output: String): Map[String, String] = {
    val model = scala.collection.mutable.Map[String, String]()

    // Yices outputs models in SMT-LIB2 format similar to CVC5
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
   * Check if Yices is available
   */
  def isAvailable: Boolean = {
    try {
      val result = s"$yicesPath --version".!!
      result.contains("Yices")
    } catch {
      case _: Exception => false
    }
  }
}

