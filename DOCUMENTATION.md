# Documentation Index

This repository contains documentation organized into user-facing and developer reference sections.

## 📖 User Documentation (Root Directory)

These files help you understand, build, and use the K language:

- **[README.md](README.md)** - Project overview and quick start
- **[SETUP.md](SETUP.md)** - Detailed setup instructions for the web server
- **[BUILD_STATUS.md](BUILD_STATUS.md)** - Current build status and known issues
- **[Z3_UPGRADE_NOTES.md](Z3_UPGRADE_NOTES.md)** - Z3 solver upgrade documentation

## 📚 Developer Reference Documentation

Additional documentation for developers working on the K language implementation:

### Features (`docs/features/`)

Documentation about implemented features:

- **[FEATURE_ROADMAP.md](docs/features/FEATURE_ROADMAP.md)** - Complete feature roadmap and planned improvements
- **[TYPE_INFERENCE.md](docs/features/TYPE_INFERENCE.md)** - Type inference for property declarations
- **[K_TYPE_CHECKER.md](docs/features/K_TYPE_CHECKER.md)** - Type checker implementation details
- **[STRING_IMPLEMENTATION_COMPLETE.md](docs/features/STRING_IMPLEMENTATION_COMPLETE.md)** - Complete string feature summary
- **[STRING_SUPPORT.md](docs/features/STRING_SUPPORT.md)** - String type support with Z3 string theory
- **[STRING_CONCATENATION.md](docs/features/STRING_CONCATENATION.md)** - String concatenation implementation
- **[STRING_OPERATIONS.md](docs/features/STRING_OPERATIONS.md)** - String operations (length, substring, etc.)
- **[MULTIPLE_INHERITANCE.md](docs/features/MULTIPLE_INHERITANCE.md)** - Multiple inheritance support
- **[NULL_LITERAL_SUPPORT.md](docs/features/NULL_LITERAL_SUPPORT.md)** - Null literal handling
- **[EXTERNAL_FUNCTION_DEBUGGING.md](docs/features/EXTERNAL_FUNCTION_DEBUGGING.md)** - Debugging external function calls

### Solver & Architecture (`docs/`)

Core solver and architecture documentation:

- **[UNIFIED_SOLVING_LOOP.md](docs/UNIFIED_SOLVING_LOOP.md)** - Iterative solving with CEGAR refinement
- **[HEAP_CEGAR_STRATEGIES.md](docs/HEAP_CEGAR_STRATEGIES.md)** - Heap allocation strategies and CEGAR
- **[SOLVER_IMPLEMENTATION_SUMMARY.md](docs/SOLVER_IMPLEMENTATION_SUMMARY.md)** - Advanced solver features overview
- **[OPAQUE_FUNCTION_SUPPORT.md](docs/OPAQUE_FUNCTION_SUPPORT.md)** - External/black-box function integration design
- **[K_DEBUGGING_AND_SOLVING.md](docs/K_DEBUGGING_AND_SOLVING.md)** - Debugging K models and understanding solver output
- **[REAL_VS_FLOATING_POINT.md](docs/REAL_VS_FLOATING_POINT.md)** - Numeric type design decisions
- **[JAVA_PYTHON_BRIDGE_OPTIONS.md](docs/JAVA_PYTHON_BRIDGE_OPTIONS.md)** - Options for Java/Python interop

### Testing & Compatibility (`docs/`)

Test infrastructure, results, and platform support:

- **[TEST_INFRASTRUCTURE.md](docs/TEST_INFRASTRUCTURE.md)** - How to run the test suite
- **[TEST_RESULTS.md](docs/TEST_RESULTS.md)** - Current test results
- **[TEST_PERFORMANCE.md](docs/TEST_PERFORMANCE.md)** - Test performance benchmarks
- **[EXAMPLES_TEST_REPORT.md](docs/EXAMPLES_TEST_REPORT.md)** - Examples test coverage
- **[CROSS_PLATFORM_COMPATIBILITY.md](docs/CROSS_PLATFORM_COMPATIBILITY.md)** - Platform support (macOS/Linux/Windows)

### IDE Support (`docs/` and `ide/`)

IDE plugins and extensions for K language development:

- **[IDE_DESIGN_VISION.md](docs/IDE_DESIGN_VISION.md)** - Complete IDE design vision and roadmap
- **[IDE_DEBUGGING.md](docs/IDE_DEBUGGING.md)** - IDE debugging setup
- **[ide/README.md](ide/README.md)** - IDE plugin overview
- **[ide/vscode/](ide/vscode/)** - VS Code extension with syntax highlighting
- **[ide/jetbrains/](ide/jetbrains/)** - JetBrains/IntelliJ plugin

### Scala 2.13 Upgrade (`docs/`)

Documentation for the Scala 2.13 upgrade:

- **[SCALA_2.13_UPGRADE_COMPLETE.md](docs/SCALA_2.13_UPGRADE_COMPLETE.md)** - Complete upgrade documentation with all changes
- **[SCALA_2.13_EXAMPLES_TESTED.md](docs/SCALA_2.13_EXAMPLES_TESTED.md)** - Testing results and verification

### Investigations (`docs/investigations/`)

Technical investigations and analysis (useful for understanding project history and upgrade decisions):

- **[SCALA_VERSION_CONSTRAINT.md](docs/investigations/SCALA_VERSION_CONSTRAINT.md)** - Why we were stuck on Scala 2.11
- **[REAL_BLOCKER_MAVEN_PLUGIN.md](docs/investigations/REAL_BLOCKER_MAVEN_PLUGIN.md)** - scala-maven-plugin version analysis
- **[PARSER_COMBINATORS_INVESTIGATION.md](docs/investigations/PARSER_COMBINATORS_INVESTIGATION.md)** - Parser-combinators dependency investigation
- **[OTHER_K_REPOSITORY_INVESTIGATION.md](docs/investigations/OTHER_K_REPOSITORY_INVESTIGATION.md)** - Analysis of the parallel K repository
- **[SCALA_ACTORS_CLARIFICATION.md](docs/investigations/SCALA_ACTORS_CLARIFICATION.md)** - scala-actors dependency clarification
- **[PROCEDURE_SYNTAX_BLOCKER.md](docs/investigations/PROCEDURE_SYNTAX_BLOCKER.md)** - Procedure syntax deprecation analysis
- **[TYPE_INFERENCE_VIA_Z3.md](docs/investigations/TYPE_INFERENCE_VIA_Z3.md)** - Type inference via Z3 investigation
- **[WHY_SLOW_COMPILATION.md](docs/investigations/WHY_SLOW_COMPILATION.md)** - Initial compilation performance analysis
- **[COMPILATION_PERFORMANCE.md](docs/investigations/COMPILATION_PERFORMANCE.md)** - Complete compilation performance analysis and benchmarks
- **[TEST_SCRIPT_CONSOLIDATION.md](docs/investigations/TEST_SCRIPT_CONSOLIDATION.md)** - Test runner consolidation notes
- **[DOCS_ORGANIZATION.md](docs/investigations/DOCS_ORGANIZATION.md)** - Documentation organization decisions

### Historical (`docs/historical/`)

Archived session notes and early design documents (preserved for historical context):

- **[historical/README.md](docs/historical/README.md)** - Index of historical documents

## 🔍 Quick Navigation

**New to K?** Start here:
1. [README.md](README.md) - Overview
2. [SETUP.md](SETUP.md) - Get it running
3. [TEST_INFRASTRUCTURE.md](docs/TEST_INFRASTRUCTURE.md) - Run tests

**Want IDE support?** Check:
- [ide/README.md](ide/README.md) - IDE plugins overview
- [docs/IDE_DESIGN_VISION.md](docs/IDE_DESIGN_VISION.md) - Design vision

**Working on features?** Check:
- [docs/features/FEATURE_ROADMAP.md](docs/features/FEATURE_ROADMAP.md) - Feature roadmap
- [docs/features/](docs/features/) - Feature documentation
- [BUILD_STATUS.md](BUILD_STATUS.md) - Current status

**Working on solver?** Check:
- [docs/UNIFIED_SOLVING_LOOP.md](docs/UNIFIED_SOLVING_LOOP.md) - Main solving loop
- [docs/HEAP_CEGAR_STRATEGIES.md](docs/HEAP_CEGAR_STRATEGIES.md) - Heap strategies
- [docs/OPAQUE_FUNCTION_SUPPORT.md](docs/OPAQUE_FUNCTION_SUPPORT.md) - External functions

**Upgrading Scala?** Read:
- [docs/investigations/REAL_BLOCKER_MAVEN_PLUGIN.md](docs/investigations/REAL_BLOCKER_MAVEN_PLUGIN.md) - Main blockers
- [docs/investigations/SCALA_VERSION_CONSTRAINT.md](docs/investigations/SCALA_VERSION_CONSTRAINT.md) - Historical context

**Need test info?**
- Run `./run-tests.sh` (see [docs/TEST_INFRASTRUCTURE.md](docs/TEST_INFRASTRUCTURE.md))
- Current status: [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md)

## 📁 Directory Structure

```
klang/
├── README.md                           # Project overview
├── SETUP.md                            # Setup instructions
├── BUILD_STATUS.md                     # Build status
├── Z3_UPGRADE_NOTES.md                 # Z3 documentation
├── DOCUMENTATION.md                    # This file (index)
│
├── docs/
│   ├── README.md                       # docs/ overview
│   │
│   ├── # Solver & Architecture
│   ├── UNIFIED_SOLVING_LOOP.md         # Main solving loop design
│   ├── HEAP_CEGAR_STRATEGIES.md        # Heap allocation strategies
│   ├── OPAQUE_FUNCTION_SUPPORT.md      # External function integration
│   ├── SOLVER_IMPLEMENTATION_SUMMARY.md
│   ├── K_DEBUGGING_AND_SOLVING.md
│   ├── REAL_VS_FLOATING_POINT.md
│   │
│   ├── # Testing
│   ├── TEST_INFRASTRUCTURE.md          # How to run tests
│   ├── TEST_RESULTS.md                 # Test results
│   ├── TEST_PERFORMANCE.md             # Performance benchmarks
│   │
│   ├── # IDE
│   ├── IDE_DESIGN_VISION.md            # IDE roadmap
│   ├── IDE_DEBUGGING.md                # IDE setup
│   │
│   ├── features/                       # Feature documentation
│   │   ├── FEATURE_ROADMAP.md          # Roadmap & planned features
│   │   ├── TYPE_INFERENCE.md
│   │   ├── STRING_*.md                 # String support docs
│   │   └── ...
│   │
│   ├── investigations/                 # Technical investigations
│   │   └── ...
│   │
│   └── historical/                     # Archived session notes
│       └── ...
│
├── ide/                                # IDE plugins
│   ├── vscode/                         # VS Code extension
│   └── jetbrains/                      # IntelliJ plugin
│
├── src/                                # Source code
├── export/                             # K runtime
└── ...
```

## 🎯 Purpose of Each Section

### User Documentation (Root)
Files that help users understand and use K:
- Quick to find (in root directory)
- Essential for getting started
- Focused on "how to use"

### Developer Reference (docs/)
Files that help developers work on K:
- Organized by topic (features, testing, solver, IDE)
- More detailed technical content
- Focused on "how it works" and "why decisions were made"

### Investigations (docs/investigations/)
Deep-dive analysis documents:
- Historical context about decisions
- Problem investigation and solutions
- Upgrade planning and blockers
- Useful for AI assistants to gather context quickly
- Not needed for day-to-day development

### Historical (docs/historical/)
Archived documents:
- Session notes from development
- Early design discussions
- Preserved for historical reference only

---

**Note for AI Assistants**: All documentation is discoverable via this index. Investigation files in `docs/investigations/` contain valuable historical context about project decisions and technical constraints.

