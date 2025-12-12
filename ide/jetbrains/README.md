# K Language JetBrains Plugin

IntelliJ IDEA plugin providing comprehensive support for the K constraint programming language.

## Features

### Navigation & Code Intelligence
- [x] File type recognition (.k files)
- [x] Syntax highlighting with customizable colors
- [x] Go to Definition (Ctrl+Click / F12)
- [x] Find Usages (Alt+F7)
- [x] Code completion (IntelliSense)
- [x] Structure view (Alt+7)
- [x] Documentation on hover (Ctrl+Q)
- [x] Breadcrumb navigation

### Editing
- [x] Code formatting (Ctrl+Alt+L)
- [x] Code folding for classes, functions, and comments
- [x] Comment support (-- // /* */ and ===...===)
- [x] Brace matching
- [x] Rename refactoring (Shift+F6)
- [x] Color scheme customization

### Running & Diagnostics
- [x] Run K files (right-click → Run, or Cmd+Shift+R)
- [x] Run configuration with customizable settings
- [x] Real-time error highlighting
- [x] Solution visualization tool window

### Planned (Future)
- [ ] Debugger integration
- [ ] UNSAT explanation visualization
- [ ] Constraint graph visualization
- [ ] Line markers for constraint status

## Building

### Prerequisites
- JDK 17+
- Gradle 8+

### Build Commands

```bash
# Build the plugin
./gradlew buildPlugin

# Run in development IDE
./gradlew runIde

# Just compile (faster)
./gradlew compileKotlin
```

The built plugin will be in `build/distributions/`.

## Installation

### From ZIP
1. Go to Settings → Plugins → ⚙️ → Install Plugin from Disk
2. Select the ZIP file from `build/distributions/`
3. Restart IDE

### Development Mode
Run `./gradlew runIde` to launch a development instance of IntelliJ IDEA with the plugin installed.

## Requirements

- IntelliJ IDEA 2024.3+ (or other JetBrains IDE 243+)
- K installation (for running files): The plugin looks for `{project}/export/k`
- Java 21+ (auto-detected from SDKMAN)

## Architecture

The plugin follows standard IntelliJ Platform architecture:

```
src/main/kotlin/nasa/jpl/klang/ide/
├── KLanguage.kt          # Language definition
├── KFileType.kt          # File type (.k)
├── KIcons.kt             # Icon resources
├── KCommenter.kt         # Comment handling
├── KBraceMatcher.kt      # Bracket matching
├── lexer/                # Lexer for syntax highlighting
├── parser/               # Parser definition
├── psi/                  # PSI element types
├── highlighting/         # Syntax & semantic highlighting
├── completion/           # Code completion
├── navigation/           # Go to definition, find usages
├── documentation/        # Documentation provider
├── structure/            # Structure view
├── formatting/           # Code formatter
├── folding/              # Code folding
├── breadcrumbs/          # Breadcrumb navigation
├── refactoring/          # Rename refactoring
├── toolwindow/           # Solution visualization
└── run/                  # Run configurations
```


## Roadmap

See [IDE_DESIGN_VISION.md](../../docs/IDE_DESIGN_VISION.md) for the complete vision document.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run `./gradlew check` to verify
5. Submit a pull request

## License

[Same as klang repository]

