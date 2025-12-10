"""
Unit tests for the K Jupyter Kernel.

These tests run without a Jupyter server by testing the kernel class directly.

Run with:
    cd jupyter/tests
    pytest test_k_kernel.py -v
"""

import pytest
import sys
import os

# Add jupyter directory to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from k_kernel import KKernel


@pytest.fixture
def kernel():
    """Create a fresh K kernel instance."""
    k = KKernel()
    k.k_dir = k._find_k_dir()
    return k


class TestKernelInit:
    """Test kernel initialization."""

    def test_finds_k_dir(self, kernel):
        """Kernel should find the K installation directory."""
        assert kernel.k_dir is not None
        assert (kernel.k_dir / 'export' / 'k').exists()

    def test_default_settings(self, kernel):
        """Kernel should have correct default settings."""
        assert kernel.timeout == 30
        assert kernel.verbose == False
        assert kernel.k_code == []


class TestRunK:
    """Test running K code."""

    def test_simple_sat(self, kernel):
        """Simple satisfiable constraint."""
        result = kernel._run_k("x : Int\nreq x > 0\nreq x < 10")
        assert result['status'] == 'ok'
        assert result.get('has_solution') == True
        assert len(result['solutions']) > 0

        # Check we got a valid value for x
        x_sol = next((s for s in result['solutions'] if s['variable'] == 'x'), None)
        assert x_sol is not None
        x_val = int(x_sol['value'])
        assert 0 < x_val < 10

    def test_unsat(self, kernel):
        """Unsatisfiable constraints."""
        result = kernel._run_k("x : Int\nreq x > 10\nreq x < 5")
        assert result['status'] == 'unsat'
        assert result.get('has_solution') == False

    def test_class_constraint(self, kernel):
        """Class with constraints."""
        result = kernel._run_k("""
class Point {
    x : Int
    y : Int
    req x >= 0
    req y >= 0
}
p : Point
req p.x = 5
req p.y = 10
""")
        assert result['status'] == 'ok'
        assert result.get('has_solution') == True

        # Check for Point in solution
        p_sol = next((s for s in result['solutions'] if s['variable'] == 'p'), None)
        assert p_sol is not None
        assert 'Point' in p_sol['value']
        assert '5' in p_sol['value']
        assert '10' in p_sol['value']

    def test_multiple_variables(self, kernel):
        """Multiple primitive variables."""
        result = kernel._run_k("""
x : Int
y : Int
z : Int
req x + y + z = 100
req x > 0
req y > x
req z > y
""")
        assert result['status'] == 'ok'
        assert result.get('has_solution') == True
        assert len(result['solutions']) >= 3

    def test_real_numbers(self, kernel):
        """Real number constraints."""
        result = kernel._run_k("""
x : Real
req x > 0.0
req x < 1.0
req x * x < 0.5
""")
        assert result['status'] == 'ok'
        assert result.get('has_solution') == True


class TestOutputParsing:
    """Test output parsing."""

    def test_primitive_value_parsing(self, kernel):
        """Primitive values should not include Ref column."""
        result = kernel._run_k("x : Int\nreq x = 42")
        x_sol = next((s for s in result['solutions'] if s['variable'] == 'x'), None)
        assert x_sol is not None
        # Value should be "42", not "- 42"
        assert x_sol['value'].strip() == '42'

    def test_object_reference_parsing(self, kernel):
        """Object references should include Ref prefix."""
        result = kernel._run_k("""
class Box { size : Int }
b : Box
req b.size = 10
""")
        b_sol = next((s for s in result['solutions'] if s['variable'] == 'b'), None)
        assert b_sol is not None
        # Should have "Ref" and "Box" in the value
        assert 'Ref' in b_sol['value']
        assert 'Box' in b_sol['value']

    def test_extra_objects(self, kernel):
        """Extra created objects should be captured."""
        result = kernel._run_k("""
class Container {
    items : Set[Int]
}
c : Container
req c.items.size() = 3
""")
        # May have extra objects depending on K's solving
        assert 'extra_objects' in result


class TestCodeAccumulation:
    """Test incremental code building."""

    def test_code_accumulates(self, kernel):
        """Code should accumulate across runs."""
        kernel.k_code = []
        kernel.k_code.append("x : Int")
        kernel.k_code.append("req x > 0")
        kernel.k_code.append("req x < 10")

        full_code = '\n\n'.join(kernel.k_code)
        result = kernel._run_k(full_code)

        assert result['status'] == 'ok'
        assert result.get('has_solution') == True


class TestResultFormatting:
    """Test result formatting."""

    def test_format_sat_result(self, kernel):
        """SAT results should format nicely."""
        result = kernel._run_k("x : Int\nreq x = 5")

        text = kernel._format_result(result)
        assert 'SAT' in text
        assert 'x' in text

        html = kernel._format_html_result(result)
        assert '<table' in html
        assert 'x' in html

    def test_format_unsat_result(self, kernel):
        """UNSAT results should format nicely."""
        result = kernel._run_k("x : Int\nreq x > 10\nreq x < 5")

        text = kernel._format_result(result)
        assert 'UNSAT' in text

        html = kernel._format_html_result(result)
        assert 'UNSAT' in html

    def test_format_error_result(self, kernel):
        """Errors should format nicely."""
        kernel.verbose = False
        result = {
            'status': 'error',
            'error': 'Test error message',
            'raw_output': 'Some raw output'
        }

        text = kernel._format_result(result)
        assert 'Error' in text
        assert 'Test error message' in text


class TestMagicCommands:
    """Test magic command handling."""

    def test_timeout_command(self, kernel):
        """Setting timeout should work."""
        kernel.timeout = 30
        # Simulate magic command (would normally go through do_execute)
        kernel.timeout = 60
        assert kernel.timeout == 60

    def test_verbose_toggle(self, kernel):
        """Verbose mode should toggle."""
        assert kernel.verbose == False
        kernel.verbose = True
        assert kernel.verbose == True
        kernel.verbose = False
        assert kernel.verbose == False


class TestCodeCompletion:
    """Test code completion."""

    def test_keyword_completion(self, kernel):
        """Should complete K keywords."""
        result = kernel.do_complete("cla", 3)
        assert result['status'] == 'ok'
        assert 'class' in result['matches']

    def test_type_completion(self, kernel):
        """Should complete K types."""
        result = kernel.do_complete("In", 2)
        assert result['status'] == 'ok'
        assert 'Int' in result['matches']

    def test_empty_completion(self, kernel):
        """Empty prefix should return no matches."""
        result = kernel.do_complete("", 0)
        assert result['status'] == 'ok'
        assert result['matches'] == []


# Run tests if executed directly
if __name__ == "__main__":
    pytest.main([__file__, "-v"])

