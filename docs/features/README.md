# Feature Documentation

Documentation for features implemented in the K language.

## Roadmap

- **[FEATURE_ROADMAP.md](FEATURE_ROADMAP.md)** - Complete feature roadmap and planned improvements

## Type System

- **[TYPE_INFERENCE.md](TYPE_INFERENCE.md)** - Type inference for property declarations
- **[K_TYPE_CHECKER.md](K_TYPE_CHECKER.md)** - Type checker implementation details
- **[MULTIPLE_INHERITANCE.md](MULTIPLE_INHERITANCE.md)** - Multiple inheritance support
- **[NULL_LITERAL_SUPPORT.md](NULL_LITERAL_SUPPORT.md)** - Null literal handling

## String Support

Complete implementation of string types with Z3 string theory integration:

- **[STRING_SUPPORT.md](STRING_SUPPORT.md)** - Basic string type support
- **[STRING_CONCATENATION.md](STRING_CONCATENATION.md)** - String concatenation with `+` operator
- **[STRING_OPERATIONS.md](STRING_OPERATIONS.md)** - String operations (length, substring, contains, etc.)
- **[STRING_IMPLEMENTATION_COMPLETE.md](STRING_IMPLEMENTATION_COMPLETE.md)** - Complete feature summary

## External Functions

- **[EXTERNAL_FUNCTION_DEBUGGING.md](EXTERNAL_FUNCTION_DEBUGGING.md)** - Debugging external function calls

## Solver Integration

K supports multiple solver backends. See the roadmap for planned improvements:

- **Z3** (default) - General-purpose SMT solver
- **CVC5** - Better performance for string constraints (`-cvc5` flag)
- **MiniZinc** - Constraint programming solver (`-minizinc` flag)
- **BAE** - Planned integration with kservices BAE solver

Planned: `// @solver: name` comment to specify preferred solver per file (following SMT-COMP/DIMACS conventions).

## Usage Examples

See the test files in `src/test/` and `src/tests/` for working examples.

Run examples with:
```bash
./export/k src/test/SimpleStringTest.k
```

