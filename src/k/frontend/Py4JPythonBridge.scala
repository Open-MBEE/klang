package k.frontend

import scala.util.{Try, Success, Failure}

/**
 * Py4J-based Python bridge for K external functions.
 *
 * This is a placeholder that delegates to PythonExternalFunctions (subprocess-based).
 * Full Py4J integration can be enabled when py4j dependency is available.
 *
 * Usage:
 *   val result = Py4JPythonBridge.call("math", "sqrt", List(25.0))
 */
object Py4JPythonBridge {

  /** Whether the bridge is running (always false for now - using subprocess) */
  def running: Boolean = false

  /** Whether to log calls */
  def logCalls: Boolean = K2Z3.debug

  /**
   * Check if Py4J is available
   */
  def isPy4JAvailable: Boolean = {
    try {
      Class.forName("py4j.GatewayServer")
      true
    } catch {
      case _: ClassNotFoundException => false
    }
  }

  /**
   * Start the Python bridge (no-op for now, uses subprocess)
   */
  def start(pythonPath: String = "python3",
            port: Int = 25333,
            debug: Boolean = false): Boolean = {
    if (logCalls) {
      println("[Py4JPythonBridge] Using subprocess mode (Py4J not enabled)")
    }
    false // Return false to indicate not using Py4J
  }

  /**
   * Stop the Python bridge (no-op)
   */
  def stop(): Unit = {
    // Nothing to stop in subprocess mode
  }

  /**
   * Call a Python function - delegates to subprocess-based PythonExternalFunctions
   */
  def call(moduleName: String, funcName: String, args: List[Any]): Try[Any] = {
    PythonExternalFunctions.evaluatePythonCall(moduleName, funcName, args)
  }

  /**
   * Get status info
   */
  def status: String = "Subprocess mode (Py4J not enabled)"
}

/**
 * Interface that the Python side would implement with Py4J
 */
trait PythonBridgeInterface {
  def call(moduleName: String, funcName: String, args: Array[Any]): Any
  def isAvailable(moduleName: String): Boolean
  def ping(): String
}
