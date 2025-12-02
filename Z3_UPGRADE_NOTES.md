# Z3 Upgrade to 4.13.0 - Summary

## Overview
Successfully upgraded the K language project from Z3 4.3.2 to Z3 4.13.0 with **multi-platform support** (macOS Intel, macOS Apple Silicon, Linux x64), updating all code to work with the modern Z3 Java API.

## Date
November 17-21, 2025

## Changes Made

### 1. Z3 Library Upgrade - Multi-Platform
- **From**: Z3 4.3.2 (x86_64 macOS only)
- **To**: Z3 4.13.0 (multi-platform support)
- **Platforms Supported**:
  - macOS x86_64 (Intel Macs)
  - macOS ARM64 (Apple Silicon Macs)
  - Linux x86_64
- **Library Organization**:
  ```
  lib/ and export/lib/
  ├── x86_64/              Z3 4.13.0 for macOS Intel
  ├── arm64/               Z3 4.13.0 for macOS Apple Silicon  
  └── linux/               Z3 4.13.0 for Linux x64
  ```
- **Automatic Selection**: `select-z3-architecture.sh` detects OS and Java architecture, automatically copying the correct libraries

### 2. Native Library Path Fix
Fixed `libz3java.dylib` to properly load `libz3.dylib` using relative path:
```bash
install_name_tool -change libz3.dylib @loader_path/libz3.dylib export/lib/libz3java.dylib
install_name_tool -change libz3.dylib @loader_path/libz3.dylib lib/libz3java.dylib
```

### 3. Code Updates for Modern Z3 API

#### K2Z3.scala - Major Type System Updates
The modern Z3 API uses generic types extensively. All type declarations were updated:

**Generic Type Parameters Added:**
- `FuncDecl[_ <: Sort]` - Function declarations with wildcard bounds
- `Expr[_ <: Sort]` - Generic expressions
- `ArithExpr[ArithSort]` - Arithmetic expressions with ArithSort
- `BoolExpr` - Boolean expressions (no generic parameter)
- `Constructor[Sort]` - Datatype constructors
- `DatatypeSort[Sort]` - Datatype sorts
- `TupleSort[Sort]` - Tuple sorts

**Key Changes:**
- Updated `DataType` case class to use `FuncDecl[_ <: Sort]`
- Changed `idents` map type to `MMap[String, (Expr[_], StringSymbol)]`
- Updated all method signatures with proper generic types
- Fixed `parseSMTLIB2String` to handle `Array[BoolExpr]` return type
- Added `mkAnd` to combine multiple assertions from parsed SMT
- Switched to `parseSMTLIB2File` for better debugging
- Added null checks for `getFuncInterp().getEntries()` compatibility

**Specific Method Updates:**
```scala
// Before (Z3 4.3.2)
def addTupleType(nr: Int) = {
  val sorts = (1 to nr).map { x => ctx.mkUninterpretedSort(s"T$x") }.toArray
  val names = (1 to nr).map { x => ctx.mkSymbol(s"_$x") }.toArray
  val mkTupleName = ctx.mkSymbol(s"mk-Tuple$nr")
  val tupleConstructor = ctx.mkConstructor(mkTupleName, ctx.mkSymbol(s"is-Tuple$nr"), names, sorts, Array.fill(nr)(0))
  val tupleName = ctx.mkSymbol(s"Tuple$nr")
  ctx.mkDatatypeSort(tupleName, Array(tupleConstructor))
}

// After (Z3 4.13.0)
def addTupleType(nr: Int) = {
  val sorts = (1 to nr).map { x => ctx.mkUninterpretedSort(s"T$x") }.toArray[Sort]
  val names = (1 to nr).map { x => ctx.mkSymbol(s"_$x") }.toArray[Symbol]
  val mkTupleName = ctx.mkSymbol(s"mk-Tuple$nr")
  val tupleConstructor: Constructor[Sort] = ctx.mkConstructor(mkTupleName, ctx.mkSymbol(s"is-Tuple$nr"), names, sorts, Array.fill(nr)(0))
  val tupleName = ctx.mkSymbol(s"Tuple$nr")
  ctx.mkDatatypeSort(tupleName, Array[Constructor[Sort]](tupleConstructor))
}
```

#### AbstractSyntax.scala - SMT Generation Fix
Commented out the `Set` type definition that conflicts with Z3 4.13.0's built-in Set:
```scala
// result1 += "(define-sort Set (T) (Array T Bool))\n"  // Commented out - Set is built-in in Z3 4.13.0
```

### 4. Build System Enhancements

#### Java 8 Configuration
Enhanced `compile.sh` and `start-server.sh` to detect and use Java 8 via SDKMAN:
```bash
# Check SDKMAN Java 8 installation
if [ -d "$HOME/.sdkman/candidates/java/8.0.462-zulu" ]; then
    export JAVA_HOME="$HOME/.sdkman/candidates/java/8.0.462-zulu"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
```

#### Maven Build
Updated `pom.xml` to use local Z3 libraries with system scope (no changes needed from original).

### 5. Testing

Created test file `bin/examples/test-z3.k`:
```k
package test

class SimpleTest {
  x : Int
  y : Int
  
  req PositiveX: x >= 0
  req XLessThanY: x < y
}
```

**Test Results:**
✅ Parsing successful
✅ Type checking successful  
✅ SMT model generation successful
✅ Z3 solving successful with satisfying model:
- Instance 1: x=0, y=1
- Instance 2: x=7719, y=7720
- Instance 3: x=21238, y=21239
- Instance 4: x=2437, y=2438
- Instance 5: x=8855, y=8856

## Verification

All components verified working:
- ✅ Java 8 build with Scala 2.11.8
- ✅ Maven compilation
- ✅ Web server startup on port 9000
- ✅ K language parsing
- ✅ Type checking
- ✅ SMT model generation
- ✅ Z3 constraint solving with ARM64 native libraries
- ✅ Model extraction and display

## Known Limitations

1. **Tuple Constructor Support**: The type checker doesn't yet support `CtorApplExp` for Tuple constructor syntax like `Tuple(x, y)`. Use simpler constraint examples without tuple construction.

2. **Set Comparisons**: Some examples (like Bank.k) use set comparisons (`customers >= accounts`) which aren't directly supported in SMT-LIB. These need to be rewritten to compare set sizes.

## Platform and Java Compatibility

### Supported Platforms
- **macOS Intel (x86_64)**: Z3 4.13.0 native libraries
- **macOS Apple Silicon (ARM64)**: Z3 4.13.0 native libraries (no Rosetta 2 needed)
- **Linux x64**: Z3 4.13.0 native libraries

### Java Version Requirements
- **Current Requirement**: Java 8
- **Reason**: Scala 2.11.8 compatibility (will not compile with Java 9+)
- **Z3 Libraries**: Support newer Java versions (11, 17, 21)
- **Future Upgrade Path**: Update to Scala 2.12+ or 2.13+ to use modern Java

### Z3 4.13.0 API Changes 
  - Generic types throughout API
  - `Set` is now a built-in type
  - `parseSMTLIB2String` returns array instead of single expression
  - Some API methods may return null (need null checks)

## Performance

No significant performance changes observed. Z3 4.13.0 ARM64 native runs efficiently on Apple Silicon.

## Future Work

1. Update type checker to support constructor application expressions
2. Add better error handling for unsupported SMT features
3. Consider upgrading to newer Scala version (would require Java 11+)
4. Add more comprehensive test suite for Z3 integration

## Files Modified

### Core Changes
- `src/k/frontend/K2Z3.scala` - Complete Z3 API modernization
- `src/k/frontend/AbstractSyntax.scala` - Commented out Set definition

### Build System
- `start-server.sh` - Added Java 8 detection

### Libraries
- `lib/com.microsoft.z3.jar`
- `lib/libz3.dylib`
- `lib/libz3java.dylib`
- `export/lib/com.microsoft.z3.osx.jar`
- `export/lib/libz3.dylib`
- `export/lib/libz3java.dylib`

### Test Files
- `bin/examples/test-z3.k` - New test case

## References

- Z3 GitHub: https://github.com/Z3Prover/z3
- Z3 Java API Documentation: https://z3prover.github.io/api/html/namespacecom_1_1microsoft_1_1z3.html
- Z3 Release 4.13.0: https://github.com/Z3Prover/z3/releases/tag/z3-4.13.0
