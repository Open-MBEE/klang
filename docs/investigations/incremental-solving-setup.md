# Incremental Solving Setup for DSN_Pass.k

## Overview
This document provides instructions for setting up a separate Cursor instance on a git worktree to work on incremental solving for the DSN_Pass.k timeout issue.

## Setup Instructions

### 1. Create a Git Worktree

```bash
cd /Users/bclement/git/klang
git worktree add ../klang-incremental-solving -b incremental-solving
cd ../klang-incremental-solving
```

This creates a new branch `incremental-solving` in a separate directory that shares the same git repository.

### 2. Open in New Cursor Instance

1. Open Cursor
2. File → Open Folder
3. Navigate to `/Users/bclement/git/klang-incremental-solving`
4. This gives you a separate workspace to work on incremental solving

### 3. Understanding the Current Architecture

#### Key Files:
- **`src/k/frontend/SolverEnhancements.scala`**: Contains `IncrementalSession` class with `push()`, `pop()`, `check()`, `assert()` methods
- **`src/k/frontend/Frontend.scala`**: Main entry point, calls `K2Z3.solveSMT()` around line 515
- **`src/k/frontend/K2Z3.scala`**: Contains `solveSMT()` method that does the actual solving

#### Current Solving Flow:
1. `Frontend.scala_main()` processes the K file
2. Generates SMT model via `combinedModel.toSMT`
3. Calls `K2Z3.solveSMT(combinedModel, smtModel, true)` 
4. `K2Z3.solveSMT()` uses a regular Z3 `Solver` (not incremental)

### 4. Implementation Strategy

#### Option A: Add Incremental Mode Flag
Add a command-line flag `-incremental` that uses `IncrementalSession` instead of regular solving.

#### Option B: Create Diagnostic Tool
Create a new class `IncrementalDiagnostic.scala` that:
- Takes a K model
- Uses `IncrementalSession` to add constraints incrementally
- Reports which constraint group causes the timeout

#### Recommended: Option B (Diagnostic Tool)

### 5. Implementation Steps

#### Step 1: Create IncrementalDiagnostic.scala

Location: `src/k/frontend/IncrementalDiagnostic.scala`

```scala
package k.frontend

import com.microsoft.z3.{Context, Status}
import k.frontend.SolverEnhancements.{IncrementalSession, SolverConfig, SolverResult}

object IncrementalDiagnostic {
  
  def diagnoseModel(model: Model, constraintGroups: List[(String, Model => String)]): Unit = {
    val ctx = K2Z3.ctx  // Reuse existing Z3 context
    val config = SolverConfig(
      timeout = Some(30000),  // 30 seconds
      produceUnsatCores = true,
      incrementalMode = true,
      verbosity = 1
    )
    
    val session = new IncrementalSession(ctx, config)
    
    // Add base constraints (class invariants, etc.)
    println("[Diagnostic] Adding base constraints...")
    session.push("base")
    // TODO: Add base constraints from model
    
    // Add constraint groups incrementally
    for ((groupName, constraintGenerator) <- constraintGroups) {
      println(s"[Diagnostic] Adding constraint group: $groupName")
      session.push(groupName)
      
      val constraintSMT = constraintGenerator(model)
      // TODO: Parse and add constraints to session
      
      val result = session.check()
      result match {
        case Satisfiable(_) =>
          println(s"  ✓ SAT - $groupName is satisfiable")
        case Unsatisfiable(core, explanation) =>
          println(s"  ✗ UNSAT - $groupName is unsatisfiable")
          println(s"    Core: ${core.mkString(", ")}")
        case Unknown(reason, _, timeout) =>
          if (timeout) {
            println(s"  ⏱ TIMEOUT - $groupName causes timeout!")
            println(s"    This is likely the problematic constraint group.")
          } else {
            println(s"  ? UNKNOWN - $groupName: $reason")
          }
      }
      
      // Keep constraints for next iteration
      // Don't pop - we want to see cumulative effects
    }
  }
}
```

#### Step 2: Integrate with Frontend

Add a new command-line option `-diagnose` to `Frontend.scala`:

```scala
case "-diagnose" :: tail => parseArgs(map ++ Map('diagnose -> true), tail)
```

Then in `scala_main()`:

```scala
options.get('diagnose) match {
  case Some(true) =>
    // Extract constraint groups from model
    val groups = extractConstraintGroups(combinedModel)
    IncrementalDiagnostic.diagnoseModel(combinedModel, groups)
    return
  case _ =>
}
```

#### Step 3: Extract Constraint Groups

For DSN_Pass.k, identify constraint groups:
- Base timeline constraint (`Nominal_Anomaly_Impact`)
- `AutonomousManeauver`
- `AvailManeauver`
- `DopplerAfterApproachOTM`
- `ProvideCarrier`
- `CoverageOTMS`

Each group should be extractable as a separate SMT string.

#### Step 4: Convert SMT to Z3 Expressions

The `IncrementalSession.assert()` method takes `BoolExpr`, not SMT strings. You'll need to:
- Parse SMT strings into Z3 expressions, OR
- Modify `IncrementalSession` to accept SMT strings, OR
- Use the existing `K2Z3` infrastructure to convert

### 6. Testing

Once implemented, test with:

```bash
./export/k src/examples/DSN_Pass.k -diagnose
```

This should:
1. Add base constraints
2. Add each constraint group incrementally
3. Report which group causes timeout/UNSAT
4. Provide unsat cores for debugging

### 7. Key Challenges

1. **SMT to Z3 Conversion**: `IncrementalSession` uses Z3 API directly, but K generates SMT strings. Need to either:
   - Parse SMT strings into Z3 expressions
   - Modify to work with SMT strings
   - Use existing K2Z3 conversion logic

2. **Constraint Extraction**: Need to identify and extract individual constraint groups from the model. This might require:
   - Modifying constraint generation to be more modular
   - Parsing the generated SMT to extract groups
   - Using the diagnostic K file structure

3. **Context Management**: `IncrementalSession` needs a Z3 `Context`. Can reuse `K2Z3.ctx` or create new one.

### 8. Alternative: Simpler Approach

Instead of full integration, create a standalone diagnostic script:

```scala
// src/k/frontend/DiagnosticMain.scala
object DiagnosticMain {
  def main(args: Array[String]): Unit = {
    val model = Frontend.getModelFromFile(args(0))
    // Use IncrementalSession to test constraint groups
  }
}
```

Then run:
```bash
java -cp ... k.frontend.DiagnosticMain src/examples/DSN_Pass.k
```

### 9. Resources

- `SolverEnhancements.scala` lines 112-250: `IncrementalSession` implementation
- `Frontend.scala` lines 500-520: Current solving path
- `K2Z3.scala`: Z3 context and solver management
- `DSN_Pass-diagnostic.k`: Already has constraint groups identified with flags

### 10. Next Steps

1. Review `SolverEnhancements.scala` to understand `IncrementalSession` API
2. Decide on SMT→Z3 conversion approach
3. Implement constraint group extraction
4. Create diagnostic tool
5. Test with DSN_Pass.k

## Questions to Answer

1. Should we parse SMT strings or modify to work directly with K AST?
2. How to extract individual constraint groups from the model?
3. Should we reuse `K2Z3.ctx` or create a new context?
4. Do we need to modify `IncrementalSession` or can we use it as-is?

