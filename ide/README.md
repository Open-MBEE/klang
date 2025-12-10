# K Language IDE Support

This directory contains IDE plugin implementations for the K constraint programming language.

## Overview

K is a declarative constraint programming language backed by the Z3 SMT solver. Unlike traditional imperative languages, IDE support for K focuses on:

- **Constraint visualization** rather than control flow
- **Solution exploration** rather than step debugging
- **UNSAT diagnostics** rather than runtime errors
- **Multi-language integration** (Java, Python) via CEGAR refinement

See [IDE_DESIGN_VISION.md](../docs/IDE_DESIGN_VISION.md) for the complete design vision.

## Available Plugins

### VS Code Extension
📁 `vscode/`

Lightweight extension providing:
- Syntax highlighting (TextMate grammar)
- Bracket matching
- Code folding
- Comment toggling

**Installation:**
```bash
cd vscode
npm install
npx vsce package
# Install the generated .vsix file
```

### JetBrains Plugin
📁 `jetbrains/`

Full-featured plugin for IntelliJ IDEA, with planned support for:
- Syntax highlighting
- Code navigation
- Completion
- Solver integration
- Solution visualization

**Installation:**
```bash
cd jetbrains
./gradlew buildPlugin
# Install the generated ZIP from build/distributions/
```

## Feature Comparison

| Feature | VS Code | JetBrains |
|---------|---------|-----------|
| Syntax Highlighting | ✅ | ✅ |
| Bracket Matching | ✅ | ✅ |
| Code Folding | ✅ | 🔜 |
| Go to Definition | 🔜 | 🔜 |
| Find References | 🔜 | 🔜 |
| Code Completion | 🔜 | 🔜 |
| Error Diagnostics | 🔜 | 🔜 |
| Solver Integration | 🔜 | 🔜 |
| Solution Viewer | 🔜 | 🔜 |
| UNSAT Analysis | 🔜 | 🔜 |
| Java Navigation | 🔜 | 🔜 |
| Python Navigation | 🔜 | 🔜 |

✅ = Implemented, 🔜 = Planned

## Architecture

Both plugins can share a common Language Server Protocol (LSP) backend:

```
┌─────────────────────────────────────────────────────────────┐
│                    IDE Clients                              │
│  ┌─────────────────────┐    ┌─────────────────────┐        │
│  │    VS Code          │    │    IntelliJ         │        │
│  │    Extension        │    │    Plugin           │        │
│  └──────────┬──────────┘    └──────────┬──────────┘        │
│             │                          │                    │
│             │     LSP (JSON-RPC)       │                    │
│             └────────────┬─────────────┘                    │
│                          │                                  │
│  ┌───────────────────────▼───────────────────────┐         │
│  │           K Language Server                    │         │
│  │  ┌─────────────────────────────────────────┐  │         │
│  │  │ Parser (ANTLR - existing Model.g4)      │  │         │
│  │  │ TypeChecker (existing TypeChecker.scala)│  │         │
│  │  │ Solver (existing K2Z3.scala)            │  │         │
│  │  │ Symbol Table                            │  │         │
│  │  │ Reference Resolution                    │  │         │
│  │  └─────────────────────────────────────────┘  │         │
│  └───────────────────────────────────────────────┘         │
│                          │                                  │
│                          │ JNI / Native                     │
│                          ▼                                  │
│  ┌─────────────────────────────────────────────┐           │
│  │                   Z3                         │           │
│  └─────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────┘
```

## Development Roadmap

### Phase 1: Basic Support (Current)
- [x] VS Code syntax highlighting
- [x] JetBrains plugin skeleton
- [ ] Complete lexer/parser integration

### Phase 2: Language Features
- [ ] Symbol table from parsed AST
- [ ] Go to definition
- [ ] Find all references
- [ ] Code completion
- [ ] Error diagnostics

### Phase 3: Solver Integration
- [ ] "Solve" command
- [ ] Solution display panel
- [ ] UNSAT core highlighting
- [ ] Incremental solving

### Phase 4: Advanced Features
- [ ] Constraint graph visualization
- [ ] "Why?" explanations for variable values
- [ ] Counterfactual ("what if") analysis
- [ ] CEGAR debugging for external functions

### Phase 5: Multi-Language
- [ ] Java import navigation
- [ ] Python import navigation
- [ ] Mixed-language debugging

## Contributing

We welcome contributions! Areas where help is needed:

1. **LSP Implementation**: Create shared language server
2. **Parser Integration**: Connect ANTLR grammar to IDE
3. **UI/UX Design**: Visualization for constraint graphs
4. **Testing**: Test cases for language features
5. **Documentation**: User guides and tutorials

## Resources

- [K Language README](../README.md)
- [IDE Design Vision](../docs/IDE_DESIGN_VISION.md)
- [K Grammar](../src/grammar/Model.g4)
- [Type Checker](../src/k/frontend/TypeChecker.scala)
- [Z3 Integration](../src/k/frontend/K2Z3.scala)

