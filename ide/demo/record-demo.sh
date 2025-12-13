#!/bin/bash
# K Language IDE Demo Recording Script
# This script automates the demo recording process for the K Language VS Code extension

# Configuration
DEMO_DIR="/Users/bclement/git/klang/ide/demo"
OUTPUT_DIR="$DEMO_DIR/recordings"
SCREENSHOT_DIR="$DEMO_DIR/screenshots"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

mkdir -p "$OUTPUT_DIR" "$SCREENSHOT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log() {
    echo -e "${GREEN}[DEMO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Take a screenshot with a name
screenshot() {
    local name="$1"
    local filename="$SCREENSHOT_DIR/${TIMESTAMP}_${name}.png"
    screencapture -x "$filename"
    log "Screenshot saved: $filename"
    echo "$filename"
}

# Take a screenshot of a specific window
screenshot_window() {
    local name="$1"
    local filename="$SCREENSHOT_DIR/${TIMESTAMP}_${name}.png"
    # -l flag captures specific window, but we'll use -x for simplicity
    screencapture -x -o "$filename"
    log "Window screenshot saved: $filename"
    echo "$filename"
}

# Focus VS Code
focus_vscode() {
    osascript -e 'tell application "Visual Studio Code" to activate'
    sleep 0.5
}

# Focus IntelliJ
focus_intellij() {
    osascript -e 'tell application "IntelliJ IDEA" to activate'
    sleep 0.5
}

# Simulate key press
key_press() {
    local keys="$1"
    osascript -e "tell application \"System Events\" to keystroke \"$keys\""
    sleep 0.3
}

# Simulate key combination (e.g., "cmd+shift+p")
key_combo() {
    local combo="$1"
    # Parse the combo
    local using=""
    local key=""

    if [[ "$combo" == *"cmd"* ]]; then
        using="$using command down,"
    fi
    if [[ "$combo" == *"shift"* ]]; then
        using="$using shift down,"
    fi
    if [[ "$combo" == *"alt"* ]] || [[ "$combo" == *"option"* ]]; then
        using="$using option down,"
    fi
    if [[ "$combo" == *"ctrl"* ]]; then
        using="$using control down,"
    fi

    # Remove trailing comma
    using="${using%,}"

    # Extract the actual key (last part after +)
    key="${combo##*+}"

    if [ -n "$using" ]; then
        osascript -e "tell application \"System Events\" to keystroke \"$key\" using {$using}"
    else
        osascript -e "tell application \"System Events\" to keystroke \"$key\""
    fi
    sleep 0.3
}

# Press special key
special_key() {
    local key="$1"
    osascript -e "tell application \"System Events\" to key code $key"
    sleep 0.3
}

# Enter key is code 36
press_enter() {
    special_key 36
}

# Escape key is code 53
press_escape() {
    special_key 53
}

# Tab key is code 48
press_tab() {
    special_key 48
}

# Type text slowly (for demo effect)
type_slow() {
    local text="$1"
    local delay="${2:-0.05}"
    for (( i=0; i<${#text}; i++ )); do
        osascript -e "tell application \"System Events\" to keystroke \"${text:$i:1}\""
        sleep "$delay"
    done
}

# Wait for user to press a key (for manual checkpoints)
wait_for_user() {
    local message="${1:-Press any key to continue...}"
    echo -e "${YELLOW}$message${NC}"
    read -n 1 -s
}

# Start screen recording
start_recording() {
    local name="$1"
    local filename="$OUTPUT_DIR/${TIMESTAMP}_${name}.mov"
    log "Starting screen recording: $filename"
    # Start recording in background
    screencapture -v "$filename" &
    RECORDING_PID=$!
    sleep 1
    echo "$filename"
}

# Stop screen recording
stop_recording() {
    if [ -n "$RECORDING_PID" ]; then
        log "Stopping screen recording..."
        # Send Ctrl+C to stop
        kill -INT $RECORDING_PID 2>/dev/null
        wait $RECORDING_PID 2>/dev/null
        RECORDING_PID=""
    fi
}

# Click at coordinates
click() {
    local x="$1"
    local y="$2"
    osascript -e "tell application \"System Events\" to click at {$x, $y}"
    sleep 0.3
}

# Open a file in VS Code
open_file_vscode() {
    local filepath="$1"
    focus_vscode
    key_combo "cmd+p"
    sleep 0.3
    type_slow "$filepath"
    sleep 0.3
    press_enter
    sleep 0.5
}

# Open command palette and run command
run_vscode_command() {
    local command="$1"
    focus_vscode
    key_combo "cmd+shift+p"
    sleep 0.3
    type_slow "$command"
    sleep 0.3
    press_enter
    sleep 0.5
}

# Demo functions for each feature

demo_syntax_highlighting() {
    log "=== Demo: Syntax Highlighting ==="
    focus_vscode
    open_file_vscode "src/examples/Shapes.k"
    sleep 1
    screenshot "01_syntax_highlighting"
    log "Syntax highlighting demo complete"
}

demo_run_k_file() {
    log "=== Demo: Run K File ==="
    focus_vscode
    open_file_vscode "src/examples/Shapes.k"
    sleep 0.5
    # Right-click context menu or use command
    run_vscode_command "Run K File"
    sleep 3
    screenshot "02_run_k_file"
    log "Run K file demo complete"
}

demo_visualization() {
    log "=== Demo: Solution Visualization ==="
    focus_vscode
    run_vscode_command "Visualize Solution"
    sleep 2
    screenshot "03_visualization"
    log "Visualization demo complete"
}

demo_auto_solve() {
    log "=== Demo: Auto-Solve Mode ==="
    focus_vscode
    run_vscode_command "Enable Auto-Solve"
    sleep 1
    screenshot "04_auto_solve_enabled"
    # Make an edit
    key_combo "cmd+end"  # Go to end
    sleep 0.5
    type_slow " "  # Trigger auto-solve
    sleep 2
    screenshot "05_auto_solve_result"
    log "Auto-solve demo complete"
}

demo_constraint_debugger() {
    log "=== Demo: Constraint Debugger ==="
    focus_vscode
    run_vscode_command "Open Debug Panel"
    sleep 2
    screenshot "06_debugger_panel"
    log "Constraint debugger demo complete"
}

demo_inline_decorations() {
    log "=== Demo: Inline Value Ranges ==="
    focus_vscode
    open_file_vscode "src/examples/Shapes.k"
    sleep 1
    run_vscode_command "Run K File"
    sleep 3
    screenshot "07_inline_decorations"
    log "Inline decorations demo complete"
}

# Full demo sequence
run_full_demo() {
    log "Starting full K Language IDE demo..."

    # Preparation
    focus_vscode
    sleep 1

    demo_syntax_highlighting
    sleep 2

    demo_run_k_file
    sleep 2

    demo_visualization
    sleep 2

    demo_auto_solve
    sleep 2

    demo_constraint_debugger
    sleep 2

    demo_inline_decorations
    sleep 2

    log "Full demo complete!"
    log "Screenshots saved to: $SCREENSHOT_DIR"
}

# Interactive demo mode
interactive_demo() {
    log "Starting interactive demo mode..."
    log "This will guide you through each feature with manual confirmation."

    wait_for_user "Ready to start? Press any key..."

    log "Step 1: Syntax Highlighting"
    demo_syntax_highlighting
    wait_for_user "Screenshot taken. Press any key for next feature..."

    log "Step 2: Run K File"
    demo_run_k_file
    wait_for_user "Screenshot taken. Press any key for next feature..."

    log "Step 3: Solution Visualization"
    demo_visualization
    wait_for_user "Screenshot taken. Press any key for next feature..."

    log "Step 4: Auto-Solve"
    demo_auto_solve
    wait_for_user "Screenshot taken. Press any key for next feature..."

    log "Step 5: Constraint Debugger"
    demo_constraint_debugger
    wait_for_user "Screenshot taken. Press any key for next feature..."

    log "Step 6: Inline Decorations"
    demo_inline_decorations

    log "Interactive demo complete!"
}

# Print usage
usage() {
    echo "K Language IDE Demo Recording Script"
    echo ""
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  screenshot <name>    Take a screenshot"
    echo "  record <name>        Start screen recording"
    echo "  stop                 Stop screen recording"
    echo "  demo                 Run full automated demo"
    echo "  interactive          Run interactive demo with manual confirmations"
    echo "  focus-vscode         Focus VS Code window"
    echo "  focus-intellij       Focus IntelliJ window"
    echo "  type <text>          Type text slowly"
    echo "  key <combo>          Press key combination (e.g., cmd+shift+p)"
    echo "  command <cmd>        Run VS Code command"
    echo ""
    echo "Examples:"
    echo "  $0 screenshot syntax-highlighting"
    echo "  $0 command \"Run K File\""
    echo "  $0 demo"
}

# Main entry point
case "$1" in
    screenshot)
        screenshot "$2"
        ;;
    record)
        start_recording "$2"
        ;;
    stop)
        stop_recording
        ;;
    demo)
        run_full_demo
        ;;
    interactive)
        interactive_demo
        ;;
    focus-vscode)
        focus_vscode
        ;;
    focus-intellij)
        focus_intellij
        ;;
    type)
        type_slow "$2"
        ;;
    key)
        key_combo "$2"
        ;;
    command)
        run_vscode_command "$2"
        ;;
    help|--help|-h)
        usage
        ;;
    *)
        if [ -z "$1" ]; then
            usage
        else
            error "Unknown command: $1"
            usage
            exit 1
        fi
        ;;
esac

