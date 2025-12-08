# Opaque Function Support for K Language

## Overview

This document captures design alternatives and research for supporting opaque (black-box) functions in K, enabling integration with JVM libraries, Python libraries, and other external code.

## Goals

1. **A. Anytime best-effort solution** - Don't wait forever for an answer
2. **B. Support for opaque (black box) functions/APIs** - Import JVM/Python libraries, create objects, call functions, have constraints on them
3. **Incremental solving** - Make progress iteratively
4. **Automated planning support** - TimeVaryingMap, Activity, temporal reasoning

---

## Relevant SMT/Constraint Programming Research

### 1. Uninterpreted Functions in SMT

SMT solvers (including Z3) natively support **uninterpreted functions**:
- Solver knows nothing about function implementation
- Only guarantees: if inputs are equal, outputs must be equal (functional consistency)
- Can reason about equality constraints involving the function

```smt2
(declare-fun f (Int) Int)  ; Uninterpreted function
(assert (= (f 1) (f 1)))   ; Always true (functional consistency)
(assert (= (f x) (f y)))   ; Implies nothing about x,y relationship
```

**Limitation**: Can't find concrete values that satisfy constraints on outputs without refinement.

### 2. Theory of Arrays / Finite Maps

For functions with finite domains:
- Arrays: `(Array Int Int)` - maps integers to integers
- Can assert specific input-output pairs
- Solver can reason about them

### 3. Abstraction-Refinement (CEGAR)

Counter-Example Guided Abstraction Refinement:
1. Start with abstract model (uninterpreted function)
2. Find a candidate solution
3. **Concretize**: Call the actual function to check
4. If wrong, add constraint (refinement) and repeat

### 4. Symbolic Execution with Concrete Dispatch

Used in tools like KLEE, SAGE:
- Execute symbolically until you hit an opaque call
- Concretize inputs, call the real function
- Continue with concrete result

### 5. Contract-Based Reasoning

If we have pre/post conditions:
```k
@requires x >= 0
@ensures result >= 0
fun sqrt(x: Real): Real = external("java.lang.Math.sqrt")
```

The solver can use contracts without knowing implementation.

### 6. Constraint Logic Programming (CLP)

Languages like Prolog with constraints (CLP(FD), CLP(R)):
- Mix of logical inference and constraint solving
- Backtracking search with constraint propagation

### 7. Hybrid Symbolic-Concrete Execution (Concolic)

- Maintain both concrete and symbolic state
- Use concrete execution to guide symbolic exploration
- Collect path constraints from concrete runs

---

## Existing Implementation: kservices/bae

### Overview

The **Behavior Analysis Engine (BAE)** in `~/git/bae` implements a custom solver for K that provides:

1. **JVM Library Integration** - Direct access to Java classes and methods
2. **Inverse Image Interface** - Functions can define how to backwards-compute inputs from outputs
3. **Arc Consistency Algorithm** - Constraint propagation using image/inverse image
4. **TimeVaryingMap** - Time-dependent values for planning/scheduling
5. **Activity/DurativeEvent** - Temporal constructs for automated planning

### Key Insight: Inverse Image Interface

From the research paper "Towards Integrating Constraint Solving and Programming":

> "K is generally undecidable, but we show that practical problems can be solved by leveraging an interface for reasoning about constraints on **inverse images of functions**. For example, given a constraint, f(x) = y, for variables x and y, potential values for x can be **backwards computed** based on the domain of y and the inverse image for f."

### The SuggestiveFunctionCall Interface

From `bae/src/gov/nasa/jpl/ae/event/Functions.java`:

```java
public static class SuggestiveFunctionCall extends FunctionCall {
    /**
     * Returns the inverse of this function with respect to a single argument.
     * If f(x) = y, then inverse(y, x) returns a FunctionCall that computes x from y.
     */
    public FunctionCall inverse(Object returnValue, Object arg);
    
    /**
     * Returns the inverse domain - the set of possible input values
     * that would produce outputs in the given return domain.
     */
    public Domain<?> inverseDomain(Domain<?> returnValue, Object argument);
    
    /** Is this function monotonic? (enables optimizations) */
    public boolean isMonotonic();
    
    /** Is this function continuous? */  
    public boolean isContinuous();
}
```

### Arc Consistency Algorithm

From `bae/src/gov/nasa/jpl/ae/event/Consistency.java`:

1. For each constraint, restrict variable domains using `inverseDomain()`
2. Iterate until no more changes (fixed point)
3. If any domain becomes empty, constraint is unsatisfiable
4. Uses `arcConsistencySolve()` method

### Solving Style: Arc Consistency + Local Search

The BAE solver uses a **two-phase approach**:

1. **Arc Consistency Phase**
   - Propagate constraints to restrict domains
   - Use inverse images to backwards-compute valid ranges
   - Detect early failures (empty domains)

2. **Local Search Phase** (AC Pick)
   - Pick values for variables iteratively
   - Re-run arc consistency after each pick
   - Use inverse image to satisfy remaining constraints

### kservices Syntax Examples

From `~/git/kservices/src/kTestCases/`:

**TimeVaryingMap.k** - Time-dependent values with Java integration:
```k
import gov.nasa.jpl.ae.event.TimeVaryingMap

class A {
    var gkhx : Real
    req myName : gkhx > 0.0
    req gkhx < java.lang.Math.sqrt(100.0)  // Direct Java call!
    var t : TimeVaryingMap[String] = TimeVaryingMap[String]("str", null, "Fred", String.class)
}
var a : A
```

**abstractFunction2.k** - Abstract functions with constraints:
```k
fun f(s: String): String   // Declared but not defined

req f("hello") = "world"   // Constrain its behavior

var x: String = f("hello")
```

**integrate.k** - Time-based simulation:
```k
Timepoint.setEpoch("2024-353T12:00:00")
Timepoint.setHorizonDuration(days(2.0))

var epoch : Time = 0
var t1 : Time = epoch + hours(1.0)

class Battery {
    var power : TimeVaryingMap[Real]
    var energy : TimeVaryingMap[Real] = power.integrate()  // Integration!
}
```

**lightSwitchPower.k** - Activities and planning:
```k
class Light {
    class PowerMode
    val OFF: PowerMode
    val ON: PowerMode

    var pmode: TimeVaryingMap[PowerMode]
    var load: TimeVaryingMap[Real] = if pmode = ON then 20.0 else 0.0

    class IS_ON extends DurativeEvent {
        req time >= startTime && time < endTime => pmode = ON
        var goal00000: TO_ON = TO_ON(startTime :: pro, endTime :: startTime)
    }

    class TO_ON extends DurativeEvent {
        req startTime + PT20S <= endTime
        req startTime + PT30S >= endTime
        var v: Object = pmode.setValue(startTime, ON)
    }
}
```

---

## Design Alternatives

### Option A: CEGAR-style Refinement (Pure SMT + Callbacks)

```
┌─────────────────────────────────────────────────────┐
│  1. Parse K model with opaque function calls        │
│  2. Replace opaque calls with uninterpreted funcs   │
│  3. Solve with Z3                                   │
│  4. Get candidate solution                          │
│  5. For each opaque call in solution:               │
│     - Extract concrete input values                 │
│     - Call actual JVM/Python function               │
│     - Check if result matches solver's assumption   │
│  6. If mismatch: add constraint, goto step 3        │
│  7. If all match: return solution                   │
└─────────────────────────────────────────────────────┘
```

**Pros**: Clean separation, leverages Z3 fully
**Cons**: May not converge for complex functions, many iterations

### Option B: Contracts + Sampling

```k
class MathLib {
  @external("java.lang.Math.sqrt")
  @requires x >= 0
  @ensures result >= 0 && result * result = x
  fun sqrt(x: Real): Real
}
```

**Pros**: User provides semantic information
**Cons**: Requires manual contract specification

### Option C: Lazy Evaluation / Symbolic References

Don't evaluate opaque functions during solving - keep them symbolic. Only evaluate when producing final output.

**Pros**: Simple, no solver modification
**Cons**: Limited constraint reasoning on outputs

### Option D: Hybrid Code Execution + Constraint Solving (BAE Style)

Recognize that much of a model is just code execution:
- Execute deterministic parts directly
- Only invoke solver for under-constrained parts
- Interleave execution and solving
- Use inverse image interface for backwards computation

**Pros**: Efficient for mostly-concrete models, proven in kservices
**Cons**: Complex control flow, need to identify what to solve

### Option E: Integrate BAE with klang

Instead of reimplementing, integrate the BAE solver:
- Keep klang for parsing and SMT-based solving
- Use BAE for JVM integration and planning features
- Route constraints appropriately based on what's needed

**Pros**: Leverages existing proven code, gets TimeVaryingMap/Activity for free
**Cons**: Two solvers, complexity of integration

### Option F: Port Inverse Image Interface to klang

Port the key concepts from BAE to klang:
- Add `SuggestiveFunctionCall` concept
- Implement inverse images for common operations
- Add arc consistency as a pre-processing step before Z3

**Pros**: Single codebase, combines best of both
**Cons**: Significant implementation effort

---

## Key Design Questions

1. **How to declare external functions?**
   - Annotation: `@external("java.lang.Math.sqrt")`
   - Special syntax: `fun sqrt(x) = jvm("java.lang.Math", "sqrt", x)`
   - Import statement: `import java.lang.Math`
   - kservices style: direct Java qualified names

2. **How to handle return values?**
   - Uninterpreted (solver picks any value satisfying constraints)
   - Cached (same inputs = same outputs)
   - Live-evaluated during refinement
   - BAE style: use inverse image to backwards compute

3. **What about side effects?**
   - Pure functions only? (easier)
   - Allow stateful with explicit modeling?
   - BAE uses setValue() pattern for state changes

4. **Type mapping?**
   - K `Int` ↔ Java `int`/`long`/`BigInteger`
   - K `String` ↔ Java `String`
   - K classes ↔ Java objects?
   - BAE does automatic type coercion

5. **When to execute vs. when to solve?**
   - All constraints go to solver?
   - Execute what we can, solve the rest?
   - User annotations to guide?
   - BAE: arc consistency first, then local search

6. **Planning/Temporal Features?**
   - Do we need TimeVaryingMap in klang?
   - Activity/DurativeEvent support?
   - Integration with existing planning tools?

---

## Recommended Approach

Given the existing implementation in kservices/bae, several paths are possible:

### Short Term: Integration
- Use klang for what it does well (SMT-based solving, simple models)
- Use kservices/bae for JVM integration, planning, TimeVaryingMap
- Define clear interface between them

### Medium Term: Port Key Features
- Port the inverse image interface concept to klang
- Implement arc consistency as preprocessing
- Add external function call syntax

### Long Term: Unified Solver
- Hybrid solver combining Z3 strength with inverse image flexibility
- Single language, single implementation
- Full JVM integration with TimeVaryingMap/Activity support

---

## References

- CEGAR: Clarke et al., "Counterexample-Guided Abstraction Refinement"
- KLEE: Cadar et al., "KLEE: Unassisted and Automatic Generation of High-Coverage Tests"
- Z3: de Moura & Bjørner, "Z3: An Efficient SMT Solver"
- CLP(FD): Jaffar & Maher, "Constraint Logic Programming: A Survey"
- BAE Paper: "Towards Integrating Constraint Solving and Programming" (~/git/aiProgrammingPaper)
- K Language: Havelund, "K: A Language for K" (MODELSWARD 2016)

---

## Related Repositories

- **klang** (this repo): SMT-based K solver with Z3
- **kservices**: K with custom BAE solver, JVM integration, TimeVaryingMap
- **bae**: Behavior Analysis Engine - the solver implementation
- **aiProgrammingPaper**: Research paper describing inverse image approach
- **utils**: Supporting utilities library
- **sysml**: SysML integration library

---

## Notes

- Created: December 7, 2025
- Status: Draft - gathering requirements and exploring alternatives
- The inverse image approach is the key innovation in BAE that allows reasoning about opaque functions
- TimeVaryingMap and Activity are critical for planning applications (Europa Clipper mission)
