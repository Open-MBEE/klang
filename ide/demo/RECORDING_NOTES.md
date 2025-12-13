# K Language IDE Demo Video Recording Notes

## Goal
Create short video clips (6-8 seconds each) demonstrating the K Language VS Code extension features for a demo/marketing video.

## Features to Demo
1. **Syntax Highlighting** - Open a .k file, show colorized code
2. **Run K File** - Execute a K file, show output panel with results
3. **Solution Visualization** - Show the interactive object diagram
4. **Auto-Solve Mode** - Enable auto-solve, make an edit, see it re-solve
5. **Constraint Debugger** - Open debug panel, step through constraints
6. **Inline Value Ranges** - Show value decorations in editor after solving

## Prerequisites

### Software Required
- **ffmpeg** - For screen recording: `brew install ffmpeg`
- **VS Code** - With K Language extension installed and working
- **K Language project** - Clone the klang repo

### macOS Permissions (CRITICAL)
The automation uses AppleScript to control VS Code. This requires permissions:

1. **System Preferences > Security & Privacy > Privacy > Accessibility**
   - Add Terminal (or iTerm)
   - Add Visual Studio Code

2. **System Preferences > Security & Privacy > Privacy > Automation**
   - Terminal must be allowed to control:
     - System Events
     - Visual Studio Code

3. **System Preferences > Security & Privacy > Privacy > Screen Recording**
   - Add Terminal (for ffmpeg screen capture)

**On locked-down corporate machines, these permissions may be blocked by MDM.**

## Scripts Available

### `simple_record.sh` (Recommended)
Simple bash script that:
- Uses ffmpeg to record screen
- Uses osascript/AppleScript to control VS Code
- Records 6 short clips, one per feature
- Generates an HTML page with embedded videos

Usage:
```bash
cd /path/to/klang/ide/demo
./simple_record.sh
```

### `record_videos.py` (Alternative)
Python version with same functionality but more complex.

Usage:
```bash
cd /path/to/klang/ide/demo
python3 record_videos.py
```

Add `--test` flag to test automation without recording:
```bash
python3 record_videos.py --test
```

## Problems Encountered on Corporate Laptop

### Problem 1: AppleScript Not Controlling VS Code
**Symptom**: Videos recorded but all showed the same static screen - no VS Code interaction visible.

**Cause**: macOS security permissions blocking Terminal from sending keystrokes to other applications.

**Diagnosis**: Run this test:
```bash
osascript -e 'tell application "Visual Studio Code" to activate'
osascript -e 'tell application "System Events" to keystroke "p" using {command down}'
```
If VS Code doesn't respond (no Quick Open), permissions are blocked.

### Problem 2: Screen Recording Permission
**Symptom**: ffmpeg fails or produces black video.

**Cause**: Terminal not allowed to record screen.

**Fix**: Add Terminal to Screen Recording in Privacy settings.

### Problem 3: Terminal Output Not Displaying
**Symptom**: Commands run but terminal shows no output.

**Cause**: Unknown - possibly related to terminal session state or IDE integration.

**Workaround**: Use `list_dir` tool to check file creation instead of terminal output.

## Alternative Approaches If Automation Fails

### Manual Recording with QuickTime
1. Open QuickTime Player
2. File > New Screen Recording
3. Select region around VS Code
4. Manually perform each feature demo (6-8 seconds each)
5. Save clips to `ide/demo/videos/` folder
6. Run the HTML generator portion of the script

### Manual Recording with OBS
1. Install OBS Studio
2. Set up screen capture source
3. Record each feature manually
4. Export as MP4 to `ide/demo/videos/`

### VS Code Extension-Based Recording
Could potentially use a VS Code extension that records the editor:
- CodeTour (for guided walkthroughs)
- GIF/Video recorder extensions

## Verifying the Setup Works

### Test 1: Check ffmpeg
```bash
ffmpeg -version
# Should show version info
```

### Test 2: Check screen capture device
```bash
ffmpeg -f avfoundation -list_devices true -i ""
# Look for "Capture screen 0" in the video devices list
```

### Test 3: Test screen recording
```bash
ffmpeg -y -f avfoundation -framerate 30 -i "1:none" -t 2 -c:v libx264 -preset ultrafast test.mp4
# Should create a 2-second video of your screen
```

### Test 4: Check AppleScript permissions
```bash
osascript -e 'tell application "Visual Studio Code" to activate'
# VS Code should come to foreground

osascript -e 'tell application "System Events" to keystroke "p" using {command down}'
# Quick Open should appear in VS Code
```

If Test 4 fails, you need to grant Accessibility/Automation permissions.

## Output Files

After successful recording:
- `ide/demo/videos/*.mp4` - Individual feature videos
- `ide/demo/output/demo_*.html` - HTML page with all videos embedded

## HTML Video Page

The generated HTML:
- Dark theme matching K Language branding
- 2-column grid of videos
- Videos auto-play when scrolled into view
- Videos loop and are muted by default
- Each video has feature title

## Tips for Good Demo Videos

1. **Clean VS Code window** - Close unnecessary panels/tabs
2. **Large font** - Increase editor font size for visibility
3. **Simple example file** - Use Shapes.k or similar
4. **Pause between actions** - Let viewer see each step
5. **Consistent timing** - 6-8 seconds per feature

## File Locations
- Scripts: `/Users/bclement/git/klang/ide/demo/`
- Videos: `/Users/bclement/git/klang/ide/demo/videos/`
- HTML output: `/Users/bclement/git/klang/ide/demo/output/`
- Test K file: `/Users/bclement/git/klang/src/examples/Shapes.k`

## Next Steps on New Computer

1. Clone the klang repo
2. Install ffmpeg: `brew install ffmpeg`
3. Open VS Code with K extension installed
4. Grant all necessary permissions (Accessibility, Automation, Screen Recording)
5. Run permission tests (see above)
6. Run `./simple_record.sh`
7. Check generated HTML in browser
8. If videos show actual VS Code interaction, success!

