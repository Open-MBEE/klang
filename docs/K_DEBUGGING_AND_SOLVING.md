# K Debugging and Solving Experience

## Overview

This document describes the vision and implementation plan for K's unique debugging and solving experience. Unlike traditional imperative debugging, K debugging focuses on:

1. **Constraint-level stepping** - Watch constraints being added to the solver
2. **Value range tracking** - See how feasible ranges narrow
3. **CEGAR loop visualization** - Step through refinement iterations
4. **External code breakpoints** - Debug Java/Python called by K
5. **Auto-solve mode** - Live solving with debounce
6. **Solution visualization** - Graph-based object diagrams

---

## 1. Auto-Solve Mode

### Concept
As the user edits a K file, automatically re-solve with debounce (e.g., 500ms after last keystroke). Show live status:

```
┌─────────────────────────────────────────────────┐
│ 🔄 Solving...  │  ✅ SAT (3 solutions)  │  ❌ UNSAT │
└─────────────────────────────────────────────────┘
```

### Implementation
- **Debounce**: Wait 500ms after last edit before solving
- **Cancellation**: Cancel in-progress solve if user edits again
- **Status bar**: Show solving status in IDE status bar
- **Incremental**: Use Z3's incremental solving when possible
- **Timeout**: Quick timeout (1-2s) for live mode, longer for explicit solve

### Settings
```json
{
  "k.autoSolve.enabled": true,
  "k.autoSolve.debounceMs": 500,
  "k.autoSolve.timeoutMs": 2000
}
```

---

## 2. Solution Visualization

### Current: Reference Table (Hard to Read)
```
+--------+-----+------------------------------------------------------------+
|Variable|Ref  |Value                                                       |
+--------+-----+------------------------------------------------------------+
|        |Ref 8|Obtuse(sides::3, a:: Ref 5, b:: Ref 4, c:: Ref 6, ...)      |
|        |Ref 6|TAngle(value::60)                                           |
```

### Better: Solution as Constraints
Instead of a table, express the solution as K constraints that would reproduce it:

```k
-- Solution #1
inst1 : Triangle
inst1.sides = 3
inst1.a = angle1
inst1.b = angle2
inst1.c = angle3
inst1.base = 10
inst1.height = 15

angle1 : TAngle
angle1.value = 60

angle2 : TAngle  
angle2.value = 60

angle3 : TAngle
angle3.value = 60
```

This is:
- Readable as K code
- Copy-pasteable as test cases
- Shows object identity clearly

### Best: UML-like Object Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        SOLUTION GRAPH                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│    ┌─────────────────────┐                                          │
│    │ inst1 : Triangle    │                                          │
│    ├─────────────────────┤                                          │
│    │ sides = 3           │                                          │
│    │ base = 10           │                                          │
│    │ height = 15         │                                          │
│    │ area() = 75.0       │                                          │
│    └────────┬────────────┘                                          │
│             │                                                       │
│    ┌────────┼────────┬────────┐                                     │
│    │ a      │ b      │ c      │  (property references)              │
│    ▼        ▼        ▼        │                                     │
│  ┌──────┐ ┌──────┐ ┌──────┐                                         │
│  │angle1│ │angle2│ │angle3│                                         │
│  │:TAngle│:TAngle│:TAngle│                                         │
│  ├──────┤ ├──────┤ ├──────┤                                         │
│  │val=60│ │val=60│ │val=60│                                         │
│  └──────┘ └──────┘ └──────┘                                         │
│                                                                     │
│  Constraints satisfied: ✓ Angles (60+60+60=180)                     │
│                         ✓ sides = 3                                 │
│                         ✓ All angles < 180 (TAngle constraint)      │
│                                                                     │
│  [◀ Prev Solution] [Next Solution ▶] [Export as K] [Export as JSON] │
└─────────────────────────────────────────────────────────────────────┘
```

### Interactive Features
- **Click object**: Expand/collapse properties
- **Click reference edge**: Highlight the constraint that created it
- **Hover property**: Show which constraints involve it
- **Filter**: Show only objects of certain type
- **Layout**: Auto-layout or manual drag

---

## 3. UNSAT Analysis & Fix Suggestions

### Current Behavior
K prints the unsat core but without actionable suggestions.

### Enhanced UNSAT View

```
┌─────────────────────────────────────────────────────────────────────┐
│                        UNSAT ANALYSIS                               │
├─────────────────────────────────────────────────────────────────────┤
│  Status: UNSATISFIABLE                                              │
│                                                                     │
│  ❌ Minimal Unsat Core (3 constraints):                             │
│                                                                     │
│  1. req MaxWeight: totalWeight <= 100        [Shapes.k:45] ⚡       │
│  2. req MinItems: items.size() >= 5          [Shapes.k:52] ⚡       │
│  3. req ItemWeight: forall i:items .         [Shapes.k:58] ⚡       │
│                      i.weight >= 25                                 │
│                                                                     │
│  💡 Explanation:                                                    │
│     5 items × 25 min weight = 125, but max allowed is 100           │
│                                                                     │
│  🔧 Suggested Fixes:                                                │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │ Option 1: Increase MaxWeight                                │    │
│  │   Change: totalWeight <= 100  →  totalWeight <= 125         │    │
│  │   [Apply Fix]                                               │    │
│  ├─────────────────────────────────────────────────────────────┤    │
│  │ Option 2: Reduce MinItems                                   │    │
│  │   Change: items.size() >= 5  →  items.size() >= 4           │    │
│  │   [Apply Fix]                                               │    │
│  ├─────────────────────────────────────────────────────────────┤    │
│  │ Option 3: Allow lighter items                               │    │
│  │   Change: i.weight >= 25  →  i.weight >= 20                 │    │
│  │   [Apply Fix]                                               │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  [🎯 Highlight in Editor] [📋 Copy Report] [🔄 Re-solve]            │
└─────────────────────────────────────────────────────────────────────┘
```

### Editor Integration
Show fix suggestions as "Quick Fix" actions (lightbulb in VS Code, Alt+Enter in IntelliJ):

```k
req MaxWeight: totalWeight <= 100   // ⚠️ Conflicts with MinItems, ItemWeight
                                    // 💡 Quick Fix: Change to <= 125
```

---

## 4. Constraint-Level Debugging

### Stepping Through Constraints

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CONSTRAINT DEBUGGER                              │
├─────────────────────────────────────────────────────────────────────┤
│  Step 4 of 7                              [⏮ First] [◀ Prev] [Next ▶] [⏭ Last] │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  📍 Current: req performance <= cost / 10        [line 23]         │
│                                                                     │
│  Variable Ranges:                                                   │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │ cost         [0 ════════════════════════════════════ 1000]  │    │
│  │              After: [0 ════════════════════════════ 1000]   │    │
│  ├─────────────────────────────────────────────────────────────┤    │
│  │ performance  [0 ════════════════════════ 100]               │    │
│  │              After: [0 ════════════ 100] (linked to cost)   │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  Status: SAT ✓                                                      │
│  Sample: cost=500, performance=50                                   │
│                                                                     │
│  Constraint History:                                                │
│  ✓ Step 1: req cost >= 0                                            │
│  ✓ Step 2: req cost <= 1000                                         │
│  ✓ Step 3: req performance >= 0                                     │
│  ▶ Step 4: req performance <= cost / 10  ◄── YOU ARE HERE          │
│    Step 5: req performance >= 50                                    │
│    Step 6: minimize cost                                            │
│    Step 7: (solve)                                                  │
│                                                                     │
│  [📊 Constraint Graph] [🔍 Why This Range?] [⏯ Auto-Step]          │
└─────────────────────────────────────────────────────────────────────┘
```

### Breakpoints on Constraints
In the editor, click the gutter to set a "constraint breakpoint":

```k
class Spacecraft {
  cost : Int
  performance : Int
  
  req cost >= 0
  req cost <= 1000
● req performance <= cost / 10   // ← Breakpoint (red dot)
  req performance >= 50
  minimize cost
}
```

When solving, pause at this constraint and show the debugger.

---

## 5. CEGAR Loop Debugging

When K calls external functions (Java/Python), step through refinement:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CEGAR DEBUGGER                                   │
├─────────────────────────────────────────────────────────────────────┤
│  Iteration 3 of ?                         [◀ Prev] [Next ▶] [▶▶ Run]│
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Opaque Function: java.lang.Math.sqrt                               │
│  Constraint: req y = Math.sqrt(x)                                   │
│                                                                     │
│  Z3 Proposed:                                                       │
│    x = 16                                                           │
│    y = 5.0  (Z3's guess for uninterpreted function)                 │
│                                                                     │
│  Actual Evaluation:                                                 │
│    Math.sqrt(16) = 4.0                                              │
│                                                                     │
│  ❌ Mismatch! Adding refinement constraint: sqrt(16) = 4.0          │
│                                                                     │
│  Learned Refinements:                                               │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │ Iter 1: sqrt(4) = 2.0                                       │    │
│  │ Iter 2: sqrt(9) = 3.0                                       │    │
│  │ Iter 3: sqrt(16) = 4.0  ◄── NEW                             │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  [🔍 View Java Source] [🐛 Debug in Java] [📊 Call Graph]           │
└─────────────────────────────────────────────────────────────────────┘
```

### Integration with Java/Python Debuggers
- "Debug in Java" opens IntelliJ/VS Code Java debugger at the method
- Set breakpoints in Java code, see K context when hit
- Pass K variable bindings to Java debug session

---

## 6. Unified Solver Loop Integration

Reference: `docs/UNIFIED_SOLVING_LOOP.md`

### Progress Reporting

```
┌─────────────────────────────────────────────────────────────────────┐
│                    SOLVING PROGRESS                                 │
├─────────────────────────────────────────────────────────────────────┤
│  Elapsed: 00:02:34                        [⏸ Pause] [⏹ Stop]        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Phase: CEGAR Refinement (iteration 47)                             │
│  ████████████████████░░░░░░░░░░ 68% constraints satisfied           │
│                                                                     │
│  Current Best (partial solution):                                   │
│  ├── spacecraft.mass = 1200 kg                                      │
│  ├── spacecraft.power = 850 W                                       │
│  ├── orbit.altitude = 400 km                                        │
│  └── 12 objects instantiated                                        │
│                                                                     │
│  Unresolved:                                                        │
│  ├── thermal.maxTemp (range: [250, 400] K)                          │
│  └── comm.bandwidth (waiting for external call)                     │
│                                                                     │
│  [💾 Save Partial] [🔄 Seed & Continue] [📊 View Progress Graph]    │
└─────────────────────────────────────────────────────────────────────┘
```

### Interrupt and Resume
1. User clicks "Pause" during long solve
2. Current partial solution is captured
3. User can:
   - **Save partial**: Export current best as soft constraints
   - **Seed & Continue**: Add partial solution as hints, resume solving
   - **Modify & Retry**: Edit model, use partial as starting point

### Timeout Handling
```k
@timeout(30000)  // 30 second timeout
@bestEffort      // Return partial solution if timeout
class Mission {
  // ...
}
```

When timeout occurs with `@bestEffort`:
- Return whatever partial solution exists
- Mark unsolved variables with their feasible ranges
- Offer to continue solving from this point

---

## 7. Implementation Status

### Implemented (VS Code)

#### Auto-Solve Mode ✅
- `src/autoSolve.ts` - Status bar indicator, debounced solving
- Settings: `k.autoSolve.enabled`, `k.autoSolve.debounceMs`, `k.autoSolve.timeoutMs`
- Commands: `k.toggleAutoSolve`, `k.solveNow`, `k.showSolution`

#### Inline Value Decorations ✅
- `src/inlineDecorations.ts` - Shows values in editor like Java debugger
- Property values shown next to declarations
- Constraint satisfaction indicators (✓/✗)
- Variable range display

#### Progress Reporting ✅
- `src/solverManager.ts` - Long-running solve management
- VS Code progress notification with cancellation
- `KProgressPanel` - Detailed progress webview
- Phase tracking: parsing, typechecking, translating, solving, CEGAR
- Commands: `k.solveWithProgress`, `k.showProgress`

#### Constraint Debugger ✅
- `src/constraintDebugger.ts` - Step through constraints
- Visual indicator of current constraint
- Variable range narrowing visualization
- Constraint stepping UI with prev/next/run-all
- Commands: `k.startConstraintDebug`, `k.debugStepNext`, `k.debugStepPrev`, `k.debugRunToEnd`, `k.debugStop`

### Remaining Work

#### Phase 2: Enhanced Visualization
- [ ] Solution graph view with D3.js (UML-like diagrams)
- [ ] Constraint graph visualization
- [ ] Interactive object exploration

#### Phase 3: UNSAT Experience
- [ ] Parse and display unsat core with highlighting
- [ ] Generate fix suggestions
- [ ] Quick fix actions in editor

#### Phase 5: CEGAR Debugging
- [ ] CEGAR iteration view with call trace
- [ ] External function result display
- [ ] Java/Python debugger integration

#### Phase 6: Unified Solver (K-side changes needed)
- [ ] Structured JSON output from K compiler
- [ ] Incremental solving support
- [ ] Partial solution extraction on timeout

---

## 8. Implementation Roadmap

### Phase 1: Foundation (Current Sprint)
- [ ] Auto-solve mode with debounce (VS Code first)
- [ ] Enhanced solution output (K constraint format)
- [ ] Status bar integration (solving indicator)

### Phase 2: Visualization
- [ ] Solution graph view (webview panel)
- [ ] Object diagram rendering (D3.js or similar)
- [ ] Interactive exploration (click to expand)

### Phase 3: UNSAT Experience
- [ ] Parse and display unsat core
- [ ] Generate fix suggestions
- [ ] Quick fix actions in editor

### Phase 4: Constraint Debugging
- [ ] Constraint stepping UI
- [ ] Variable range visualization
- [ ] Constraint breakpoints

### Phase 5: CEGAR Debugging
- [ ] CEGAR iteration view
- [ ] External function call tracing
- [ ] Java/Python debugger integration

### Phase 6: Unified Solver Integration
- [ ] Progress reporting UI
- [ ] Interrupt/resume capability
- [ ] Partial solution handling

---

## 8. Technical Architecture

### VS Code Extension

```
┌─────────────────────────────────────────────────────────────────┐
│                    K Language Extension                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ Auto-Solve  │  │ Solution    │  │ Constraint Debugger     │  │
│  │ Controller  │  │ Visualizer  │  │ (Webview)               │  │
│  │             │  │ (Webview)   │  │                         │  │
│  │ - debounce  │  │ - D3.js     │  │ - stepping UI           │  │
│  │ - cancel    │  │ - objects   │  │ - range bars            │  │
│  │ - status    │  │ - edges     │  │ - breakpoints           │  │
│  └──────┬──────┘  └──────┬──────┘  └───────────┬─────────────┘  │
│         │                │                      │               │
│         └────────────────┼──────────────────────┘               │
│                          │                                      │
│                          ▼                                      │
│              ┌───────────────────────┐                          │
│              │   K Runner Service    │                          │
│              │                       │                          │
│              │ - spawn k process     │                          │
│              │ - parse output        │                          │
│              │ - stream progress     │                          │
│              │ - interrupt/resume    │                          │
│              └───────────┬───────────┘                          │
│                          │                                      │
└──────────────────────────┼──────────────────────────────────────┘
                           │
                           ▼
              ┌───────────────────────┐
              │   K Compiler (JVM)    │
              │                       │
              │ - Frontend.scala      │
              │ - K2Z3.scala          │
              │ - UnifiedSolver       │
              └───────────────────────┘
```

### Communication Protocol
For rich debugging, K compiler needs to emit structured output:

```json
{
  "type": "solving_progress",
  "iteration": 47,
  "phase": "cegar",
  "satisfiedConstraints": 68,
  "totalConstraints": 100,
  "partialSolution": {
    "spacecraft.mass": 1200,
    "spacecraft.power": 850
  },
  "unresolvedRanges": {
    "thermal.maxTemp": [250, 400]
  }
}
```

---

## 9. Getting Started

Let's begin with:

1. **Auto-solve mode** - Immediate value, builds on existing runner
2. **Solution as K constraints** - Better output format
3. **Status bar indicator** - User feedback during solving

These provide the foundation for more advanced debugging features.

