# K Language IDE Demo

This folder contains tools for generating demos and documentation for the K Language IDE extensions.

## Quick Start

### Generate Video Demo (Recommended)
```bash
# Record video clips of each feature (requires ffmpeg)
python3 record_videos.py
```

This will:
1. Record 6 short video clips (6-8 seconds each) demonstrating key features
2. Generate an HTML page with all videos embedded
3. Open the result in your browser

### Generate Screenshot Slideshow  
```bash
# Run the full automated screenshot demo
python3 generate_demo.py

# Quick test - just take one screenshot
python3 generate_demo.py --quick
```

### Manual Screenshot/Recording
```bash
# Take a single screenshot
./record-demo.sh screenshot my_feature

# Focus VS Code and run a command
./record-demo.sh focus-vscode
./record-demo.sh command "Run K File"

# Interactive demo with manual confirmations
./record-demo.sh interactive
```

## Output Files

- `screenshots/` - Captured screenshots with timestamps
- `videos/` - Recorded video clips
- `output/` - Generated HTML demos

## Generated Demo Features

The automated demos capture these K Language IDE features:

1. **Syntax Highlighting** - Rich syntax coloring for .k files
2. **Run K Files** - Execute K files with results in output panel
3. **Solution Visualization** - Interactive object diagram
4. **Auto-Solve Mode** - Automatic re-solving on edit
5. **Constraint Debugger** - Step through constraints
6. **Inline Value Ranges** - Editor decorations showing values

## Prerequisites

- **ffmpeg** - For video recording: `brew install ffmpeg`
- **VS Code** - With K Language extension installed
- **macOS** - Uses native screen capture and AppleScript

## Manual Video Recording

If automated recording doesn't work, you can use macOS QuickTime:
1. Open QuickTime Player
2. File → New Screen Recording
3. Select a portion of screen containing VS Code
4. Demonstrate each feature for 6-8 seconds
5. Save clips to `videos/` folder

## Customizing the Demo

Edit `record_videos.py` to:
- Add new features to capture
- Change recording duration per feature
- Modify the HTML template styling
- Add custom narration text

## Notes

- The demo automation uses AppleScript on macOS to control VS Code
- Screen recording uses ffmpeg with avfoundation
- Videos auto-play when scrolled into view in the HTML output
- Keep your hands off keyboard/mouse during automated recording!
