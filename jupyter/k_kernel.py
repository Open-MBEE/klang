#!/usr/bin/env python3
"""
K Language Jupyter Kernel

A Jupyter kernel for the K declarative constraint programming language.
K models are compiled to SMT-LIB2 and solved using Z3.

Features:
- Define K classes and constraints incrementally
- Solve models and display solutions
- Visualize constraint satisfaction
- Mix K code with markdown documentation

Magic commands:
- %reset     - Clear the current K model
- %solve     - Solve the current model
- %smt       - Show generated SMT-LIB2 code
- %timeout N - Set solver timeout to N seconds
- %load file - Load a K file
- %save file - Save current model to file
"""

from ipykernel.kernelbase import Kernel
import subprocess
import tempfile
import os
import json
import re
from pathlib import Path


class KKernel(Kernel):
    implementation = 'K'
    implementation_version = '1.0'
    language = 'k'
    language_version = '2.3.6'
    language_info = {
        'name': 'K',
        'mimetype': 'text/x-k',
        'file_extension': '.k',
        'codemirror_mode': 'text/x-k',
    }
    banner = """K Language Kernel
A declarative constraint programming language backed by Z3.

Use %help for available magic commands.
"""

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.k_code = []  # Accumulated K code blocks
        self.timeout = 30  # Default timeout in seconds

        # Find the K installation directory
        self.k_dir = self._find_k_dir()
        if not self.k_dir:
            self.log.warning("Could not find K installation directory")

    def _find_k_dir(self):
        """Find the K language installation directory."""
        # Try relative to this file
        kernel_dir = Path(__file__).parent
        k_dir = kernel_dir.parent

        if (k_dir / 'export' / 'k').exists():
            return k_dir

        # Try environment variable
        k_home = os.environ.get('K_HOME')
        if k_home and Path(k_home).exists():
            return Path(k_home)

        # Try common locations
        for path in ['/usr/local/klang', os.path.expanduser('~/klang')]:
            if Path(path).exists():
                return Path(path)

        return None

    def _run_k(self, k_code):
        """Run K code and return the result."""
        if not self.k_dir:
            return {
                'status': 'error',
                'error': 'K installation not found. Set K_HOME environment variable.'
            }

        # Write K code to temp file
        with tempfile.NamedTemporaryFile(mode='w', suffix='.k', delete=False) as f:
            f.write(k_code)
            temp_file = f.name

        try:
            # Run K
            k_script = self.k_dir / 'export' / 'k'
            result = subprocess.run(
                [str(k_script), temp_file],
                capture_output=True,
                text=True,
                timeout=self.timeout,
                cwd=str(self.k_dir)
            )

            output = result.stdout + result.stderr

            # Parse the output
            return self._parse_k_output(output, result.returncode)

        except subprocess.TimeoutExpired:
            return {
                'status': 'timeout',
                'error': f'Solver timed out after {self.timeout} seconds'
            }
        except Exception as e:
            return {
                'status': 'error',
                'error': str(e)
            }
        finally:
            os.unlink(temp_file)

    def _parse_k_output(self, output, returncode):
        """Parse K output into structured result."""
        result = {
            'status': 'ok' if returncode == 0 else 'error',
            'raw_output': output,
            'solutions': [],
            'statistics': {},
            'smt': None
        }

        # Extract solution table if present
        if 'Top level objects created:' in output:
            result['has_solution'] = True
            # Parse the solution table
            lines = output.split('\n')
            in_table = False
            for line in lines:
                if 'Variable' in line and 'Value' in line:
                    in_table = True
                    continue
                if in_table and line.strip().startswith('+'):
                    continue
                if in_table and line.strip() and '|' not in line:
                    in_table = False
                if in_table and '|' in line:
                    # Parse table row
                    parts = [p.strip() for p in line.split('|') if p.strip()]
                    if len(parts) >= 2:
                        result['solutions'].append({
                            'variable': parts[0],
                            'value': parts[-1] if len(parts) > 2 else parts[1]
                        })

        # Check for UNSAT
        if 'unsatisfiable' in output.lower() or 'UNSAT' in output:
            result['status'] = 'unsat'
            result['has_solution'] = False

        # Check for type errors (must have Exception, not just "Type checking")
        if 'TypeCheckException' in output:
            result['status'] = 'type_error'
        elif 'Exception' in output and result['status'] != 'unsat':
            result['status'] = 'error'

        return result

    def _format_result(self, result):
        """Format the result for display in Jupyter."""
        if result['status'] == 'error':
            return f"❌ Error: {result.get('error', 'Unknown error')}\n\n{result.get('raw_output', '')}"

        if result['status'] == 'timeout':
            return f"⏱️ {result['error']}"

        if result['status'] == 'type_error':
            return f"❌ Type Error\n\n{result.get('raw_output', '')}"

        if result['status'] == 'unsat':
            return "❌ UNSATISFIABLE\n\nThe constraints cannot be satisfied simultaneously."

        if result.get('has_solution') and result['solutions']:
            # Format as a nice table
            output = "✅ **SAT** - Solution found:\n\n"
            output += "| Variable | Value |\n"
            output += "|----------|-------|\n"
            for sol in result['solutions']:
                output += f"| `{sol['variable']}` | `{sol['value']}` |\n"
            return output

        # Default: return raw output
        return result.get('raw_output', 'No output')

    def _format_html_result(self, result):
        """Format result as HTML for richer display."""
        if result['status'] == 'error':
            return f'<div style="color: red; font-family: monospace;"><b>Error:</b><br><pre>{result.get("raw_output", "")}</pre></div>'

        if result.get('has_solution') and result['solutions']:
            html = '<div style="font-family: sans-serif;">'
            html += '<h4 style="color: green;">✅ SAT - Solution Found</h4>'
            html += '<table style="border-collapse: collapse; margin: 10px 0;">'
            html += '<tr style="background: #f0f0f0;"><th style="padding: 8px; border: 1px solid #ddd;">Variable</th><th style="padding: 8px; border: 1px solid #ddd;">Value</th></tr>'
            for sol in result['solutions']:
                html += f'<tr><td style="padding: 8px; border: 1px solid #ddd; font-family: monospace;">{sol["variable"]}</td>'
                html += f'<td style="padding: 8px; border: 1px solid #ddd; font-family: monospace;">{sol["value"]}</td></tr>'
            html += '</table></div>'
            return html

        if result['status'] == 'unsat':
            return '<div style="color: red;"><h4>❌ UNSATISFIABLE</h4><p>The constraints cannot be satisfied simultaneously.</p></div>'

        return f'<pre>{result.get("raw_output", "")}</pre>'

    def do_execute(self, code, silent, store_history=True, user_expressions=None, allow_stdin=False):
        """Execute K code or magic command."""

        code = code.strip()

        # Handle magic commands
        if code.startswith('%'):
            return self._handle_magic(code, silent)

        # Handle empty input
        if not code:
            return {'status': 'ok', 'execution_count': self.execution_count,
                    'payload': [], 'user_expressions': {}}

        # Accumulate K code
        self.k_code.append(code)

        # Combine all K code and run
        full_code = '\n\n'.join(self.k_code)
        result = self._run_k(full_code)

        if not silent:
            # Send both plain text and HTML
            text_output = self._format_result(result)
            html_output = self._format_html_result(result)

            self.send_response(self.iopub_socket, 'display_data', {
                'data': {
                    'text/plain': text_output,
                    'text/html': html_output,
                    'text/markdown': text_output
                },
                'metadata': {}
            })

        return {
            'status': 'ok',
            'execution_count': self.execution_count,
            'payload': [],
            'user_expressions': {}
        }

    def _handle_magic(self, code, silent):
        """Handle magic commands."""
        parts = code.split(None, 1)
        magic = parts[0].lower()
        args = parts[1] if len(parts) > 1 else ''

        if magic == '%reset':
            self.k_code = []
            if not silent:
                self.send_response(self.iopub_socket, 'stream', {
                    'name': 'stdout',
                    'text': '🔄 K model reset. Start fresh!\n'
                })

        elif magic == '%solve':
            if not self.k_code:
                if not silent:
                    self.send_response(self.iopub_socket, 'stream', {
                        'name': 'stderr',
                        'text': 'No K code to solve. Enter some K code first.\n'
                    })
            else:
                full_code = '\n\n'.join(self.k_code)
                result = self._run_k(full_code)
                if not silent:
                    self.send_response(self.iopub_socket, 'display_data', {
                        'data': {
                            'text/plain': self._format_result(result),
                            'text/html': self._format_html_result(result)
                        },
                        'metadata': {}
                    })

        elif magic == '%smt':
            if not silent:
                self.send_response(self.iopub_socket, 'stream', {
                    'name': 'stdout',
                    'text': 'SMT output display not yet implemented\n'
                })

        elif magic == '%timeout':
            try:
                self.timeout = int(args)
                if not silent:
                    self.send_response(self.iopub_socket, 'stream', {
                        'name': 'stdout',
                        'text': f'⏱️ Timeout set to {self.timeout} seconds\n'
                    })
            except ValueError:
                if not silent:
                    self.send_response(self.iopub_socket, 'stream', {
                        'name': 'stderr',
                        'text': f'Invalid timeout value: {args}\n'
                    })

        elif magic == '%load':
            filepath = args.strip()
            if os.path.exists(filepath):
                with open(filepath, 'r') as f:
                    self.k_code = [f.read()]
                if not silent:
                    self.send_response(self.iopub_socket, 'stream', {
                        'name': 'stdout',
                        'text': f'📂 Loaded {filepath}\n'
                    })
            else:
                if not silent:
                    self.send_response(self.iopub_socket, 'stream', {
                        'name': 'stderr',
                        'text': f'File not found: {filepath}\n'
                    })

        elif magic == '%save':
            filepath = args.strip()
            if filepath:
                with open(filepath, 'w') as f:
                    f.write('\n\n'.join(self.k_code))
                if not silent:
                    self.send_response(self.iopub_socket, 'stream', {
                        'name': 'stdout',
                        'text': f'💾 Saved to {filepath}\n'
                    })

        elif magic == '%show':
            if not silent:
                self.send_response(self.iopub_socket, 'display_data', {
                    'data': {
                        'text/plain': '\n\n'.join(self.k_code) if self.k_code else '(empty model)',
                        'text/markdown': f'```k\n{chr(10).join(self.k_code) if self.k_code else "(empty model)"}\n```'
                    },
                    'metadata': {}
                })

        elif magic == '%help':
            help_text = """
# K Jupyter Kernel - Magic Commands

| Command | Description |
|---------|-------------|
| `%reset` | Clear the current K model |
| `%solve` | Solve the current model |
| `%show` | Display the current K model |
| `%smt` | Show generated SMT-LIB2 code |
| `%timeout N` | Set solver timeout to N seconds |
| `%load file` | Load a K file |
| `%save file` | Save current model to file |
| `%help` | Show this help |

## Usage

Enter K code in cells to build up a model incrementally.
Each cell's code is accumulated until you run `%reset`.

Example:
```k
class Point {
    x : Int
    y : Int
    req x >= 0
    req y >= 0
}

p : Point
req p.x + p.y = 10
```
"""
            if not silent:
                self.send_response(self.iopub_socket, 'display_data', {
                    'data': {
                        'text/plain': help_text,
                        'text/markdown': help_text
                    },
                    'metadata': {}
                })

        else:
            if not silent:
                self.send_response(self.iopub_socket, 'stream', {
                    'name': 'stderr',
                    'text': f'Unknown magic command: {magic}\nUse %help for available commands.\n'
                })

        return {
            'status': 'ok',
            'execution_count': self.execution_count,
            'payload': [],
            'user_expressions': {}
        }

    def do_complete(self, code, cursor_pos):
        """Provide code completion."""
        # Basic completion for K keywords
        keywords = [
            'class', 'extends', 'fun', 'req', 'soft', 'forall', 'exists',
            'if', 'then', 'else', 'match', 'case',
            'Int', 'Real', 'Bool', 'String', 'Time', 'Duration',
            'Set', 'Seq', 'Bag', 'OSet',
            'true', 'false', 'null',
            'isin', 'union', 'inter', 'subset',
            'minimize', 'maximize',
            'import', 'package'
        ]

        # Find the word being typed
        code_to_cursor = code[:cursor_pos]
        match = re.search(r'(\w+)$', code_to_cursor)

        if match:
            prefix = match.group(1)
            start = cursor_pos - len(prefix)
            matches = [k for k in keywords if k.startswith(prefix)]
        else:
            start = cursor_pos
            matches = []

        return {
            'status': 'ok',
            'matches': matches,
            'cursor_start': start,
            'cursor_end': cursor_pos,
            'metadata': {}
        }

    def do_inspect(self, code, cursor_pos, detail_level=0):
        """Provide inspection/documentation."""
        return {
            'status': 'ok',
            'found': False,
            'data': {},
            'metadata': {}
        }


if __name__ == '__main__':
    from ipykernel.kernelapp import IPKernelApp
    IPKernelApp.launch_instance(kernel_class=KKernel)

