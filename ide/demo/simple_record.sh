#!/bin/bash
# Simple video recording script for K Language IDE demo
# Records short clips of VS Code demonstrating K Language features

DEMO_DIR="/Users/bclement/git/klang/ide/demo"
VIDEO_DIR="$DEMO_DIR/videos"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

mkdir -p "$VIDEO_DIR"

echo "K Language IDE Demo Recorder"
echo "============================"
echo ""
echo "This script will record 6 short video clips."
echo "Please watch VS Code - it should respond to automation."
echo ""

# Function to focus VS Code
focus_vscode() {
    osascript -e 'tell application "Visual Studio Code" to activate'
    sleep 1
}

# Function to type text
type_text() {
    osascript -e "tell application \"System Events\" to keystroke \"$1\""
    sleep 0.3
}

# Function to press key combo
key_combo() {
    local key="$1"
    local mod="$2"
    osascript -e "tell application \"System Events\" to keystroke \"$key\" using {$mod down}"
    sleep 0.5
}

# Function to press enter
press_enter() {
    osascript -e 'tell application "System Events" to key code 36'
    sleep 0.3
}

# Function to press escape
press_escape() {
    osascript -e 'tell application "System Events" to key code 53'
    sleep 0.3
}

# Function to open command palette and run command
run_vscode_cmd() {
    focus_vscode
    key_combo "p" "command, shift"
    sleep 0.5
    type_text "$1"
    sleep 0.5
    press_enter
    sleep 1
}

# Function to open file via quick open
open_file() {
    focus_vscode
    key_combo "p" "command"
    sleep 0.5
    type_text "$1"
    sleep 0.5
    press_enter
    sleep 1
}

# Record a video clip
record_clip() {
    local name="$1"
    local duration="$2"
    local output="$VIDEO_DIR/${TIMESTAMP}_${name}.mp4"

    echo "Recording: $name ($duration seconds)..."
    ffmpeg -y -f avfoundation -framerate 30 -i "1:none" -t "$duration" \
           -c:v libx264 -preset ultrafast -crf 23 -pix_fmt yuv420p \
           "$output" 2>/dev/null &
    FFMPEG_PID=$!
    sleep 0.5
}

stop_recording() {
    if [ -n "$FFMPEG_PID" ]; then
        kill -INT $FFMPEG_PID 2>/dev/null
        wait $FFMPEG_PID 2>/dev/null
        echo "   ✓ Saved"
    fi
}

# Start the demo
echo "Starting demo in 3 seconds..."
sleep 3

focus_vscode
sleep 1

# Demo 1: Syntax Highlighting
echo ""
echo "=== Demo 1: Syntax Highlighting ==="
record_clip "01_syntax" 8
sleep 0.5
key_combo "w" "command"  # Close current file
sleep 0.5
open_file "Shapes.k"
sleep 2
# Scroll down
for i in {1..10}; do
    osascript -e 'tell application "System Events" to key code 125'  # Down arrow
    sleep 0.3
done
sleep 2
stop_recording

# Demo 2: Run K File
echo ""
echo "=== Demo 2: Run K File ==="
record_clip "02_run" 10
sleep 0.5
open_file "Shapes.k"
sleep 1
run_vscode_cmd "Run K File"
sleep 6
stop_recording

# Demo 3: Visualization
echo ""
echo "=== Demo 3: Solution Visualization ==="
record_clip "03_viz" 8
sleep 0.5
run_vscode_cmd "Visualize Solution"
sleep 5
stop_recording

# Demo 4: Auto-Solve
echo ""
echo "=== Demo 4: Auto-Solve Mode ==="
record_clip "04_autosolve" 10
sleep 0.5
open_file "Shapes.k"
sleep 1
run_vscode_cmd "Enable Auto-Solve"
sleep 1
# Go to line 15 and edit
key_combo "g" "control"  # Go to line
sleep 0.3
type_text "15"
press_enter
sleep 0.5
osascript -e 'tell application "System Events" to keystroke " "'  # Space to trigger
sleep 4
stop_recording

# Demo 5: Constraint Debugger
echo ""
echo "=== Demo 5: Constraint Debugger ==="
record_clip "05_debugger" 8
sleep 0.5
run_vscode_cmd "Open Debug Panel"
sleep 5
stop_recording

# Demo 6: Inline Values
echo ""
echo "=== Demo 6: Inline Value Ranges ==="
record_clip "06_inline" 10
sleep 0.5
open_file "Shapes.k"
sleep 1
run_vscode_cmd "Run K File"
sleep 5
# Scroll to see decorations
for i in {1..5}; do
    osascript -e 'tell application "System Events" to key code 125'
    sleep 0.3
done
sleep 2
stop_recording

# Cleanup
press_escape
press_escape

echo ""
echo "============================"
echo "Demo recording complete!"
echo "Videos saved to: $VIDEO_DIR"
echo ""
ls -la "$VIDEO_DIR"/*.mp4 2>/dev/null | tail -10

# Generate simple HTML
HTML_FILE="$DEMO_DIR/output/demo_${TIMESTAMP}.html"
mkdir -p "$DEMO_DIR/output"

cat > "$HTML_FILE" << 'HTMLEOF'
<!DOCTYPE html>
<html>
<head>
    <title>K Language IDE Demo</title>
    <style>
        body { font-family: sans-serif; background: #1a1a2e; color: white; padding: 40px; }
        h1 { text-align: center; color: #00d2ff; }
        .grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px; max-width: 1200px; margin: 0 auto; }
        .feature { background: rgba(255,255,255,0.1); border-radius: 10px; padding: 15px; }
        .feature h3 { color: #00d2ff; margin-bottom: 10px; }
        video { width: 100%; border-radius: 5px; }
    </style>
</head>
<body>
    <h1>K Language IDE Features</h1>
    <div class="grid">
HTMLEOF

# Add video entries
FEATURES=("Syntax Highlighting" "Run K File" "Solution Visualization" "Auto-Solve Mode" "Constraint Debugger" "Inline Values")
NAMES=("01_syntax" "02_run" "03_viz" "04_autosolve" "05_debugger" "06_inline")

for i in {0..5}; do
    VIDEO_FILE=$(ls "$VIDEO_DIR"/*"${NAMES[$i]}"*.mp4 2>/dev/null | tail -1)
    if [ -n "$VIDEO_FILE" ]; then
        VIDEO_NAME=$(basename "$VIDEO_FILE")
        cat >> "$HTML_FILE" << HTMLEOF
        <div class="feature">
            <h3>${FEATURES[$i]}</h3>
            <video controls loop muted>
                <source src="../videos/$VIDEO_NAME" type="video/mp4">
            </video>
        </div>
HTMLEOF
    fi
done

cat >> "$HTML_FILE" << 'HTMLEOF'
    </div>
</body>
</html>
HTMLEOF

echo "HTML demo page: $HTML_FILE"
open "$HTML_FILE"

