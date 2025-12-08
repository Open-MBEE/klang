# Opaque Function Support for K Language

## Overview

This document captures design alternatives and research for supporting opaque (black-box) functions in K, enabling integration with JVM libraries, Python libraries, and other external code.

**This effort is effectively a rewrite of kservices/bae**, learning from what worked while avoiding the problems.

## Goals

1. **A. Anytime best-effort solution** - Don't wait forever for an answer
2. **B. Support for opaque (black box) functions/APIs** - Import JVM/Python libraries, create objects, call functions, have constraints on them
3. **Incremental solving** - Make progress iteratively
4. **Automated planning support** - TimeVaryingMap, Activity, temporal reasoning

---

## Why Not Build on BAE Directly

The existing BAE implementation has several issues that make it unsuitable as a foundation:

1. **Arc consistency is weaker than SMT constraint solving** - Z3 can handle problems that arc consistency cannot (e.g., polynomials, complex arithmetic)

2. **Based on old Java patterns** - Heavy use of reflection; modern Java has easier approaches (records, sealed classes, pattern matching, better type inference)

3. **Maintenance burden** - Complex codebase that's hard to understand and modify

4. **Performance concerns** - Large K programs had performance issues, possibly due to bloat from K→Java translation

5. **Two codebases** - Having separate klang (SMT-based) and kservices (BAE-based) creates confusion and duplicate effort

**Decision**: This klang effort is a **rewrite** that:
- Keeps Z3/SMT as the primary solving engine (stronger than arc consistency)
- Adds opaque function support via CEGAR-style refinement
- Eventually adds TimeVaryingMap/Activity concepts natively
- Uses modern Java/Scala patterns
- Single codebase, single language implementation

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

## Recommended Approach: Z3-First Rewrite

This klang effort is a rewrite of kservices/bae with Z3/SMT as the primary solving engine.

### Phase 1: CEGAR-style External Function Calls (Current Priority)

Add support for calling JVM functions with CEGAR-style refinement:

```k
// Syntax option 1: Annotation
@external("java.lang.Math.sqrt")
fun sqrt(x: Real): Real

// Syntax option 2: Direct qualified name (kservices style)
req y < java.lang.Math.sqrt(100.0)

// Syntax option 3: Import + simple name
import java.lang.Math
req y < Math.sqrt(100.0)
```

**Implementation**:
1. Parse external function calls
2. During SMT generation, use uninterpreted function
3. After Z3 finds candidate solution, evaluate actual JVM call
4. If mismatch, add constraint `f(concrete_inputs) = concrete_output` and re-solve
5. Repeat until consistent or max iterations

### Phase 2: Compile-Time Evaluation

For deterministic expressions with concrete inputs, evaluate at compile time:
- `java.lang.Math.sqrt(100.0)` → `10.0` (constant folding)
- Only use uninterpreted functions when inputs are symbolic

### Phase 3: TimeVaryingMap as Native Type

Add TimeVaryingMap as a built-in K type with SMT support:

```k
class Battery {
    power : TimeVaryingMap[Real]
    
    // Constraint: power is always non-negative
    req forall t: Time . power.getValue(t) >= 0
    
    // Integration could be special-cased
    energy : TimeVaryingMap[Real] = power.integrate()
}
```

**SMT Encoding Ideas**:
- TimeVaryingMap as Z3 Array: `(Array Int Real)` where Int is time in ms
- Or as uninterpreted function with piecewise constraints
- Leverage our new Time/Duration types

### Phase 4: Activity/DurativeEvent for Planning

Add planning primitives:

```k
class TurnOn extends Activity {
    duration : Duration
    req duration >= PT20S
    req duration <= PT30S
    
    effect { target.state = ON }
    precondition { target.state = OFF }
}
```

**Approach**: Encode as constraints on time intervals with Z3's arithmetic.

### Key Principles

1. **Z3 is the solver** - Don't reimplement constraint solving
2. **CEGAR for opaque functions** - Use Z3's strength, verify with actual calls
3. **Compile what you can** - Constant fold deterministic expressions
4. **Modern patterns** - Use Java 21+, avoid heavy reflection
5. **Single codebase** - Everything in klang, deprecate kservices over time

---

## IR-Centric Architecture (from Research Session)

Based on the ChatGPT research session (see `docs/declarative_language_research_session.txt`), the recommended architecture is **IR-first**:

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Layer 1: Front-ends                          │
│  ┌─────────┐  ┌─────────────────┐  ┌──────────────────┐         │
│  │ K (klang│  │ Java+Annotations│  │ Python DSL       │         │
│  │ parser) │  │ (future)        │  │ (future)         │         │
│  └────┬────┘  └────────┬────────┘  └────────┬─────────┘         │
│       │                │                    │                    │
│       └────────────────┼────────────────────┘                    │
│                        ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              Layer 2: K AST → IR Compilation                ││
│  │         (symbols, constraints, activities, TVMs)            ││
│  └─────────────────────────┬───────────────────────────────────┘│
│                            ▼                                     │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │        Layer 3: Core Symbolic Engine ("new BAE")            ││
│  │  - Symbolic state (SymbolId → Term/Value)                   ││
│  │  - Constraint store with domain tags                        ││
│  │  - BAE-style search (guess + backtrack)                     ││
│  │  - Planning/scheduling primitives                           ││
│  └─────────────────────────┬───────────────────────────────────┘│
│                            ▼                                     │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │            Layer 4: Solver Adapters                         ││
│  │  ┌───────────┐  ┌───────────────┐  ┌──────────────┐         ││
│  │  │ Z3 (SMT)  │  │ CP/MiniZinc   │  │ BAE Heuristic│         ││
│  │  │ (primary) │  │ (future)      │  │ (fallback)   │         ││
│  │  └───────────┘  └───────────────┘  └──────────────┘         ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### Why IR-First (Not Deep EDSL Java)

The old BAE approach: `K → deep EDSL Java → heavy reflection → custom solver`

Problems:
1. Execution and representation entangled
2. Hard to add new host languages
3. Hard to swap solver backends
4. Difficult to test core logic in isolation

New approach: `K/Java/Python → AST → IR → engine → solvers`

Benefits:
1. Core engine operates only on IR (language-agnostic)
2. Easy to add new front-ends
3. Pluggable solver backends
4. Unit-testable with synthetic IR programs
5. Less reflection, more compile-time verification

---

## IR Design Sketch

### Sorts (Types in the IR)

```
Sort =
  Bool | Int | Real | String | Time
  | Object(ClassId)          // heap object
  | ActivityType(ClassId)    // activity instance
  | TVM(Sort valueSort)      // TimeVaryingMap<valueSort>
  | Array(Sort index, Sort elem)
  | Map(Sort key, Sort value)
  | Set(Sort elem)
  | Tuple(List<Sort>)
  | Uninterpreted(String name)  // for opaque domains
```

### Symbols

```
Symbol = {
  id: SymbolId,
  name: String,
  sort: Sort,
  origin: SymbolOrigin,  // ProgramVar | FreshFromSolver | Parameter
  mutable: Boolean
}
```

### Terms

```
Term =
  | Var(SymbolId)
  | Const(Sort, LiteralValue)
  | App(Operator, List<Term>)     // operator application
  | FieldAccess(Term obj, FieldId)
  | TVMRead(Term tvm, Term time)  // tvm.getValue(time)
  | ActivityField(Term activity, FieldId)
  | OpaqueCall(CallId, List<Term> args, Option<LiteralValue> cached)
```

### Operators

```
Operator =
  // Boolean
  And | Or | Not | Implies | Iff
  // Comparison
  | Eq | Neq | Lt | Le | Gt | Ge
  // Arithmetic
  | Add | Sub | Mul | Div | Mod | Neg
  // Strings (map to Z3 str theory)
  | StrConcat | StrLen | StrAt | StrSubstr
  | StrContains | StrPrefixOf | StrSuffixOf
  | StrReplace | StrToInt | IntToStr
  | StrInRe  // regex match
  // Collections
  | SetUnion | SetInter | SetDiff | SetMember
  | MapSelect | MapStore
  // Temporal
  | Before | After | Overlaps | Meets
```

### Constraints

```
Constraint = {
  id: ConstraintId,
  expr: Term,           // must be Bool-typed
  tags: Set<Tag>,       // Domain_Arith, Domain_String, Solver_Friendly, etc.
  soft: Boolean,        // soft constraint for optimization
  weight: Double,       // weight if soft
  source: SourceLocation
}

Tag = Domain_Arith | Domain_Bool | Domain_String | Domain_Time
    | Domain_TVM | Domain_Scheduling | Domain_Heap | Domain_Opaque
    | Solver_Friendly | Solver_Avoid | UserTag(String)
```

### Activities and TimeVaryingMaps

```
ActivityInstance = {
  id: ActivityId,
  classId: ClassId,
  fields: Map<FieldId, SymbolId>,  // includes start, end, duration
  localConstraints: List<ConstraintId>
}

TVMInstance = {
  id: TVMId,
  valueSort: Sort,
  symbol: SymbolId,
  piecewiseConstant: Boolean,
  monotoneTime: Boolean
}
```

### Opaque Calls

```
OpaqueCallDescriptor = {
  id: OpaqueCallId,
  name: String,           // e.g., "java.lang.Math.sqrt"
  argSorts: List<Sort>,
  resultSort: Sort,
  semantics: OpaqueSemantics
}

OpaqueSemantics =
  | BlackBox            // no SMT encoding; evaluate concretely
  | HasSMTEncoding(Id)  // known encoding to SMT
  | HasInverseImage     // can backwards-compute (BAE-style)
  | Hybrid              // try SMT, fall back to concrete
```

### Solver Interface

```
SolverQuery = {
  symbols: List<SymbolId>,      // unknowns to solve for
  constraints: List<ConstraintId>,  // subset to send
  objective: Option<Objective>,
  timeoutMs: Int
}

SolverResult = Sat(Model) | Unsat | Unknown | Timeout

Model = {
  assignments: Map<SymbolId, LiteralValue>,
  partialOk: Boolean,   // true if incomplete but usable
  unchecked: List<ConstraintId>  // constraints not verified
}
```

---

## How klang Fits

**klang as the K front-end**:
- Keep klang for: parsing, AST representation, type checking
- Add: K AST → IR compilation (a new backend alongside the existing Z3 encoder)

**New core module** (could be in klang or separate):
- IR types as above
- Symbolic engine with BAE-style search
- Solver adapters (Z3 first, others later)

This means:
- K syntax and static semantics owned by klang
- New runtime semantics (hybrid solver, partial solutions, planning) in new IR-based core
- Existing klang Z3 backend preserved as legacy/comparison mode

---

## Python Front-end Option

For a future Python front-end:

```python
from kpy import constraint_block, activity, TimeVaryingMap, Symbol, IntSort

x = Symbol("x", IntSort)

@constraint_block
def model():
    return (x > 0) & (x < 10)

@activity
class HeatUp:
    start: Time
    end: Time
    
    @constraint_block
    def constraints(self):
        return self.end - self.start >= Duration("PT1H")
```

Implementation: Use Python's built-in `ast` module to:
1. Find decorated functions/classes
2. Translate Boolean expressions to IR Terms
3. Build Constraints from returned expressions

MyPy could be added later for richer type inference, but `ast` is sufficient to start.

---

## Hybrid Solving Strategy

The key insight from BAE that must be preserved:

**"The programmer controls complexity"** - They decide how hard a problem to create for the solver.

### Solving Flow

1. **Collect constraints** from IR with tags
2. **Partition** into solver-friendly vs. opaque
3. **Send solver-friendly subset to Z3**:
   - Get `Sat(model)` → integrate assignments
   - Get `Unsat` → prune search branch
   - Get `Unknown/Timeout` → mark unchecked, continue with heuristics
4. **For opaque calls in solution**:
   - If inputs are concrete: evaluate concretely, add result as constraint
   - If has inverse image: use it to backwards-compute valid inputs
   - Otherwise: treat result as fresh symbolic with minimal constraints
5. **BAE-style search** for remaining unknowns:
   - Guess values at choice points
   - Re-run constraint propagation
   - Backtrack on contradiction
6. **Return partial solution** if @bestEffort, otherwise continue until complete

### Interaction with @timeout and @bestEffort

Already implemented in klang:
- `@timeout(N)` - solver gets N ms time budget per query
- `@bestEffort` - return partial solution on timeout instead of failing

These annotations naturally integrate with the hybrid solving:
- Timeout causes `Unknown` from Z3, triggering heuristic fallback
- BestEffort allows returning whatever partial solution exists

---

## References

- CEGAR: Clarke et al., "Counterexample-Guided Abstraction Refinement"
- KLEE: Cadar et al., "KLEE: Unassisted and Automatic Generation of High-Coverage Tests"
- Z3: de Moura & Bjørner, "Z3: An Efficient SMT Solver"
- CLP(FD): Jaffar & Maher, "Constraint Logic Programming: A Survey"
- BAE Paper: "Towards Integrating Constraint Solving and Programming" (~/git/aiProgrammingPaper)
- K Language: Havelund, "K: A Language for K" (MODELSWARD 2016)
- Rosette: Torlak & Bodik, "Growing Solver-Aided Languages with Rosette"
- Leon: Kneuss et al., "Synthesis Modulo Recursive Functions"
- Dafny: Leino, "Dafny: An Automatic Program Verifier"

---

## Related Repositories

- **klang** (this repo): SMT-based K solver with Z3
- **kservices**: K with custom BAE solver, JVM integration, TimeVaryingMap
- **bae**: Behavior Analysis Engine - the solver implementation
- **aiProgrammingPaper**: Research paper describing inverse image approach
- **utils**: Supporting utilities library
- **sysml**: SysML integration library

---

## Research Session Reference

The ChatGPT research session that informed this design is preserved in:
- `docs/declarative_language_research_session.txt` (plain text conversion)
- `docs/declarative language research chatgpt session.docx` (original)

Key insights from that session:
1. K is very close to the "solver-aided language" vision but focused on SysML modeling
2. BAE's inverse image interface is the key innovation for opaque function support
3. An IR-first architecture with pluggable solver backends is recommended
4. Z3's modern string/sequence theories make reimplementation viable
5. Python's `ast` module is sufficient for a Python front-end
6. Preserve BAE's "partial solution" and "programmer controls complexity" properties

---

## Notes

- Created: December 7, 2025
- Updated: December 7, 2025 - Added IR design from research session
- Status: Draft - gathering requirements and exploring alternatives
- The inverse image approach is the key innovation in BAE that allows reasoning about opaque functions
- TimeVaryingMap and Activity are critical for planning applications (Europa Clipper mission)
