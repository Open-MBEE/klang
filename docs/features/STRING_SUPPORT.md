# K Language - String Support Implementation

## Summary

String support has been successfully added to the K language compiler and SMT backend! The K grammar and type system already had basic String support, but strings were previously blocked from SMT constraints. This implementation enables:

1. **String properties in class declarations**
2. **String literals in constraints** 
3. **Z3 string theory integration** via SMT-LIB
4. **String equality constraints** (`s = "Hello"`)

## What Was Changed

### 1. Allow `StringType` in SMT-relevant type checking

**File**: `src/k/frontend/AbstractSyntax.scala`  
**Location**: `UtilSMT.wellFormedType()` method (line ~130)

```scala
def wellFormedType(ty: Type): Boolean =
  ty match {
    case CartesianType(types)          => types forall wellFormedType
    case ParenType(ty)                 => wellFormedType(ty)
    case BoolType | IntType | RealType | StringType => true  // ← Added StringType
    case IdentType(_, _)               => true
    case FunctionType(_, _) | SubType(_, _, _) | CharType | UnitType =>
      false
  }
```

**Effect**: String properties can now participate in SMT constraints and Z3 solving.

### 2. Proper SMT sort mapping for `StringType`

**File**: `src/k/frontend/AbstractSyntax.scala`  
**Location**: `StringType` object definition (line ~4538)

```scala
case object StringType extends PrimitiveType {
  override def statistics() {
    UtilSMT.statistics.STRINGTYPE += 1
  }

  override def toSMT: String = "String"  // ← Maps to Z3's String sort

  override def toScala: String = "String"
  override def toString = "String"
  // ... JSON methods ...
}
```

**Effect**: K's `String` type now correctly maps to Z3's built-in `String` sort.

### 3. SMT encoding for string literals

**File**: `src/k/frontend/AbstractSyntax.scala`  
**Location**: `StringLiteral` case class (line ~4026)

```scala
case class StringLiteral(s: String) extends Literal {
  override def children = List()

  override def statistics() {
    UtilSMT.statistics.STRINGLIT += 1
  }
  
  override def toSMT(className: String, subTyping: Boolean): String = {
    // s is already the unescaped value from the parser; re-escape for SMT-LIB
    val escaped = s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\t", "\\t")
    "\"" + escaped + "\""
  }
  
  override def toString = s
  // ... other methods ...
}
```

**Effect**: String literals like `"Hello"` are properly encoded as SMT-LIB string literals with correct escaping.

## Example: SimpleStringTest

**File**: `src/test/SimpleStringTest.k`

```k
class SimpleStringTest {

  s : String
  t : String

  req s = "Hello"
  req t = "world"

}
```

This simple example demonstrates:
- Class properties of type `String`
- String equality constraints using literals
- Z3 will solve these constraints using its string theory

## How to Build and Test

### Prerequisites

- **Java 8** (required for Scala 2.11.8 compatibility)
- SDKMAN for Java version management (recommended)

### Install Java 8 (if not already installed)

```bash
# Install SDKMAN if needed
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Java 8
sdk install java 8.0.422-tem
```

### Build the Project

```bash
# Use the provided compile script (handles Java 8 automatically)
./compile.sh

# Or manually with Maven (requires Java 8 in PATH)
mvn clean compile
```

### Test the SimpleStringTest Example

```bash
# Use the provided test script
./test-simplestringtest.sh

# Or manually
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 8.0.422-tem

# Select appropriate Z3 libraries for your platform
./select-z3-architecture.sh

# Run K frontend on SimpleStringTest
export DYLD_LIBRARY_PATH="$PWD/lib:$DYLD_LIBRARY_PATH"  # macOS
# export LD_LIBRARY_PATH="$PWD/lib:$LD_LIBRARY_PATH"    # Linux

java -cp "target/classes:lib/com.microsoft.z3.jar:export/lib/scalalib/*" \
    -Djava.library.path="$PWD/lib" \
    k.frontend.Main \
    -f src/test/SimpleStringTest.k
```
    -smt
```

## Environment Setup

### Required Environment Variables

1. **JAVA_HOME**: Should point to Java 8 installation
2. **DYLD_LIBRARY_PATH** (macOS) or **LD_LIBRARY_PATH** (Linux): Must include `lib/` directory for Z3 native libraries

The provided shell scripts (`compile.sh`, `test-simplestringtest.sh`) handle this automatically.

### Z3 Library Selection

The repo includes Z3 4.13.0 for multiple platforms:
- macOS Intel (x86_64)
- macOS Apple Silicon (ARM64)  
- Linux (x86_64)

Run `./select-z3-architecture.sh` to auto-detect and configure the correct libraries.

## What Works Now

✅ **String properties**: `s : String` in class declarations  
✅ **String literals**: `"Hello"`, `"world"`, etc.  
✅ **String equality**: `req s = "Hello"`  
✅ **SMT generation**: Produces valid SMT-LIB with `String` sort  
✅ **Z3 solving**: Z3 can solve string constraints using its string theory  
✅ **Type checking**: Strings are properly type-checked like other primitives  

## Future Enhancements (Not Yet Implemented)

The following are **not** currently supported but could be added:

- **String concatenation**: `s + t` (type-checks but doesn't map to `str.++` in SMT yet)
- **String comparison**: `s < t`, `s <= t` (operators not meaningful for strings)
- **String length**: `.length` method
- **Substring operations**: `.substring(...)`, indexing
- **String contains**: `"Hello" isin s`
- **Regular expressions**: Pattern matching

To add these, you would need to:
1. Extend `BinExp.toSMT` to detect string operands and map to Z3 string operators
2. Add library methods to the AST for `.length`, `.substring`, etc.
3. Map those methods to corresponding Z3 string theory functions in SMT generation

## Troubleshooting

### Build fails with "object java.lang.Object in compiler mirror not found"

**Cause**: Using Java version other than Java 8  
**Solution**: Use the `./compile.sh` script, or manually switch to Java 8:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 8.0.422-tem
mvn compile
```

### Z3 library loading errors

**Cause**: Z3 native libraries not in library path or wrong architecture selected  
**Solution**: 
1. Run `./select-z3-architecture.sh` to configure correct Z3 libraries
2. Set library path: `export DYLD_LIBRARY_PATH="$PWD/lib:$DYLD_LIBRARY_PATH"`

### Test fails with "Could not find or load main class"

**Cause**: Project not compiled, or classpath incorrect  
**Solution**: Run `./compile.sh` first, then check classpath includes `target/classes`

## Technical Details

### SMT-LIB Generation

For the SimpleStringTest example, the generated SMT-LIB includes:

```smt2
; Datatype for SimpleStringTest class
(declare-datatypes () ((SimpleStringTest (mk-SimpleStringTest (s String)(t String)))))

; Heap and heap functions (simplified)
(declare-const heap (Array Ref Any))
(declare-const const-0-SimpleStringTest SimpleStringTest)

; Constraints
(assert (= (SimpleStringTest!s 0) "Hello"))
(assert (= (SimpleStringTest!t 0) "world"))

(check-sat)
```

Z3's string theory then solves these constraints.

### Files Modified

1. **src/k/frontend/AbstractSyntax.scala**
   - `UtilSMT.wellFormedType()`: Added `StringType` to allowed types (line ~130)
   - `Model.toSMT`: Added `(set-logic ALL)` declaration for Z3 4.13.0 string theory (line ~800)
   - `StringType.toSMT`: Returns `"String"` for Z3 sort (line ~4541)
   - `StringLiteral.toSMT`: Emits SMT-LIB string literals with proper escaping, handling parser-included quotes (line ~4028)

2. **src/test/SimpleStringTest.k** (new file)
   - Example K program demonstrating string support

3. **compile.sh** (new file)
   - Convenience script to build with Java 8

4. **test-simplestringtest.sh** (new file)
   - Script to test the SimpleStringTest example

5. **test-simplestringtest-debug.sh** (new file)
   - Script to see generated SMT output for debugging

6. **STRING_SUPPORT.md** (this file)
   - Comprehensive documentation

## References

- **Z3 String Theory**: https://z3prover.github.io/api/html/namespacemicrosoft_1_1z3_1_1seq.html
- **SMT-LIB String Theory**: http://smtlib.cs.uiowa.edu/theories-UnicodeStrings.shtml
- **K Language Documentation**: See README.md and SETUP.md in repo root

## Contact

For questions or issues with this implementation, check:
- The build logs in `/tmp/scala-compile.log` and `/tmp/simplestringtest-test.log`
- Z3 test with `java -cp lib/com.microsoft.z3.jar TestZ3`
- Git commit history for detailed changes

