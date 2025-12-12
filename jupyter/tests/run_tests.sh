#!/bin/bash
# Run K Jupyter kernel tests

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "================================================"
echo "K Jupyter Kernel Tests"
echo "================================================"

# Check dependencies
if ! python3 -c "import pytest" &> /dev/null; then
    echo "Installing pytest..."
    pip3 install pytest
fi

# Run unit tests (fast, no browser needed)
echo ""
echo "Running unit tests..."
echo "------------------------------------------------"
python3 -m pytest test_k_kernel.py -v "$@"

# Check if Playwright UI tests were requested
if [[ "$1" == "--ui" ]] || [[ "$1" == "-u" ]]; then
    echo ""
    echo "Running UI tests (requires browser)..."
    echo "------------------------------------------------"

    if ! python3 -c "import playwright" &> /dev/null; then
        echo "Installing playwright..."
        pip3 install playwright pytest-playwright
        playwright install chromium
    fi

    # UI tests need a running Jupyter server - skip for now
    echo "NOTE: UI tests require manual Jupyter server setup."
    echo "Start Jupyter with: jupyter notebook --port=18888 --NotebookApp.token=test_token"
    echo "Then run: pytest test_k_kernel_ui.py -v --headed"
fi

echo ""
echo "================================================"
echo "Tests complete!"
echo "================================================"

