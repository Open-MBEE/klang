# K Language IDE Debugging Features

## Overview

The K Language IDE plugins (VS Code and IntelliJ) provide sophisticated debugging capabilities for K constraint programs, including:

1. **Constraint Debugging** - Step through constraints to see how the solution space narrows
2. **Solution Visualization** - View solved objects and their properties graphically
3. **External Function Debugging** - Step into Java/Python external functions
4. **Auto-Solve Mode** - Automatically re-solve after edits

## VS Code Features

### Auto-Solve Toggle

Enable auto-solve mode to automatically run the K solver after you make edits:
- **Editor Title Bar**: Click the sync icon (⟳) to toggle auto-solve
- **Debug Panel**: Check the "Auto-solve" checkbox in the status bar
- **Keyboard**: `Cmd+Alt+A` (Mac) / `Ctrl+Alt+A` (Windows/Linux)
- **Settings**: `k.autoSolve.enabled` in VS Code settings

### Unified Debug Panel

The unified debug panel combines constraint debugging and solution visualization:

1. **Open Panel**: 
   - Click the debug icon in the editor title bar
   - Run command: "K Debug: Open Debug Panel"
   - Keyboard: `Cmd+Alt+D`

2. **Features**:
   - **Session Tabs**: Multiple debug sessions like IntelliJ
   - **Constraint List**: Toggle constraints on/off, see which are processed
   - **Solution Objects**: View created objects with inline property values
   - **External Functions**: See Java/Python imports and step into them
   - **Auto-Solve Toggle**: Quick checkbox in the panel

### Java External Function Debugging

When your K file imports Java classes:

```k
import java com.example.Calculator

class MyModel {
    req Calculator.compute(x) > 10
}
```

To debug the Java code:

1. **Click "Step Into"** on the external function in the debug panel
2. **K will restart** with the Java debug agent attached (port 5005)
3. **VS Code will prompt** to attach the Java debugger
4. **Breakpoints** are automatically set at the method

**Manual Method**:
```bash
JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005" ./export/k myfile.k
```

Then in VS Code: Run → Add Configuration → Java → Attach to Remote JVM

### Python External Function Debugging

When your K file imports Python modules:

```k
import python my_math_module

class MyModel {
    req my_math_module.validate(x)
}
```

To debug Python code:
1. Ensure `debugpy` is installed: `pip install debugpy`
2. Run K with Python debugging enabled
3. VS Code will attach to the Python debugger

## IntelliJ Features

### Run Configuration

- Right-click a `.k` file → Run
- Use the green gutter icon next to class definitions
- Keyboard: `Cmd+Shift+R`

### Structure View

- `Alt+7` opens the structure view
- Navigate classes, functions, properties
- Click to jump to definition

### Code Completion

- Type `class`, `fun`, `req` for keyword completion
- Type `.` after a class name for member completion
- Built-in types: `Int`, `Real`, `Bool`, `String`

## Configuration

### VS Code Settings

```json
{
    "k.installation.path": "/path/to/klang",
    "k.java.home": "/path/to/java",
    "k.autoSolve.enabled": false,
    "k.autoSolve.debounceMs": 1000,
    "k.autoSolve.timeoutMs": 5000
}
```

### IntelliJ Settings

Settings → Languages & Frameworks → K Language

## Tips

1. **Use Auto-Solve for Small Files**: Great for interactive development
2. **Disable Auto-Solve for Complex Models**: Avoid timeout issues
3. **Set Breakpoints First**: When debugging Java, set breakpoints before stepping in
4. **Check Architecture**: On Apple Silicon, ensure you're using ARM64 Java

## Troubleshooting

### Z3 Library Errors
```bash
./select-z3-architecture.sh
```

### Java Debug Agent Fails
Ensure Java 8+ is installed and JAVA_HOME is set correctly.

### Auto-Solve Timeout
Increase `k.autoSolve.timeoutMs` in settings.

