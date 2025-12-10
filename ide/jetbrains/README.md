# K Language JetBrains Plugin

IntelliJ IDEA plugin providing support for the K constraint programming language.

## Features

### Implemented
- [x] File type recognition (.k files)
- [x] Syntax highlighting
- [x] Comment support (line `--` and block `==...==`)
- [x] Brace matching

### Planned (Phase 2)
- [ ] Parser integration (using existing ANTLR grammar)
- [ ] Go to Definition
- [ ] Find Usages
- [ ] Code completion
- [ ] Structure view
- [ ] Error highlighting

### Planned (Phase 3 - Solver Integration)
- [ ] "Solve" action (Ctrl+Shift+S)
- [ ] Solutions tool window
- [ ] UNSAT explanation
- [ ] Constraint graph visualization
- [ ] Line markers for constraint status

## Building

### Prerequisites
- JDK 17+
- Gradle 8+

### Build Commands

```bash
# Build the plugin
./gradlew build

# Run in development IDE
./gradlew runIde

# Package as ZIP
./gradlew buildPlugin
```

The built plugin will be in `build/distributions/`.

## Installation

### From ZIP
1. Go to Settings → Plugins → ⚙️ → Install Plugin from Disk
2. Select the ZIP file from `build/distributions/`
3. Restart IDE

### Development Mode
Run `./gradlew runIde` to launch a development instance of IntelliJ IDEA with the plugin installed.

## Architecture

The plugin follows standard IntelliJ Platform architecture:

```
src/main/kotlin/nasa/jpl/klang/ide/
├── KLanguage.kt          # Language definition
├── KFileType.kt          # File type (.k)
├── KIcons.kt             # Icon resources
├── KCommenter.kt         # Comment handling
├── parser/               # Parser (TODO: integrate ANTLR)
├── highlighting/         # Syntax highlighter
├── completion/           # Code completion
├── navigation/           # Go to definition, find usages
├── structure/            # Structure view
├── annotator/            # Semantic highlighting
├── folding/              # Code folding
├── toolwindow/           # Solutions panel
├── actions/              # IDE actions
└── run/                  # Run configurations
```

### Integration with K Compiler

The plugin can integrate with the existing K compiler by:

1. **Embedding**: Include klang JAR as a dependency
2. **Process**: Run klang as an external process
3. **LSP**: (Future) Implement Language Server Protocol

For best performance, we recommend embedding the klang JAR and calling
the Scala APIs directly:

```kotlin
// Example: Using K type checker
val model = KScalaVisitor().visitModel(parseTree)
val typeChecker = TypeChecker()
val errors = typeChecker.check(model)
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

