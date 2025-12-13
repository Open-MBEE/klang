# Java-Python Bridge Options for K Language

## Overview

K needs to call Python external functions during CEGAR refinement. The current implementation uses subprocess calls, which works but has limitations for debugging integration.

## Options Comparison

### 1. **Py4J** (Current Recommendation)
- **Website**: https://www.py4j.org/
- **Architecture**: Socket-based communication between JVM and Python
- **How it works**: Python runs as a separate process with a gateway server; Java connects via TCP

**Pros:**
- Clean separation - Python runs in its own process
- Native Python debugging works (can attach debugpy to Python process)
- Mature and well-documented
- Used by Apache Spark (PySpark)
- Supports callbacks from Python to Java
- No native code dependencies

**Cons:**
- Slightly higher latency than in-process solutions
- Requires starting a Python gateway server

**Debugging Integration:**
```python
# Python side - can use debugpy
import debugpy
debugpy.listen(5678)
from py4j.java_gateway import JavaGateway, CallbackServerParameters

gateway = JavaGateway(callback_server_parameters=CallbackServerParameters())
```

### 2. **JPype**
- **Website**: https://jpype.readthedocs.io/
- **Architecture**: In-process - embeds Python in JVM via JNI

**Pros:**
- Very fast - no IPC overhead
- Direct memory sharing
- Seamless type conversion

**Cons:**
- Native code dependencies (must match architecture)
- Can't easily debug Python - it's running inside JVM
- GIL issues with threading
- More complex setup on different platforms
- Crashes in native code can bring down JVM

**Debugging Integration:** Limited - Python runs inside JVM

### 3. **jpy**
- **Website**: https://github.com/jpy-consortium/jpy
- **Architecture**: In-process via JNI (similar to JPype)

**Pros:**
- Bidirectional - can call Java from Python AND Python from Java
- Good NumPy integration
- Used by Deephaven

**Cons:**
- Same native code issues as JPype
- Debugging Python is difficult
- Less mature than Py4J

### 4. **GraalPython (GraalVM)**
- **Architecture**: Python implemented on GraalVM

**Pros:**
- True polyglot - seamless interop
- Can debug both languages together
- High performance with JIT

**Cons:**
- Requires GraalVM (not standard JVM)
- Python compatibility isn't 100%
- Larger runtime footprint

### 5. **Current: Subprocess**
- **Architecture**: Spawn Python process for each call

**Pros:**
- Simple, no dependencies
- Full Python compatibility
- Easy to debug Python side

**Cons:**
- High overhead per call
- No persistent state
- Complex data serialization

## Recommendation for K: **Py4J**

### Why Py4J is Best for K:

1. **Debugging Support**: Python runs in its own process, so we can attach debugpy and step through Python code from VS Code while K is solving.

2. **Clean Architecture**: Keeps Python and Java/Scala separate, matching K's philosophy of integrating with external languages.

3. **No Native Dependencies**: Works on any platform without architecture-specific binaries (important given K's Z3 architecture issues).

4. **Battle-Tested**: Used by PySpark, so it's proven at scale.

5. **CEGAR Compatibility**: We can keep a Python gateway running across multiple CEGAR iterations, avoiding subprocess startup overhead.

## Implementation Plan

### Phase 1: Py4J Integration

```scala
// In PythonExternalFunctions.scala
import py4j.GatewayServer

object PythonBridge {
  private var gateway: GatewayServer = _
  private var pythonProcess: Process = _
  
  def start(): Unit = {
    // Start Python gateway process
    val pb = new ProcessBuilder("python3", "-m", "k_python_bridge")
    pythonProcess = pb.start()
    
    // Connect from Java side
    gateway = new GatewayServer.GatewayServerBuilder()
      .javaPort(25333)
      .build()
  }
  
  def call(module: String, func: String, args: Seq[Any]): Any = {
    val pythonGateway = gateway.getPythonServerEntryPoint(classOf[KPythonInterface])
    pythonGateway.call(module, func, args.toArray)
  }
}
```

```python
# k_python_bridge.py
from py4j.java_gateway import JavaGateway, CallbackServerParameters
import importlib

class KPythonInterface:
    def call(self, module_name, func_name, args):
        module = importlib.import_module(module_name)
        func = getattr(module, func_name)
        return func(*args)

if __name__ == "__main__":
    # Enable debugging if requested
    import os
    if os.environ.get("K_PYTHON_DEBUG"):
        import debugpy
        debugpy.listen(int(os.environ.get("K_PYTHON_DEBUG_PORT", 5678)))
        print("Python debugger listening on port", os.environ.get("K_PYTHON_DEBUG_PORT", 5678))
    
    from py4j.java_gateway import JavaGateway, CallbackServerParameters
    gateway = JavaGateway(
        callback_server_parameters=CallbackServerParameters(),
        python_server_entry_point=KPythonInterface()
    )
```

### Phase 2: IDE Integration

The VS Code extension can:
1. Detect `import python` statements
2. Start K with `K_PYTHON_DEBUG=1`
3. Attach debugpy to the Python gateway process
4. Set breakpoints in Python source files
5. Step through Python code while K waits

### Phase 3: Unified Debugging

With Py4J, we can have:
- Java debugger on port 5005 (K/Scala code)
- Python debugger on port 5678 (external functions)
- Both attached simultaneously in VS Code

## Dependencies to Add

### Maven (pom.xml)
```xml
<dependency>
    <groupId>net.sf.py4j</groupId>
    <artifactId>py4j</artifactId>
    <version>0.10.9.7</version>
</dependency>
```

### Python
```bash
pip install py4j debugpy
```

## Migration Path

1. Keep subprocess as fallback
2. Add Py4J as optional (check if py4j available)
3. Use Py4J when debugging enabled
4. Eventually make Py4J default

## Alternative Consideration: Hybrid Approach

For maximum flexibility:
- **Simple calls**: Use subprocess (no setup needed)
- **Debugging**: Use Py4J gateway with debugpy
- **High-performance**: Consider JPype for compute-intensive models

The IDE could offer a setting to choose the bridge method based on needs.

