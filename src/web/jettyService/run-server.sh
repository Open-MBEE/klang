#!/bin/bash

# Script to run the K web server
# This script sets up the classpath and starts the Jetty server

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Set up classpath - src directory for package structure, plus Jetty libraries
CLASSPATH="$SRC_DIR"
for jar in "$SCRIPT_DIR/jetty-distribution-9.2.12.v20150709/lib"/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done

echo "Starting K web server on port 9000..."
echo "Server will be available at http://localhost:9000"
echo "Press Ctrl+C to stop the server"
echo ""

cd "$SRC_DIR"
java -cp "$CLASSPATH" web.jettyService.KServlet

