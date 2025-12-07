# K Language Test Results Summary

## Date: December 6, 2025 (Updated)

## Test Infrastructure

The test runner (`run-tests.sh`) now supports multiple test directories:
- `src/tests/` - Core regression tests (66 tests)
- `src/test/` - New feature tests (5 tests)
- `src/examples/` - Example K files (44 tests)

### Usage

```bash
./run-tests.sh              # Run core tests only (src/tests/)
./run-tests.sh -all         # Run all tests from all directories
./run-tests.sh -new         # Run src/test/ only
./run-tests.sh -examples    # Run src/examples/ only
./run-tests.sh -test <file> # Run single test
./run-tests.sh -filter <pattern>  # Run tests matching pattern
./run-tests.sh -string      # Run string-related tests
./run-tests.sh -opt         # Run optimization tests
./run-tests.sh -v           # Verbose mode
```

## Test Results Summary

### Core Tests (src/tests/) - Latest Run

| Metric | Count |
|--------|-------|
| Total | 66 |
| ✅ Passed | 64 |
| ❓ Unknown | 2 |
| 💥 Crashed | 0 |
| **Pass Rate** | **96%** |

### Known Baseline Failures (Expected)

| Test | Reason | Status |
|------|--------|--------|
| testsmt2.k | Uses unsupported `Duration` type | Expected failure |
| testsmt16.k | Uses unsupported `Time` type | Expected failure |

These two tests use `Duration` and `Time` built-in types that are not fully implemented in the type checker. They were failing before our changes and remain expected failures.

### New Feature Tests (All Passing ✅)

| Test | Feature | Status |
|------|---------|--------|
| opt1.k | Minimize objective | ✅ PASSED |
| opt2.k | Maximize objective | ✅ PASSED |
| opt3.k | Multi-objective with weights | ✅ PASSED |
| regex1.k | String regex matching | ✅ PASSED |
| besteffort1.k | @bestEffort/@timeout | ✅ PASSED |
| timeout1.k | @timeout annotation | ✅ PASSED |
| stringcase1.k | String equality | ✅ PASSED |
| stringconcat1.k | String concatenation (+) | ✅ PASSED |
| stringint1.k | String-Int conversion | ✅ PASSED |
| stringops1.k | length, substring, charAt | ✅ PASSED |
| stringops2.k | indexOf, replace | ✅ PASSED |
| stringpred1.k | startsWith, endsWith, contains | ✅ PASSED |

### Negative Tests (Correctly Failing)

| Test | Purpose | Status |
|------|---------|--------|
| tc1.k, tc2.k, tc3.k | Test type check errors | ✅ PASSED (expected type check failure) |
| scope1.k | Test duplicate declaration detection | ✅ PASSED (expected type check failure) |
| inheritance5.k | Test cyclic inheritance detection | ✅ PASSED (expected type check failure) |

### Regression Analysis

**Baseline** (before feature additions): 54/56 passed (2 known failures: testsmt2.k, testsmt16.k)
**Current**: 64/66 passed (same 2 known failures)

| Metric | Count |
|--------|-------|
| Original tests | 56 |
| New tests added | 10 |
| Total tests | 66 |
| Originally passing | 54 |
| Currently passing | 64 |
| **Regressions** | **0** |

✅ **No regressions!** All originally passing tests still pass, and all new feature tests pass.

### Test Categories Breakdown

| Category | Tests | Status |
|----------|-------|--------|
| Inheritance tests | inheritance1-12.k | ✅ All passing |
| SMT tests | testsmt1-20.k (except 2, 16) | ✅ All passing |
| Set operations | testsets1-6.k | ✅ All passing |
| Type check tests | tc1-4.k | ✅ All passing |
| Unsatisfiable tests | unsat1-5.k | ✅ All passing |
| Optimization tests | opt1-3.k | ✅ All passing |
| String tests | string*.k | ✅ All passing |
| Best-effort/Timeout | besteffort1.k, timeout1.k | ✅ All passing |
| Regex tests | regex1.k | ✅ All passing |

## Important Build Notes

⚠️ **The project MUST be compiled with Java 8** due to Z3 native library compatibility.

```bash
# Using SDKMAN to switch to Java 8
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 8.0.462-zulu
mvn clean compile
./run-tests.sh
```

If you compile with a newer Java version (e.g., Java 21), the tests will fail with:
```
java.lang.UnsupportedClassVersionError: k/frontend/Main has been compiled by a more recent version of the Java Runtime
```

## New Tests Added

```
src/tests/
├── opt1.k            # Minimize objective
├── opt2.k            # Maximize objective  
├── opt3.k            # Multi-objective with weights
├── regex1.k          # Regex matching
├── besteffort1.k     # Best-effort solving
├── timeout1.k        # Timeout annotation
├── stringcase1.k     # String comparisons
├── stringconcat1.k   # String concatenation
├── stringint1.k      # String/Int conversion
├── stringops1.k      # String operations (length, substr, charAt)
├── stringops2.k      # String operations (indexOf, replace)
└── stringpred1.k     # String predicates (startsWith, endsWith, contains)
```
