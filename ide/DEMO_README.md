# K Language IDE Demo Resources

This directory contains tools and scripts for creating demo videos and screenshots of the K Language IDE features.

## Quick Start

### Option 1: Guided Recording (Recommended)

Run the interactive demo guide while screen recording:

```bash
# 1. Start screen recording (Cmd+Shift+5 on macOS)
# 2. Run the guide
./demo-recording-guide.sh
# 3. Follow the on-screen prompts
```

### Option 2: Automated Screenshots

Capture screenshots automatically:

```bash
python3 demo-screenshots.py
```

Screenshots are saved to `klang/.tmp/demo_screenshots/`

## Files

| File | Description |
|------|-------------|
| `demo-recording-guide.sh` | Interactive terminal guide for manual recording |
| `demo-screenshots.py` | Python script for automated screenshot capture |
| `DEMO_VIDEO_SCRIPT.md` | Full script with text overlays and narration |

## Features to Demo

### 1. **Syntax Highlighting**
- Open any `.k` file
- Show keywords, types, comments

### 2. **Navigation**
- `Cmd+Shift+O` - Document symbols
- `Cmd+Click` - Go to definition

### 3. **Running K Files**
- Right-click → Run K File
- Show solver output

### 4. **Debug Panel**
- Command Palette → "K: Open Debug Panel"
- Session tabs, status indicators
- Constraint list with stepping

### 5. **Constraint Breakpoints**
- Click red dot to toggle
- Visual breakpoint indicator

### 6. **External Functions**
- Open `src/tests/mixed_external1.k`
- Show Java/Python badges
- CEGAR iterations

### 7. **Auto-Solve**
- Toggle auto-solve checkbox
- Edit file, watch re-solve

### 8. **Inline Decorations**
- Show values inline after solving
- Satisfied/unsatisfied indicators

## Recording Tips

### Resolution
- 1920x1080 or 2560x1440
- VS Code zoom: 120-140%

### Theme
- Dark theme for contrast
- High contrast colors

### Audio
- Use external microphone
- Record in quiet environment
- Follow `DEMO_VIDEO_SCRIPT.md` for narration

### Duration
- Target: 5-8 minutes
- Can be shorter for social media

## Sample Files

Good K files for demos:

1. **Basic**: `src/examples/Shapes.k`
   - Classes, inheritance, constraints
   - Good for syntax and navigation

2. **External Functions**: `src/tests/mixed_external1.k`
   - Java + Python imports
   - CEGAR demonstration

3. **Complex**: `src/examples/spacecraft.k` (if available)
   - Real-world use case

## Post-Processing

### Add Text Overlays
Use the captions from `DEMO_VIDEO_SCRIPT.md` with:
- iMovie (macOS)
- DaVinci Resolve (free, cross-platform)
- Adobe Premiere

### Compress for Web
```bash
ffmpeg -i demo.mov -c:v libx264 -crf 23 -preset medium -c:a aac -b:a 128k demo.mp4
```

## Accessibility

- Include captions/subtitles
- Use high contrast colors
- Speak clearly if narrating
- Pause on important features


