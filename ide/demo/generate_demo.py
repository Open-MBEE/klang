#!/usr/bin/env python3
"""
K Language IDE Demo Generator
Creates an HTML slideshow with screenshots and narration for all features.
"""

import subprocess
import time
import os
import json
from datetime import datetime
from pathlib import Path

DEMO_DIR = Path("/Users/bclement/git/klang/ide/demo")
SCREENSHOTS_DIR = DEMO_DIR / "screenshots"
OUTPUT_DIR = DEMO_DIR / "output"
RECORD_SCRIPT = DEMO_DIR / "record-demo.sh"

# Ensure directories exist
SCREENSHOTS_DIR.mkdir(parents=True, exist_ok=True)
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

def run_cmd(cmd, wait=True):
    """Run a shell command."""
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if wait:
        time.sleep(0.5)
    return result.stdout.strip()

def screenshot(name):
    """Take a screenshot and return the path."""
    result = run_cmd(f'"{RECORD_SCRIPT}" screenshot {name}')
    # Get the filename from the output
    lines = result.split('\n')
    for line in lines:
        if '.png' in line:
            return line.strip()
    return None

def focus_vscode():
    """Focus VS Code window."""
    run_cmd('osascript -e \'tell application "Visual Studio Code" to activate\'')
    time.sleep(0.5)

def run_vscode_command(command):
    """Run a VS Code command via command palette."""
    focus_vscode()
    # Open command palette
    run_cmd('osascript -e \'tell application "System Events" to keystroke "p" using {command down, shift down}\'')
    time.sleep(0.5)
    # Type command
    for char in command:
        run_cmd(f'osascript -e \'tell application "System Events" to keystroke "{char}"\'')
        time.sleep(0.03)
    time.sleep(0.3)
    # Press enter
    run_cmd('osascript -e \'tell application "System Events" to key code 36\'')
    time.sleep(1)

def open_file(filepath):
    """Open a file in VS Code."""
    focus_vscode()
    run_cmd('osascript -e \'tell application "System Events" to keystroke "p" using {command down}\'')
    time.sleep(0.3)
    for char in filepath:
        run_cmd(f'osascript -e \'tell application "System Events" to keystroke "{char}"\'')
        time.sleep(0.03)
    time.sleep(0.3)
    run_cmd('osascript -e \'tell application "System Events" to key code 36\'')
    time.sleep(1)

def escape():
    """Press escape to close dialogs."""
    run_cmd('osascript -e \'tell application "System Events" to key code 53\'')
    time.sleep(0.3)

class DemoRecorder:
    def __init__(self):
        self.slides = []
        self.timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    def add_slide(self, title, description, screenshot_path, features=None):
        """Add a slide to the demo."""
        self.slides.append({
            'title': title,
            'description': description,
            'screenshot': screenshot_path,
            'features': features or []
        })

    def capture_feature(self, name, title, description, setup_fn=None, features=None):
        """Capture a feature with optional setup."""
        print(f"\n📸 Capturing: {title}")

        if setup_fn:
            setup_fn()
            time.sleep(1)

        screenshot_path = screenshot(name)

        if screenshot_path:
            self.add_slide(title, description, screenshot_path, features)
            print(f"   ✓ Screenshot saved: {screenshot_path}")
        else:
            print(f"   ✗ Failed to capture screenshot")

    def generate_html(self):
        """Generate an HTML slideshow."""
        html = '''<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>K Language IDE - Feature Demo</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
            color: white;
            min-height: 100vh;
        }
        .header {
            text-align: center;
            padding: 40px 20px;
            background: rgba(0,0,0,0.3);
        }
        .header h1 {
            font-size: 3rem;
            margin-bottom: 10px;
            background: linear-gradient(90deg, #00d2ff, #3a7bd5);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .header p {
            font-size: 1.2rem;
            opacity: 0.8;
        }
        .slideshow {
            max-width: 1400px;
            margin: 0 auto;
            padding: 20px;
        }
        .slide {
            background: rgba(255,255,255,0.1);
            border-radius: 20px;
            margin-bottom: 40px;
            overflow: hidden;
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255,255,255,0.1);
        }
        .slide-header {
            padding: 30px;
            background: rgba(0,0,0,0.2);
        }
        .slide-number {
            display: inline-block;
            background: linear-gradient(90deg, #00d2ff, #3a7bd5);
            color: white;
            width: 40px;
            height: 40px;
            border-radius: 50%;
            text-align: center;
            line-height: 40px;
            font-weight: bold;
            margin-right: 15px;
        }
        .slide-title {
            display: inline;
            font-size: 1.8rem;
        }
        .slide-description {
            padding: 20px 30px;
            font-size: 1.1rem;
            line-height: 1.6;
            opacity: 0.9;
        }
        .slide-image {
            width: 100%;
            border-top: 1px solid rgba(255,255,255,0.1);
        }
        .slide-image img {
            width: 100%;
            display: block;
        }
        .features-list {
            padding: 20px 30px;
            background: rgba(0,0,0,0.2);
        }
        .features-list h4 {
            margin-bottom: 15px;
            color: #00d2ff;
        }
        .features-list ul {
            list-style: none;
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 10px;
        }
        .features-list li {
            padding: 10px 15px;
            background: rgba(255,255,255,0.05);
            border-radius: 8px;
            border-left: 3px solid #00d2ff;
        }
        .features-list li::before {
            content: "✓ ";
            color: #00d2ff;
        }
        .nav {
            position: fixed;
            bottom: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: rgba(0,0,0,0.8);
            padding: 15px 30px;
            border-radius: 50px;
            display: flex;
            gap: 20px;
            backdrop-filter: blur(10px);
        }
        .nav button {
            background: linear-gradient(90deg, #00d2ff, #3a7bd5);
            border: none;
            color: white;
            padding: 10px 25px;
            border-radius: 25px;
            cursor: pointer;
            font-size: 1rem;
            transition: transform 0.2s;
        }
        .nav button:hover {
            transform: scale(1.05);
        }
        .nav span {
            line-height: 40px;
        }
        .footer {
            text-align: center;
            padding: 40px;
            opacity: 0.6;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>🔷 K Language IDE</h1>
        <p>Constraint Programming Language Support for VS Code & IntelliJ</p>
        <p style="margin-top: 10px; font-size: 0.9rem;">Generated: ''' + datetime.now().strftime("%Y-%m-%d %H:%M:%S") + '''</p>
    </div>
    
    <div class="slideshow">
'''

        for i, slide in enumerate(self.slides, 1):
            features_html = ""
            if slide['features']:
                features_html = f'''
        <div class="features-list">
            <h4>Key Features</h4>
            <ul>
                {''.join(f'<li>{f}</li>' for f in slide["features"])}
            </ul>
        </div>'''

            # Convert absolute path to relative for HTML
            img_path = slide['screenshot']
            if img_path:
                # Copy image to output directory
                img_name = os.path.basename(img_path)

                html += f'''
        <div class="slide" id="slide-{i}">
            <div class="slide-header">
                <span class="slide-number">{i}</span>
                <h2 class="slide-title">{slide["title"]}</h2>
            </div>
            <div class="slide-description">
                {slide["description"]}
            </div>
            <div class="slide-image">
                <img src="screenshots/{img_name}" alt="{slide["title"]}">
            </div>
            {features_html}
        </div>
'''

        html += '''
    </div>
    
    <div class="footer">
        <p>K Language - Constraint Programming Made Easy</p>
        <p>NASA JPL / Open Source</p>
    </div>
    
    <script>
        // Smooth scroll to slides
        document.querySelectorAll('.nav button').forEach(btn => {
            btn.addEventListener('click', () => {
                const slides = document.querySelectorAll('.slide');
                const current = Math.round(window.scrollY / window.innerHeight);
                if (btn.textContent === '← Previous' && current > 0) {
                    slides[current - 1].scrollIntoView({ behavior: 'smooth' });
                } else if (btn.textContent === 'Next →' && current < slides.length - 1) {
                    slides[current + 1].scrollIntoView({ behavior: 'smooth' });
                }
            });
        });
    </script>
</body>
</html>
'''
        return html

    def save(self, filename="demo.html"):
        """Save the demo as HTML."""
        output_path = OUTPUT_DIR / filename
        html = self.generate_html()

        with open(output_path, 'w') as f:
            f.write(html)

        # Create symlinks for screenshots
        screenshots_link = OUTPUT_DIR / "screenshots"
        if not screenshots_link.exists():
            screenshots_link.symlink_to(SCREENSHOTS_DIR)

        print(f"\n✅ Demo saved to: {output_path}")
        return output_path


def run_demo():
    """Run the full demo capture sequence."""
    recorder = DemoRecorder()

    print("=" * 60)
    print("K Language IDE - Demo Recording")
    print("=" * 60)

    # Feature 1: Syntax Highlighting
    def setup_syntax():
        open_file("src/examples/Shapes.k")
        time.sleep(1)

    recorder.capture_feature(
        "01_syntax",
        "Syntax Highlighting",
        "The K Language extension provides rich syntax highlighting for .k files, making your constraint specifications easy to read and understand. Keywords, types, identifiers, literals, and comments are all distinctly colored.",
        setup_syntax,
        ["Keyword highlighting (class, fun, req, extends)",
         "Type highlighting (Int, Real, Bool)",
         "String and number literal support",
         "Comment support (-- and /* */)",
         "Bracket matching"]
    )

    # Feature 2: Run K File
    def setup_run():
        open_file("src/examples/Shapes.k")
        time.sleep(0.5)
        run_vscode_command("K: Run K File")
        time.sleep(3)

    recorder.capture_feature(
        "02_run",
        "Run K Files",
        "Execute K files directly from VS Code with a single command. Results appear in a dedicated output panel showing statistics, solutions, and any type errors.",
        setup_run,
        ["Right-click context menu to run",
         "Command palette integration",
         "Output panel with formatted results",
         "Error highlighting in editor",
         "Support for command-line arguments"]
    )

    # Feature 3: Solution Visualization
    def setup_viz():
        run_vscode_command("K: Visualize Solution")
        time.sleep(2)

    recorder.capture_feature(
        "03_visualization",
        "Solution Visualization",
        "View your constraint solutions as a beautiful visual diagram. Objects are shown with their properties, and relationships are displayed clearly. Interactive pan and zoom let you explore complex solutions.",
        setup_viz,
        ["Automatic object layout",
         "Property values displayed inline",
         "Reference relationships shown",
         "Pan and zoom navigation",
         "Copy solution as text"]
    )

    # Feature 4: Auto-Solve Mode
    def setup_auto():
        run_vscode_command("K: Enable Auto-Solve")
        time.sleep(1)

    recorder.capture_feature(
        "04_auto_solve",
        "Auto-Solve Mode",
        "Enable auto-solve to automatically re-run the solver whenever you make changes. With intelligent debouncing, the solver runs after you stop typing, providing instant feedback on your constraints.",
        setup_auto,
        ["Toggle on/off from command palette",
         "Status bar indicator",
         "Intelligent debouncing (500ms default)",
         "Cancellation of in-progress solves",
         "Error feedback inline"]
    )

    # Feature 5: Constraint Debugger
    def setup_debug():
        run_vscode_command("K: Open Debug Panel")
        time.sleep(2)

    recorder.capture_feature(
        "05_debugger",
        "Constraint Debugger",
        "Step through constraints one at a time to understand how your specification is satisfied. Toggle constraints on/off to explore what-if scenarios. UNSAT core highlighting shows which constraints conflict.",
        setup_debug,
        ["Step forward/backward through constraints",
         "Toggle individual constraints",
         "UNSAT core highlighting",
         "External function detection (Java/Python)",
         "Breakpoint support"]
    )

    # Feature 6: Inline Decorations
    def setup_inline():
        open_file("src/examples/Shapes.k")
        time.sleep(0.5)
        run_vscode_command("K: Run K File")
        time.sleep(2)

    recorder.capture_feature(
        "06_inline_values",
        "Inline Value Ranges",
        "See feasible value ranges directly in your editor, just like a debugger shows variable values. After solving, properties are annotated with their possible values, helping you understand the solution space.",
        setup_inline,
        ["Value ranges shown inline",
         "SAT/UNSAT status per constraint",
         "Hover for detailed information",
         "Clear decorations command",
         "Works with auto-solve"]
    )

    # Feature 7: CEGAR Loop (if available)
    def setup_cegar():
        run_vscode_command("K: Open Debug Panel")
        time.sleep(2)

    recorder.capture_feature(
        "07_cegar",
        "CEGAR Loop Visibility",
        "Monitor the Counter-Example Guided Abstraction Refinement loop as it iterates. See how the solver progressively refines its understanding of your constraints, with iteration counts and timing information.",
        setup_cegar,
        ["Iteration counter display",
         "Progress visualization",
         "Timing information",
         "Refinement details on click"]
    )

    # Close any open panels
    escape()
    escape()

    # Save the demo
    output_path = recorder.save(f"k_language_demo_{recorder.timestamp}.html")

    print("\n" + "=" * 60)
    print("Demo recording complete!")
    print(f"Open in browser: file://{output_path}")
    print("=" * 60)

    # Try to open in browser
    run_cmd(f'open "{output_path}"')

    return output_path


if __name__ == "__main__":
    import sys

    if len(sys.argv) > 1 and sys.argv[1] == "--quick":
        # Quick mode - just take one screenshot
        focus_vscode()
        time.sleep(0.5)
        print(screenshot("quick_test"))
    else:
        run_demo()

