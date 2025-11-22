#!/bin/bash

# Legacy build script - DEPRECATED
# Maven already handles Scala-first compilation via scala-maven-plugin
# Use: mvn compile (with Java 8)
# This script is kept for backward compatibility but is no longer necessary

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

# Use Java 8 for Scala 2.11 compatibility
if [ -d "/Users/bclement/.sdkman/candidates/java/8.0.462-zulu" ]; then
    export JAVA_HOME="/Users/bclement/.sdkman/candidates/java/8.0.462-zulu"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "Using Java 8 for Scala compatibility"
    java -version
elif [ -d "/Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "Using Java 8 for Scala compatibility"
    java -version
else
    echo "Warning: Java 8 not found. Scala 2.11 requires Java 8 for best compatibility."
    echo "Current Java version:"
    java -version
fi

# Select appropriate Z3 libraries for this Java architecture
echo ""
"$PROJECT_ROOT/select-z3-architecture.sh"
echo ""

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

# Compile web server (KServlet) with Jetty libraries
echo ""
echo "Compiling web server..."
JETTY_LIB="$PROJECT_ROOT/src/web/jettyService/jetty-distribution-9.2.12.v20150709/lib"
if [ -f "$SRC_DIR/web/jettyService/KServlet.java" ]; then
    javac -d "$BIN_DIR" \
        -cp "$BIN_DIR:$JETTY_LIB/*" \
        -sourcepath "$SRC_DIR" \
        "$SRC_DIR/web/jettyService/KServlet.java" 2>&1 | tee /tmp/webserver-compile.log
    
    if [ ${PIPESTATUS[0]} -ne 0 ]; then
        echo "Web server compilation had errors. Check /tmp/webserver-compile.log"
    else
        echo "✓ Web server compilation complete"
    fi
else
    echo "KServlet.java not found"
fi

echo ""
echo "Build complete! Classes are in $BIN_DIR"

