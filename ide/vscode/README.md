# K Language Support for VS Code

Syntax highlighting and language support for the K constraint programming language.

## Features

- **Syntax Highlighting**: Full syntax highlighting for K language constructs
  - Keywords: `class`, `req`, `fun`, `forall`, `exists`, etc.
  - Types: `Int`, `Real`, `Bool`, `String`, `Set`, `Seq`, etc.
  - Constraints: `req`, `soft req`, `minimize`, `maximize`
  - Annotations: `@timeout`, `@bestEffort`, `@opaque`, etc.
  - Operators: `=>`, `<=>`, `isin`, `union`, `inter`, etc.

- **Code Folding**: Fold class and function definitions

- **Bracket Matching**: Auto-closing and matching for `{}`, `[]`, `()`

- **Comment Support**: Line comments (`--`) and block comments (`==...==`)

## Installation

### From VSIX (Local)

1. Build the extension:
   ```bash
   cd ide/vscode
   npm install
   npx vsce package
   ```

2. Install the `.vsix` file:
   - Open VS Code
   - Go to Extensions (Ctrl+Shift+X)
   - Click "..." menu → "Install from VSIX..."
   - Select the generated `.vsix` file

### Development Mode

1. Open this folder in VS Code
2. Press F5 to launch Extension Development Host
3. Open a `.k` file to see syntax highlighting

## Example

```k
class Spacecraft {
  name : String
  weight : Real
  maxWeight : Real = 1000

  -- Hard constraint
  req notTooHeavy: weight <= maxWeight

  -- Soft constraint with optimization
  soft req preferLight: weight <= 500
  minimize weight

  fun totalWeight : Real {
    instrument.collect(i -> i.weight).sum()
  }
}
```

## Roadmap

See [IDE_DESIGN_VISION.md](../../docs/IDE_DESIGN_VISION.md) for the full vision.

### Phase 1 (Current)
- [x] Syntax highlighting
- [x] Bracket matching
- [x] Code folding

### Phase 2 (Planned)
- [ ] Language Server Protocol (LSP) support
- [ ] Go-to-definition
- [ ] Find references
- [ ] Code completion

### Phase 3 (Planned)
- [ ] Solver integration
- [ ] Solution visualization
- [ ] UNSAT diagnostics

## Contributing

Contributions are welcome! Please see the main repository README for guidelines.

## License

[Same as klang repository]

