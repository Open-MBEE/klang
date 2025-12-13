# Examples Test Report

Generated: 2024-12-12

## Summary Statistics
- **Passed: 26/47** (55%)
- **Type check failures: 15**
- **Parse failures: 4**
- **Other failures: 2**

## Detailed Results

### ✅ Passing Tests (26)

| File | Notes |
|------|-------|
| a.k | Class hierarchy test |
| borges.k | Event duration calculation |
| c.k | Class hierarchy test |
| conservative-extension.k | Proof constraints |
| d.k | Class hierarchy test |
| e.k | Simple class |
| f.k | Simple class |
| Fruits.k | Type hierarchy test |
| FunctionSynthesis.k | Query solving |
| lightswitch.k | State machine |
| mathutil.k | Math utilities |
| nesting.k | Nested classes |
| orbital-petals.k | Activity scheduling |
| order-transformed.k | Event ordering |
| pkg1.k | Package test |
| pkg2.k | Package test |
| planning-simple.k | Planning constraints |
| prepost.k | Pre/post conditions |
| scheduling.k | Event scheduling |
| seq_vs_array.k | Sequence operations |
| Shapes.k | Geometric shapes |
| sm.k | State machine |
| stakr.k | Activity scheduling |
| StringDemo.k | String operations |
| sysml.k | SysML modeling |
| testsmt.k | SMT solving test |

### ❌ Type Check Failures (15)

| File | Error | Analysis | Status |
|------|-------|----------|--------|
| b.k | `freal declared multiple times` | Multiple class inheritance declares same field - **language limitation** | Expected |
| Bank.k | `customers >= accounts does not type check` | Set comparison not supported between different types | Expected |
| bnf.k | `substring requires exactly 2 arguments` | K only supports 2-arg substring, Java has 1-arg overload | **Limitation** |
| DSN_Pass.k | `Redefining annotation ID` | Duplicate annotation definition | Expected |
| employee.k | `Cannot infer type for 'age'` | Missing type annotation | Test file issue |
| fibonacci.k | `Non-unit type expression found in function body` | Assignment statement not allowed in function | **Limitation** |
| flight.k | `Redefining annotation doc` | Duplicate annotation definition | Expected |
| library.k | `Cannot infer type for 'size'` | Missing type annotation | Test file issue |
| math.k | `integrate already defined` | Duplicate function definition | Expected |
| order.k | `Unexpected type info for given expression` | Complex type handling issue | Potential bug |
| small.k | `Unknown type in constructor call: g -> g.garageValue` | Lambda type inference issue | **Limitation** |
| spacecraft-annotations.k | `Unexpected expression type: $result.length AnyType` | Function return value handling | Potential bug |
| TrajectoryTimeline2.k | `Could not find Real * Real` | Operator overloading issue | Potential bug |
| util.k | `Non tuple type found with tuple indexing` | Tuple indexing on non-tuple | Expected |
| WSTS.k | `Cannot infer type for 'Functioncall'` | Missing type annotation | Test file issue |

### ❌ Parse Failures (4)

| File | Analysis | Status |
|------|----------|--------|
| k.k | Self-referential K syntax - uses K to define K grammar | **Known limitation** |
| spacecraft-aspects.k | Aspect-oriented syntax not fully supported | **Known limitation** |
| TrajectoryTimeline.k | Complex syntax not supported | **Known limitation** |
| WSTS2.k | Complex syntax not supported | **Known limitation** |

### ❌ Other Failures (2)

| File | Error | Analysis | Status |
|------|-------|----------|--------|
| GravityScience.k | `key not found: assoc JupiterTwoBody` | Association declarations not fully supported | **Known limitation** |
| spacecraft.k | `Import nasa.jpl.physics could not be found` | Missing dependency | Test file issue |

## Categorization

### Known Language Limitations (Not bugs)
- Single-argument `substring()` not supported (only 2-arg version)
- Assignment statements (`x := y`) not allowed in function bodies
- Complex lambda type inference
- Association declarations (`assoc`)
- Aspect-oriented programming syntax
- Self-referential grammar definitions

### Test File Issues (Should be fixed or moved)
- employee.k - Missing type annotations
- library.k - Missing type annotations  
- spacecraft.k - Missing import dependency
- WSTS.k - Missing type annotations

### Investigated "Potential Bugs" - Actually Language Limitations

| File | Error | Analysis | Status |
|------|-------|----------|--------|
| order.k | `Cannot retrieve owning decl for power_on` | Uses `event` shorthand for top-level instances - SMT generation can't find owning class | **Language limitation** |
| spacecraft-annotations.k | `$result.length AnyType` | Uses `$result` in postcondition which requires special handling + missing imports | **Language limitation** |
| TrajectoryTimeline2.k | `Could not find Real * Real` | Class extends tuple type `Real * Real` - tuple inheritance not supported | **Language limitation** |

### Expected Behavior (Valid errors)
- Multiple field declarations in inheritance
- Duplicate annotation definitions
- Duplicate function definitions
- Type mismatches in comparisons
- Tuple indexing on non-tuples

## Recommendations

1. **Documentation**: The parse failures and language limitations should be documented
2. **Test cleanup**: Move broken/legacy examples to an `examples/legacy/` folder
3. **Bug investigation**: The 3 potential bugs should be investigated further
4. **Test file fixes**: Add type annotations to employee.k, library.k, WSTS.k
