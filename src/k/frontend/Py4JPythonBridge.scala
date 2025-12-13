package k.frontend

import scala.util.{Try, Success, Failure}
import scala.collection.mutable.{Map => MMap}
import scala.sys.process._
import java.io.File
import java.net.Socket

/**
 * Py4J-based Python bridge for K external functions.
 *
 * This provides a more efficient alternative to subprocess-based Python calls,
 * especially useful for:
 * - CEGAR refinement with many Python calls
 * - Debugging Python code with debugpy
 * - Keeping Python state across calls
 *
 * Usage:
 *   // Start the bridge
 *   Py4JPythonBridge.start()
 *
 *   // Call Python functions
 *   val result = Py4JPythonBridge.call("math", "sqrt", List(25.0))
 *
 *   // Stop when done
 *   Py4JPythonBridge.stop()
 */
object Py4JPythonBridge {

  /** Default Py4J gateway port */
  val DEFAULT_PORT = 25333

  /** Whether the bridge is currently running */
  private var isRunning: Boolean = false

  /** The Python gateway process */
  private var pythonProcess: Option[Process] = None

  /** Py4J gateway client (lazy initialization) */
  private var gateway: Option[py4j.GatewayServer] = None

  /** Python entry point via Py4J */
  private var pythonBridge: Option[AnyRef] = None

  /** Whether to log calls */
  def logCalls: Boolean = K2Z3.debug

  /** Python path for starting the bridge */
  var pythonPath: String = detectPython()

  /** Port for Py4J communication */
  var port: Int = DEFAULT_PORT

  /** Enable Python debugging */
  var debugEnabled: Boolean = sys.env.get("K_PYTHON_DEBUG").contains("1")

  /** Debug port for debugpy */
  var debugPort: Int = sys.env.get("K_PYTHON_DEBUG_PORT").map(_.toInt).getOrElse(5678)

  /**
   * Detect Python interpreter
   */
  private def detectPython(): String = {
    val candidates = List("python3", "python", "/usr/bin/python3", "/usr/local/bin/python3")
    candidates.find { cmd =>
      try {
        Seq(cmd, "--version").!!
        true
      } catch {
        case _: Exception => false
      }
    }.getOrElse("python3")
  }

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
   * Start the Python bridge
   *
   * @param pythonPath Path to Python interpreter
   * @param port Py4J gateway port
   * @param debug Enable debugpy
   * @return true if started successfully
   */
  def start(pythonPath: String = this.pythonPath,
            port: Int = this.port,
            debug: Boolean = this.debugEnabled): Boolean = {

    if (isRunning) {
      if (logCalls) println("[Py4JPythonBridge] Already running")
      return true
    }

    if (!isPy4JAvailable) {
      println("[Py4JPythonBridge] Py4J not available. Using subprocess fallback.")
      return false
    }

    try {
      // Find the bridge script
      val bridgeScript = findBridgeScript()
      if (bridgeScript.isEmpty) {
        println("[Py4JPythonBridge] Bridge script not found")
        return false
      }

      // Build command
      val cmd = List(
        pythonPath,
        bridgeScript.get.getAbsolutePath,
        s"--port=$port"
      ) ++ (if (debug) List("--debug", s"--debug-port=$debugPort") else Nil)

      if (logCalls) {
        println(s"[Py4JPythonBridge] Starting: ${cmd.mkString(" ")}")
      }

      // Set environment
      val env = sys.env ++ Map(
        "K_PYTHON_DEBUG" -> (if (debug) "1" else "0"),
        "K_PYTHON_DEBUG_PORT" -> debugPort.toString
      )

      // Start Python process
      val pb = new ProcessBuilder(cmd: _*)
      pb.environment().putAll(scala.jdk.CollectionConverters.MapHasAsJava(env).asJava)
      pb.inheritIO() // Show Python output
      pythonProcess = Some(pb.start())

      // Wait for gateway to be ready
      var attempts = 0
      val maxAttempts = 30
      while (attempts < maxAttempts && !isPortOpen(port)) {
        Thread.sleep(200)
        attempts += 1
      }

      if (!isPortOpen(port)) {
        println(s"[Py4JPythonBridge] Timeout waiting for gateway on port $port")
        stop()
        return false
      }

      // Connect to Python gateway
      val gatewayClient = new py4j.GatewayServer.GatewayServerBuilder()
        .javaPort(port)
        .build()

      gateway = Some(gatewayClient)

      // Get Python entry point
      // Note: This requires the Python side to have registered an entry point
      // For now, we'll use reflection to call Python methods

      isRunning = true
      this.port = port
      this.pythonPath = pythonPath

      if (logCalls) {
        println(s"[Py4JPythonBridge] Started on port $port")
        if (debug) {
          println(s"[Py4JPythonBridge] Python debugger available on port $debugPort")
        }
      }

      true
    } catch {
      case e: Exception =>
        println(s"[Py4JPythonBridge] Failed to start: ${e.getMessage}")
        stop()
        false
    }
  }

  /**
   * Stop the Python bridge
   */
  def stop(): Unit = {
    pythonProcess.foreach { p =>
      p.destroy()
      p.waitFor()
    }
    pythonProcess = None
    gateway.foreach(_.shutdown())
    gateway = None
    pythonBridge = None
    isRunning = false
    if (logCalls) println("[Py4JPythonBridge] Stopped")
  }

  /**
   * Call a Python function
   *
   * @param moduleName Python module (e.g., "math", "numpy")
   * @param funcName Function name (e.g., "sqrt", "linalg.det")
   * @param args Arguments to pass
   * @return The function result
   */
  def call(moduleName: String, funcName: String, args: List[Any]): Try[Any] = {
    if (!isRunning) {
      // Try to start if not running
      if (!start()) {
        // Fall back to subprocess
        return PythonExternalFunctions.evaluatePythonCall(moduleName, funcName, args)
      }
    }

    Try {
      // Use Py4J to call Python
      // This requires proper Py4J setup with entry point
      // For now, delegate to subprocess as a working implementation

      // TODO: Implement direct Py4J call when gateway is properly connected
      // gateway.foreach { g =>
      //   val python = g.getPythonServerEntryPoint(classOf[PythonBridgeInterface])
      //   python.call(moduleName, funcName, args.toArray)
      // }

      // Fallback to subprocess for now
      PythonExternalFunctions.evaluatePythonCall(moduleName, funcName, args) match {
        case Success(v) => v
        case Failure(e) => throw e
      }
    }
  }

  /**
   * Check if a port is open
   */
  private def isPortOpen(port: Int): Boolean = {
    try {
      val socket = new Socket("localhost", port)
      socket.close()
      true
    } catch {
      case _: Exception => false
    }
  }

  /**
   * Find the Python bridge script
   */
  private def findBridgeScript(): Option[File] = {
    val candidates = List(
      // In source tree
      new File(sys.props.getOrElse("user.dir", "."), "src/python/k_python_bridge.py"),
      // Installed location
      new File(sys.props.getOrElse("user.dir", "."), "lib/k_python_bridge.py"),
      // Development location
      new File("src/python/k_python_bridge.py")
    )
    candidates.find(_.exists())
  }

  /**
   * Check if the bridge is running
   */
  def running: Boolean = isRunning

  /**
   * Get status info
   */
  def status: String = {
    if (isRunning) {
      s"Running on port $port" + (if (debugEnabled) s", debug on $debugPort" else "")
    } else {
      "Not running"
    }
  }
}

/**
 * Interface that the Python side implements
 */
trait PythonBridgeInterface {
  def call(moduleName: String, funcName: String, args: Array[Any]): Any
  def isAvailable(moduleName: String): Boolean
  def ping(): String
}

