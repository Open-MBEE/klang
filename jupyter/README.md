# K Language Jupyter Kernel

Interactive constraint programming with K in Jupyter notebooks.

## Features

- **Incremental Model Building**: Define K models cell by cell
- **Rich Output**: Solutions displayed as formatted HTML tables
- **Clean by Default**: Verbose output (parse trees, etc.) hidden unless requested
- **SMT Inspection**: View generated SMT-LIB2 code with `%smt`
- **Magic Commands**: Control the kernel with special commands
- **Code Completion**: Basic completion for K keywords
- **Load/Save**: Load existing K files or save your work

## Installation

### Prerequisites

- Python 3.7+
- Jupyter (notebook or lab)
- K language (this repository)
- Java 11+ (for K runtime)

### Quick Install

```bash
cd jupyter
./install.sh
```

### Manual Install

1. Install Python dependencies:
   ```bash
   pip install jupyter ipykernel
   ```

2. Copy kernel files:
   ```bash
   mkdir -p ~/.local/share/jupyter/kernels/k
   cp k_kernel.py ~/.local/share/jupyter/kernels/k/
   ```

3. Create `~/.local/share/jupyter/kernels/k/kernel.json`:
   ```json
   {
     "argv": ["python3", "/path/to/k_kernel.py", "-f", "{connection_file}"],
     "display_name": "K",
     "language": "k",
     "env": {"K_HOME": "/path/to/klang"}
   }
   ```

## Usage

### Starting Jupyter

```bash
jupyter notebook
# or
jupyter lab
```

Then create a new notebook and select the "K" kernel.

### Jupyter Notebook 7 Note

If double-clicking `.ipynb` files opens them as raw JSON instead of as a notebook,
you can fix this by creating `~/.jupyter/labconfig/default_setting_overrides.json`:

```json
{
  "@jupyterlab/docmanager-extension:plugin": {
    "defaultViewers": {
      "ipynb": "Notebook"
    }
  }
}
```

Alternatively, right-click the file and select "Open With" → "Notebook".

### Magic Commands

| Command | Description |
|---------|-------------|
| `%reset` | Clear the current K model |
| `%solve` | Explicitly solve the current model |
| `%show` | Display the accumulated K code |
| `%verbose [on/off]` | Toggle verbose output (parse trees, statistics) |
| `%smt` | Show generated SMT-LIB2 code |
| `%stats` | Show model statistics |
| `%raw` | Show raw K output |
| `%timeout N` | Set solver timeout to N seconds |
| `%load file` | Load a K file |
| `%save file` | Save current model to file |
| `%help` | Show help |

### Example Session

**Cell 1:**
```k
class Point {
    x : Int
    y : Int
    req x >= 0
    req y >= 0
}
```

**Cell 2:**
```k
p : Point
req p.x + p.y = 10
req p.x < p.y
```

**Output:**
```
✅ SAT - Solution found:

| Variable | Value |
|----------|-------|
| p | Point(x=4, y=6) |
```

### Incremental Development

Each cell's K code is accumulated. Use `%reset` to start fresh:

```k
%reset
-- Start a new model
```

### Loading Files

Load existing K models:

```k
%load ../src/examples/Shapes.k
```

## Troubleshooting

### Kernel not found

Make sure the kernel is installed:
```bash
jupyter kernelspec list
```

Should show:
```
k    /path/to/kernels/k
```

### K_HOME not set

Set the K_HOME environment variable to your klang directory:
```bash
export K_HOME=/path/to/klang
```

### Java not found

Ensure Java 11+ is installed and in your PATH:
```bash
java -version
```

## Development

### Running Tests

Unit tests (fast, no browser):
```bash
cd jupyter/tests
./run_tests.sh
# or
pytest test_k_kernel.py -v
```

UI tests with Playwright (requires browser):
```bash
# Install Playwright if needed
pip install playwright pytest-playwright
playwright install chromium

# Run with visible browser
pytest test_k_kernel_ui.py -v --headed
```

### Debugging

Enable verbose output:
```bash
jupyter console --kernel k --debug
```

## Architecture

```
┌─────────────────────┐
│  Jupyter Notebook   │
└──────────┬──────────┘
           │ JSON messages
           ▼
┌─────────────────────┐
│    K Kernel         │
│  (k_kernel.py)      │
└──────────┬──────────┘
           │ subprocess
           ▼
┌─────────────────────┐
│   K Frontend        │
│  (export/k)         │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   Z3 Solver         │
└─────────────────────┘
```

## Future Enhancements

- [ ] Syntax highlighting in notebooks (CodeMirror mode)
- [ ] Constraint visualization
- [ ] Solution exploration (multiple solutions)
- [ ] UNSAT core display
- [ ] Variable inspection on hover
- [ ] Inline documentation for K constructs

## License

Same as the K language project.

