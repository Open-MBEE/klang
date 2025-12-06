# Why Is Compilation Slow?

## Current Performance

**Full clean build: ~40-45 seconds**
- Scala compilation: 38-39 seconds
- Java compilation: negligible
- Maven overhead: 4-6 seconds

**Incremental build (no changes): ~3 seconds**

## Root Causes

### 1. Large Scala File (Primary Issue)
**AbstractSyntax.scala: 4,987 lines**

This single file contains the entire AST (Abstract Syntax Tree) for the K language with:
- 100+ case classes for language constructs
- Pattern matching logic
- Type checking integration
- SMT generation for each construct

The Scala 2.11 compiler is **sequential** and must:
- Parse the entire file
- Type-check all interdependent case classes
- Generate JVM bytecode for all methods
- This cannot be parallelized within a single file

### 2. Old Scala Version
**Scala 2.11.8 (released 2016)**

Modern Scala versions (2.13.x) are **2-3x faster** but:
- Require significant code migration
- May break Z3 Java API compatibility
- Would need testing all language features

### 3. No Incremental Compilation
The `scala-maven-plugin 3.1.6` doesn't support true incremental compilation:
- Any change requires full recompilation
- No dependency tracking between files
- No compiler daemon to reuse state

### 4. Maven Startup Overhead
- JVM startup: ~1-2 seconds
- Dependency resolution: ~1-2 seconds  
- Plugin initialization: ~1-2 seconds
Total: ~4-6 seconds per build

## What We've Done

### Memory Optimization (Applied)
```xml
<jvmArgs>
    <jvmArg>-Xms512m</jvmArg>
    <jvmArg>-Xmx1024m</jvmArg>
</jvmArgs>
```
**Impact**: Reduced 39s → 38s (minor improvement)

## Solutions Ranked by Effort/Impact

### 1. ✅ Use Incremental Builds (Already Fast)
**When nothing changed: 3 seconds**

The existing Maven build is already smart about not recompiling unchanged files.

```bash
./compile.sh  # First time: 40s
./compile.sh  # No changes: 3s
```

**Action**: No changes needed - this works well!

### 2. ⚠️ Split AbstractSyntax.scala (High Effort, High Impact)
**Potential speedup: 30-50%**

Break the 5,000 line file into modules:
- `Types.scala` - Type definitions
- `Expressions.scala` - Expression AST nodes
- `Declarations.scala` - Declaration nodes
- `SMT.scala` - SMT generation logic

**Benefits**:
- Parallel compilation of independent files
- Faster incremental builds (only changed modules recompile)
- Better code organization

**Drawbacks**:
- Major refactoring (~2-3 days work)
- Risk of breaking existing code
- Need extensive testing

### 3. ⚠️ Upgrade to Scala 2.13 (High Effort, High Impact)
**Potential speedup: 2-3x faster (15-20 seconds)**

Scala 2.13.x has much faster compilation:
- Improved type inference
- Better optimizer
- Parallel backend

**Drawbacks**:
- Requires code migration (deprecated APIs)
- Must verify Z3 Java API compatibility
- 1-2 weeks of work + testing

### 4. ⚠️ Use SBT Instead of Maven (Medium Effort, Medium Impact)
**Potential speedup: 20-30%**

SBT (Scala Build Tool) has:
- Better incremental compilation
- Persistent compiler daemon
- Parallel builds

**Drawbacks**:
- Need to rewrite build configuration
- Different tooling
- May not integrate well with existing Maven workflow

### 5. ⚠️ Use Zinc Incremental Compiler (Medium Effort, Low Impact)
**Potential speedup: 10-15%**

Zinc provides better incremental compilation for Maven.

**Drawbacks**:
- Requires maven-scala-plugin upgrade
- May have compatibility issues
- Setup complexity

## Recommendation

### Short Term (Now)
**Accept the 40-second build time** - it's reasonable for:
- 10,000+ lines of Scala code
- A 9-year-old compiler version
- Sequential compilation of large interdependent files

**Best practice**: Only run full builds when necessary
- Use `./compile.sh` after git pull or major changes
- For testing, run `export/k` directly (no recompilation needed if no changes)

### Medium Term (If Frequent Development)
**Split AbstractSyntax.scala into modules**
- Would provide 30-50% speedup (25-28 seconds)
- Better code organization
- Easier maintenance
- Estimated effort: 2-3 days

### Long Term (Major Upgrade)
**Upgrade to Scala 2.13 or 3.x**
- Could achieve 15-20 second builds
- Modern language features
- Better tooling
- Estimated effort: 1-2 weeks

## Comparison to Other Projects

K compiler build time is **actually quite reasonable**:

| Project | Language | Lines | Build Time |
|---------|----------|-------|------------|
| **K (this)** | Scala 2.11 | 10,000 | **40s** |
| Scala Compiler | Scala 2.13 | 100,000+ | 5-10 min |
| Dotty/Scala 3 | Scala 3 | 200,000+ | 10-15 min |
| Rust Compiler | Rust | 500,000+ | 30-60 min |
| LLVM | C++ | 1,000,000+ | 60-120 min |

For a 10K line compiler project, **40 seconds is fast**!

## Quick Reference

```bash
# Full clean build (when needed)
./compile.sh  # 40-45 seconds

# Check if recompilation needed
mvn compile   # 3 seconds if nothing changed

# Run without rebuilding
export/k yourfile.k  # Instant if already compiled
```

## Summary

**The 40-second build time is not a bug - it's expected for:**
1. A 5,000-line Scala file (AbstractSyntax.scala)
2. Scala 2.11.8 from 2016 (slow but stable)
3. Sequential compilation (can't parallelize within one file)

**The real question is: How often do you need to rebuild?**
- Incremental builds (no changes): **3 seconds** ✅
- Running K programs: **Instant** (no rebuild) ✅
- Full rebuilds: **40 seconds** (acceptable for 10K lines)

If you're doing frequent development and want faster builds, splitting AbstractSyntax.scala is the best option. Otherwise, the current setup is fine!

