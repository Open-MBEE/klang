# K Language Enhancement Session Summary

## Date: December 6, 2025

## Session Overview

This document captures the plans, implementations, and discussions from our Copilot chat session focused on enhancing the K language with advanced Z3 solver features.

---

## Part 1: Initial Goals and Feature Planning

### User's Primary Goals

1. **Anytime Best-Effort Solutions** - Don't wait forever for an answer; get partial/approximate solutions when full solving takes too long
2. **Support for Opaque (Black Box) Functions/APIs** - Import JVM/Python libraries, create objects, call functions, and have constraints on them

### Z3 Features We Identified for Implementation

Based on the newer Z3 version capabilities, we identified these features to make K more expressive:

#### 1. String Support (Implemented ✅)
- **String Type**: Native `String` type in K
- **Operations**: `length`, `substring`, `charAt`, `indexOf`, `replace`
- **Predicates**: `startsWith`, `endsWith`, `contains`
- **Concatenation**: Using `+` operator
- **Conversions**: `toInt`, `fromInt` (String ↔ Int)

#### 2. Regex Support (Implemented ✅)
- **`matches`** operator for pattern matching
- Example: `name matches "[A-Z][a-z]*"` 

#### 3. Optimization/Objectives (Implemented ✅)
- **`@minimize`** annotation - find solutions minimizing an expression
- **`@maximize`** annotation - find solutions maximizing an expression
- **Weighted multi-objective** optimization support

#### 4. Best-Effort Solving (Implemented ✅)
- **`@bestEffort`** annotation - return partial solutions when full solving is hard
- **`@timeout(ms)`** annotation - time-limited solving

#### 5. Anytime Solving & Sampling (Implemented ✅)
- **`K2Z3.interrupt()`** - Stop solving and return best result found
- **`K2Z3.requestSample()`** - Snapshot current solution without stopping
- **`K2Z3.requestPause()`** / **`K2Z3.resume()`** - Pause and resume solving
- **`K2Z3.getSampleAsConstraints`** - Get solution as K constraints
- **`K2Z3.exportSolutionAsK`** - Export solution as K code snippet
- SIGINT (Ctrl+C) handler for CLI interruption

#### 6. Incremental Solving (Planned - High Interest)
- Push/pop assertion contexts
- Efficient for exploring variations
- Would enable "what-if" analysis scenarios

---

## Part 2: Features NOT Yet Implemented (Future Work)

### A. Interrupt/Anytime Solving (Implemented ✅)
**What was implemented:**
- `K2Z3.interrupt()` function callable from any thread
- `@volatile interrupted` flag for cooperative cancellation
- SIGINT (Ctrl+C) signal handler for CLI interruption
- `solvingInProgress` flag to track when solver is active
- `bestModelSoFar` to return partial results on interrupt
- `iterationsCompleted` for progress reporting

### B. Sample/Pause/Resume (Implemented ✅)
**What was implemented:**
- `K2Z3.requestSample()` - Get current best solution without stopping
- `K2Z3.getSample` / `K2Z3.getSampleAsConstraints` - Access sampled solution
- `K2Z3.requestPause()` - Pause solver, preserve state
- `K2Z3.resume()` - Continue from paused state
- `K2Z3.canResume` - Check if resume is possible

### C. Solution as Constraints (Implemented ✅)
**What was implemented:**
- `K2Z3.modelToConstraints(model)` - Convert Z3 model to K constraints
- `K2Z3.getBestModelAsConstraints` - Get best solution as constraints
- `K2Z3.exportSolutionAsK` - Export as K code with metadata

**Use cases:**
1. Check consistency of partial solutions by adding constraints to original model
2. Export solutions for reproducibility
3. Create "fixed" versions of partial solutions
4. Compare different solving runs

**Behavior:**
- During solving, Ctrl+C triggers interrupt() instead of exit
- Web applications can call `K2Z3.interrupt()` via API
- Best solution found so far is returned with warning
- When not solving, Ctrl+C exits normally

**API for web applications:**
```scala
K2Z3.interrupt()      // Request interruption  
K2Z3.wasInterrupted   // Check if interrupted
K2Z3.clearInterrupt() // Reset for new solve
```

### B. Incremental Solving (Future Work)
**Why it's valuable:**
- Efficiently solve related problems by reusing solver state
- Push/pop assertion contexts
- Perfect for "what-if" analysis

**Potential K syntax:**
```k
@incremental
class Scenario {
  // base constraints
  
  @push
  fun variant1() { /* additional constraints */ }
  
  @pop  
  fun reset() { /* back to base */ }
}
```

### C. Opaque/Black-Box Function Support
**User's Goal:** Import JVM/Python libraries and constrain their behavior

**Challenges:**
- Z3 can't reason about arbitrary code
- Need to model function behavior abstractly

**Potential Approaches:**
1. **Uninterpreted Functions** - Declare function signature, let Z3 find consistent interpretations
2. **Abstract Specifications** - User provides pre/post conditions
3. **Concrete Execution + Symbolic** - Hybrid approach

**Potential K syntax:**
```k
@external("java:com.example.MyClass")
class MyAPI {
  @uninterpreted
  fun compute(x: Int): Int
  
  // User-provided specification
  req forall(x: Int) . compute(x) >= 0
  req forall(x: Int) . compute(x) <= x * 2
}
```

### D. Quantifiers (Partial Support Exists)
- `forall` and `exists` quantifiers
- May need enhancement for better Z3 integration

### E. Sequences and Arrays
- Z3 has native sequence theory
- Could complement existing Set/Bag support

### E. Floating Point
- Z3 supports IEEE floating point
- K currently focuses on Int/Real

---

## Part 3: Implementation Summary

### Files Created/Modified

**New Files:**
| File | Purpose |
|------|---------|
| `src/k/frontend/SolverEnhancements.scala` | Utility functions for new solver features |
| `docs/SOLVER_IMPLEMENTATION_SUMMARY.md` | Technical documentation |
| `docs/features/ADVANCED_SOLVER_FEATURES.md` | Feature documentation |
| `regenerate-parser.sh` | Script to regenerate ANTLR parser |

**Modified Files:**
| File | Changes |
|------|---------|
| `src/grammar/Model.g4` | Added String type and operations |
| `src/k/frontend/AbstractSyntax.scala` | String expression AST nodes |
| `src/k/frontend/KScalaVisitor.scala` | Parse tree visitor for strings |
| `src/k/frontend/TypeChecker.scala` | String type checking |
| `src/k/frontend/ReservedAnnotations.scala` | @minimize, @maximize, @bestEffort, @timeout |
| `run-tests.sh` | Enhanced test runner with filtering |

**New Tests (12 total):**
```
src/tests/
├── stringcase1.k      # String equality
├── stringconcat1.k    # String concatenation
├── stringint1.k       # String/Int conversion
├── stringops1.k       # length, substring, charAt
├── stringops2.k       # indexOf, replace
├── stringpred1.k      # startsWith, endsWith, contains
├── opt1.k             # Minimize objective
├── opt2.k             # Maximize objective
├── opt3.k             # Multi-objective
├── regex1.k           # Regex matching
├── besteffort1.k      # Best-effort solving
└── timeout1.k         # Timeout annotation
```

### Test Results

| Metric | Value |
|--------|-------|
| Total Tests | 66 |
| Passed | 64 |
| Known Failures | 2 (testsmt2.k, testsmt16.k - Duration/Time types) |
| Regressions | **0** |
| Pass Rate | **96%** |

---

## Part 4: Git Information

**Branch:** `feature/advanced-solver-features`

**Commit:** `83d12886` - "Add advanced Z3 solver features: strings, optimization, best-effort, regex"

**Base Branch:** `scala-2.13-upgrade`

---

## Part 5: Important Technical Notes

### Java Version Requirement

⚠️ **Must use Java 8** for Z3 native library compatibility.

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 8.0.462-zulu
mvn clean compile
./run-tests.sh
```

### Z3 Library Setup

The project includes platform-specific Z3 libraries:
- `lib/arm64/` - Apple Silicon (M1/M2/M3)
- `lib/x86_64/` - Intel Macs
- `lib/linux/` - Linux
- `lib/windows/` - Windows

The `select-z3-architecture.sh` script automatically selects the right libraries.

---

## Part 6: Future Roadmap Priorities

Based on our discussion, here's the recommended priority order:

### High Priority
1. **Incremental Solving** - User expressed special interest
2. **Better Opaque Function Support** - Core user requirement

### Medium Priority
3. **Enhanced Quantifier Support** - Improve forall/exists handling
4. **Sequence/Array Theory** - Complement existing collections

### Lower Priority
5. **Floating Point Support** - Specialized use cases
6. **Duration/Time Types** - Fix the 2 failing tests

---

## Part 7: Running the Project

### Quick Start
```bash
cd /Users/bclement/git/klang

# Setup Java 8
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 8.0.462-zulu

# Compile
mvn clean compile

# Run tests
./run-tests.sh           # Core tests
./run-tests.sh -all      # All tests
./run-tests.sh -string   # String tests only
./run-tests.sh -opt      # Optimization tests

# Run a single K file
./export/k src/tests/stringops1.k

# Start web server
./start-server.sh
```

### Test Runner Options
```bash
./run-tests.sh -h        # Show help
./run-tests.sh -v        # Verbose mode
./run-tests.sh -test X   # Run single test
./run-tests.sh -filter P # Filter by pattern
```

---

## Part 8: Example K Code with New Features

### String Operations
```k
class Person {
  name: String
  email: String
  
  req name.length >= 2
  req name.length <= 50
  req email.contains("@")
  req email.endsWith(".com") || email.endsWith(".org")
}
```

### Optimization
```k
class Resource {
  cost: Int
  benefit: Int
  
  req cost >= 0
  req benefit >= 0
  req cost <= 1000
  
  @maximize
  req benefit - cost  // Maximize net benefit
}
```

### Best-Effort with Timeout
```k
@bestEffort
@timeout(5000)  // 5 second limit
class ComplexProblem {
  // Many constraints...
  // Will return partial solution if full solving takes too long
}
```

### Regex Matching
```k
class Identifier {
  value: String
  
  req value matches "[a-zA-Z_][a-zA-Z0-9_]*"
  req value.length <= 64
}
```

---

## Session Notes

- Terminal output was intermittently not visible during the session (known JetBrains/Copilot issue)
- Workaround: Writing output to files and reading them back
- The project was initially compiled with Java 21, causing test failures until recompiled with Java 8

---

*This document was auto-generated to preserve session context and planning information.*

