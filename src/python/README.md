# K Python Bridge

This package provides Python integration for the K constraint programming language, enabling K models to call Python functions during CEGAR (Counter-Example Guided Abstraction Refinement) solving.

## Installation

```bash
# Basic installation
pip install py4j

# With debugging support
pip install py4j debugpy

# Or install this package
cd klang/src/python
pip install -e .
```

## Usage

### Starting the Bridge

```bash
# Basic mode
python -m k_python_bridge

# With debugging enabled (for VS Code attachment)
python -m k_python_bridge --debug

# Custom ports
python -m k_python_bridge --port 25333 --debug-port 5678
```

### Using in K Files

```k
-- Import Python modules
import python math
import python numpy

class MyModel {
    x : Real
    y : Real
    
    -- Call Python math functions
    req y = math.sqrt(x)
    
    -- Call numpy functions
    req numpy.sin(x) > 0.5
}
```

### Debugging Python Code

1. Start K with Python debugging:
   ```bash
   K_PYTHON_DEBUG=1 ./export/k myfile.k
   ```

2. Or use VS Code command: **"K Debug: Run with Python Debugger"**

3. Attach VS Code debugger to `localhost:5678`

4. Set breakpoints in your Python code

## Architecture

```
┌──────────────┐     Py4J      ┌──────────────────┐
│     K/JVM    │ ◄──────────► │  Python Bridge   │
│  (Scala/Z3)  │   TCP/25333   │  (k_python_bridge)│
└──────────────┘               └──────────────────┘
                                        │
                                        ▼
                               ┌──────────────────┐
                               │  Python Modules  │
                               │ (math, numpy...) │
                               └──────────────────┘
```

## API

### KPythonBridge Class

The main entry point exposed to Java/Scala:

```python
class KPythonBridge:
    def call(module_name: str, func_name: str, args: List[Any]) -> Any
    def is_available(module_name: str) -> bool
    def ping() -> str
```

### Type Conversions

| Python Type | Java/Scala Type |
|-------------|-----------------|
| int         | Long            |
| float       | Double          |
| bool        | Boolean         |
| str         | String          |
| list        | List            |
| dict        | Map             |
| None        | null            |
| numpy.ndarray | List          |

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `K_PYTHON_DEBUG` | Enable debugging (1/0) | 0 |
| `K_PYTHON_DEBUG_PORT` | debugpy port | 5678 |

## Troubleshooting

### "py4j not installed"
```bash
pip install py4j
```

### "debugpy not installed" (for debugging)
```bash
pip install debugpy
```

### Connection refused
- Check that the bridge is running: `python -m k_python_bridge`
- Verify port 25333 is not in use
- Check firewall settings

### Import errors in K
- Ensure the Python module is installed in the same environment
- Check `PYTHONPATH` if using custom modules

