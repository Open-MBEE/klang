#!/usr/bin/env python3
"""
K Language IDE Demo Screenshot Automation

This script uses AppleScript to automate VS Code and capture screenshots
for demo purposes. It's not a full video recording, but captures key states.

Prerequisites:
- macOS with AppleScript support
- VS Code with K Language extension
- Accessibility permissions for Terminal (System Preferences → Security & Privacy → Privacy → Accessibility)

Usage:
    python3 demo-screenshots.py

Output:
    Screenshots saved to klang/.tmp/demo_screenshots/
"""

import subprocess
import os
import time
from datetime import datetime

# Output directory
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), '.tmp', 'demo_screenshots')
os.makedirs(OUTPUT_DIR, exist_ok=True)

def run_applescript(script):
    """Run an AppleScript command"""
    try:
        result = subprocess.run(
            ['osascript', '-e', script],
            capture_output=True,
            text=True,
            timeout=30
        )
        return result.returncode == 0, result.stdout, result.stderr
    except subprocess.TimeoutExpired:
        return False, "", "Timeout"
    except Exception as e:
        return False, "", str(e)

def take_screenshot(name):
    """Take a screenshot of the current screen"""
    timestamp = datetime.now().strftime("%H%M%S")
    filename = os.path.join(OUTPUT_DIR, f"{timestamp}_{name}.png")

    # Use screencapture command (captures entire screen)
    result = subprocess.run(
        ['screencapture', '-x', filename],  # -x = no sound
        capture_output=True
    )

    if result.returncode == 0:
        print(f"  📸 Captured: {name}")
        return filename
    else:
        print(f"  ❌ Failed to capture: {name}")
        return None

def activate_vscode():
    """Bring VS Code to front"""
    script = '''
    tell application "Visual Studio Code"
        activate
    end tell
    '''
    return run_applescript(script)

def open_command_palette():
    """Open VS Code command palette"""
    script = '''
    tell application "System Events"
        tell process "Code"
            keystroke "p" using {command down, shift down}
        end tell
    end tell
    '''
    return run_applescript(script)

def type_text(text):
    """Type text in VS Code"""
    script = f'''
    tell application "System Events"
        tell process "Code"
            keystroke "{text}"
        end tell
    end tell
    '''
    return run_applescript(script)

def press_enter():
    """Press Enter key"""
    script = '''
    tell application "System Events"
        tell process "Code"
            keystroke return
        end tell
    end tell
    '''
    return run_applescript(script)

def press_escape():
    """Press Escape key"""
    script = '''
    tell application "System Events"
        tell process "Code"
            key code 53
        end tell
    end tell
    '''
    return run_applescript(script)

def demo_sequence():
    """Run the demo sequence with screenshots"""

    print("\n" + "="*60)
    print("K Language IDE - Automated Demo Screenshot Capture")
    print("="*60 + "\n")

    print("⚠️  This script will control your keyboard and mouse.")
    print("⚠️  Make sure VS Code is open with a K file.")
    print("⚠️  Press Ctrl+C at any time to abort.\n")

    input("Press ENTER to start (or Ctrl+C to cancel)...")
    print()

    # Step 1: Activate VS Code
    print("Step 1: Activating VS Code...")
    success, _, err = activate_vscode()
    if not success:
        print(f"  ⚠️  Could not activate VS Code: {err}")
        print("  Please make sure VS Code is running.")
        return
    time.sleep(1)
    take_screenshot("01_initial_editor")

    # Step 2: Open Document Symbols
    print("\nStep 2: Opening Document Symbols (Cmd+Shift+O)...")
    script = '''
    tell application "System Events"
        tell process "Code"
            keystroke "o" using {command down, shift down}
        end tell
    end tell
    '''
    run_applescript(script)
    time.sleep(1)
    take_screenshot("02_document_symbols")
    press_escape()
    time.sleep(0.5)

    # Step 3: Open Command Palette and run K file
    print("\nStep 3: Opening Command Palette...")
    open_command_palette()
    time.sleep(0.5)
    take_screenshot("03_command_palette")

    print("\nStep 4: Typing 'K: Run'...")
    type_text("K: Run")
    time.sleep(0.5)
    take_screenshot("04_run_command")
    press_escape()
    time.sleep(0.5)

    # Step 5: Open Debug Panel
    print("\nStep 5: Opening Debug Panel...")
    open_command_palette()
    time.sleep(0.5)
    type_text("K: Open Debug")
    time.sleep(0.5)
    take_screenshot("05_debug_command")
    press_enter()
    time.sleep(2)  # Wait for panel to load
    take_screenshot("06_debug_panel")

    print("\n" + "="*60)
    print("Demo screenshots complete!")
    print(f"Screenshots saved to: {OUTPUT_DIR}")
    print("="*60 + "\n")

    # List captured screenshots
    screenshots = sorted(os.listdir(OUTPUT_DIR))
    if screenshots:
        print("Captured files:")
        for f in screenshots:
            print(f"  • {f}")

if __name__ == '__main__':
    try:
        demo_sequence()
    except KeyboardInterrupt:
        print("\n\nDemo cancelled by user.")
    except Exception as e:
        print(f"\nError: {e}")

