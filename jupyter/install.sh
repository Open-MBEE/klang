#!/bin/bash
# Install the K Jupyter Kernel

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "Installing K Jupyter Kernel..."
echo "K installation directory: $K_DIR"

# Check for Python and Jupyter
if ! command -v python3 &> /dev/null; then
    echo "ERROR: python3 not found. Please install Python 3."
    exit 1
fi

if ! python3 -c "import jupyter" &> /dev/null; then
    echo "Installing Jupyter..."
    pip3 install jupyter
fi

if ! python3 -c "import ipykernel" &> /dev/null; then
    echo "Installing ipykernel..."
    pip3 install ipykernel
fi

# Create kernel directory
KERNEL_DIR="$HOME/.local/share/jupyter/kernels/k"
mkdir -p "$KERNEL_DIR"

# Copy kernel files
cp "$SCRIPT_DIR/k_kernel.py" "$KERNEL_DIR/"

# Create kernel.json with correct path
cat > "$KERNEL_DIR/kernel.json" << EOF
{
  "argv": [
    "python3",
    "$KERNEL_DIR/k_kernel.py",
    "-f",
    "{connection_file}"
  ],
  "display_name": "K",
  "language": "k",
  "env": {
    "K_HOME": "$K_DIR"
  },
  "metadata": {
    "debugger": false
  }
}
EOF

echo ""
echo "K Jupyter Kernel installed successfully!"
echo ""
echo "To use:"
echo "  1. Start Jupyter: jupyter notebook"
echo "  2. Create a new notebook with 'K' kernel"
echo ""
echo "Or run directly:"
echo "  jupyter console --kernel k"
echo ""
echo "Kernel location: $KERNEL_DIR"

