#!/bin/bash

# Build script that compiles Scala first, then Java
# This fixes the issue where Java files depend on Scala classes

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

# Use Java 8 for Scala 2.11 compatibility
if [ -d "/Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "Using Java 8 for Scala compatibility"
    java -version
fi
SRC_DIR="$PROJECT_ROOT/src"
BIN_DIR="$PROJECT_ROOT/bin"
SCALA_COMPILER="$PROJECT_ROOT/export/lib/scalalib/scala-compiler.jar"
SCALA_LIB="$PROJECT_ROOT/export/lib/scalalib/scala-library.jar"
SCALA_REFLECT="$PROJECT_ROOT/export/lib/scalalib/scala-reflect.jar"
ANTLR_JAR="$PROJECT_ROOT/src/grammar/antlr-4.7-complete.jar"
Z3_JAR="$PROJECT_ROOT/lib/com.microsoft.z3.jar"

echo "Building K project..."
echo "Project root: $PROJECT_ROOT"
echo ""

# Create bin directory
mkdir -p "$BIN_DIR"

# Copy non-Java/Scala files
echo "Copying resources..."
find "$SRC_DIR" -type f ! -name "*.java" ! -name "*.scala" ! -name "*.launch" ! -name "*.xtend" -exec cp --parents {} "$BIN_DIR" \; 2>/dev/null || true

# Build classpath for Scala compilation
SCALA_CP="$SCALA_LIB:$SCALA_REFLECT:$ANTLR_JAR:$Z3_JAR"
SCALA_CP="$SCALA_CP:$PROJECT_ROOT/lib/*"
SCALA_CP="$SCALA_CP:$PROJECT_ROOT/export/lib/scalalib/*"
SCALA_CP="$SCALA_CP:$PROJECT_ROOT/export/lib/elasticsearch-1.5.0/*"
SCALA_CP="$SCALA_CP:$BIN_DIR"

# Compile Scala files
echo "Compiling Scala files..."
SCALA_FILES=$(find "$SRC_DIR" -name "*.scala" -type f | tr '\n' ' ')
if [ -n "$SCALA_FILES" ]; then
    java -Xmx2g -cp "$SCALA_COMPILER:$SCALA_CP" \
        scala.tools.nsc.Main \
        -d "$BIN_DIR" \
        -classpath "$SCALA_CP" \
        -sourcepath "$SRC_DIR" \
        $SCALA_FILES 2>&1 | tee /tmp/scala-compile.log
    
    if [ ${PIPESTATUS[0]} -ne 0 ]; then
        echo "Scala compilation had errors. Check /tmp/scala-compile.log"
        exit 1
    fi
    echo "✓ Scala compilation complete"
else
    echo "No Scala files found"
fi

# Build classpath for Java compilation (now includes compiled Scala classes)
JAVA_CP="$BIN_DIR:$SCALA_LIB:$SCALA_REFLECT:$ANTLR_JAR:$Z3_JAR"
JAVA_CP="$JAVA_CP:$PROJECT_ROOT/lib/*"
JAVA_CP="$JAVA_CP:$PROJECT_ROOT/export/lib/scalalib/*"
JAVA_CP="$JAVA_CP:$PROJECT_ROOT/export/lib/elasticsearch-1.5.0/*"

# Compile Java files (exclude web server files - they're built separately)
echo ""
echo "Compiling Java files..."
JAVA_FILES=$(find "$SRC_DIR" -name "*.java" -type f ! -path "*/web/jettyService/*" | tr '\n' ' ')
if [ -n "$JAVA_FILES" ]; then
    javac -d "$BIN_DIR" \
        -sourcepath "$SRC_DIR" \
        -classpath "$JAVA_CP" \
        -source 1.8 \
        -target 1.8 \
        $JAVA_FILES 2>&1 | tee /tmp/java-compile.log
    
    if [ ${PIPESTATUS[0]} -ne 0 ]; then
        echo "Java compilation had errors. Check /tmp/java-compile.log"
        # Don't exit - show errors but continue
    else
        echo "✓ Java compilation complete"
    fi
else
    echo "No Java files found"
fi

echo ""
echo "Build complete! Classes are in $BIN_DIR"

