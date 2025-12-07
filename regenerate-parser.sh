#!/bin/bash
# Regenerate ANTLR parser from Model.g4 grammar
# Run this after modifying Model.g4

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
GRAMMAR_DIR="$SCRIPT_DIR/src/grammar"
OUTPUT_DIR="$SCRIPT_DIR/src/k/frontend"
ANTLR_JAR="$GRAMMAR_DIR/antlr-4.7-complete.jar"

# Download ANTLR if not present
if [ ! -f "$ANTLR_JAR" ]; then
    echo "ANTLR jar not found. Downloading..."
    curl -o "$ANTLR_JAR" https://www.antlr.org/download/antlr-4.7-complete.jar
    if [ $? -ne 0 ]; then
        echo "Failed to download ANTLR. Please download manually from:"
        echo "  https://www.antlr.org/download/antlr-4.7-complete.jar"
        echo "And place it in: $GRAMMAR_DIR/"
        exit 1
    fi
fi

echo "Regenerating parser from Model.g4..."

# Generate Java parser and visitor
java -jar "$ANTLR_JAR" -visitor -no-listener -package k.frontend \
    -o "$OUTPUT_DIR" \
    "$GRAMMAR_DIR/Model.g4"

if [ $? -eq 0 ]; then
    echo "Parser regenerated successfully!"
    echo ""
    echo "Files updated:"
    ls -la "$OUTPUT_DIR"/Model*.java 2>/dev/null
    echo ""
    echo "Now uncomment visitOptimizeDeclaration in KScalaVisitor.scala"
    echo "Then rebuild with: ./compile.sh"
else
    echo "Parser generation failed!"
    exit 1
fi