#!/usr/bin/env python3
"""
K Language Python Bridge

This module provides a Py4J gateway server that allows the K language (running on JVM)
to call Python functions during CEGAR refinement.

Usage:
    python -m k_python_bridge [--port PORT] [--debug] [--debug-port DEBUG_PORT]

The bridge supports:
    - Calling arbitrary Python functions from any installed module
    - Type conversion between Python and Java
    - Debug mode with debugpy for stepping through Python code

Author: K Language Team
"""

import sys
import os
import importlib
import json
import traceback
from typing import Any, List, Optional

# Check for py4j
try:
    from py4j.java_gateway import JavaGateway, CallbackServerParameters, GatewayParameters
    PY4J_AVAILABLE = True
except ImportError:
    PY4J_AVAILABLE = False
    print("Warning: py4j not installed. Install with: pip install py4j", file=sys.stderr)


class KPythonBridge:
    """
    Entry point for K to call Python functions.

    This class is exposed to Java via Py4J and handles:
    - Function resolution and invocation
    - Type conversion
    - Error handling
    """

    def __init__(self):
        self.module_cache = {}
        self.debug_mode = os.environ.get('K_PYTHON_DEBUG', '0') == '1'

    def call(self, module_name: str, func_name: str, args: List[Any]) -> Any:
        """
        Call a Python function and return the result.

        Args:
            module_name: The Python module name (e.g., 'math', 'numpy')
            func_name: The function name (e.g., 'sqrt', 'linalg.det')
            args: List of arguments (already converted from Java types)

        Returns:
            The function result (will be converted to Java type by Py4J)
        """
        try:
            # Get or import the module
            module = self._get_module(module_name)

            # Handle nested function names (e.g., 'linalg.det' in numpy)
            func = module
            for part in func_name.split('.'):
                func = getattr(func, part)

            # Convert arguments from Java types if needed
            converted_args = [self._convert_arg(arg) for arg in args]

            # Call the function
            result = func(*converted_args)

            # Convert result for Java
            return self._convert_result(result)

        except Exception as e:
            # Return error info that K can handle
            error_msg = f"Python error in {module_name}.{func_name}: {str(e)}"
            if self.debug_mode:
                error_msg += f"\n{traceback.format_exc()}"
            raise RuntimeError(error_msg)

    def _get_module(self, module_name: str):
        """Import and cache a module."""
        if module_name not in self.module_cache:
            self.module_cache[module_name] = importlib.import_module(module_name)
        return self.module_cache[module_name]

    def _convert_arg(self, arg: Any) -> Any:
        """Convert Java types to Python types."""
        # Py4J handles most conversions automatically
        # Handle special cases here
        if hasattr(arg, 'tolist'):  # Java arrays
            return arg.tolist()
        return arg

    def _convert_result(self, result: Any) -> Any:
        """Convert Python types to Java-compatible types."""
        import numbers

        if result is None:
            return None
        elif isinstance(result, bool):
            return result
        elif isinstance(result, numbers.Integral):
            return int(result)
        elif isinstance(result, numbers.Real):
            return float(result)
        elif isinstance(result, str):
            return result
        elif isinstance(result, (list, tuple)):
            return [self._convert_result(x) for x in result]
        elif isinstance(result, dict):
            return {str(k): self._convert_result(v) for k, v in result.items()}
        elif hasattr(result, 'tolist'):  # numpy arrays
            return result.tolist()
        else:
            # Convert to string as fallback
            return str(result)

    def is_available(self, module_name: str) -> bool:
        """Check if a module is available."""
        try:
            importlib.import_module(module_name)
            return True
        except ImportError:
            return False

    def get_version(self) -> str:
        """Get Python version info."""
        return f"Python {sys.version}"

    def ping(self) -> str:
        """Health check."""
        return "pong"

    class Java:
        implements = ['k.frontend.PythonBridgeInterface']


def start_gateway(port: int = 25333, debug: bool = False, debug_port: int = 5678):
    """
    Start the Py4J gateway server.

    Args:
        port: Port for Py4J communication
        debug: Enable debugpy for Python debugging
        debug_port: Port for debugpy
    """
    if not PY4J_AVAILABLE:
        print("Error: py4j is required. Install with: pip install py4j", file=sys.stderr)
        sys.exit(1)

    # Set up debugging if requested
    if debug or os.environ.get('K_PYTHON_DEBUG', '0') == '1':
        try:
            import debugpy
            actual_debug_port = int(os.environ.get('K_PYTHON_DEBUG_PORT', debug_port))
            debugpy.listen(('0.0.0.0', actual_debug_port))
            print(f"[K Python Bridge] Debugpy listening on port {actual_debug_port}")
            print(f"[K Python Bridge] Attach VS Code debugger to localhost:{actual_debug_port}")
        except ImportError:
            print("Warning: debugpy not installed. Debug mode disabled.", file=sys.stderr)
            print("Install with: pip install debugpy", file=sys.stderr)

    # Create the bridge instance
    bridge = KPythonBridge()

    # Start Py4J gateway
    from py4j.java_gateway import JavaGateway, CallbackServerParameters

    gateway = JavaGateway(
        gateway_parameters=GatewayParameters(port=port, auto_convert=True),
        callback_server_parameters=CallbackServerParameters(port=0),
        python_server_entry_point=bridge
    )

    print(f"[K Python Bridge] Gateway started on port {port}")
    print(f"[K Python Bridge] Python {sys.version}")
    print("[K Python Bridge] Ready to receive calls from K")

    # Keep the server running
    try:
        import time
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n[K Python Bridge] Shutting down...")
        gateway.shutdown()


def main():
    """Command-line entry point."""
    import argparse

    parser = argparse.ArgumentParser(description='K Language Python Bridge')
    parser.add_argument('--port', type=int, default=25333,
                        help='Py4J gateway port (default: 25333)')
    parser.add_argument('--debug', action='store_true',
                        help='Enable debugpy for Python debugging')
    parser.add_argument('--debug-port', type=int, default=5678,
                        help='Debugpy port (default: 5678)')

    args = parser.parse_args()

    start_gateway(port=args.port, debug=args.debug, debug_port=args.debug_port)


if __name__ == '__main__':
    main()

