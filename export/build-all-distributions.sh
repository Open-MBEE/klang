#!/bin/bash

# Build distributions for all supported platforms
# This script creates distributable packages for macOS (Intel/ARM64), Windows, and Linux

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DATE=$(date +%Y-%m-%d)
DIST_DIR="${1:-$SCRIPT_DIR/../../k-distributions}"

echo "=========================================="
echo "K Language Distribution Builder"
echo "=========================================="
echo "Date: $DATE"
echo "Output directory: $DIST_DIR"
echo ""

# Create output directory
mkdir -p "$DIST_DIR"

# Build each platform
PLATFORMS="macos-intel macos-arm64 windows linux"

for platform in $PLATFORMS; do
    echo "----------------------------------------"
    echo "Building: $platform"
    echo "----------------------------------------"
    
    "$SCRIPT_DIR/createKDrop" "$platform" "$DIST_DIR"
    
    # Create archives
    cd "$DIST_DIR"
    DROPDIR="k-$platform-$DATE"
    
    if [ "$platform" = "windows" ]; then
        # Create ZIP for Windows
        if command -v zip &> /dev/null; then
            zip -r -q "$DROPDIR.zip" "$DROPDIR"
            echo "✅ Created: $DROPDIR.zip"
        else
            echo "⚠️  zip command not found, skipping archive creation"
        fi
    else
        # Create tar.gz for Unix-like systems
        tar czf "$DROPDIR.tar.gz" "$DROPDIR"
        echo "✅ Created: $DROPDIR.tar.gz"
    fi
    
    echo ""
done

echo "=========================================="
echo "Build complete!"
echo "=========================================="
echo "Distribution archives created in:"
echo "  $DIST_DIR"
echo ""
echo "Distributions built:"
ls -lh "$DIST_DIR"/*.tar.gz "$DIST_DIR"/*.zip 2>/dev/null || true
echo ""
echo "To publish these, upload to GitHub Releases:"
echo "  https://github.com/Open-MBEE/klang/releases/new"
