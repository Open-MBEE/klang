package k.frontend

import java.io.{File, PrintWriter}
import scala.sys.process._
import scala.collection.mutable.ListBuffer

/**
 * CVC5Solver - Integration with CVC5 SMT solver
 *
 * CVC5 has significantly better performance for string constraints
 * compared to Z3 (often 10-100x faster).
 */
object CVC5Solver {

  /** Path to CVC5 binary */
  var cvc5Path: String = {
    // Try to find cvc5 in various locations
    val candidates = List(
      "cvc5",  // In PATH
      "export/lib/cvc5",  // In klang export
      "../export/lib/cvc5",  // Relative path
      "/usr/local/bin/cvc5",
      "/opt/homebrew/bin/cvc5"
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
            result.contains("cvc5")
          } catch {
            case _: Exception => false
          }
      }
    }.getOrElse("cvc5")
  }

  /** Debug logging */
  var debug: Boolean = false

  /** Result of CVC5 solving */
  sealed trait CVC5Result
  object CVC5Result {
    case class Sat(model: Map[String, String]) extends CVC5Result
    case object Unsat extends CVC5Result
    case class Unknown(reason: String) extends CVC5Result
    case class Error(message: String) extends CVC5Result
  }

  /**
   * Solve an SMT-LIB2 model using CVC5
   *
   * @param smtModel The SMT-LIB2 model string
   * @param timeout Optional timeout in seconds
   * @return CVC5Result
   */
  def solve(smtModel: String, timeout: Option[Int] = None): CVC5Result = {
    // Write SMT to temp file
    val tempFile = new File(".tmp/k_cvc5_model.smt2")
    tempFile.getParentFile.mkdirs()

    // Filter out any non-SMT header lines (like "=== SMT Model Generated...")
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
      println(s"[CVC5] Wrote SMT model to ${tempFile.getAbsolutePath}")
    }

    try {
      // Build command
      val cmd = ListBuffer(cvc5Path, "--produce-models", "--arrays-exp")
      timeout.foreach(t => cmd += s"--tlimit=${t * 1000}")
      cmd += tempFile.getAbsolutePath

      if (debug) {
        println(s"[CVC5] Running: ${cmd.mkString(" ")}")
      }

      // Run CVC5 and capture both stdout and stderr
      val stdout = new StringBuilder
      val stderr = new StringBuilder
      val process = Process(cmd.toSeq)
      val exitCode = process ! ProcessLogger(
        line => stdout.append(line).append("\n"),
        line => stderr.append(line).append("\n")
      )

      val output = stdout.toString + stderr.toString

      if (debug) {
        println(s"[CVC5] Exit code: $exitCode")
        println(s"[CVC5] Output:\n$output")
      }

      // Check for parse errors (CVC5 doesn't support all Z3 syntax)
      if (output.contains("Parse Error") || output.contains("unsupported")) {
        return CVC5Result.Error(s"CVC5 syntax incompatibility: ${output.take(200)}")
      }

      // Parse result
      parseResult(output)

    } catch {
      case e: RuntimeException if e.getMessage != null && e.getMessage.contains("Nonzero exit") =>
        // CVC5 returns non-zero for unsat
        CVC5Result.Unsat
      case e: Exception =>
        CVC5Result.Error(s"Failed to run CVC5: ${e.getMessage}")
    }
  }

  /**
   * Solve using CVC5 and print results similar to K2Z3
   */
  def solveSMT(model: Model, smtModel: String, printModel: Boolean): Unit = {
    println("[CVC5]")

    val result = solve(smtModel)

    result match {
      case CVC5Result.Sat(modelMap) =>
        println("[CVC5] The given model is satisfiable.")
        if (printModel) {
          println("\n[CVC5] Model values:")
          modelMap.foreach { case (name, value) =>
            println(s"  $name = $value")
          }
        }

      case CVC5Result.Unsat =>
        println("[CVC5] The given model is NOT satisfiable.")

      case CVC5Result.Unknown(reason) =>
        println(s"[CVC5] Unknown: $reason")

      case CVC5Result.Error(message) =>
        println(s"[CVC5] Error: $message")
    }

    println("[CVC5]")
  }

  /**
   * Parse CVC5 output
   */
  private def parseResult(output: String): CVC5Result = {
    val lines = output.trim.split("\n").map(_.trim)

    if (lines.isEmpty) {
      return CVC5Result.Error("Empty output from CVC5")
    }

    val firstLine = lines.head.toLowerCase

    if (firstLine == "sat") {
      // Parse model
      val modelMap = parseModel(output)
      CVC5Result.Sat(modelMap)
    } else if (firstLine == "unsat") {
      CVC5Result.Unsat
    } else if (firstLine == "unknown") {
      CVC5Result.Unknown("solver returned unknown")
    } else {
      CVC5Result.Error(s"Unexpected output: $firstLine")
    }
  }

  /**
   * Parse model from CVC5 output
   *
   * CVC5 outputs models in the form:
   * (
   * (define-fun name () Type value)
   * ...
   * )
   */
  private def parseModel(output: String): Map[String, String] = {
    val model = scala.collection.mutable.Map[String, String]()

    // Simple regex to extract define-fun declarations
    val defineRegex = """\(define-fun\s+(\S+)\s+\([^)]*\)\s+(\S+)\s+(.+?)\)(?=\s*\(define-fun|\s*\)$|\s*$)""".r

    // Try a simpler line-by-line approach
    val lines = output.split("\n")
    var inModel = false
    var currentDef = ""

    for (line <- lines) {
      val trimmed = line.trim
      if (trimmed.startsWith("(define-fun")) {
        currentDef = trimmed
        // Check if it's complete on one line
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
    // (define-fun name () Type value)
    val pattern = """\(define-fun\s+(\S+)\s+\([^)]*\)\s+\S+\s+(.+)\)$""".r
    expr match {
      case pattern(name, value) => Some((name, value.trim))
      case _ => None
    }
  }

  /**
   * Check if CVC5 is available
   */
  def isAvailable: Boolean = {
    try {
      val result = s"$cvc5Path --version".!!
      result.contains("cvc5")
    } catch {
      case _: Exception => false
    }
  }
}

