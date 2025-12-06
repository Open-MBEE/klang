# K Language Test Infrastructure

## Test Directories

### `src/tests/` - Original Test Suite
- **54 test files** covering core K language features
- Tests inheritance, type checking, scoping, etc.
- Has a `baseline.json` file with expected results
- Used for regression testing

### `src/test/` - New Tests
- Recently added tests (string operations, etc.)
- **7 files** including:
  - `SimpleStringTest.k`
  - `StringConcatTest.k`
  - `StringOperationsTest.k`
  - `SimpleStringOpsTest.k`
  - `TestCrossPackage.scala`

## Running Tests

### Using the Test Runner Script

```bash
./run-tests.sh
```

#### Run All Tests
```bash
./run-tests.sh -tests
```

This will:
1. Run all 54 tests in `src/tests/`
2. Compare results against `src/tests/baseline.json`
3. Show a summary table with:
   - TypeChecks: Whether type checking matches
   - ModelEqual: Whether the model matches
   - JSON1/JSON2Equal: JSON representation equality
   - SMTEqual: SMT output equality
   - SMTModelEqual: Z3 model equality

#### Run a Single Test
```bash
./run-tests.sh -test as1.k
```

Or run interactively (it will prompt for filename):
```bash
./run-tests.sh -test
```

#### Update Baseline
When you've made intentional changes and want to update the expected results:
```bash
./run-tests.sh -tests -baseline
```

### Using Individual Test Scripts

For string tests, there are dedicated scripts:
```bash
./test-simplestringtest.sh
./test-simplestringtest-debug.sh  # Shows debug output
```

### Direct Invocation

You can also run tests directly:
```bash
# Run specific K file
./export/k src/tests/as1.k

# Run all tests via Java
java -cp "target/classes:lib/com.microsoft.z3.jar:export/lib/scalalib/*:..." \
    -Djava.library.path="lib" \
    k.frontend.Main -tests
```

## Test Flags (Frontend.scala)

The K frontend (`k.frontend.Main`) supports these test-related flags:

### `-tests`
Runs all tests in `src/tests/` and compares against baseline

**Implementation**: `doTests(saveBaseline: Boolean)` in `Frontend.scala:527`

**Process**:
1. Finds all `.k` files in `src/tests/`
2. Runs each test via `doTest(file, false)`
3. Compares results with `baseline.json`
4. Generates comparison table

### `-test`
Runs a single test interactively or by name

**Implementation**: Line 189 in `Frontend.scala`

**Process**:
1. Prompts for test case name
2. Runs the test
3. Compares detailed results with baseline
4. Saves baseline and current results to separate files for comparison

### `-baseline`
Used with `-tests` to save current results as new baseline

**Usage**: `./run-tests.sh -tests -baseline`

## Test File Examples

### src/tests/as1.k
```k
class A {
  x : Int
  y : Int
}

class B extends A {
  z : Int
}

bobj : B

class C 

req (bobj as C).y = 4
```

This tests type checking for invalid casts (should fail).

### src/tests/inheritance1.k
Tests basic inheritance and property access.

### src/tests/tc1.k - tc15.k
Type checking tests (15 tests).

### src/tests/scope1.k - scope9.k
Scoping and visibility tests (9 tests).

## Test Infrastructure Details

### Baseline File Format
`src/tests/baseline.json` stores expected results for each test:

```json
{
  "as1.k": {
    "typeChecks": true/false,
    "model": "...",
    "json1": "...",
    "json2": "...",
    "smt": "...",
    "smtModel": "..."
  },
  ...
}
```

### Test Comparison
Tests are compared on multiple dimensions:
1. **TypeChecks**: Does it type check?
2. **ModelEqual**: Is the parsed model the same?
3. **JSON1/JSON2Equal**: JSON representations match?
4. **SMTEqual**: Generated SMT matches?
5. **SMTModelEqual**: Z3 solution matches?

### Test Execution
Each test goes through:
1. **Parse** (ANTLR)
2. **Type Check** (TypeChecker.scala)
3. **SMT Generation** (K2Z3.scala)
4. **Z3 Solving** (via Java API)
5. **Model Extraction** (if successful)

## Known Issues

### Z3 Crash on Some Tests
Some tests may cause Z3 native library crashes:
```
SIGSEGV at Z3_ast_vector_size
```

This is often due to:
- Incompatible Z3 versions
- Memory issues with complex models
- Known Z3 bugs with certain patterns

**Workaround**: Run tests individually instead of all at once

### Java Version Sensitivity
Tests require Java 8 with native Z3 libraries. Using Java 11+ will cause crashes.

**Solution**: Use Java 8 (automatically handled by run-tests.sh)

## Adding New Tests

### For src/tests/ (Regression Suite)
1. Create `mytest.k` in `src/tests/`
2. Run: `./run-tests.sh -test mytest.k`
3. Verify it works as expected
4. Update baseline: `./run-tests.sh -tests -baseline`

### For src/test/ (Development Tests)
1. Create `MyTest.k` in `src/test/`
2. Run: `./export/k src/test/MyTest.k`
3. Create optional script: `test-mytest.sh`

## Testing During Development

### Quick Test (No Baseline)
```bash
./export/k src/tests/as1.k
```

### Test String Operations
```bash
./export/k src/test/StringOperationsTest.k
```

### Full Regression Test
```bash
./run-tests.sh
```

Shows (safe mode by default):
```
Test Summary:
  Total:   54
  ✅ Passed: 52
  ❌ Failed: 2
  💥 Crashed: 0
```

**96.3% pass rate!** (52/54 tests)

#### Known Failing Tests
- `testsmt2.k` - Uses `Duration` as class name, conflicts with primitive type
- `testsmt16.k` - Uses `Time` as class name, conflicts with primitive type

These are pre-existing issues where test files use built-in type names as class names, causing parse/type check conflicts.

## Maven/Ant Integration

### Maven
The `pom.xml` doesn't currently have a test phase configured, but could be added:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>2.22.2</version>
</plugin>
```

### Ant (build.xml)
Has test targets defined but they're Windows-specific:
- `Klang TEST`
- `Klang TESTS`
- `Klang TESTS SAVE`

These need updating for cross-platform use.

## Summary

**Easy way to run tests**:
```bash
# Run all regression tests
./run-tests.sh -tests

# Run one test
./run-tests.sh -test as1.k

# Update baseline after changes
./run-tests.sh -tests -baseline
```

**Two test directories**:
- `src/tests/` - 54 regression tests with baseline
- `src/test/` - New development tests (string ops)

**Test runner created**: `run-tests.sh` handles Java version and classpath automatically

