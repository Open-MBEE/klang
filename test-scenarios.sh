#!/bin/bash
# Test each scenario of DSN_Pass-diagnostic.k separately

cd /Users/bclement/git/klang-incremental-solving
export JAVA_HOME="/Users/bclement/.sdkman/candidates/java/21.0.5-tem/Contents/Home"

echo "=========================================="
echo "Testing DSN_Pass Scenarios Separately"
echo "=========================================="
echo ""

# Create temporary K files for each scenario
TMP_DIR=".tmp/scenario-tests"
mkdir -p "$TMP_DIR"

# Scenario 1: Nominal only
echo "=== Scenario 1: Nominal (missedpass=false) ==="
cat > "$TMP_DIR/scenario1-nominal.k" << 'EOF'
package Kevin.DSN_Pass

ENABLE_NOMINAL_BRANCH : Bool = true
ENABLE_ANOMALOUS_BRANCH : Bool = false
ENABLE_IMPACT_BRANCH : Bool = false
ENABLE_AUTONOMOUS_MANEUVER : Bool = true
ENABLE_AVAIL_MANEUVER : Bool = true
ENABLE_DOPPLER_AFTER_OTM : Bool = true
ENABLE_PROVIDE_CARRIER : Bool = true
ENABLE_COVERAGE_OTMS : Bool = true
EOF
# Append rest of DSN_Pass-diagnostic.k (skip the flag declarations)
tail -n +26 src/examples/DSN_Pass-diagnostic.k >> "$TMP_DIR/scenario1-nominal.k"

echo "Testing Scenario 1..."
./export/k "$TMP_DIR/scenario1-nominal.k" -timeout 25000 2>&1 | grep -E "SAT|UNSAT|TIMEOUT|satisfiable|NOT satisfiable|Model|Top level" | head -10
echo ""

# Scenario 2: Anomalous Tolerable
echo "=== Scenario 2: Anomalous Tolerable (missedpass=true, tolerate=true) ==="
cat > "$TMP_DIR/scenario2-anomalous-tolerable.k" << 'EOF'
package Kevin.DSN_Pass

ENABLE_NOMINAL_BRANCH : Bool = false
ENABLE_ANOMALOUS_BRANCH : Bool = true
ENABLE_IMPACT_BRANCH : Bool = false
ENABLE_AUTONOMOUS_MANEUVER : Bool = true
ENABLE_AVAIL_MANEUVER : Bool = true
ENABLE_DOPPLER_AFTER_OTM : Bool = true
ENABLE_PROVIDE_CARRIER : Bool = true
ENABLE_COVERAGE_OTMS : Bool = true
EOF
tail -n +26 src/examples/DSN_Pass-diagnostic.k >> "$TMP_DIR/scenario2-anomalous-tolerable.k"

echo "Testing Scenario 2..."
./export/k "$TMP_DIR/scenario2-anomalous-tolerable.k" -timeout 25000 2>&1 | grep -E "SAT|UNSAT|TIMEOUT|satisfiable|NOT satisfiable|Model|Top level" | head -10
echo ""

# Scenario 3: Anomalous Not Tolerable
echo "=== Scenario 3: Anomalous Not Tolerable (missedpass=true, tolerate=false) ==="
cat > "$TMP_DIR/scenario3-anomalous-not-tolerable.k" << 'EOF'
package Kevin.DSN_Pass

ENABLE_NOMINAL_BRANCH : Bool = false
ENABLE_ANOMALOUS_BRANCH : Bool = false
ENABLE_IMPACT_BRANCH : Bool = true
ENABLE_AUTONOMOUS_MANEUVER : Bool = true
ENABLE_AVAIL_MANEUVER : Bool = true
ENABLE_DOPPLER_AFTER_OTM : Bool = true
ENABLE_PROVIDE_CARRIER : Bool = true
ENABLE_COVERAGE_OTMS : Bool = true
EOF
tail -n +26 src/examples/DSN_Pass-diagnostic.k >> "$TMP_DIR/scenario3-anomalous-not-tolerable.k"

echo "Testing Scenario 3..."
./export/k "$TMP_DIR/scenario3-anomalous-not-tolerable.k" -timeout 25000 2>&1 | grep -E "SAT|UNSAT|TIMEOUT|satisfiable|NOT satisfiable|Model|Top level" | head -10
echo ""

echo "=========================================="
echo "Scenario Testing Complete"
echo "=========================================="

