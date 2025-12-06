# Technical Investigations

This directory contains detailed technical investigations and analysis documents. These are useful for:

- Understanding historical project decisions
- Planning upgrades (e.g., Scala version upgrade)
- Debugging complex issues
- Providing context to AI assistants

## Scala Upgrade Investigation

Documents related to upgrading from Scala 2.11.8:

- **[SCALA_VERSION_CONSTRAINT.md](SCALA_VERSION_CONSTRAINT.md)** - Overview of why we were stuck on Scala 2.11
- **[REAL_BLOCKER_MAVEN_PLUGIN.md](REAL_BLOCKER_MAVEN_PLUGIN.md)** - scala-maven-plugin 3.1.6 analysis (main blocker)
- **[SCALA_ACTORS_CLARIFICATION.md](SCALA_ACTORS_CLARIFICATION.md)** - scala-actors dependency investigation
- **[PARSER_COMBINATORS_INVESTIGATION.md](PARSER_COMBINATORS_INVESTIGATION.md)** - scala-parser-combinators dependency analysis
- **[PROCEDURE_SYNTAX_BLOCKER.md](PROCEDURE_SYNTAX_BLOCKER.md)** - Procedure syntax deprecation (40 occurrences)

## Repository Analysis

- **[OTHER_K_REPOSITORY_INVESTIGATION.md](OTHER_K_REPOSITORY_INVESTIGATION.md)** - Analysis of the parallel K repository

## Performance

- **[WHY_SLOW_COMPILATION.md](WHY_SLOW_COMPILATION.md)** - Compilation performance analysis
  - Current: 40 seconds (acceptable)
  - After Scala 2.13 upgrade: 15-20 seconds (2-3x faster)

## Testing

- **[TEST_SCRIPT_CONSOLIDATION.md](TEST_SCRIPT_CONSOLIDATION.md)** - Test runner consolidation notes

## Summary of Findings

### Blockers for Scala 2.13 Upgrade

**Already Removed:**
1. ✅ scala-actors dependency (not used)
2. ✅ scala-parser-combinators 1.0.3 (not used, version doesn't exist for 2.12+)

**Remaining:**
1. ⚠️ scala-maven-plugin 3.1.6 → needs upgrade to 4.8.1+
2. ⚠️ build.xml hardcoded to Scala 2.11.5 (or just use Maven)
3. ⚠️ Procedure syntax (40 occurrences, trivial to fix)

### Current Test Status

- **52/54 tests passing** (96.3%)
- 2 failures due to primitive type name conflicts (pre-existing)
- Test runner working: `./run-tests.sh`

---

**Note**: These documents are primarily for historical reference and context gathering. New developers should start with the root documentation files.

