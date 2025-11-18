#!/bin/bash

# Script to run the K web server
# This script sets up the classpath and starts the Jetty server

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

# Set up classpath - use Maven's target/classes or fallback to bin directory
if [ -d "$PROJECT_ROOT/target/classes" ]; then
    CLASSES_DIR="$PROJECT_ROOT/target/classes"
else
    CLASSES_DIR="$PROJECT_ROOT/bin"
fi

# Set up classpath - compiled classes plus Jetty libraries
CLASSPATH="$CLASSES_DIR"
for jar in "$SCRIPT_DIR/jetty-distribution-9.2.12.v20150709/lib"/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done

echo "Starting K web server on port 9000..."
echo "Server will be available at http://localhost:9000"
echo "Press Ctrl+C to stop the server"
echo ""

# Run from project root so KServlet can find src/web and src/examples
cd "$PROJECT_ROOT"
java -cp "$CLASSPATH" web.jettyService.KServlet

