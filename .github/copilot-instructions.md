# Copilot Instructions for K Language Project

## Project Overview
K is a constraint-based specification language with a Scala frontend that compiles to Z3 SMT solver.

## Running the Project

### Running K Files
```bash
./export/k <file.k>           # Run a single K file
./run-tests.sh                # Run all tests
./run-tests.sh -f <pattern>   # Run tests matching pattern
```

### Compiling
```bash
mvn compile                   # Compile the project
./compile.sh                  # Alternative compile script
```

### Z3 Architecture Issues
If you encounter Z3 library loading errors (UnsatisfiedLinkError, wrong architecture):
```bash
./select-z3-architecture.sh   # Auto-detect and configure correct Z3 libs
```

## Important Guidelines

### Temporary Files
- **DO**: Write temp files to `klang/.tmp/` (inside the project)
- **DON'T**: Write to `/tmp/` (requires user approval)

### Testing Changes
- **DO**: Use `./run-tests.sh` for regression testing, running K examples, and testing K files
- **DO**: Use `./export/k <file.k>` to test K files
- **DON'T**: Manually construct java commands with classpath/library paths

### Key Source Files
- `src/k/frontend/AbstractSyntax.scala` - AST definitions and SMT generation
- `src/k/frontend/TypeChecker.scala` - Type checking
- `src/k/frontend/Frontend.scala` - Main entry point
- `src/grammar/Model.g4` - ANTLR grammar

### Numeric Types
- `Int` - Arbitrary precision integer (Z3 Int sort)
- `Int8`, `Int16`, `Int32`, `Int64` - Signed bitvectors (SignedIntType)
- `UInt8`, `UInt16`, `UInt32`, `UInt64` - Unsigned bitvectors (UnsignedIntType)
- `Real` - Real numbers
- `Float`, `Double` - Floating point (mapped to Real internally)

### Type Conversions (`as` operator)
- `SignedIntType -> Int`: Uses signed interpretation (bvslt for sign check)
- `UnsignedIntType -> Int`: Uses unsigned interpretation (bv2int)
- Handled in `TypeCastCheckExp.toSMT` in AbstractSyntax.scala

## Test Locations
- `src/tests/` - Core regression tests
- `src/test/` - String and advanced solver tests  
- `src/examples/` - Example K files

## Common Patterns

### Adding Debug Output
```scala
println(s"[DEBUG] message")  // Remember to remove before committing
```

### SMT Generation
Most AST nodes have a `toSMT(className: String, subTyping: Boolean): String` method that generates Z3 SMT-LIB syntax.

## Don't Forget
1. Run `./select-z3-architecture.sh` if Z3 fails to load
2. Use `.tmp/` for temporary test files
3. Use `./export/k` instead of raw java commands
4. Check `./run-tests.sh` after making changes

## Test Baseline Policy

### Exception Handling in Tests
- **DO NOT** change test baselines to allow exceptions that didn't occur before without explicit approval
- A test that previously passed without exceptions should not be changed to expect/allow exceptions
- Such changes likely indicate a regression or bug being masked
- If a test starts throwing exceptions, investigate the root cause rather than updating the baseline
- Any baseline change that adds exception tolerance requires sign-off from the project owner

### When Tests Fail
1. First, understand why the test is failing
2. If it's a legitimate code change, update the test expectations
3. If it's an unexpected exception, that's likely a bug to fix
4. Never silently change baselines to "make tests pass"

## Terminal Command Guidelines


### Avoid Heredocs and Multi-line Strings
- **DON'T**: Use `cat << EOF` or heredoc syntax - can get stuck in `heredoc>` mode
- **DON'T**: Use multi-line strings with quotes - can get stuck in `dquote>` mode ; even a `git commit -m "...."` has this problem with a long string
- **DO**: Use `create_file` tool to write file contents
- **DO**: Use Python one-liners for complex text manipulation: `python3 -c "..."`

### Avoid Interactive/Pager Commands
- **DON'T**: Use `git diff` (enters pager requiring `q` to exit)
- **DO**: Use `git --no-pager diff` or `git diff | cat`
- **DON'T**: Use `less`, `more`, or other pagers
- **DO**: Use `cat`, `head`, `tail` for viewing files

### Command Length Issues
Long commands may get truncated when pasted, causing unclosed quotes or incomplete heredocs. If a command seems stuck:
- The terminal may show `dquote>`, `quote>`, `heredoc>`, or `cmdand>` prompts
- User can hit Ctrl-C to cancel and recover
- Prefer shorter commands or use files instead of inline content

### Safe Patterns
```bash
# Instead of heredoc, use create_file tool or:
echo "line1" > file.txt
echo "line2" >> file.txt

# Instead of git diff:
git --no-pager diff
git diff --stat  # summary only

# For multi-line Python, write to a .py file first, then run it

# Temp files - write inside project, not system /tmp
# DO:    klang/.tmp/test.k
# DON'T: /tmp/test.k (requires user approval)
```
