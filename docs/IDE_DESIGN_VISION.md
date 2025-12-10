# K Language IDE Design Vision

## Overview

K is a declarative constraint programming language backed by the Z3 SMT solver, designed for modeling, verification, and specification at NASA. Unlike traditional imperative languages, K's declarative nature and constraint-solving semantics create unique opportunities and challenges for IDE support.

This document explores what an IDE experience might look like for K, covering:
1. Basic IDE features (syntax highlighting, go-to-definition)
2. Declarative-specific features (constraint visualization, solution exploration)
3. Multi-language integration (Java, Python)
4. Novel debugging concepts for constraint languages

---

## Part 1: Basic IDE Features

### 1.1 Syntax Highlighting

K has a rich syntax that would benefit from semantic highlighting:

| Token Type | Examples | Color Suggestion |
|------------|----------|------------------|
| Keywords | `class`, `req`, `fun`, `forall`, `exists`, `if`, `then`, `else`, `match`, `case` | Blue |
| Primitive Types | `Int`, `Real`, `Bool`, `String`, `Time`, `Duration` | Teal |
| Collection Types | `Set`, `Seq`, `Bag`, `OSet` | Teal (italic) |
| Operators | `=>`, `<=>`, `isin`, `union`, `inter`, `subset` | Orange |
| Constraints | `req`, `soft req` | Purple (bold) |
| Optimization | `minimize`, `maximize` | Purple |
| Annotations | `@timeout`, `@bestEffort`, `@opaque`, `@axiom` | Gray |
| Properties | Member declarations | Default |
| Functions | `fun` declarations and calls | Yellow |
| External References | Java/Python qualified names | Green |
| Literals | Numbers, strings, dates | Green |
| Comments | `--` and `==...==` sections | Gray (italic) |

**TextMate Grammar Skeleton:**
```json
{
  "name": "K",
  "scopeName": "source.k",
  "patterns": [
    {"include": "#comments"},
    {"include": "#keywords"},
    {"include": "#constraints"},
    {"include": "#types"},
    {"include": "#annotations"},
    {"include": "#strings"},
    {"include": "#numbers"}
  ]
}
```

### 1.2 Go-to-Definition / Find References

K has several kinds of symbol references:

| Reference Kind | Example | Behavior |
|----------------|---------|----------|
| **Property** | `attitude.distanceToEarth` | Jump to property declaration in class |
| **Class** | `Instrument` in `inst : Instrument` | Jump to class definition |
| **Type Parameter** | `T` in `class Foo[T]` | Jump to type parameter |
| **Function** | `totalWeight()` | Jump to function definition |
| **Constraint name** | `req OperatingPower:` | Jump to constraint |
| **Package/Import** | `import nasa.jpl.physics` | Open imported file/package |
| **External (Java)** | `java.lang.Math.sqrt` | Open in decompiler or source |
| **External (Python)** | `numpy.array` | Open Python source |
| **Quantifier variable** | `forall i : items` | Highlight scope of `i` |

**Implementation Strategy:**
1. Build a symbol table during parsing
2. Track scopes (class, function, quantifier, match case)
3. Map each identifier to its definition location
4. For external references, provide navigation to:
   - Attached source jars (Java)
   - Python stubs or source files
   - Web documentation links

### 1.3 Code Completion

Context-aware completion for K:

| Context | Suggestions |
|---------|-------------|
| After `:` in property | Types: `Int`, `Real`, `String`, `Bool`, class names, `Set[_]`, `Seq[_]` |
| After `extends` | Superclass names |
| After `.` on expression | Properties and functions of that type |
| Inside `req` | Properties in scope, operators `=>`, `<=>`, `forall`, `exists` |
| After `@` | Known annotations: `timeout`, `bestEffort`, `opaque`, etc. |
| Inside collection | `isin`, `!isin`, `subset`, `union`, `inter`, `collect`, `forall` |
| After `import` | Available K packages, Java packages, Python modules |

### 1.4 Error Diagnostics

Real-time diagnostics for:
- **Parse errors**: Syntax issues
- **Type errors**: Type mismatches from TypeChecker
- **Undefined references**: Unknown identifiers
- **Import errors**: Missing files/packages
- **Constraint warnings**: Likely unsatisfiable patterns

---

## Part 2: Declarative-Specific IDE Features

### 2.1 Constraint Visualization

Unlike imperative code where you "step through" execution, K models define a **constraint graph**. The IDE should visualize:

```
┌─────────────────────────────────────────────────────────────┐
│                    CONSTRAINT GRAPH VIEW                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│    ┌──────────┐       ┌──────────────────┐                  │
│    │cost : Int│──────▶│ req cost >= 0    │                  │
│    └──────────┘       │ req cost <= 1000 │                  │
│         │             └──────────────────┘                  │
│         │                                                   │
│         ▼                                                   │
│    ┌────────────────────────────────────┐                   │
│    │ req performance <= cost / 10      │                   │
│    └────────────────────────────────────┘                   │
│         │                                                   │
│         ▼                                                   │
│    ┌──────────────┐                                         │
│    │performance   │                                         │
│    │  : Int       │                                         │
│    └──────────────┘                                         │
│                                                             │
│  Legend: [Variable] ──▶ [Constraint]                        │
│          Hard constraint: solid line                        │
│          Soft constraint: dashed line                       │
│          Optimization: thick line                           │
└─────────────────────────────────────────────────────────────┘
```

**Interactive Features:**
- Click a variable to highlight all constraints involving it
- Click a constraint to see which variables it connects
- Color constraints by: hard/soft, satisfied/unsatisfied, active/inactive
- Show constraint dependency chains

### 2.2 Solution Explorer

When a model is satisfiable, show the solution space:

```
┌─────────────────────────────────────────────────────────────┐
│                    SOLUTION EXPLORER                        │
├─────────────────────────────────────────────────────────────┤
│  Model: OptimizationTest                    Status: SAT ✓   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Solution #1 (minimized cost):                              │
│  ├── cost = 500                                             │
│  ├── performance = 50                                       │
│  └── memory = 25                                            │
│                                                             │
│  [▶ Find More Solutions] [◀▶ Navigate] [📊 Compare]         │
│                                                             │
│  Variable Ranges (given constraints):                       │
│  ├── cost ∈ [500, 1000]    ████████████░░░░░░░░             │
│  ├── performance ∈ [50, 100]   █████░░░░░░░░░░░░            │
│  └── memory ∈ [25, 64]      ████████░░░░░░░░░░░░            │
│                                                             │
│  [🔍 Why this value?] [📐 Sensitivity Analysis]             │
└─────────────────────────────────────────────────────────────┘
```

**Features:**
- **Multiple solutions**: Request additional solutions from Z3
- **Sensitivity analysis**: What happens if we change a constraint?
- **Why this value?**: Show which constraints determined a variable's value
- **Range visualization**: Show feasible ranges for each variable

### 2.3 Unsatisfiability Analysis

When UNSAT, provide actionable diagnostics:

```
┌─────────────────────────────────────────────────────────────┐
│                    UNSAT ANALYSIS                           │
├─────────────────────────────────────────────────────────────┤
│  Model: ConflictingRequirements         Status: UNSAT ✗     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ❌ Minimal Unsatisfiable Core:                             │
│                                                             │
│  1. req MaxWeight: totalWeight <= 100      [line 45]        │
│  2. req MinItems: items.size() >= 5        [line 52]        │
│  3. req ItemWeight: forall i : items .     [line 58]        │
│                      i.weight >= 25                         │
│                                                             │
│  💡 Explanation:                                             │
│     5 items × 25 weight each = 125 > 100 max weight         │
│                                                             │
│  🔧 Suggestions:                                             │
│     • Increase MaxWeight to >= 125                          │
│     • Decrease MinItems to <= 4                             │
│     • Allow lighter items (weight >= 20 gives 5×20=100)     │
│                                                             │
│  [🎯 Highlight in Editor] [📋 Copy Report]                  │
└─────────────────────────────────────────────────────────────┘
```

### 2.4 Live Solving / Incremental Feedback

As you type, show constraint status:

```
class Spacecraft {
  weight : Real                           // ✓ Defined
  maxWeight : Real = 1000                 // ✓ Constrained  
  
  req notTooHeavy: weight <= maxWeight    // ✓ Satisfiable
  req positive: weight > 0                 // ✓ Satisfiable
  req impossible: weight > 2000            // ⚠️ Conflicts with notTooHeavy
                                          //    (weight > 2000 ∧ weight <= 1000)
}
```

**Incremental solving benefits:**
- Immediate feedback on new constraints
- Highlight conflicts as you type
- Show which constraints are redundant
- Identify always-true or always-false constraints

---

## Part 3: Multi-Language Integration

### 3.1 Java Integration

K can call Java methods via external function support:

```k
// Java import
import java.lang.Math

class Computation {
  x : Real
  y : Real
  
  // External call treated as opaque function
  req y = java.lang.Math.sqrt(x)
  req x = 100.0
}
```

**IDE Support:**
- **Auto-complete** Java class and method names
- **Go-to-definition** opens Java source/decompiled class
- **Type inference** from Java method signatures
- **Javadoc** hover tooltips
- **Import suggestions** for unresolved Java references

**Debugging Consideration:**
When the solver uses CEGAR refinement for Java calls:
1. Show which Java methods are being called
2. Display input/output pairs from concrete evaluations
3. Highlight refinement constraints added from mismatches

### 3.2 Python Integration

Design for Python integration (not limited to Jython):

```k
// Python import
import numpy as np
import scipy.optimize

class OptimizationProblem {
  coefficients : Seq[Real]
  result : Real
  
  @opaque
  req result = scipy.optimize.minimize(coefficients)
}
```

**Implementation Options:**
1. **Subprocess communication**: Call Python via subprocess, JSON marshalling
2. **GraalVM polyglot**: If using GraalPython
3. **Py4J bridge**: Python ↔ JVM communication

**IDE Support:**
- Python syntax highlighting in K files for embedded calls
- Type stubs for common Python libraries
- Go-to-definition for Python functions
- Show Python documentation on hover

### 3.3 Mixed-Language Debugging

Scenario: Debugging a K model that calls Java and Python:

```
┌─────────────────────────────────────────────────────────────┐
│              MIXED-LANGUAGE DEBUG SESSION                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  K Model: IntegratedSystem.k                                │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ class Analysis {                                    │    │
│  │   data : Seq[Real]                                  │    │
│  │   result : Real                                     │    │
│  │   validation : Bool                                 │    │
│  │                                                     │    │
│  │   req result = numpy.mean(data)         ◄── CEGAR  │    │
│  │   req validation = Validator.check(data) ◄── CEGAR │    │
│  │   req validation = true                             │    │
│  │ }                                                   │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  CEGAR Trace:                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ Iteration 1:                                        │    │
│  │   Z3 proposed: data = [1.0, 2.0, 3.0]               │    │
│  │   Python call: numpy.mean([1.0, 2.0, 3.0]) = 2.0    │    │
│  │   Java call: Validator.check([1.0, 2.0, 3.0]) = true│    │
│  │   Result: Consistent ✓                              │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  [Step Refinement] [View Z3 Model] [Inspect Python State]   │
└─────────────────────────────────────────────────────────────┘
```

---

## Part 4: Debugging a Declarative Language

### 4.1 What Does "Debugging" Mean for K?

Traditional debugging (breakpoints, stepping) doesn't apply directly. Instead:

| Traditional Concept | K Equivalent |
|---------------------|--------------|
| Breakpoint | "Pause at this constraint evaluation" |
| Step over | "Add next constraint to solver" |
| Watch variable | "Track this variable's feasible range" |
| Call stack | "Constraint dependency chain" |
| Memory state | "Current Z3 model state" |

### 4.2 Constraint-Level Debugging

**"Stepping" through constraint addition:**

```
┌─────────────────────────────────────────────────────────────┐
│              CONSTRAINT DEBUGGING                           │
├─────────────────────────────────────────────────────────────┤
│  Step 3 of 7                          [◀ Prev] [Next ▶]    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Adding constraint: req performance <= cost / 10            │
│                                                             │
│  Before:                                                    │
│    cost ∈ [0, 1000]                                         │
│    performance ∈ [0, 100]                                   │
│                                                             │
│  After:                                                     │
│    cost ∈ [0, 1000]                                         │
│    performance ∈ [0, 100]  (now linked to cost)             │
│                                                             │
│  Status: Still SAT                                          │
│  Sample solution: cost=500, performance=50                  │
│                                                             │
│  [📊 Show Constraint Graph] [🔍 Probe Variable Ranges]      │
└─────────────────────────────────────────────────────────────┘
```

### 4.3 "Why?" Debugging

For any variable value in a solution, explain why:

```
Query: Why is cost = 500?

Answer:
  1. minimize cost                        → cost should be minimal
  2. req performance >= 50                → performance must be ≥ 50
  3. req performance <= cost / 10         → cost must be ≥ performance × 10
  
  Therefore: cost ≥ 50 × 10 = 500
  Minimal satisfying value: cost = 500
```

### 4.4 Counterfactual Exploration

"What if" analysis:

```
┌─────────────────────────────────────────────────────────────┐
│              COUNTERFACTUAL EXPLORER                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Current: req maxWeight = 1000                              │
│                                                             │
│  What if maxWeight = 500?                                   │
│    → Model becomes UNSAT                                    │
│    → Conflict with: req items.size() >= 5                   │
│                     req forall i : items . i.weight >= 150  │
│                                                             │
│  What if maxWeight = 1500?                                  │
│    → Model still SAT                                        │
│    → Allows up to 10 items at weight 150                    │
│                                                             │
│  [Try Value: ____] [Find Threshold]                         │
└─────────────────────────────────────────────────────────────┘
```

### 4.5 CEGAR Debugging (for External Functions)

When using opaque functions, step through the refinement:

```
┌─────────────────────────────────────────────────────────────┐
│              CEGAR REFINEMENT DEBUG                         │
├─────────────────────────────────────────────────────────────┤
│  Iteration 3                           [◀ Prev] [Next ▶]    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Opaque function: java.lang.Math.sqrt                       │
│                                                             │
│  Z3 proposed: input = 16                                    │
│  Z3 assumed:  output = 5  (arbitrary, uninterpreted)        │
│                                                             │
│  Actual evaluation:                                         │
│    java.lang.Math.sqrt(16) = 4.0                            │
│                                                             │
│  Mismatch detected!                                         │
│  Adding refinement: sqrt(16) = 4.0                          │
│                                                             │
│  Refinements so far:                                        │
│    sqrt(4) = 2.0                                            │
│    sqrt(9) = 3.0                                            │
│    sqrt(16) = 4.0  ← new                                    │
│                                                             │
│  [Call Stack] [View Java Source] [Set Breakpoint in Java]   │
└─────────────────────────────────────────────────────────────┘
```

### 4.6 Debugging Workflow Summary

```
┌─────────────────────────────────────────────────────────────┐
│                  K DEBUG WORKFLOW                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. AUTHOR                                                  │
│     └── Write K model with constraints                      │
│                                                             │
│  2. VALIDATE (Live)                                         │
│     ├── Type checking                                       │
│     ├── Reference resolution                                │
│     └── Quick SAT check                                     │
│                                                             │
│  3. SOLVE                                                   │
│     ├── If SAT → Explore solutions                          │
│     │   ├── "Why this value?"                               │
│     │   ├── "What if...?"                                   │
│     │   └── "Find more solutions"                           │
│     │                                                       │
│     └── If UNSAT → Diagnose                                 │
│         ├── View unsat core                                 │
│         ├── Identify conflicting constraints                │
│         └── Get fix suggestions                             │
│                                                             │
│  4. REFINE (for external calls)                             │
│     ├── Step through CEGAR iterations                       │
│     ├── Inspect concrete function calls                     │
│     └── Debug Java/Python code if needed                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Part 5: Implementation Roadmap

### Phase 1: Basic IDE Support (Foundation)
- [ ] **TextMate grammar** for syntax highlighting (VS Code, Sublime, JetBrains)
- [ ] **Language Server Protocol (LSP)** skeleton
- [ ] **Parse error diagnostics**
- [ ] **Go-to-definition** for local references
- [ ] **Basic code completion**

### Phase 2: Semantic Features
- [ ] **Type checking integration** with TypeChecker.scala
- [ ] **Find all references**
- [ ] **Rename symbol**
- [ ] **Hover information** (types, documentation)
- [ ] **Import management**

### Phase 3: Solver Integration
- [ ] **Quick SAT check** command
- [ ] **Solution view** panel
- [ ] **UNSAT core** display
- [ ] **Incremental validation** as you type

### Phase 4: Advanced Debugging
- [ ] **Constraint graph visualization**
- [ ] **"Why?" explanations**
- [ ] **Counterfactual explorer**
- [ ] **CEGAR stepping**
- [ ] **Mixed-language debugging**

### Phase 5: Multi-Language Integration
- [ ] **Java import support** (class path scanning)
- [ ] **Java navigation** (decompiled sources)
- [ ] **Python import support**
- [ ] **Python navigation**

---

## Part 6: Technical Architecture

### 6.1 VS Code Extension Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   VS Code Extension                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐    ┌─────────────────────────────────────┐ │
│  │ TextMate    │    │         K Language Server          │ │
│  │ Grammar     │    │  ┌─────────────────────────────┐   │ │
│  │ (syntax)    │    │  │ Parser (ANTLR)              │   │ │
│  └─────────────┘    │  │ TypeChecker                 │   │ │
│                     │  │ Symbol Table                │   │ │
│  ┌─────────────┐    │  │ Reference Resolver          │   │ │
│  │ Webview     │    │  └─────────────────────────────┘   │ │
│  │ Panels      │    │                                     │ │
│  │ - Solutions │    │  ┌─────────────────────────────┐   │ │
│  │ - Graphs    │    │  │ K2Z3 (Solver Interface)     │   │ │
│  │ - CEGAR     │    │  │ ExternalFunctions           │   │ │
│  └─────────────┘    │  │ UnifiedSolver               │   │ │
│                     │  └─────────────────────────────┘   │ │
│                     └─────────────────────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ (Process communication)
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    External Runtimes                        │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │    Z3       │    │    JVM      │    │   Python    │     │
│  │  (Native)   │    │  (Java)     │    │  (External) │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 JetBrains Plugin Architecture

```
┌─────────────────────────────────────────────────────────────┐
│               IntelliJ IDEA Plugin                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                  K Language Module                   │   │
│  ├─────────────────────────────────────────────────────┤   │
│  │ KFileType, KLanguage                                │   │
│  │ KLexer (ANTLR-based)                                │   │
│  │ KParser (ANTLR-based)                               │   │
│  │ KPsiElement hierarchy                               │   │
│  │ KAnnotator (semantic highlighting)                  │   │
│  │ KCompletionContributor                              │   │
│  │ KFindUsagesProvider                                 │   │
│  │ KRefactoringSupportProvider                         │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                 Solver Integration                   │   │
│  ├─────────────────────────────────────────────────────┤   │
│  │ KSolverService (background solving)                 │   │
│  │ KSolutionToolWindow                                 │   │
│  │ KConstraintGraphPanel                               │   │
│  │ KDebugProcess (constraint debugging)                │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Java/Python Integration                 │   │
│  ├─────────────────────────────────────────────────────┤   │
│  │ KJavaReferenceContributor                           │   │
│  │ KPythonReferenceContributor                         │   │
│  │ (Leverages existing IntelliJ Java/Python support)   │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 6.3 Shared Language Server (LSP)

For cross-IDE support, implement a K Language Server:

```scala
// K Language Server - Scala implementation
class KLanguageServer extends LanguageServer {
  
  // Reuse existing K frontend
  private val parser = new KScalaVisitor()
  private val typeChecker = new TypeChecker()
  private val solver = new UnifiedSolver()
  
  // LSP methods
  def textDocumentCompletion(params: CompletionParams): CompletionList
  def textDocumentDefinition(params: DefinitionParams): Location
  def textDocumentReferences(params: ReferenceParams): List[Location]
  def textDocumentHover(params: HoverParams): Hover
  def textDocumentDiagnostic(params: DiagnosticParams): DiagnosticReport
  
  // K-specific extensions
  def kSolve(params: SolveParams): SolveResult
  def kExplainValue(params: ExplainParams): Explanation
  def kUnsatCore(params: UnsatCoreParams): UnsatCore
}
```

---

## Appendix: File Extension and MIME Type

| Property | Value |
|----------|-------|
| File extension | `.k` |
| MIME type | `text/x-k` |
| Language ID | `k` |
| Icon | NASA/JPL-inspired design |

---

## Summary

An IDE for K should embrace its declarative, constraint-based nature:

1. **Syntax support**: Standard highlighting, completion, navigation
2. **Constraint awareness**: Visualize constraint graphs, show dependencies
3. **Solution exploration**: Not just "run" but "solve and explore"
4. **UNSAT diagnostics**: Explain why models fail, suggest fixes
5. **Multi-language bridge**: Seamlessly navigate to Java/Python code
6. **Novel debugging**: Step through constraints, not statements

The developer experience shifts from "what does this code do?" to "what solutions satisfy these constraints?" and "why is this constraint unsatisfied?"

