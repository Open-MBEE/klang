# K IDE External Function Debugging

## Overview

K is a constraint-based language that can import and call functions from Java and Python. The K IDE provides integrated debugging support that allows you to:

1. **See external function calls** in your K constraints
2. **Navigate to source** files of imported Java/Python modules
3. **Step into external debuggers** to debug Java/Python code alongside K constraint solving

## Current Implementation Status

✅ **Working Features:**
- Python external function imports (`import python math`)
- Java external function imports (`import java.lang.Math`)
- Mixed Java + Python in same K file
- VS Code debug commands for Java/Python
- debugpy integration for Python debugging
- Unified Debug Panel with external function detection

## How It Works

### Import Statements

K can import external functions using:

```k
-- Import a Java class
import java.lang.Math

-- Import a Python module  
import python math

-- Use both in the same file!
class MixedExample {
  javaResult : Real
  pythonResult : Real
  
  req javaResult = Math.sqrt(16.0)    -- Java
  req pythonResult = math.sqrt(16.0)  -- Python
  req javaResult = pythonResult       -- They must match!
}
```

### Detection and Display

When you open the **Unified Debug Panel** (`K Debug: Open Debug Panel`), the IDE:

1. Parses your K file to find `import java` and `import python` statements
2. Scans constraints for calls to imported functions
3. Displays badges on constraints that call external functions
4. Shows an "External Imports" section listing all imported modules with source availability status

### Stepping Into External Code

When a constraint contains an external function call, you'll see:
- A badge with ☕ (Java) or 🐍 (Python) icon
- **⏎ (Step Into)**: Launches the Java/Python debugger and sets a breakpoint
- **→ (Go To)**: Navigates to the source file without starting a debugger

## Setting Up External Debugging

### Java Debugging

To step into Java code:

1. Install the **Debugger for Java** extension (`vscjava.vscode-java-debug`)
2. Run your K application with JPDA debugging enabled:
   ```bash
   java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 ...
   ```
3. When you click "Step Into" on a Java function, VS Code will:
   - Open the Java source file
   - Set a breakpoint at the method
   - Attempt to attach to the Java debugger on port 5005

### Python Debugging

To step into Python code:

1. Install the **Python** extension (`ms-python.python`)
2. Run your K application with debugpy:
   ```bash
   python -m debugpy --listen 5678 --wait-for-client your_script.py
   ```
3. When you click "Step Into" on a Python function, VS Code will:
   - Open the Python source file
   - Set a breakpoint at the function
   - Attempt to attach to debugpy on port 5678

## Source File Discovery

The IDE searches for source files in these locations:

**Java:**
- `src/main/java/<package>/<Class>.java`
- `src/<package>/<Class>.java`
- `<package>/<Class>.java`

**Python:**
- `<module>.py`
- `src/<module>.py`

If a source file is found, you'll see ✓ next to the import. If not found, you'll see ?.

## Multiple Debug Sessions

Like IntelliJ, the Unified Debug Panel supports multiple simultaneous sessions:
- Each K file you debug gets its own tab
- Sessions show SAT/UNSAT status with colored indicators
- You can switch between sessions while keeping their state

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Unified Debug Panel                          │
├─────────────────────────────────────────────────────────────────┤
│  [Shapes.k ●] [Geometry.k ○]                     <- Session tabs│
├─────────────────────────────────────────────────────────────────┤
│  Status: SAT                                                    │
│  ◀ Prev │ Next ▶ │ ▶▶ Run All │ ↻ Refresh                       │
├─────────────────────────────────────────────────────────────────┤
│  EXTERNAL IMPORTS                                               │
│  [JAVA] MyCalculator ✓  [PYTHON] utils ✓                        │
├─────────────────────────────────────────────────────────────────┤
│  CONSTRAINTS                                                    │
│  ☑ ✓ 1  Shape.req: sides > 0                                   │
│  ☑ ✓ 2  Triangle.req: sides = 3                                │
│  ☑ ▶ 3  Triangle.area: base * height / 2                       │
│         [☕ MyCalculator.compute ⏎ →]  <- External call         │
├─────────────────────────────────────────────────────────────────┤
│  SOLUTION OBJECTS                                               │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Triangle                                                    ││
│  │   sides: 3                                                  ││
│  │   base: 10                                                  ││
│  │   height: 5                                                 ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

## Commands

| Command | Description |
|---------|-------------|
| `K Debug: Open Debug Panel` | Opens the unified debug panel |
| `K Debug: Step Next (Unified)` | Step to next constraint |
| `K Debug: Step Prev (Unified)` | Step to previous constraint |
| `K Debug: Run All (Unified)` | Run all constraints |

## Future Enhancements

- **Breakpoints in external code**: Automatically trigger K debugger to continue when external breakpoint is hit
- **Variable inspection**: Show K variable values alongside external debugger variables
- **Call stack integration**: Unified call stack showing K constraints and external stack frames

