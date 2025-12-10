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
        self.verbose = False  # Show raw K output (parse tree, etc.)
        self.last_smt = None  # Store last generated SMT-LIB2
        self.last_result = None  # Store last result for inspection

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
            'extra_objects': [],
            'statistics': {},
            'smt': None,
            'parse_tree': None
        }

        lines = output.split('\n')

        # Extract parse tree (for verbose mode)
        for i, line in enumerate(lines):
            if line.startswith('PARSE TREE:'):
                if i + 1 < len(lines):
                    result['parse_tree'] = lines[i + 1]
                break

        # Extract statistics
        in_stats = False
        for line in lines:
            if 'STATISTICS:' in line:
                in_stats = True
                continue
            if in_stats and '---' in line:
                continue
            if in_stats and ':' in line and not line.strip().startswith('-'):
                parts = line.split(':')
                if len(parts) == 2:
                    key = parts[0].strip()
                    val = parts[1].strip()
                    if val.isdigit():
                        result['statistics'][key] = int(val)
            if in_stats and line.strip() == '':
                in_stats = False

        # Extract solution table if present
        if 'Top level objects created:' in output:
            result['has_solution'] = True
            in_table = False
            in_extra = False
            for line in lines:
                if 'Top level objects created:' in line:
                    in_table = True
                    in_extra = False
                    continue
                if 'Extra objects created' in line:
                    in_table = False
                    in_extra = True
                    continue
                if 'No extra objects' in line:
                    in_extra = False
                    continue

                # Parse table rows
                target_list = result['solutions'] if in_table else result['extra_objects'] if in_extra else None
                if target_list is not None:
                    if 'Variable' in line and ('Value' in line or 'Ref' in line):
                        continue
                    if line.strip().startswith('+'):
                        continue
                    if line.strip() and '|' not in line and not line.strip().startswith('+'):
                        in_table = False
                        in_extra = False
                    if '|' in line or (line.strip() and not line.strip().startswith('+')):
                        # Try to parse as table row
                        # Format: "variable  Ref N  Value" or with | separators
                        parts = [p.strip() for p in line.replace('|', ' ').split() if p.strip()]
                        if len(parts) >= 2:
                            var_name = parts[0]
                            # Skip header-like rows
                            if var_name in ['Variable', '+', '-', '']:
                                continue
                            # Find value - typically last part or after "Ref N"
                            value = ' '.join(parts[1:])
                            target_list.append({
                                'variable': var_name,
                                'value': value
                            })

        # Check for UNSAT
        if 'unsatisfiable' in output.lower() or 'UNSAT' in output:
            result['status'] = 'unsat'
            result['has_solution'] = False

        # Check for type errors (must have Exception, not just "Type checking")
        if 'TypeCheckException' in output:
            result['status'] = 'type_error'
            # Extract error message
            for line in lines:
                if '[TypeChecker]' in line:
                    result['error_message'] = line.split('[TypeChecker]')[-1].strip()
                    break
        elif 'K2Z3Exception' in output or 'K2SMTException' in output:
            result['status'] = 'smt_error'
        elif 'Exception' in output and result['status'] != 'unsat':
            result['status'] = 'error'

        return result

    def _format_result(self, result):
        """Format the result for display in Jupyter."""
        if result['status'] == 'error':
            error_msg = result.get('error', result.get('error_message', 'Unknown error'))
            if self.verbose:
                return f"❌ **Error:** {error_msg}\n\n```\n{result.get('raw_output', '')}\n```"
            return f"❌ **Error:** {error_msg}"

        if result['status'] == 'timeout':
            return f"⏱️ {result.get('error', 'Solver timed out')}"

        if result['status'] == 'type_error':
            error_msg = result.get('error_message', 'Type checking failed')
            if self.verbose:
                return f"❌ **Type Error:** {error_msg}\n\n```\n{result.get('raw_output', '')}\n```"
            return f"❌ **Type Error:** {error_msg}"

        if result['status'] == 'smt_error':
            if self.verbose:
                return f"❌ **SMT Error**\n\n```\n{result.get('raw_output', '')}\n```"
            return "❌ **SMT Error:** Could not generate or solve SMT constraints"

        if result['status'] == 'unsat':
            return "❌ **UNSATISFIABLE**\n\nThe constraints cannot be satisfied simultaneously."

        if result.get('has_solution'):
            output = "✅ **SAT** - Solution found:\n\n"

            if result['solutions']:
                output += "| Variable | Value |\n"
                output += "|----------|-------|\n"
                for sol in result['solutions']:
                    output += f"| `{sol['variable']}` | `{sol['value']}` |\n"

            if result.get('extra_objects'):
                output += "\n**Additional objects:**\n\n"
                output += "| Object | Value |\n"
                output += "|--------|-------|\n"
                for obj in result['extra_objects']:
                    output += f"| `{obj['variable']}` | `{obj['value']}` |\n"

            if self.verbose and result.get('statistics'):
                output += "\n**Statistics:**\n"
                for key, val in result['statistics'].items():
                    output += f"- {key}: {val}\n"

            return output

        # Default: return raw output if verbose, otherwise minimal
        if self.verbose:
            return f"```\n{result.get('raw_output', 'No output')}\n```"
        return "✅ Model processed successfully"

    def _format_html_result(self, result):
        """Format result as HTML for richer display."""
        if result['status'] == 'error':
            error_msg = result.get('error', result.get('error_message', 'Unknown error'))
            html = f'<div style="color: #c0392b;"><b>❌ Error:</b> {error_msg}</div>'
            if self.verbose:
                html += f'<pre style="background: #fdf2f2; padding: 10px; border-radius: 4px; overflow-x: auto;">{result.get("raw_output", "")}</pre>'
            return html

        if result['status'] == 'type_error':
            error_msg = result.get('error_message', 'Type checking failed')
            html = f'<div style="color: #c0392b;"><b>❌ Type Error:</b> {error_msg}</div>'
            if self.verbose:
                html += f'<pre style="background: #fdf2f2; padding: 10px; border-radius: 4px; overflow-x: auto;">{result.get("raw_output", "")}</pre>'
            return html

        if result['status'] == 'unsat':
            return '''<div style="color: #c0392b;">
                <h4 style="margin: 0;">❌ UNSATISFIABLE</h4>
                <p style="margin: 5px 0 0 0;">The constraints cannot be satisfied simultaneously.</p>
            </div>'''

        if result.get('has_solution'):
            html = '<div style="font-family: -apple-system, BlinkMacSystemFont, sans-serif;">'
            html += '<h4 style="color: #27ae60; margin: 0 0 10px 0;">✅ SAT - Solution Found</h4>'

            if result['solutions']:
                html += '''<table style="border-collapse: collapse; margin: 10px 0; width: auto;">
                    <tr style="background: #f8f9fa;">
                        <th style="padding: 8px 16px; border: 1px solid #dee2e6; text-align: left;">Variable</th>
                        <th style="padding: 8px 16px; border: 1px solid #dee2e6; text-align: left;">Value</th>
                    </tr>'''
                for sol in result['solutions']:
                    html += f'''<tr>
                        <td style="padding: 8px 16px; border: 1px solid #dee2e6; font-family: monospace; background: #fff;">{sol["variable"]}</td>
                        <td style="padding: 8px 16px; border: 1px solid #dee2e6; font-family: monospace; background: #fff;">{sol["value"]}</td>
                    </tr>'''
                html += '</table>'

            if result.get('extra_objects'):
                html += '<h5 style="margin: 15px 0 5px 0; color: #666;">Additional Objects:</h5>'
                html += '''<table style="border-collapse: collapse; margin: 5px 0; width: auto;">
                    <tr style="background: #f8f9fa;">
                        <th style="padding: 6px 12px; border: 1px solid #dee2e6; text-align: left; font-size: 0.9em;">Object</th>
                        <th style="padding: 6px 12px; border: 1px solid #dee2e6; text-align: left; font-size: 0.9em;">Value</th>
                    </tr>'''
                for obj in result['extra_objects']:
                    html += f'''<tr>
                        <td style="padding: 6px 12px; border: 1px solid #dee2e6; font-family: monospace; font-size: 0.9em;">{obj["variable"]}</td>
                        <td style="padding: 6px 12px; border: 1px solid #dee2e6; font-family: monospace; font-size: 0.9em;">{obj["value"]}</td>
                    </tr>'''
                html += '</table>'

            if self.verbose and result.get('statistics'):
                html += '<details style="margin-top: 10px;"><summary style="cursor: pointer; color: #666;">Statistics</summary>'
                html += '<ul style="margin: 5px 0; padding-left: 20px;">'
                for key, val in result['statistics'].items():
                    html += f'<li><code>{key}</code>: {val}</li>'
                html += '</ul></details>'

            html += '</div>'
            return html

        if result['status'] == 'unsat':
            return '<div style="color: #c0392b;"><h4>❌ UNSATISFIABLE</h4><p>The constraints cannot be satisfied simultaneously.</p></div>'

        if self.verbose:
            return f'<pre style="background: #f8f9fa; padding: 10px; border-radius: 4px;">{result.get("raw_output", "")}</pre>'
        return '<div style="color: #27ae60;">✅ Model processed successfully</div>'

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
        self.last_result = result  # Store for %smt, %stats, %raw commands

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
            self.last_result = None
            self.last_smt = None
            if not silent:
                self.send_response(self.iopub_socket, 'stream', {
                    'name': 'stdout',
                    'text': '🔄 Model reset. Start fresh!\n'
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
                self.last_result = result
                if not silent:
                    self.send_response(self.iopub_socket, 'display_data', {
                        'data': {
                            'text/plain': self._format_result(result),
                            'text/html': self._format_html_result(result)
                        },
                        'metadata': {}
                    })

        elif magic == '%verbose':
            arg = args.strip().lower()
            if arg in ['on', 'true', '1', 'yes']:
                self.verbose = True
                msg = '🔍 Verbose mode ON - showing full K output\n'
            elif arg in ['off', 'false', '0', 'no']:
                self.verbose = False
                msg = '🔇 Verbose mode OFF - showing clean output\n'
            else:
                self.verbose = not self.verbose
                msg = f'🔍 Verbose mode {"ON" if self.verbose else "OFF"}\n'
            if not silent:
                self.send_response(self.iopub_socket, 'stream', {
                    'name': 'stdout',
                    'text': msg
                })

        elif magic == '%smt':
            if not self.k_code:
                if not silent:
                    self.send_response(self.iopub_socket, 'stream', {
                        'name': 'stderr',
                        'text': 'No K code. Enter some K code first.\n'
                    })
            else:
                # Run with -smt flag to get SMT output
                full_code = '\n\n'.join(self.k_code)
                # For now, show raw output which includes SMT if available
                # TODO: Add -smt flag support to K frontend
                if not silent:
                    if self.last_result and self.last_result.get('raw_output'):
                        # Try to extract SMT from output
                        output = self.last_result['raw_output']
                        smt_start = output.find('(set-logic')
                        if smt_start == -1:
                            smt_start = output.find('(declare-')
                        if smt_start >= 0:
                            # Find a reasonable end point
                            smt_content = output[smt_start:]
                            self.send_response(self.iopub_socket, 'display_data', {
                                'data': {
                                    'text/plain': f'SMT-LIB2 Output:\n\n{smt_content[:2000]}...' if len(smt_content) > 2000 else f'SMT-LIB2 Output:\n\n{smt_content}',
                                    'text/html': f'<details><summary><b>SMT-LIB2 Output</b> (click to expand)</summary><pre style="background: #f5f5f5; padding: 10px; max-height: 400px; overflow: auto;">{smt_content}</pre></details>'
                                },
                                'metadata': {}
                            })
                        else:
                            self.send_response(self.iopub_socket, 'stream', {
                                'name': 'stdout',
                                'text': 'No SMT output found. Run %verbose on and re-execute to capture SMT.\n'
                            })
                    else:
                        self.send_response(self.iopub_socket, 'stream', {
                            'name': 'stdout',
                            'text': 'No previous result. Execute your K model first.\n'
                        })

        elif magic == '%stats':
            if self.last_result and self.last_result.get('statistics'):
                stats = self.last_result['statistics']
                text = "**Model Statistics:**\n\n"
                for key, val in stats.items():
                    text += f"- {key}: {val}\n"
                if not silent:
                    self.send_response(self.iopub_socket, 'display_data', {
                        'data': {
                            'text/plain': text,
                            'text/markdown': text
                        },
                        'metadata': {}
                    })
            else:
                if not silent:
                    self.send_response(self.iopub_socket, 'stream', {
                        'name': 'stdout',
                        'text': 'No statistics available. Execute your K model first.\n'
                    })

        elif magic == '%raw':
            if self.last_result and self.last_result.get('raw_output'):
                if not silent:
                    self.send_response(self.iopub_socket, 'display_data', {
                        'data': {
                            'text/plain': self.last_result['raw_output'],
                            'text/html': f'<pre style="background: #f5f5f5; padding: 10px; max-height: 500px; overflow: auto;">{self.last_result["raw_output"]}</pre>'
                        },
                        'metadata': {}
                    })
            else:
                if not silent:
                    self.send_response(self.iopub_socket, 'stream', {
                        'name': 'stdout',
                        'text': 'No output available. Execute your K model first.\n'
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
            # Try relative to K_HOME if not absolute
            if not os.path.isabs(filepath) and self.k_dir:
                k_path = self.k_dir / filepath
                if k_path.exists():
                    filepath = str(k_path)

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
            help_text = """# K Jupyter Kernel

Interactive constraint programming with K backed by Z3.

## Magic Commands

| Command | Description |
|---------|-------------|
| `%reset` | Clear the current K model |
| `%solve` | Explicitly solve the current model |
| `%show` | Display the accumulated K code |
| `%verbose [on/off]` | Toggle verbose output (parse trees, etc.) |
| `%smt` | Show generated SMT-LIB2 code |
| `%stats` | Show model statistics |
| `%raw` | Show raw K output |
| `%timeout N` | Set solver timeout to N seconds |
| `%load file` | Load a K file |
| `%save file` | Save current model to file |
| `%help` | Show this help |

## Usage

Enter K code in cells to build up a model incrementally.
Each cell's code is accumulated and solved automatically.
Use `%reset` to start a new model.

## Example

```k
-- Cell 1: Define a class
class Point {
    x : Int
    y : Int
    req x >= 0
    req y >= 0
}

-- Cell 2: Add constraints
p : Point
req p.x + p.y = 10
req p.x < p.y
```

## K Language Basics

- **Classes**: `class Name { properties and constraints }`
- **Properties**: `name : Type`
- **Constraints**: `req expression`
- **Soft constraints**: `soft req expression`
- **Functions**: `fun name(params) : ReturnType { body }`
- **Types**: `Int`, `Real`, `Bool`, `String`, `Set[T]`, `Seq[T]`
- **Optimization**: `minimize expr` or `maximize expr`
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

