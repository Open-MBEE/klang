# K Language IDE Demo - Text Overlays for Video

This file contains text overlays and captions for creating a demo video
of the K Language IDE features in VS Code.

## Video Timeline & Captions

### 00:00 - 00:10 | Title Screen
```
K LANGUAGE IDE
Constraint-Based Programming Made Easy
VS Code Extension Demo
```

### 00:10 - 00:40 | Syntax Highlighting
```
SYNTAX HIGHLIGHTING

• Keywords: class, req, fun, extends
• Types: Int, Real, Bool, String
• Comments: -- and /* */
• Literals and operators
```

### 00:40 - 01:10 | Navigation
```
DOCUMENT SYMBOLS (Cmd+Shift+O)

• Quick navigation to classes
• Outline view support
• Jump to any definition
```

### 01:10 - 01:40 | Go to Definition
```
GO TO DEFINITION (Cmd+Click)

• Navigate from type references
• Works for all class names
• Follow inheritance chain
```

### 01:40 - 02:00 | Hover
```
HOVER DOCUMENTATION

• Class signatures with properties
• Type information
• Comments included in docs
```

### 02:00 - 02:40 | Running
```
RUN K FILE

• Right-click → Run K File
• Command Palette: "K: Run File"
• Output shows solver results
```

### 02:40 - 03:30 | Debug Panel
```
UNIFIED DEBUG PANEL

• Multiple session tabs
• SAT/UNSAT status indicator
• Constraint list with stepping
• Solution object visualization
```

### 03:30 - 04:00 | Stepping
```
CONSTRAINT STEPPING

◀ Prev | Next ▶ | ▶▶ Run All

• Add constraints incrementally
• Progress bar shows position
• Editor highlights current constraint
```

### 04:00 - 04:30 | Breakpoints
```
CONSTRAINT BREAKPOINTS

● Click red dot to toggle
• Visual indicator on constraint
• Pause solving at breakpoint
```

### 04:30 - 05:00 | Solution Objects
```
SOLUTION VISUALIZATION

┌─────────────────────────┐
│ Triangle                │
│   sides: 3              │
│   a: TAngle(value: 60)  │
│   b: TAngle(value: 60)  │
│   c: TAngle(value: 60)  │
└─────────────────────────┘
```

### 05:00 - 05:40 | External Functions
```
EXTERNAL JAVA/PYTHON FUNCTIONS

import java.lang.Math
import python math

☕ Java    🐍 Python

req result = Math.sqrt(input)
req result = math.sqrt(input)
```

### 05:40 - 06:10 | CEGAR
```
CEGAR REFINEMENT LOOP

🔄 Started → 💡 Candidate → ❌ Counterexample → 🔧 Refined → ✅ Verified

Counter-Example Guided Abstraction Refinement
for external function validation
```

### 06:10 - 06:40 | Auto-Solve
```
AUTO-SOLVE MODE

☑ Auto-solve

• Automatically re-solve on edit
• Real-time constraint validation
• Instant feedback loop
```

### 06:40 - 07:10 | Inline Values
```
INLINE VALUE DECORATIONS

sides : Int     = 3
base : Int      = 10  
height : Int    = 5
area : Real     = 25.0

✓ Satisfied constraints
✗ Unsatisfied (highlighted)
```

### 07:10 - 07:40 | External Debugging
```
EXTERNAL FUNCTION DEBUGGING

⏎ Step into Java/Python debugger
→ Navigate to source file

Unified debugging across languages!
```

### 07:40 - 08:00 | Closing
```
K LANGUAGE IDE

✓ Syntax Highlighting
✓ Navigation & Symbols
✓ Constraint Debugging
✓ External Functions (Java + Python)
✓ CEGAR Visualization
✓ Auto-Solve Mode

Available for VS Code and JetBrains
```

---

## Suggested Audio Script (if using narration)

### Introduction (0:00)
"Welcome to the K Language IDE demo. K is a constraint-based specification language that compiles to the Z3 SMT solver. Let me show you the powerful IDE features we've built for VS Code."

### Syntax Highlighting (0:10)
"First, notice the rich syntax highlighting. Keywords like class, req, and fun are highlighted. Types have distinct colors. Comments are supported with double-dash or C-style syntax."

### Navigation (0:40)
"Press Command-Shift-O to open the document symbols panel. You can quickly jump to any class definition in your file."

### Go to Definition (1:10)
"Command-click on any type reference to go to its definition. Here I'm clicking on TAngle to see where it's defined. This also works with the extends keyword to follow inheritance."

### Running (2:00)
"To run a K file, right-click and select Run K File, or use the Command Palette. The output panel shows the Z3 solver finding a solution that satisfies all your constraints."

### Debug Panel (2:40)
"The Unified Debug Panel is where the magic happens. Like IntelliJ, you can have multiple debug sessions. The status shows whether your constraints are satisfiable."

### Stepping (3:30)
"Step through constraints one at a time. Each step adds constraints incrementally and re-solves. Watch the progress bar and notice how constraints are highlighted in the editor."

### Breakpoints (4:00)
"Click the red dot to set a breakpoint on any constraint. When stepping, the solver will pause here. This is great for understanding which constraint causes issues."

### External Functions (5:00)
"Here's something exciting - K can call external Java AND Python functions! Notice the language badges - coffee cup for Java, snake for Python. Both work in the same file!"

### CEGAR (5:40)
"When external functions are involved, K uses CEGAR - Counter-Example Guided Abstraction Refinement. The debug panel shows each iteration as the solver learns about the external function's behavior."

### Auto-Solve (6:10)
"Enable auto-solve for real-time feedback. As you edit, the solver runs automatically. Great for iterative development - you see immediately if your constraints work."

### Inline Values (6:40)
"Like a Java debugger, values appear inline in the editor. Satisfied constraints get green checkmarks. If unsatisfiable, conflicting constraints are highlighted in red."

### Closing (7:40)
"That's the K Language IDE - making constraint-based programming accessible and debuggable. Available for VS Code and JetBrains IDEs. Thanks for watching!"

---

## Technical Notes for Recording

### Resolution
- Record at 1920x1080 or higher
- VS Code zoom level: 120-140% for visibility

### Font Size
- Editor font: 14-16pt
- Terminal font: 14pt

### VS Code Theme
- Dark theme recommended for contrast
- Or any theme with good syntax highlighting

### Files to Have Open
1. `src/examples/Shapes.k` - Main demo file
2. `src/tests/mixed_external1.k` - External functions demo
3. Debug panel open on the right

### Recording Software
- macOS: QuickTime Player → New Screen Recording
- Windows: OBS Studio or Windows Game Bar
- Include system audio for narration


