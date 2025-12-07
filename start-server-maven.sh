#!/bin/bash

# K Language Web Server - Maven-based Startup Script
# This script uses Maven to build and run the K web server

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
echo "K Language Web Server (Maven build)"
echo "Project root: $PROJECT_ROOT"
echo ""

# Function to check if Java 21 is available
check_java21() {
    if [ -d "$HOME/.sdkman/candidates/java/21.0.8-tem" ]; then
        export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-tem"
        export PATH="$JAVA_HOME/bin:$PATH"
        return 0
    elif [ -d "$HOME/.sdkman/candidates/java/21.0.5-tem" ]; then
        export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.5-tem"
        export PATH="$JAVA_HOME/bin:$PATH"
        return 0
    elif command -v java >/dev/null 2>&1; then
        JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$JAVA_VERSION" -ge 21 ] 2>/dev/null; then
            return 0
        fi
    fi
    return 1
}

# Check for Java 21
if ! check_java21; then
    echo "❌ Java 21 not found!"
    echo "Please install Java 21 using SDKMAN:"
    echo "  curl -s 'https://get.sdkman.io' | bash"
    echo "  source \"\$HOME/.sdkman/bin/sdkman-init.sh\""
    echo "  sdk install java 21.0.5-tem"
    exit 1
fi

echo "✅ Java 21 found and configured"
java -version

# Select appropriate Z3 libraries for this Java architecture
echo ""
"$PROJECT_ROOT/select-z3-architecture.sh"
echo ""

# Check if we need to build
BUILD_NEEDED=false
if [ ! -d "$PROJECT_ROOT/target/classes" ] || [ ! -f "$PROJECT_ROOT/target/classes/web/jettyService/KServlet.class" ]; then
    BUILD_NEEDED=true
elif [ "$PROJECT_ROOT/src" -nt "$PROJECT_ROOT/target" ]; then
    BUILD_NEEDED=true
fi

if [ "$BUILD_NEEDED" = true ]; then
    echo ""
    echo "🔨 Building with Maven..."
    
    cd "$PROJECT_ROOT"
    mvn clean compile
    
    echo "✅ Maven build complete"
else
    echo "✅ Project already built"
fi

# Check if server is already running
if lsof -ti:9000 >/dev/null 2>&1; then
    echo ""
    echo "⚠️  Port 9000 is already in use. Killing existing processes..."
    lsof -ti:9000 | xargs kill -9 2>/dev/null || true
    sleep 2
fi

# Start the server
echo ""
echo "🚀 Starting K web server..."
cd "$PROJECT_ROOT"

JETTY_LIB="src/web/jettyService/jetty-distribution-9.2.12.v20150709/lib"

# Start server in background and capture PID
# Run from project root so KServlet can find src/web and src/examples
java -cp "target/classes:$JETTY_LIB/*" web.jettyService.KServlet &
SERVER_PID=$!

# Wait a moment for server to start
sleep 3

# Check if server started successfully
if kill -0 $SERVER_PID 2>/dev/null; then
    echo ""
    echo "✅ K web server is running!"
    echo "🌐 Web interface: http://localhost:9000"
    echo "🔗 API endpoint: http://localhost:9000/k-service"
    echo ""
    echo "Press Ctrl+C to stop the server"
    echo ""
    
    # Wait for the server process
    wait $SERVER_PID
else
    echo "❌ Failed to start server"
    exit 1
fi