package k.frontend

import java.io.{File, PrintWriter}
import scala.sys.process._
import scala.collection.mutable.ListBuffer

/**
 * BAESolver - Integration with BAE solver from kservices
 *
 * BAE (from kservices at ~/git/kservices) provides an alternative solving approach.
 */
object BAESolver {

  /** Path to kservices k script */
  var kservicesPath: String = {
    val candidates = List(
      s"${System.getProperty("user.home")}/git/kservices/k",
      "~/git/kservices/k",
      "../kservices/k"
    )

    candidates.find { path =>
      val expandedPath = path.replaceFirst("^~", System.getProperty("user.home"))
      val file = new File(expandedPath)
      file.exists() && file.canExecute()
    }.getOrElse(s"${System.getProperty("user.home")}/git/kservices/k")
  }

  /** Debug logging */
  var debug: Boolean = false

  /** Result of BAE solving */
  sealed trait BAEResult
  object BAEResult {
    case class Sat(model: String) extends BAEResult
    case object Unsat extends BAEResult
    case class Unknown(reason: String) extends BAEResult
    case class Error(message: String) extends BAEResult
  }

  /**
   * Check if BAE/kservices is available
   */
  def isAvailable: Boolean = {
    try {
      val expandedPath = kservicesPath.replaceFirst("^~", System.getProperty("user.home"))
      val file = new File(expandedPath)
      file.exists() && file.canExecute()
    } catch {
      case _: Exception => false
    }
  }

  /**
   * Solve a K model file using BAE via kservices
   *
   * @param kFilePath Path to the .k file
   * @param timeout Optional timeout in seconds
   * @return BAEResult
   */
  def solveKFile(kFilePath: String, timeout: Option[Int] = None): BAEResult = {
    val expandedPath = kservicesPath.replaceFirst("^~", System.getProperty("user.home"))
    val kFile = new File(kFilePath)
    
    if (!kFile.exists()) {
      return BAEResult.Error(s"K file not found: $kFilePath")
    }

    if (!isAvailable) {
      return BAEResult.Error("kservices/k script not found or not executable")
    }

    try {
      // Build command: ./k --solve <kfile>
      val cmd = ListBuffer("bash", expandedPath, "--solve", kFile.getAbsolutePath)

      if (debug) {
        println(s"[BAE] Running: ${cmd.mkString(" ")}")
      }

      // Run kservices k script and capture output
      val stdout = new StringBuilder
      val stderr = new StringBuilder

      val processLogger = ProcessLogger(
        line => stdout.append(line).append("\n"),
        line => stderr.append(line).append("\n")
      )

      val exitCode = if (timeout.isDefined) {
        // Use timeout if available (gtimeout on macOS with coreutils, timeout on Linux)
        val timeoutCmd = if (System.getProperty("os.name").toLowerCase.contains("mac")) {
          // macOS: try gtimeout (from GNU coreutils) or just run without timeout
          try {
            val result = "which gtimeout".!!
            s"gtimeout ${timeout.get}s ${cmd.mkString(" ")}"
          } catch {
            case _: Exception =>
              // No timeout available, just run the command
              // Note: This means timeout won't be enforced on macOS without gtimeout
              if (debug) println("[BAE] WARNING: timeout not available on macOS, running without timeout")
              cmd.mkString(" ")
          }
        } else {
          s"timeout ${timeout.get}s ${cmd.mkString(" ")}"
        }
        timeoutCmd ! processLogger
      } else {
        cmd ! processLogger
      }

      val output = stdout.toString()
      val error = stderr.toString()

      if (debug) {
        println(s"[BAE] Exit code: $exitCode")
        if (output.nonEmpty) println(s"[BAE] Output: $output")
        if (error.nonEmpty) println(s"[BAE] Error: $error")
      }

      // Parse output - kservices outputs JSON
      if (exitCode == 0) {
        // Look for SAT/UNSAT in the JSON output
        if (output.contains("\"sat\":true") || output.contains("sat") && !output.contains("unsat")) {
          BAEResult.Sat(output)
        } else if (output.contains("\"sat\":false") || output.contains("unsat")) {
          BAEResult.Unsat
        } else if (output.contains("error") || error.nonEmpty) {
          BAEResult.Error(if (error.nonEmpty) error else output)
        } else {
          // Unknown - might be a model or other output
          BAEResult.Sat(output)
        }
      } else {
        BAEResult.Error(s"kservices exited with code $exitCode: $error")
      }
    } catch {
      case e: Exception =>
        BAEResult.Error(s"Exception running BAE: ${e.getMessage}")
    }
  }

  /**
   * Solve using BAE (main entry point matching CVC5Solver interface)
   *
   * @param model The K Model
   * @param kFilePath Path to the .k file (since BAE works with K files, not SMT)
   * @param printModel Whether to print the model
   */
  def solveSMT(model: Model, kFilePath: String, printModel: Boolean): Unit = {
    println("[BAE]")
    println("[BAE] Using BAE solver from kservices")

    if (!isAvailable) {
      println("[BAE] WARNING: kservices/k script not found.")
      println(s"[BAE] Expected at: $kservicesPath")
      println("[BAE] Falling back to Z3...")
      val smtModel = model.toSMT
      val res = Frontend.runWithTimeout(Frontend.timeoutValue) {
        K2Z3.solveSMT(model, smtModel, true)
      }
      if (res.isEmpty) Frontend.log("Timeout")
      return
    }

    val result = solveKFile(kFilePath, Some(Frontend.timeoutValue))

    result match {
      case BAEResult.Sat(modelStr) =>
        println("[BAE] The given model is satisfiable.")
        if (printModel) {
          println("\n[BAE] Model:")
          println(modelStr)
        }

      case BAEResult.Unsat =>
        println("[BAE] The given model is NOT satisfiable.")

      case BAEResult.Unknown(reason) =>
        println(s"[BAE] Unknown: $reason")

      case BAEResult.Error(message) =>
        println(s"[BAE] Error: $message")
        println("[BAE] Falling back to Z3...")
        val smtModel = model.toSMT
        val res = Frontend.runWithTimeout(Frontend.timeoutValue) {
          K2Z3.solveSMT(model, smtModel, true)
        }
        if (res.isEmpty) Frontend.log("Timeout")
    }

    println("[BAE]")
  }
}

