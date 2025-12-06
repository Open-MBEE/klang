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

- **[STRING_SUPPORT.md](docs/features/STRING_SUPPORT.md)** - String type support with Z3 string theory
- **[STRING_CONCATENATION.md](docs/features/STRING_CONCATENATION.md)** - String concatenation implementation
- **[STRING_OPERATIONS.md](docs/features/STRING_OPERATIONS.md)** - String operations (length, substring, etc.)
- **[STRING_IMPLEMENTATION_COMPLETE.md](docs/features/STRING_IMPLEMENTATION_COMPLETE.md)** - Complete string feature summary

### Testing & Compatibility (`docs/`)

Test infrastructure, results, and platform support:

- **[TEST_INFRASTRUCTURE.md](docs/TEST_INFRASTRUCTURE.md)** - How to run the test suite
- **[TEST_RESULTS.md](docs/TEST_RESULTS.md)** - Current test results (52/54 passing - 96.3%)
- **[CROSS_PLATFORM_COMPATIBILITY.md](docs/CROSS_PLATFORM_COMPATIBILITY.md)** - Platform support (macOS/Linux/Windows)

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
- **[WHY_SLOW_COMPILATION.md](docs/investigations/WHY_SLOW_COMPILATION.md)** - Compilation performance analysis
- **[TEST_SCRIPT_CONSOLIDATION.md](docs/investigations/TEST_SCRIPT_CONSOLIDATION.md)** - Test runner consolidation notes

## 🔍 Quick Navigation

**New to K?** Start here:
1. [README.md](README.md) - Overview
2. [SETUP.md](SETUP.md) - Get it running
3. [TEST_INFRASTRUCTURE.md](docs/TEST_INFRASTRUCTURE.md) - Run tests

**Working on features?** Check:
- [docs/features/](docs/features/) - Feature documentation
- [BUILD_STATUS.md](BUILD_STATUS.md) - Current status

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
│   ├── TEST_INFRASTRUCTURE.md          # How to run tests
│   ├── TEST_RESULTS.md                 # Test results
│   │
│   ├── features/                       # Feature documentation
│   │   ├── STRING_SUPPORT.md
│   │   ├── STRING_CONCATENATION.md
│   │   ├── STRING_OPERATIONS.md
│   │   └── STRING_IMPLEMENTATION_COMPLETE.md
│   │
│   └── investigations/                 # Technical investigations
│       ├── SCALA_VERSION_CONSTRAINT.md
│       ├── REAL_BLOCKER_MAVEN_PLUGIN.md
│       ├── PARSER_COMBINATORS_INVESTIGATION.md
│       ├── OTHER_K_REPOSITORY_INVESTIGATION.md
│       ├── SCALA_ACTORS_CLARIFICATION.md
│       ├── PROCEDURE_SYNTAX_BLOCKER.md
│       ├── WHY_SLOW_COMPILATION.md
│       └── TEST_SCRIPT_CONSOLIDATION.md
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
- Organized by topic (features, testing, investigations)
- More detailed technical content
- Focused on "how it works" and "why decisions were made"

### Investigations (docs/investigations/)
Deep-dive analysis documents:
- Historical context about decisions
- Problem investigation and solutions
- Upgrade planning and blockers
- Useful for AI assistants to gather context quickly
- Not needed for day-to-day development

---

**Note for AI Assistants**: All documentation is discoverable via this index. Investigation files in `docs/investigations/` contain valuable historical context about project decisions and technical constraints.

