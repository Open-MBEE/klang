# K Language Roadmap

## Vision: K Written in K

The long-term goal is for K to be **self-hosting** - the K language implementation should itself be written in K wherever possible. This provides:

1. **Dogfooding** - We use our own language, finding issues and improvements
2. **Simplicity** - K constraints are often clearer than Scala/Java code
3. **Flexibility** - K programs can use different solvers (Z3, CVC5, MiniZinc, etc.)
4. **Elegance** - A language that can describe its own semantics

## Completed

### Type Checking via K (2026-01-01)
- KTypeChecker generates a K program encoding type constraints
- Type variables are Int (type IDs), type rules are constraints
- Z3 solves the constraints; UNSAT means type error
- No longer depends on the Scala TypeChecker

### Scoping as Constraints (2026-01-02)
- Variable scoping is encoded in the generated K program
- Type variables use qualified names: `_ty_global_x`, `_ty_ClassName_x`
- Scope lookup searches current class → parent classes → global
- Inheritance relationships are registered for proper scope resolution
- This demonstrates the "K written in K" principle: scoping rules are constraints

## In Progress

### Remove Legacy TypeChecker
- TypeChecker still used for building SMT state (exp2Type, etc.)
- Goal: move this state building to TypeResolver or eliminate need for it
- Currently blocked by property-as-constraint and other complex patterns

## Completed

### Deferred Subclass Instance Strategy (Heap CEGAR Optimization) ✅
- **Implemented**: 2026-01-19
- **Problem**: Heap CEGAR was creating subclass instances immediately when parent
  class instances were required. This inflated models with unnecessary objects.
- **Solution**: Defer subclass instance creation to second CEGAR iteration:
  - Iteration 1: Skip subclass propagation (simpler models)
  - Iteration 2+: Include subclass instances if needed
- **Implementation**:
  - `ASTOptions.deferSubclassInstances` (default true)
  - `ASTOptions.cegarIteration` tracks current iteration
  - `propagateInstancesToSubclasses()` skips on iteration 1
- **Benefit**: Simpler solutions found faster; more minimal models
- **See**: [HEAP_CEGAR_STRATEGIES.md](HEAP_CEGAR_STRATEGIES.md) for details

## Future Opportunities

### Test Infrastructure as K
Instead of 600+ lines of shell script parsing output:
```k
class TestCase {
  file : String
  expectedResult : Result  // SAT, UNSAT, ERROR
  actualResult : Result

  req expectedResult = actualResult
}
```

### Solver Selection as K
Express heuristics as constraints:
```k
class SolverChoice {
  hasQuantifiers : Bool
  hasDynamicHeap : Bool
  complexity : Complexity
  solver : Solver

  req hasQuantifiers => solver != Optimize
  req hasDynamicHeap => solver = HeapCEGAR
}
```

### Inheritance Resolution as K
Diamond inheritance and field shadowing as constraint satisfaction.

### Baseline Comparison as K
Model equivalence checking using K's equality constraints.

### K→SMT Translation as K
The biggest win: express the K→SMT-LIB2 translation as a K program:
```k
class SMTTranslation {
  input : KExpression
  output : String

  req (input.isIntLiteral => output = input.value.toString)
  req (input.isBinExp && input.op = "+" =>
       output = "(+ " + translate(input.left) + " " + translate(input.right) + ")")
}
```
This would allow K to describe its own compilation semantics declaratively.

### Other Code Generation as K
Transform AST to other target languages (Scala, Java, Python) via K programs.

## Principles

1. **Prefer K over Scala/Java** for logic that is naturally constraint-based
2. **Keep orchestration in Scala** for performance-critical loops and I/O
3. **Test K features using K** - meta-circular testing
4. **Document in K** - examples are executable specifications
