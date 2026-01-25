# Soft Constraints and Max-SAT Implementation Analysis

**Date:** January 24, 2026  
**Purpose:** Document the current state of soft constraint and max-SAT support in the K solver infrastructure.

## Executive Summary

The K solver has **partial infrastructure** for soft constraints and max-SAT solving:
- ✅ **K syntax** (`soft req`) works and generates correct SMT
- ✅ **K2Z3 path** handles soft constraints via Z3 Optimize API
- ✅ **Max-SAT for partial models** works (for debugging UNSAT)
- ⚠️ **UnifiedSolver** extracts soft constraints but doesn't use them for relaxation
- ❌ **Dynamic relaxation** (hard→soft on timeout) is NOT implemented
- ❌ **solveWithSoftHeap()** is complete but never called (dead code)

---

## 1. K Language Syntax for Soft Constraints

### 1.1 Syntax

```k
class Example {
  x : Int
  
  req x > 0           // Hard constraint - must be satisfied
  soft req x < 100    // Soft constraint - prefer but not required
}
```

### 1.2 AST Representation

In `AbstractSyntax.scala`, constraints are represented as:

```scala
case class ConstraintDecl(
  name: Option[String],  // Optional constraint name
  exp: Exp,              // The constraint expression
  soft: Boolean = false  // true for soft constraints
) extends MemberDecl
```

### 1.3 SMT Generation

**File:** `src/k/frontend/AbstractSyntax.scala` (lines 2509-2521)

```scala
// In ConstraintDecl.toSMT:
if (soft) {
  s"(assert-soft $smtExp :weight 1)"  // Soft constraint with weight
} else {
  s"(assert $smtExp)"                  // Hard constraint
}
```

**Result:** `soft req x < 100` generates `(assert-soft (< x 100) :weight 1)`

---

## 2. Solving Paths and Soft Constraint Handling

### 2.1 K2Z3.solveSMT Path

**File:** `src/k/frontend/K2Z3.scala`

This is the original/legacy solving path that properly handles soft constraints.

#### Detection (lines 1567-1597)

```scala
def extractSolverConfig(model: Model): Unit = {
  hasSoftConstraints = false
  for (decl <- model.decls) {
    decl match {
      case ed: EntityDecl =>
        for (member <- ed.members) {
          member match {
            case ConstraintDecl(_, _, soft) if soft =>
              hasSoftConstraints = true
            case _ =>
          }
        }
      case _ =>
    }
  }
}
```

#### Dispatch (lines 1599-1650)

```scala
def solveSMT(model: Model, smtModel: String, printModel: Boolean): Unit = {
  // ...
  if (hasSoftConstraints) {
    // Use Optimize solver for soft constraints
    solveSMTWithOptimize(model, smtModel, printModel)
  } else if (hasExternalCalls) {
    // Use CEGAR loop for models with external function calls
    solveSMTWithCEGAR(model, smtModel, printModel)
  } else {
    // Regular solving
    solveSMTDirect(model, smtModel, printModel)
  }
}
```

#### solveSMTWithOptimize (lines 1660+)

Uses Z3's `Optimize` API which natively understands `(assert-soft ...)` from the parsed SMT:

```scala
def solveSMTWithOptimize(model: Model, smtModel: String, printModel: Boolean): Unit = {
  val optimize = ctx.mkOptimize()
  // Parse SMT - assert-soft statements are handled by Optimize API
  val assertions = ctx.parseSMTLIB2String(smtModel, ...)
  for (a <- assertions) {
    optimize.Add(a)  // Optimize API handles soft constraints automatically
  }
  val status = optimize.Check()
  // ...
}
```

### 2.2 UnifiedSolver Path

**File:** `src/k/frontend/UnifiedSolver.scala`

This is the newer solving infrastructure with CEGAR and heap management.

#### Soft Constraint Extraction (lines 1048-1070)

```scala
private val softConstraints: MMap[String, Double] = MMap()

private def extractSoftConstraints(model: KModel): Unit = {
  if (model == null) return
  
  for (decl <- model.decls) {
    decl match {
      case ed: EntityDecl =>
        for (member <- ed.members) {
          member match {
            case cd @ ConstraintDecl(_, exp, soft) if soft =>
              softConstraints += (exp.toString -> 1.0)
            case _ =>
          }
        }
      case _ =>
    }
  }
}
```

Called during `solve()` at line 184:
```scala
def solve(model: KModel, smtModel: String, ...): SolveResult = {
  // ...
  extractSoftConstraints(model)
  // ...
}
```

#### ⚠️ The Only Usage - A Placeholder (lines 1213-1220)

```scala
case SolveResult.Unsat =>
  log("UNSAT - checking if can relax")
  
  if (useScenarioTracking && viableScenarios.size > 1) {
    // Try next scenario...
  } else if (canIncreaseObjectBounds()) {
    // Try more objects...
  } else if (softConstraints.nonEmpty) {
    // Could implement soft constraint relaxation here
    log("Could try relaxing soft constraints (not yet implemented)")
    done = true
    result = SolveResult.Unsat
  } else {
    done = true
    result = SolveResult.Unsat
  }
```

**This is just a placeholder!** The soft constraints are extracted but never used for actual relaxation.

---

## 3. Max-SAT for Partial Models (Debugging UNSAT)

### 3.1 UnifiedSolver.tryGetPartialModel

**File:** `src/k/frontend/UnifiedSolver.scala` (lines 1445-1516)

When the problem is UNSAT, this function tries to find a partial model that satisfies the maximum number of constraints:

```scala
private def tryGetPartialModel(boolExps: List[BoolExpr], config: SolveConfig,
                               kModel: Option[KModel] = None, 
                               smtModel: Option[String] = None,
                               shouldPrint: Boolean = false): Unit = {
  try {
    val optimize = K2Z3.ctx.mkOptimize()
    
    // Set short timeout for partial model
    val partialTimeout = config.timeout.map(ms => math.min(ms, 5000L)).getOrElse(5000L)
    
    // Filter out quantified constraints (Optimize doesn't support them)
    val nonQuantifiedExps = boolExps.filterNot(containsQuantifierExpr)
    
    // Add ALL constraints as SOFT with weight 1
    // This turns it into max-SAT: maximize # satisfied
    for (expr <- nonQuantifiedExps) {
      optimize.AssertSoft(expr, 1, "partial")
    }
    
    val status = optimize.Check()
    if (status == Status.SATISFIABLE) {
      val partialModel = optimize.getModel
      bestSoFar = Some(partialModel)
      // Log which constraints are violated...
    }
  } catch {
    case e: Exception =>
      log(s"Could not get partial model: ${e.getMessage}")
  }
}
```

#### Called From (line 1660)

```scala
case SolveResult.Unsat =>
  log("Regular Solver: UNSAT - attempting to get partial model via Optimize...")
  tryGetPartialModel(boolExpsList, config, Some(model), Some(smtModel))
  return SolveResult.Unsat
```

### 3.2 Frontend.tryGetPartialModelForUnsat

**File:** `src/k/frontend/Frontend.scala` (lines 1670-1715)

Similar functionality in the Frontend for the K2Z3 path:

```scala
def tryGetPartialModelForUnsat(model: Model, smtStr: String): Unit = {
  try {
    val optimize = K2Z3.ctx.mkOptimize()
    val assertions = K2Z3.ctx.parseSMTLIB2String(smtStr, ...)
    
    // Add as soft constraints for max-SAT
    for (a <- assertions) {
      optimize.AssertSoft(a.asInstanceOf[BoolExpr], 1, "partial")
    }
    
    if (optimize.Check() == Status.SATISFIABLE) {
      val partialModel = optimize.getModel
      K2Z3.z3Model = partialModel
      println("Partial model (max-SAT):")
      K2Z3.PrintModel(model)
    }
  } catch {
    case e: Exception => // ignore
  }
}
```

---

## 4. Hard Constraint Verification

### 4.1 UnifiedSolver.verifyHardConstraints

**File:** `src/k/frontend/UnifiedSolver.scala` (lines 2140-2220)

After getting a solution (especially from Optimize which may relax constraints), this verifies that all **hard** constraints are satisfied:

```scala
private def verifyHardConstraints(model: KModel, smtModel: String, 
                                   z3Model: Z3Model,
                                   scenario: Option[String]): Boolean = {
  // Re-parse the SMT to get constraint expressions
  val assertions = K2Z3.ctx.parseSMTLIB2String(smtModel, ...)
  
  // Evaluate each constraint against the model
  for (assertion <- assertions) {
    val result = z3Model.evaluate(assertion, true)
    if (!result.isTrue) {
      // Constraint violated!
      return false
    }
  }
  true
}
```

This is important for best-effort mode where the Optimize API might return a solution that doesn't satisfy all hard constraints.

---

## 5. Dead Code: solveWithSoftHeap

### 5.1 Complete But Unused Implementation

**File:** `src/k/frontend/UnifiedSolver.scala` (lines 430-600)

This is a complete implementation of soft-bounded heap solving that is **never called**:

```scala
/**
 * Solve using soft constraints to minimize heap usage.
 */
def solveWithSoftHeap(model: KModel, printModel: Boolean): SolveResult = {
  logSoft("Starting Soft-Bounded Heap Solver")
  
  val poolMultiplier = 4
  ASTOptions.instanceMultiplier = poolMultiplier
  
  // Generate SMT model with larger pool
  val baseSmt = model.toSMT
  
  // Add soft constraints for heap minimization
  val smtWithSoft = addHeapSoftConstraints(model, baseSmt)
  
  // Solve with Optimize
  solveWithOptimize(model, smtWithSoft, printModel)
}
```

#### addHeapSoftConstraints (lines 480-560)

Adds soft constraints preferring heap objects to be "dead" (unused):

```scala
private def addHeapSoftConstraints(model: KModel, baseSmt: String): String = {
  val sb = new StringBuilder(baseSmt)
  
  for (className <- allClasses) {
    val entries = UtilSMT.objectGraph.getHeapEntries(className)
    for (ref <- entries) {
      val aliveName = s"k!alive!$className!$ref"
      
      // Declare alive flag
      sb.append(s"(declare-const $aliveName Bool)\n")
      
      // Link to actual heap usage
      sb.append(s"(assert (= $aliveName (deref-is-$className $ref)))\n")
      
      // Soft constraint: prefer NOT alive (minimize heap usage)
      sb.append(s"(assert-soft (not $aliveName) :weight 1)\n")
    }
  }
  
  sb.toString
}
```

**Why unused?** The `HeapStrategy.SoftBounded` case exists in the enum but nothing dispatches to `solveWithSoftHeap()`.

---

## 6. Model-to-Soft-Constraints Conversion (Seeding)

### 6.1 K2Z3 Soft Constraint Seeding

**File:** `src/k/frontend/K2Z3.scala` (lines 275-380)

Infrastructure for converting a found model back into soft constraints for seeding subsequent solves:

```scala
/**
 * Convert a Z3 model to soft constraint assertions.
 * Used to seed subsequent solves with a partial solution.
 */
def modelToSoftConstraints(model: com.microsoft.z3.Model): List[String] = {
  val softConstraints = ListBuffer[String]()
  
  for (decl <- model.getConstDecls) {
    val name = decl.getName.toString
    val value = model.getConstInterp(decl)
    
    // Create soft constraint asserting this value
    softConstraints += s"(assert-soft (= $name $value) :weight 1)"
  }
  
  softConstraints.toList
}

/**
 * Add soft constraints from a previous solution to guide solving.
 */
def seedFromSoftConstraints(softConstraints: List[String]): Unit = {
  for (sc <- softConstraints) {
    // Parse and add as soft constraint
    optimize.fromString(sc)
  }
}
```

**Usage:** Could be used to warm-start a solve with a previous partial solution, but this functionality is not integrated into the main solving loops.

---

## 7. Known Limitations

### 7.1 Optimize API and Quantifiers

The Z3 Optimize API does **not** correctly handle quantified constraints (`forall`, `exists`). From `UnifiedSolver.scala`:

```scala
/**
 * Check if an expression contains any quantifiers (forall/exists).
 * Used to decide whether to use Optimize or regular Solver.
 */
private def containsQuantifierExpr(expr: Expr[_]): Boolean = {
  if (expr.isQuantifier) return true
  for (i <- 0 until expr.getNumArgs) {
    if (containsQuantifierExpr(expr.getArgs()(i))) return true
  }
  false
}
```

When quantifiers are present, `tryGetPartialModel` filters them out:

```scala
val nonQuantifiedExps = boolExps.filterNot(containsQuantifierExpr)
```

This means partial models may not reflect the full constraint set.

### 7.2 canIncreaseObjectBounds is Disabled

**File:** `src/k/frontend/UnifiedSolver.scala` (lines 1036-1042)

```scala
private def canIncreaseObjectBounds(): Boolean = {
  // DISABLED: Inner loop bound-increasing doesn't actually change heap size.
  // The SMT length stays constant despite objectBounds increasing.
  // Heap size changes only happen via HeapCEGAR's instanceMultiplier.
  // TODO: Re-enable when total instance bound constraint via `used` flags is implemented.
  false
}
```

This prevents the unified loop from trying to increase object bounds on UNSAT, which blocks one path to soft constraint relaxation.

---

## 8. What's Missing for Full Soft Constraint Support

### 8.1 Dynamic Relaxation (Hard → Soft on Timeout/UNSAT)

The placeholder at line 1213-1215 says it all:

```scala
} else if (softConstraints.nonEmpty) {
  // Could implement soft constraint relaxation here
  log("Could try relaxing soft constraints (not yet implemented)")
```

**What would be needed:**
1. Track which hard constraints could be relaxed
2. On UNSAT or timeout, convert selected hard constraints to soft
3. Re-solve with Optimize API
4. Report which constraints were relaxed in the solution

### 8.2 Incremental Relaxation Strategy

No implementation for:
- Relaxing constraints one-by-one
- Using constraint weights/priorities
- Binary search to find minimal relaxation

### 8.3 Integration of solveWithSoftHeap

The complete `solveWithSoftHeap()` implementation needs:
- A dispatch path from `solveWithHeapCegar()` or a configuration option
- Integration with the HeapStrategy enum

---

## 9. Git History Context: What Was Claimed vs What Was Actually Done

The commit messages describe ambitious features, but the reality is more limited.

### Commit `8eabe08`: "Implement unified solving loop with soft constraints (Phases 1-4)"

**What the commit message claimed:**
- Phase 1: UnifiedSolver class with iterative solving loop
- Phase 2: Object bounds infrastructure
- Phase 3: Incremental solving support with pause/resume
- Phase 4: Soft constraints with grammar, AST, parser changes

**What was actually implemented:**
| Component | Status | Notes |
|-----------|--------|-------|
| `soft req` grammar syntax | ✅ Done | Model.g4 updated |
| AST `soft: Boolean` flag | ✅ Done | ConstraintDecl updated |
| Parser visitSoftConstraint | ✅ Done | KScalaVisitor updated |
| UnifiedSolver class | ✅ Created | 646 lines added |
| `extractSoftConstraints()` | ✅ Done | Extracts soft constraints from model |
| **Actually using soft constraints** | ❌ Placeholder | Only logs "not yet implemented" |
| Object bounds infrastructure | ⚠️ Partial | Tracking exists, but disabled |
| Pause/resume | ⚠️ Stub | Methods exist but not tested/used |

**The Gap:** Soft constraints are *parsed* and *extracted* but the code that would actually use them for relaxation is just a comment:
```scala
} else if (softConstraints.nonEmpty) {
  // Could implement soft constraint relaxation here
  log("Could try relaxing soft constraints (not yet implemented)")
```

---

### Commit `fb7e361`: "Implement unified solver with Optimize API, incremental constraints, and adaptive soft constraints"

**What the commit message claimed:**
- Integrated Z3 Optimize API for better partial model support on timeout
- Added incremental constraint addition within scenarios for max-SAT approach
- **Implemented adaptive soft constraints: automatically makes problematic groups soft when they cause UNSAT**
- Enhanced scenario tracking
- Improved partial model extraction

**What was actually implemented:**
| Component | Status | Notes |
|-----------|--------|-------|
| Scenario tracking state | ✅ Done | `scenarioVars`, `viableScenarios` added |
| Best-effort mode | ✅ Done | Returns partial solutions on timeout |
| Incremental constraint addition | ✅ Done | Groups constraints, adds incrementally |
| `problematicGroups` tracking | ✅ Done | Identifies groups that cause UNSAT |
| **Making problematic groups soft** | ✅ Code exists | `softOptimize.AssertSoft()` calls present |
| **Code actually reached** | ❌ NO | See below |

**The Problem:** The code for "adaptive soft constraints" was added (~350 lines), but commit `ff688de` later added a short-circuit that bypasses it:

```scala
// STEP 1: Use regular Solver first to get authoritative SAT/UNSAT
val authoritativeResult = solveWithRegularSolver(model, smtModel, boolExpsList, config)

authoritativeResult match {
  case SolveResult.Unsat =>
    // Confirmed UNSAT - use Optimize to get a partial model for debugging
    tryGetPartialModel(boolExpsList, config, ...)
    return SolveResult.Unsat   // <-- RETURNS HERE, never reaches adaptive soft code
    
  case SolveResult.Timeout =>
    return SolveResult.Timeout  // <-- RETURNS HERE
```

The incremental constraint/adaptive soft code at lines 1680-2100 is only reached when:
1. The regular solver returns `UNKNOWN` (rare), OR
2. The regular solver returns `SAT` and we continue to Optimize "for verification"

**In practice:** For UNSAT cases (where adaptive soft would help), the code exits immediately after `tryGetPartialModel()`. The 350+ lines of adaptive soft constraint code is essentially dead.

---

### Commit `ff688de`: "Use regular Solver instead of Optimize for quantified constraints"

**What the commit message claimed:**
- Fix for Optimize API giving wrong results with quantifiers
- Use regular Solver for models with forall/exists
- Still use Optimize for models without quantifiers

**What was actually implemented:**
| Component | Status | Notes |
|-----------|--------|-------|
| `containsQuantifierExpr()` | ✅ Done | Checks for forall/exists |
| `solveWithRegularSolver()` | ✅ Done | Falls back to regular Z3 Solver |
| Short-circuit on quantifiers | ✅ Done | Bypasses Optimize entirely |

**Unintended Consequence:** This commit also changed the flow to **always** use regular solver first:

```scala
// STEP 1: Use regular Solver first to get authoritative SAT/UNSAT
val authoritativeResult = solveWithRegularSolver(...)
```

This effectively disabled the incremental/adaptive soft constraint logic for all SAT/UNSAT cases, not just quantified ones. The original intent was to only bypass Optimize for quantified constraints.

---

### Summary: Claimed vs Reality

| Feature | Commit Claims | Actually Works |
|---------|--------------|----------------|
| `soft req` syntax | ✅ | ✅ Parsing works |
| Soft → SMT `(assert-soft)` | ✅ | ✅ Generation works |
| K2Z3 uses Optimize for soft | ✅ | ✅ Works |
| UnifiedSolver extracts soft | ✅ | ✅ Extraction works |
| **Dynamic hard→soft relaxation** | ✅ Claimed | ❌ Not implemented |
| **Adaptive soft on UNSAT** | ✅ Claimed, code added | ❌ Code bypassed |
| Incremental constraint adding | ✅ Claimed, code added | ⚠️ Only on UNKNOWN/SAT path |
| `problematicGroups` → soft | ✅ Code exists | ❌ Never reached |

**Root Cause:** Commit `ff688de` fixed a real bug (Optimize + quantifiers = wrong SAT) but the fix was too broad. Instead of only using regular solver for quantified constraints, it now always uses regular solver first, which short-circuits before reaching the adaptive soft constraint code.

---

## 10. Current Control Flow (Why Adaptive Soft Is Dead)

### solveWithTimeout() Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                     solveWithTimeout()                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ Parse SMT, create boolExps    │
              └───────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ solveWithRegularSolver()      │◄── STEP 1: Always called first
              │ (Z3 regular Solver)           │
              └───────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
      ┌───────┐          ┌─────────┐         ┌─────────┐
      │ UNSAT │          │ TIMEOUT │         │   SAT   │
      └───────┘          └─────────┘         └─────────┘
          │                   │                   │
          ▼                   ▼                   │
    ┌─────────────┐    ┌─────────────┐           │
    │tryGetPartial│    │return       │           │
    │Model()      │    │TIMEOUT      │           │
    └─────────────┘    └─────────────┘           │
          │                                       │
          ▼                                       ▼
    ┌─────────────┐                     ┌─────────────────┐
    │return UNSAT │                     │ Continue to     │
    │             │                     │ Optimize path   │
    └─────────────┘                     │ (lines 1680+)   │
                                        └─────────────────┘
                                                  │
                                                  ▼
                                        ┌─────────────────────────┐
                                        │ Incremental constraint  │
                                        │ addition with           │
                                        │ problematicGroups       │
                                        │ (ONLY ON SAT/UNKNOWN)   │
                                        └─────────────────────────┘
```

### The Problem

For **UNSAT cases** (where adaptive soft constraints would be most useful):
1. Regular solver returns UNSAT
2. `tryGetPartialModel()` is called (max-SAT for debugging)  
3. **Function returns immediately** with `SolveResult.Unsat`
4. The 400+ lines of incremental/adaptive soft code (lines 1680-2100) is **never reached**

For **SAT cases**:
- The Optimize path is reached for "verification"
- But the problem is already SAT, so adaptive relaxation isn't needed!

### What Would Need to Change

To actually use adaptive soft constraints on UNSAT:

```scala
case SolveResult.Unsat =>
  // CURRENT: Just get partial model and return
  tryGetPartialModel(boolExpsList, config, ...)
  return SolveResult.Unsat  // <-- Dies here
  
  // NEEDED: Try adaptive soft relaxation instead
  log("UNSAT - attempting adaptive soft constraint relaxation...")
  val adaptiveResult = solveWithAdaptiveSoftConstraints(model, boolExpsList, config)
  adaptiveResult match {
    case SolveResult.Sat(model) => 
      log("Found solution by relaxing some constraints")
      return adaptiveResult
    case _ =>
      return SolveResult.Unsat
  }
```

---

## 11. Recommendations
1. **Use K2Z3 path for soft constraints** - It works correctly with `soft req` syntax
2. **Use `@bestEffort` annotation** - Returns partial solutions on timeout

### For Future Development
1. **Implement dynamic relaxation** - Convert hard constraints to soft on UNSAT
2. **Enable solveWithSoftHeap** - Add dispatch path for heap minimization
3. **Re-enable canIncreaseObjectBounds** - With proper `used` flag implementation
4. **Add constraint priority/weight syntax** - Allow `soft req ... weight 10`

---

## 13. DSN_Pass Scenario Solver: Specialized vs Generalized

### The Original Problem

DSN_Pass.k has a **top-level disjunction** that partitions the problem into 3 mutually exclusive scenarios:
1. **Nominal**: `requirements.missedpass = false`
2. **Anomalous Tolerable**: `requirements.missedpass = true && requirements.tolerate = true`
3. **Anomalous Not Tolerable**: `requirements.missedpass = true && requirements.tolerate = false`

Solving each scenario separately is **much faster** than solving the combined problem because the solver doesn't waste time exploring impossible combinations.

### What Was Implemented

| Component | File | Status |
|-----------|------|--------|
| `-dsn-pass` flag | `Frontend.scala` line 243 | ✅ Works |
| `DSNPassSolver` | `DSNPassSolver.scala` | ✅ Works (599 lines) |
| `DisjunctionDetector` | `DisjunctionDetector.scala` | ❌ Never called |
| `ScenarioTrackingDiagnostic` | `ScenarioTrackingDiagnostic.scala` | ✅ Works |
| `UnifiedSolverWithScenarios` | `UnifiedSolverWithScenarios.scala` | ⚠️ Works but separate path |
| `-unified-scenarios` flag | `Frontend.scala` line 242 | ✅ Works |

### DSNPassSolver: Hardcoded Scenarios

**File:** `src/k/frontend/DSNPassSolver.scala` (lines 12-20)

```scala
/**
 * Solver specifically designed for DSN_Pass.k and similar models with 
 * disjunctive structure based on missedpass/tolerate boolean flags.
 * 
 * This is NOT a general-purpose solver - it hardcodes the 3 scenarios 
 * specific to DSN_Pass.k:
 * 1. Nominal: missedpass = false
 * 2. Anomalous Tolerable: missedpass = true && tolerate = true
 * 3. Anomalous Not Tolerable: missedpass = true && tolerate = false
 */
```

The comment explicitly states this is **not** general-purpose. Usage:
```bash
./export/k -dsn-pass src/examples/DSN_Pass.k
```

This triggers `IncrementalMode.Scenarios` which calls `DSNPassSolver.solveByScenariosIncremental()`.

### DisjunctionDetector: Built but Never Used

**File:** `src/k/frontend/DisjunctionDetector.scala`

A 150-line implementation that:
- Detects top-level OR expressions
- Extracts branches as separate scenarios
- Handles nested ORs

**Problem:** Nobody calls it! Search results:
```
grep "detectDisjunctiveStructure" → Only found in its own definition
grep "DisjunctionDetector" → No imports in any other file
```

This was the **generalization** that was supposed to replace the hardcoded DSNPassSolver, but it was never wired in.

### UnifiedSolverWithScenarios: Alternative Path

**File:** `src/k/frontend/UnifiedSolverWithScenarios.scala`

A 232-line implementation that:
1. Uses `ScenarioTrackingDiagnostic` to identify viable scenarios
2. Solves each scenario using unified loop

Usage:
```bash
./export/k -unified-scenarios <file.k>
```

**Problem:** This is a completely separate code path from the main UnifiedSolver, and the scenario tracking inside UnifiedSolver (`viableScenarios`, `useScenarioTracking`) is yet another separate system.

### Three Competing Scenario Systems

There are actually **three** separate scenario tracking implementations:

| System | How to Enable | Where |
|--------|---------------|-------|
| DSNPassSolver | `-dsn-pass` | Hardcoded 3 scenarios for DSN_Pass.k |
| UnifiedSolverWithScenarios | `-unified-scenarios` | Uses ScenarioTrackingDiagnostic |
| UnifiedSolver internal | N/A (disabled) | `useScenarioTracking` flag, hardcoded scenarios |

None of them use `DisjunctionDetector` to automatically detect scenarios from the model.

### What Generalization Would Look Like

Instead of hardcoding scenarios in DSNPassSolver:

```scala
// CURRENT (DSNPassSolver.extractScenarios):
scenarios += Scenario("Nominal", Nil, "requirements.missedpass = false")
scenarios += Scenario("AnomalousTolerable", Nil, "missedpass = true && tolerate = true")
scenarios += Scenario("AnomalousNotTolerable", Nil, "missedpass = true && tolerate = false")
```

Should be:

```scala
// NEEDED (using DisjunctionDetector):
val groups = IncrementalDiagnostic.groupAssertionsByConstraint(assertions, smtModel)
for (group <- groups) {
  DisjunctionDetector.detectDisjunctiveStructure(group.name, group.assertions) match {
    case Some(disjGroup) =>
      // Found a disjunction! Create scenarios from branches
      for (branch <- disjGroup.branches) {
        scenarios += Scenario(s"${group.name}_branch${branch.index}", branch.expression)
      }
    case None =>
      // Not disjunctive, add as normal constraint
  }
}
```

### Summary

| Question | Answer |
|----------|--------|
| Is `-dsn-pass` related to what I found? | **Yes** - it uses scenarios with adaptive soft constraints |
| Was the technique generalized? | **No** - DisjunctionDetector exists but is dead code |
| Can I use it for other disjunctive problems? | **No** - DSNPassSolver hardcodes DSN_Pass.k scenarios |
| What would it take to generalize? | Wire DisjunctionDetector into the constraint grouping logic |

---

## 14. Appendix: File Locations

| Component | File | Lines |
|-----------|------|-------|
| `soft req` SMT generation | `AbstractSyntax.scala` | 2509-2521 |
| K2Z3 soft detection | `K2Z3.scala` | 1567-1597 |
| K2Z3 Optimize dispatch | `K2Z3.scala` | 1599-1650 |
| solveSMTWithOptimize | `K2Z3.scala` | 1660+ |
| UnifiedSolver softConstraints | `UnifiedSolver.scala` | 1048-1070 |
| Placeholder (not implemented) | `UnifiedSolver.scala` | 1213-1220 |
| tryGetPartialModel | `UnifiedSolver.scala` | 1445-1516 |
| verifyHardConstraints | `UnifiedSolver.scala` | 2140-2220 |
| solveWithSoftHeap (dead) | `UnifiedSolver.scala` | 430-600 |
| addHeapSoftConstraints | `UnifiedSolver.scala` | 480-560 |
| modelToSoftConstraints | `K2Z3.scala` | 275-320 |
| seedFromSoftConstraints | `K2Z3.scala` | 330-365 |
| containsQuantifierExpr | `UnifiedSolver.scala` | 1390-1400 |
| canIncreaseObjectBounds | `UnifiedSolver.scala` | 1036-1042 |
| DSNPassSolver | `DSNPassSolver.scala` | 1-599 |
| DisjunctionDetector (dead) | `DisjunctionDetector.scala` | 1-150 |
| ScenarioTrackingDiagnostic | `ScenarioTrackingDiagnostic.scala` | 1-100+ |
| UnifiedSolverWithScenarios | `UnifiedSolverWithScenarios.scala` | 1-232 |
