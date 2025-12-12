# K Language Support for VS Code

This extension provides comprehensive language support for the K constraint programming language.

## Features

### Navigation & Code Intelligence
- **Syntax Highlighting**: Full syntax highlighting for K language constructs
- **Go to Definition** (F12 / Ctrl+Click): Navigate to symbol definitions
- **Find All References** (Shift+F12): Find all usages of a symbol
- **Hover Documentation**: See documentation for symbols on hover
- **Document Outline** (Ctrl+Shift+O): Navigate symbols within a file
- **Workspace Symbol Search** (Ctrl+T): Search for symbols across all K files
- **Code Completion**: IntelliSense for keywords, types, and symbols

### Editing
- **Code Formatting** (Shift+Alt+F): Format your K code
- **Code Folding**: Fold classes, functions, and block comments
- **Code Comments**: Toggle comments with Ctrl+/
- **Bracket Matching**: Automatic matching of (), {}, []
- **Rename Symbol** (F2): Rename symbols across files

### Running & Diagnostics
- **Run K Files** (Cmd+Alt+R or right-click): Execute K files directly
- **Real-time Diagnostics**: See type errors as you type
- **Solution Visualization** (Cmd+Alt+V): View solver results in a pretty webview

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
   - Navigate to the generated `k-language-0.5.0.vsix` file

### Development Mode

1. Open the `ide/vscode` folder in VS Code
2. Run `npm install` to install dependencies
3. Press F5 to launch the extension in development mode

## Configuration

The extension provides the following settings:

- `k.installation.path`: Path to your K installation (auto-detected from workspace)
- `k.java.home`: Path to Java home (auto-detected from SDKMAN if available)

## Supported K Language Features

### Keywords
- Declaration: `class`, `assoc`, `package`, `import`, `extends`, `type`
- Functions: `fun`, `pre`, `post`
- Constraints: `req`, `soft req`, `assert`
- Modifiers: `part`, `var`, `val`, `ordered`, `unique`
- Control flow: `if`, `then`, `else`, `match`, `case`
- Quantifiers: `forall`, `exists`

### Built-in Types
- Primitives: `Bool`, `Char`, `Int`, `Real`, `String`, `Unit`, `Time`, `Duration`
- Collections: `Set`, `OSet`, `Bag`, `Seq`

### Comments
- Line comments: `--` or `//`
- Block comments: `/* ... */`
- Documentation blocks: `/** ... */` or `======...======`

## Usage Examples

### Go to Definition
Click on a class name while holding Ctrl (or Cmd on Mac) to jump to its definition.

### Find References
Right-click on a symbol and select "Find All References" to see everywhere it's used.

### Run K File
Right-click in a K file and select "Run K File", or press Cmd+Alt+R.

### View Solution
After running a K file, use Cmd+Alt+V to visualize the solver's solution in a formatted view.

## Requirements

- **K Installation**: The extension expects to find the K runner at `{workspace}/export/k`
- **Java**: Java 21+ is required to run K files (automatically detected from SDKMAN)

## Contributing

See the main repository README for contribution guidelines.

