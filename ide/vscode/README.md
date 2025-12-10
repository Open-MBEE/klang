# K Language Support for VS Code

This extension provides language support for the K constraint programming language.

## Features

- **Syntax Highlighting**: Full syntax highlighting for K language constructs
- **Go to Definition** (F12 / Ctrl+Click): Navigate to symbol definitions
- **Find All References** (Shift+F12): Find all usages of a symbol
- **Hover Documentation**: See documentation for symbols on hover
- **Document Outline** (Ctrl+Shift+O): Navigate symbols within a file
- **Workspace Symbol Search** (Ctrl+T): Search for symbols across all K files
- **Code Comments**: Toggle comments with Ctrl+/
- **Bracket Matching**: Automatic matching of (), {}, []
- **Code Folding**: Fold class and function definitions

## Installation

### From VSIX (Local Install)

1. Build the extension (requires Node.js 20+):
   ```bash
   cd ide/vscode
   npm install
   npm run compile
   npm run package
   ```

2. Install the generated `.vsix` file:
   - Open VS Code
   - Go to Extensions view (Ctrl+Shift+X)
   - Click the "..." menu at the top
   - Select "Install from VSIX..."
   - Navigate to the generated `k-language-0.1.0.vsix` file

### Development Mode

1. Open the `ide/vscode` folder in VS Code
2. Run `npm install` to install dependencies
3. Press F5 to launch the extension in development mode

## Supported K Language Features

### Keywords
- Declaration: `class`, `assoc`, `package`, `import`, `extends`, `type`
- Functions: `fun`, `pre`, `post`
- Constraints: `req`, `soft req`
- Modifiers: `part`, `var`, `val`, `ordered`, `unique`
- Control flow: `if`, `then`, `else`, `match`, `case`

### Built-in Types
- Primitives: `Bool`, `Char`, `Int`, `Real`, `String`, `Unit`, `Time`, `Duration`
- Collections: `Set`, `OSet`, `Bag`, `Seq`, `Class`, `Tuple`

### Comments
- Line comments: `--` or `//`
- Block comments: `/* ... */`
- Documentation blocks: `/** ... */` or `====== ... ======`

## Usage Examples

### Go to Definition
Click on a class name while holding Ctrl (or Cmd on Mac) to jump to its definition.

### Find References
Right-click on a symbol and select "Find All References" to see everywhere it's used.

### Outline View
Open the Outline panel in the Explorer sidebar to see the structure of your K file.

## Contributing

See the main repository README for contribution guidelines.

