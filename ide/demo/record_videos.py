#!/usr/bin/env python3
"""
K Language IDE Video Demo Generator
Creates short video clips demonstrating each IDE feature.
Uses macOS screen recording and ffmpeg for processing.
"""

import subprocess
import time
import os
import signal
from datetime import datetime
from pathlib import Path

DEMO_DIR = Path("/Users/bclement/git/klang/ide/demo")
VIDEOS_DIR = DEMO_DIR / "videos"
OUTPUT_DIR = DEMO_DIR / "output"

VIDEOS_DIR.mkdir(parents=True, exist_ok=True)
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

class VideoRecorder:
    def __init__(self):
        self.process = None
        self.timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    def start_recording(self, name, duration=None):
        """Start screen recording using ffmpeg."""
        filename = VIDEOS_DIR / f"{self.timestamp}_{name}.mp4"

        # Use ffmpeg with avfoundation to capture screen
        # Device 1 is typically the main display on macOS
        cmd = [
            'ffmpeg',
            '-y',  # Overwrite output
            '-f', 'avfoundation',
            '-framerate', '30',
            '-i', '1:none',  # Screen capture, no audio
            '-c:v', 'libx264',
            '-preset', 'ultrafast',
            '-crf', '23',
            '-pix_fmt', 'yuv420p',
        ]

        if duration:
            cmd.extend(['-t', str(duration)])

        cmd.append(str(filename))

        print(f"🎬 Recording: {name}")
        self.process = subprocess.Popen(
            cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        self.current_file = filename
        return filename

    def stop_recording(self):
        """Stop the current recording."""
        if self.process:
            self.process.send_signal(signal.SIGINT)
            self.process.wait()
            self.process = None
            print(f"   ✓ Saved: {self.current_file}")
            return self.current_file
        return None

def run_applescript(script, check_result=False):
    """Run an AppleScript command."""
    result = subprocess.run(['osascript', '-e', script], capture_output=True, text=True)
    time.sleep(0.3)
    if check_result and result.returncode != 0:
        print(f"   ⚠ AppleScript error: {result.stderr.strip()}")
    return result

def focus_vscode():
    """Focus VS Code window."""
    # Use a more robust activation script
    script = '''
    tell application "Visual Studio Code"
        activate
        delay 0.5
    end tell
    tell application "System Events"
        tell process "Code"
            set frontmost to true
        end tell
    end tell
    '''
    run_applescript(script)
    time.sleep(0.8)

def keystroke(key):
    """Type a key."""
    # Escape special characters for AppleScript
    if key == '"':
        run_applescript('tell application "System Events" to keystroke quote')
    elif key == '\\':
        run_applescript('tell application "System Events" to keystroke backslash')
    else:
        run_applescript(f'tell application "System Events" to keystroke "{key}"')
    time.sleep(0.05)

def key_combo(key, modifiers):
    """Press a key with modifiers."""
    mod_str = ', '.join([f'{m} down' for m in modifiers])
    script = f'''
    tell application "System Events"
        keystroke "{key}" using {{{mod_str}}}
    end tell
    '''
    run_applescript(script)
    time.sleep(0.5)

def key_code(code):
    """Press a key by code (e.g., 36 for Enter, 53 for Escape)."""
    script = f'''
    tell application "System Events"
        key code {code}
    end tell
    '''
    run_applescript(script)
    time.sleep(0.3)

def type_text(text, delay=0.08):
    """Type text character by character."""
    for char in text:
        keystroke(char)
        time.sleep(delay)

def cmd_p():
    """Open quick open (Cmd+P)."""
    key_combo('p', ['command'])
    time.sleep(0.5)

def cmd_shift_p():
    """Open command palette (Cmd+Shift+P)."""
    key_combo('p', ['command', 'shift'])
    time.sleep(0.5)

def enter():
    """Press Enter."""
    key_code(36)
    time.sleep(0.3)

def escape():
    """Press Escape."""
    key_code(53)
    time.sleep(0.3)

def open_file(filename):
    """Open a file via quick open."""
    focus_vscode()
    time.sleep(0.3)
    cmd_p()
    time.sleep(0.5)
    type_text(filename)
    time.sleep(0.5)
    enter()
    time.sleep(1.5)  # Wait for file to open

def run_command(command):
    """Run a VS Code command."""
    focus_vscode()
    time.sleep(0.3)
    cmd_shift_p()
    time.sleep(0.5)
    type_text(command)
    time.sleep(0.8)
    enter()
    time.sleep(1.5)  # Wait for command to execute

def record_feature(recorder, name, title, actions, duration=8):
    """Record a feature demo."""
    print(f"\n{'='*50}")
    print(f"Feature: {title}")
    print(f"{'='*50}")

    focus_vscode()
    time.sleep(0.5)

    # Start recording
    recorder.start_recording(name, duration)

    # Wait a moment for recording to start
    time.sleep(0.5)

    # Execute actions
    actions()

    # Wait for recording to complete (ffmpeg with -t will auto-stop)
    time.sleep(max(0, duration - 1))

    return recorder.stop_recording()

def demo_syntax_highlighting():
    """Demo: Open a K file and show syntax highlighting."""
    # First close any open files
    key_combo('w', ['command'])
    time.sleep(0.5)

    # Open a K file
    open_file("Shapes.k")
    time.sleep(1)

    # Scroll through the file to show highlighting
    key_combo('Home', ['command'])  # Go to top
    time.sleep(0.5)
    for _ in range(8):
        key_code(125)  # Down arrow
        time.sleep(0.3)
    time.sleep(1)

def demo_run_file():
    """Demo: Run a K file."""
    # Make sure we have a K file open
    open_file("Shapes.k")
    time.sleep(1)

    # Run the file
    run_command("Run K File")
    time.sleep(5)  # Wait for solver to complete

def demo_visualization():
    """Demo: Show solution visualization."""
    # Run first to have a solution
    open_file("Shapes.k")
    time.sleep(0.5)
    run_command("Run K File")
    time.sleep(3)

    # Open visualization
    run_command("Visualize Solution")
    time.sleep(3)

def demo_auto_solve():
    """Demo: Enable auto-solve and make an edit."""
    open_file("Shapes.k")
    time.sleep(1)

    # Enable auto-solve
    run_command("Enable Auto-Solve")
    time.sleep(1)

    # Go to a line and make a small edit
    key_combo('g', ['control'])  # Go to line (Ctrl+G in VS Code)
    time.sleep(0.5)
    type_text("15")
    enter()
    time.sleep(0.5)

    # Type a space to trigger auto-solve
    key_code(49)  # Space key
    time.sleep(3)  # Wait for auto-solve to trigger

def demo_debugger():
    """Demo: Open constraint debugger and step through."""
    open_file("Shapes.k")
    time.sleep(0.5)

    # Run first
    run_command("Run K File")
    time.sleep(2)

    # Open debugger
    run_command("Open Debug Panel")
    time.sleep(3)

def demo_inline_values():
    """Demo: Show inline value decorations."""
    open_file("Shapes.k")
    time.sleep(1)

    # Run the file to get values
    run_command("Run K File")
    time.sleep(4)

    # Scroll to see decorations
    key_combo('Home', ['command'])  # Go to top
    time.sleep(0.3)
    for _ in range(5):
        key_code(125)
        time.sleep(0.3)
    time.sleep(1)

def generate_html_with_videos(videos):
    """Generate an HTML page with embedded videos."""
    html = '''<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>K Language IDE - Video Demo</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
            color: white;
            min-height: 100vh;
            padding: 40px 20px;
        }
        .header {
            text-align: center;
            margin-bottom: 50px;
        }
        .header h1 {
            font-size: 3rem;
            background: linear-gradient(90deg, #00d2ff, #3a7bd5);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .header p { font-size: 1.2rem; opacity: 0.8; margin-top: 10px; }
        .features {
            max-width: 1200px;
            margin: 0 auto;
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
            gap: 30px;
        }
        .feature {
            background: rgba(255,255,255,0.1);
            border-radius: 15px;
            overflow: hidden;
            backdrop-filter: blur(10px);
        }
        .feature-header {
            padding: 20px;
            background: rgba(0,0,0,0.3);
        }
        .feature-header h2 {
            font-size: 1.4rem;
            color: #00d2ff;
        }
        .feature-header p {
            margin-top: 10px;
            opacity: 0.8;
            font-size: 0.95rem;
        }
        .feature video {
            width: 100%;
            display: block;
        }
        .footer {
            text-align: center;
            margin-top: 50px;
            opacity: 0.6;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>🔷 K Language IDE</h1>
        <p>Constraint Programming Language Support for VS Code & IntelliJ</p>
    </div>
    
    <div class="features">
'''

    feature_info = [
        ("Syntax Highlighting", "Rich syntax coloring for K specification files with support for keywords, types, literals, and comments."),
        ("Run K Files", "Execute K files directly from VS Code with results shown in a dedicated output panel."),
        ("Solution Visualization", "View constraint solutions as interactive object diagrams with properties and relationships."),
        ("Auto-Solve Mode", "Automatically re-run the solver when you make changes, with intelligent debouncing."),
        ("Constraint Debugger", "Step through constraints one at a time, toggle them on/off, and see UNSAT cores."),
        ("Inline Value Ranges", "See feasible value ranges directly in your editor after solving."),
    ]

    for i, (video_path, (title, desc)) in enumerate(zip(videos, feature_info)):
        video_name = os.path.basename(video_path)
        html += f'''
        <div class="feature">
            <div class="feature-header">
                <h2>{i+1}. {title}</h2>
                <p>{desc}</p>
            </div>
            <video controls loop muted playsinline>
                <source src="videos/{video_name}" type="video/mp4">
            </video>
        </div>
'''

    html += '''
    </div>
    
    <div class="footer">
        <p>K Language - Constraint Programming Made Easy</p>
    </div>
    
    <script>
        // Auto-play videos when they come into view
        const videos = document.querySelectorAll('video');
        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.play();
                } else {
                    entry.target.pause();
                }
            });
        }, { threshold: 0.5 });
        videos.forEach(v => observer.observe(v));
    </script>
</body>
</html>
'''
    return html

def main():
    print("=" * 60)
    print("K Language IDE - Video Demo Generator")
    print("=" * 60)
    print("\nThis will record short video clips of each feature.")
    print("Please don't interact with the computer during recording.\n")

    # Test mode - verify automation works
    import sys
    if "--test" in sys.argv:
        print("Testing automation (no recording)...")
        focus_vscode()
        time.sleep(1)
        print("Opening file...")
        open_file("Shapes.k")
        time.sleep(2)
        print("Running command...")
        run_command("Run K File")
        time.sleep(3)
        print("Test complete! If VS Code responded, automation is working.")
        return

    recorder = VideoRecorder()
    videos = []

    # Record each feature
    features = [
        ("01_syntax", "Syntax Highlighting", demo_syntax_highlighting, 6),
        ("02_run", "Run K File", demo_run_file, 8),
        ("03_viz", "Solution Visualization", demo_visualization, 6),
        ("04_autosolve", "Auto-Solve Mode", demo_auto_solve, 8),
        ("05_debugger", "Constraint Debugger", demo_debugger, 6),
        ("06_inline", "Inline Value Ranges", demo_inline_values, 8),
    ]

    focus_vscode()
    time.sleep(1)

    for name, title, action_fn, duration in features:
        video = record_feature(recorder, name, title, action_fn, duration)
        if video and video.exists():
            videos.append(video)

    # Close any open panels
    escape()
    escape()

    # Generate HTML
    if videos:
        html = generate_html_with_videos(videos)
        output_file = OUTPUT_DIR / f"video_demo_{recorder.timestamp}.html"

        # Create symlink for videos
        videos_link = OUTPUT_DIR / "videos"
        if videos_link.exists():
            videos_link.unlink()
        videos_link.symlink_to(VIDEOS_DIR)

        with open(output_file, 'w') as f:
            f.write(html)

        print(f"\n{'='*60}")
        print("Demo recording complete!")
        print(f"Output: {output_file}")
        print(f"Videos: {len(videos)} clips recorded")
        print(f"{'='*60}")

        # Open in browser
        subprocess.run(['open', str(output_file)])
    else:
        print("\nNo videos were recorded successfully.")

if __name__ == "__main__":
    main()

