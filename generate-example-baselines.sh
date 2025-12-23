#!/bin/bash

# Generate individual baseline JSON files for K examples
# Uses export/k's classpath setup to call GenerateBaseline

set -e

# Get script directory and project root (same as export/k does)
SCRIPT_PATH="${BASH_SOURCE[0]}"
DIR=$( cd "$( dirname "$SCRIPT_PATH" )" && pwd )
PROJECT_ROOT="$DIR"

EXAMPLES_DIR="src/examples"
BASELINE_DIR="$EXAMPLES_DIR/baseline"

# Create baseline directory
mkdir -p "$BASELINE_DIR"

# List of K files to generate baselines for
K_FILES=(
  "a.k"
  "b.k"
  "b2.k"
  "Bank.k"
  "bnf.k"
  "borges.k"
  "c.k"
  "d.k"
  "e.k"
  "f.k"
)

# Setup classpath exactly like export/k does
CLASSPATH="$PROJECT_ROOT/target/classes"
CLASSPATH="$CLASSPATH:$PROJECT_ROOT/src/grammar/antlr-4.7-complete.jar"
if [ -f "$PROJECT_ROOT/export/lib/com.microsoft.z3.osx.jar" ]; then
    CLASSPATH="$CLASSPATH:$PROJECT_ROOT/export/lib/com.microsoft.z3.osx.jar"
else
    CLASSPATH="$CLASSPATH:$PROJECT_ROOT/lib/com.microsoft.z3.jar"
fi
CLASSPATH="$CLASSPATH:$PROJECT_ROOT/export/lib/*"
CLASSPATH="$CLASSPATH:$PROJECT_ROOT/export/lib/scalalib/*"

# Setup library path for Z3
export DYLD_LIBRARY_PATH="$PROJECT_ROOT/export/lib:$DYLD_LIBRARY_PATH"

# Find Java
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA="java"
fi

echo "Generating baselines for example K files..."
echo "Output directory: $BASELINE_DIR"
echo ""

for kfile in "${K_FILES[@]}"; do
  echo "Processing $kfile..."
  
  kpath="$EXAMPLES_DIR/$kfile"
  baseline_json="$BASELINE_DIR/${kfile}.json"
  
  if [ ! -f "$kpath" ]; then
    echo "  WARNING: $kpath not found, skipping"
    continue
  fi
  
  # Run GenerateBaseline with proper classpath
  "$JAVA" -Djava.library.path="$PROJECT_ROOT/export/lib" \
       -Djava.awt.headless=true \
       -classpath "$CLASSPATH" \
       k.frontend.GenerateBaseline "$kpath" "$baseline_json" 2>&1 | grep -E "^✓|saved|ERROR" || true
  
done

echo ""
echo "Done! Baselines generated in $BASELINE_DIR/"
ls -lh "$BASELINE_DIR"
