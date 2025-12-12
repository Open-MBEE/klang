#!/bin/bash

# Automatically select the correct Z3 libraries based on OS and JVM architecture
# Supports: macOS (x86_64, ARM64), Linux (x86_64)
# Also warns if Java architecture doesn't match the native CPU architecture

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

# Detect native CPU architecture
CPU_ARCH=$(uname -m)
case "$CPU_ARCH" in
    x86_64)
        NATIVE_ARCH="x86_64"
        ;;
    arm64|aarch64)
        NATIVE_ARCH="arm64"
        ;;
    *)
        NATIVE_ARCH="unknown"
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
echo "   Native CPU: $NATIVE_ARCH, Java: $JAVA_ARCH"

# Warn if Java architecture doesn't match native CPU (running under Rosetta)
if [ "$OS_TYPE" = "macos" ] && [ "$NATIVE_ARCH" = "arm64" ] && [ "$JAVA_ARCH" = "x86_64" ]; then
    echo ""
    echo "⚠️  WARNING: You are running x86_64 Java on an ARM64 Mac (via Rosetta 2)"
    echo "   This will cause performance degradation and may cause Z3 crashes."
    echo ""
    echo "   To fix, install native ARM64 Java:"
    echo "   1. Using SDKMAN:"
    echo "      sdk install java 21.0.5-tem"
    echo "      (Make sure to download the aarch64/arm64 version)"
    echo ""
    echo "   2. Or download directly from Adoptium:"
    echo "      https://adoptium.net/temurin/releases/?os=mac&arch=aarch64"
    echo ""
    echo "   3. Or using Homebrew:"
    echo "      brew install openjdk@21"
    echo ""

    # Check if there's an arm64 Java available in common locations
    ARM64_JAVA=""

    # Check SDKMAN installations
    if [ -d "$HOME/.sdkman/candidates/java" ]; then
        for dir in "$HOME/.sdkman/candidates/java"/*; do
            if [ -d "$dir" ] && [ "$(basename "$dir")" != "current" ]; then
                # Check both direct bin and macOS Contents/Home structure
                for java_path in "$dir/bin/java" "$dir/Contents/Home/bin/java"; do
                    if [ -f "$java_path" ]; then
                        arch=$(file "$java_path" | grep -o "arm64\|aarch64" | head -1)
                        if [ -n "$arch" ]; then
                            ARM64_JAVA="$java_path"
                            ARM64_JAVA_DIR="$dir"
                            break 2
                        fi
                    fi
                done
            fi
        done
    fi

    # Check Homebrew Java
    if [ -z "$ARM64_JAVA" ] && [ -f "/opt/homebrew/opt/openjdk@21/bin/java" ]; then
        arch=$(file "/opt/homebrew/opt/openjdk@21/bin/java" | grep -o "arm64\|aarch64" | head -1)
        if [ -n "$arch" ]; then
            ARM64_JAVA="/opt/homebrew/opt/openjdk@21/bin/java"
            ARM64_JAVA_DIR="/opt/homebrew/opt/openjdk@21"
        fi
    fi

    if [ -n "$ARM64_JAVA" ]; then
        echo "   ✅ Found ARM64 Java at: $ARM64_JAVA_DIR"
        echo "   Set JAVA_HOME to use it:"
        echo "      export JAVA_HOME=\"$ARM64_JAVA_DIR\""
        # Handle macOS JDK structure (Contents/Home)
        if [[ "$ARM64_JAVA" == *"/Contents/Home/"* ]]; then
            echo "      export JAVA_HOME=\"${ARM64_JAVA_DIR}/Contents/Home\""
        fi
        echo ""
    fi
fi

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
    cp -f "$PROJECT_ROOT/export/lib/$LIB_DIR"/* "$PROJECT_ROOT/export/lib/"
    # Sign the libraries on macOS (required for Gatekeeper on unsigned binaries)
    if [ "$OS_TYPE" = "macos" ]; then
        codesign -s - "$PROJECT_ROOT/export/lib/libz3.dylib" 2>/dev/null || true
        codesign -s - "$PROJECT_ROOT/export/lib/libz3java.dylib" 2>/dev/null || true
    fi
    echo "   ✅ Updated export/lib/"
else
    echo "   ⚠️  Directory export/lib/$LIB_DIR not found"
    exit 1
fi

if [ -d "$PROJECT_ROOT/lib/$LIB_DIR" ]; then
    cp -f "$PROJECT_ROOT/lib/$LIB_DIR"/* "$PROJECT_ROOT/lib/"
    # Sign the libraries on macOS
    if [ "$OS_TYPE" = "macos" ]; then
        codesign -s - "$PROJECT_ROOT/lib/libz3.dylib" 2>/dev/null || true
        codesign -s - "$PROJECT_ROOT/lib/libz3java.dylib" 2>/dev/null || true
    fi
    echo "   ✅ Updated lib/"
else
    echo "   ⚠️  Directory lib/$LIB_DIR not found"
fi

echo "✅ Z3 libraries configured for $OS_TYPE ($JAVA_ARCH)"
