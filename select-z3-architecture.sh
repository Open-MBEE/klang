#!/bin/bash

# Automatically select the correct Z3 libraries based on OS and JVM architecture
# Supports: macOS (x86_64, ARM64), Linux (x86_64)

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

# Detect operating system
OS=$(uname -s)
case "$OS" in
    Darwin)
        OS_TYPE="macos"
        LIB_EXT="dylib"
        ;;
    Linux)
        OS_TYPE="linux"
        LIB_EXT="so"
        ;;
    *)
        echo "⚠️  Unsupported OS: $OS"
        exit 1
        ;;
esac

# Detect Java architecture
if [ -n "$JAVA_HOME" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
else
    JAVA_BIN="java"
fi

# Get the architecture of the Java binary
JAVA_ARCH=$(file "$JAVA_BIN" | grep -o "x86_64\|x86-64\|aarch64\|arm64" | head -1)

# Normalize architecture names
case "$JAVA_ARCH" in
    x86-64|x86_64)
        JAVA_ARCH="x86_64"
        ;;
    aarch64|arm64)
        JAVA_ARCH="arm64"
        ;;
    *)
        echo "⚠️  Could not detect Java architecture, defaulting to x86_64"
        JAVA_ARCH="x86_64"
        ;;
esac

echo "🔍 Detected platform: $OS_TYPE ($JAVA_ARCH)"

# Determine which library directory to use
if [ "$OS_TYPE" = "linux" ]; then
    # Linux only has x86_64 support currently
    LIB_DIR="linux"
else
    # macOS uses architecture-specific directories
    LIB_DIR="$JAVA_ARCH"
fi

# Check if the correct libraries are already in place
CURRENT_ARCH=$(file "$PROJECT_ROOT/export/lib/libz3.$LIB_EXT" 2>/dev/null | grep -o "x86_64\|x86-64\|aarch64\|arm64" | head -1)

# Normalize current architecture
case "$CURRENT_ARCH" in
    x86-64) CURRENT_ARCH="x86_64" ;;
    aarch64) CURRENT_ARCH="arm64" ;;
esac

# Check if we're on Linux or if architecture matches
if [ "$OS_TYPE" = "linux" ]; then
    # On Linux, check if we have .so files (not .dylib)
    if [ -f "$PROJECT_ROOT/export/lib/libz3.so" ]; then
        echo "✅ Z3 libraries already configured for Linux"
        exit 0
    fi
elif [ "$CURRENT_ARCH" = "$JAVA_ARCH" ]; then
    echo "✅ Z3 libraries already match platform: $OS_TYPE ($JAVA_ARCH)"
    exit 0
fi

# Copy the appropriate libraries
if [ "$OS_TYPE" = "linux" ]; then
    echo "🔄 Switching to Linux Z3 libraries..."
else
    echo "🔄 Switching Z3 libraries from $CURRENT_ARCH to $JAVA_ARCH..."
fi

if [ -d "$PROJECT_ROOT/export/lib/$LIB_DIR" ]; then
    cp "$PROJECT_ROOT/export/lib/$LIB_DIR"/* "$PROJECT_ROOT/export/lib/"
    echo "   ✅ Updated export/lib/"
else
    echo "   ⚠️  Directory export/lib/$LIB_DIR not found"
    exit 1
fi

if [ -d "$PROJECT_ROOT/lib/$LIB_DIR" ]; then
    cp "$PROJECT_ROOT/lib/$LIB_DIR"/* "$PROJECT_ROOT/lib/"
    echo "   ✅ Updated lib/"
else
    echo "   ⚠️  Directory lib/$LIB_DIR not found"
fi

echo "✅ Z3 libraries configured for $OS_TYPE ($JAVA_ARCH)"
