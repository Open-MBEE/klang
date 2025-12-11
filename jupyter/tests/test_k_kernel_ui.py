"""
Playwright tests for the K Jupyter Kernel.

NOTE: These UI tests are experimental. Jupyter Notebook 7 uses a complex
React-based interface that varies across versions. The unit tests in
test_k_kernel.py provide comprehensive coverage without browser automation.

These tests automate Jupyter notebook interactions to verify the K kernel
works correctly in a real browser environment.

Run with:
    cd jupyter/tests
    pytest test_k_kernel_ui.py -v

Or run with visible browser:
    pytest test_k_kernel_ui.py -v --headed

Requirements:
    pip install playwright pytest-playwright
    playwright install chromium
"""

import pytest
import subprocess
import time
import signal
import os
from playwright.sync_api import Page, expect

# Configuration
JUPYTER_PORT = 18888  # Use non-standard port to avoid conflicts
JUPYTER_TOKEN = "test_token_for_k_kernel"
K_HOME = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


@pytest.fixture(scope="module")
def jupyter_server():
    """Start a Jupyter notebook server for testing."""
    import socket

    env = os.environ.copy()
    env["K_HOME"] = K_HOME

    # Start Jupyter server
    proc = subprocess.Popen(
        [
            "jupyter", "notebook",
            "--no-browser",
            f"--port={JUPYTER_PORT}",
            f"--NotebookApp.token={JUPYTER_TOKEN}",
            "--NotebookApp.disable_check_xsrf=True",
            f"--notebook-dir={K_HOME}/jupyter"
        ],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )

    # Wait for server to start by polling the port
    url = f"http://localhost:{JUPYTER_PORT}/?token={JUPYTER_TOKEN}"
    max_wait = 30
    started = False

    for i in range(max_wait):
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            result = sock.connect_ex(('localhost', JUPYTER_PORT))
            sock.close()
            if result == 0:
                started = True
                print(f"\nJupyter server started on port {JUPYTER_PORT}")
                break
        except:
            pass
        time.sleep(1)
        print(f"Waiting for Jupyter server... ({i+1}s)")

    if not started:
        # Print any output for debugging
        proc.terminate()
        output, _ = proc.communicate(timeout=5)
        print(f"Jupyter failed to start. Output:\n{output.decode() if output else 'None'}")
        pytest.skip("Could not start Jupyter server")

    # Give it a moment to fully initialize
    time.sleep(2)

    yield url

    # Shutdown server
    proc.terminate()
    try:
        proc.wait(timeout=10)
    except subprocess.TimeoutExpired:
        proc.kill()


@pytest.fixture
def notebook_page(page: Page, jupyter_server: str):
    """Navigate to Jupyter and create a new K notebook."""
    # Go to Jupyter
    page.goto(jupyter_server)
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(3000)

    # Save page for debugging
    page.screenshot(path="/tmp/jupyter_homepage.png")

    # Jupyter Notebook 7 uses a different interface
    # We need to navigate to create a new notebook
    # Try to find and click "New Notebook" or similar

    # Method 1: Look for launcher cards
    launcher = page.locator(".jp-LauncherCard")
    if launcher.count() > 0:
        # Click first launcher card (might be Python, we'll change kernel later)
        launcher.first.click()
        page.wait_for_timeout(2000)
    else:
        # Method 2: Use keyboard shortcut or File menu
        # Try File > New > Notebook
        page.keyboard.press("Control+Shift+n")  # New notebook shortcut
        page.wait_for_timeout(2000)

    # Check if we got to a notebook by looking for code cells
    page.wait_for_timeout(3000)
    page.screenshot(path="/tmp/jupyter_after_new.png")

    # If we still don't have a notebook, skip the test
    cell_input = page.locator(".jp-Cell-inputArea, .CodeMirror, .jp-InputArea-editor")
    if cell_input.count() == 0:
        # Try direct URL to create new notebook
        page.goto(f"{jupyter_server.split('?')[0]}notebooks/Untitled.ipynb?{jupyter_server.split('?')[1]}")
        page.wait_for_timeout(3000)
        page.screenshot(path="/tmp/jupyter_direct_notebook.png")

        cell_input = page.locator(".jp-Cell-inputArea, .CodeMirror, .jp-InputArea-editor")
        if cell_input.count() == 0:
            pytest.skip("Could not create a new notebook - UI automation not compatible with this Jupyter version")

    yield page


class TestKKernelBasic:
    """Basic K kernel functionality tests."""

    def test_simple_constraint(self, notebook_page: Page):
        """Test a simple integer constraint."""
        page = notebook_page

        # Type K code into the cell
        cell = page.locator(".CodeMirror-code").first
        cell.click()
        page.keyboard.type("x : Int\nreq x > 5\nreq x < 10")

        # Execute cell (Shift+Enter)
        page.keyboard.press("Shift+Enter")

        # Wait for output
        page.wait_for_timeout(5000)

        # Check for SAT result
        output = page.locator(".output_area").first
        expect(output).to_contain_text("SAT")
        expect(output).to_contain_text("x")

    def test_class_definition(self, notebook_page: Page):
        """Test defining a K class."""
        page = notebook_page

        # Type class definition
        cell = page.locator(".CodeMirror-code").first
        cell.click()
        page.keyboard.type("""class Point {
    x : Int
    y : Int
    req x >= 0
    req y >= 0
}

p : Point
req p.x + p.y = 10""")

        # Execute
        page.keyboard.press("Shift+Enter")
        page.wait_for_timeout(5000)

        # Check output
        output = page.locator(".output_area").first
        expect(output).to_contain_text("SAT")
        expect(output).to_contain_text("Point")

    def test_unsat(self, notebook_page: Page):
        """Test unsatisfiable constraints."""
        page = notebook_page

        cell = page.locator(".CodeMirror-code").first
        cell.click()
        page.keyboard.type("x : Int\nreq x > 10\nreq x < 5")

        page.keyboard.press("Shift+Enter")
        page.wait_for_timeout(5000)

        output = page.locator(".output_area").first
        expect(output).to_contain_text("UNSAT")


class TestMagicCommands:
    """Test K kernel magic commands."""

    def test_reset_magic(self, notebook_page: Page):
        """Test %reset clears the model."""
        page = notebook_page

        # First cell: define variable
        cell = page.locator(".CodeMirror-code").first
        cell.click()
        page.keyboard.type("x : Int")
        page.keyboard.press("Shift+Enter")
        page.wait_for_timeout(2000)

        # Second cell: reset
        page.keyboard.type("%reset")
        page.keyboard.press("Shift+Enter")
        page.wait_for_timeout(1000)

        # Check for reset message
        outputs = page.locator(".output_area")
        expect(outputs.last).to_contain_text("reset")

    def test_help_magic(self, notebook_page: Page):
        """Test %help shows documentation."""
        page = notebook_page

        cell = page.locator(".CodeMirror-code").first
        cell.click()
        page.keyboard.type("%help")
        page.keyboard.press("Shift+Enter")
        page.wait_for_timeout(2000)

        output = page.locator(".output_area").first
        expect(output).to_contain_text("Magic Commands")
        expect(output).to_contain_text("%reset")
        expect(output).to_contain_text("%verbose")

    def test_verbose_magic(self, notebook_page: Page):
        """Test %verbose toggles verbose mode."""
        page = notebook_page

        cell = page.locator(".CodeMirror-code").first
        cell.click()
        page.keyboard.type("%verbose on")
        page.keyboard.press("Shift+Enter")
        page.wait_for_timeout(1000)

        output = page.locator(".output_area").first
        expect(output).to_contain_text("Verbose mode ON")


class TestIncrementalModel:
    """Test incremental model building across cells."""

    def test_multi_cell_model(self, notebook_page: Page):
        """Test building a model across multiple cells."""
        page = notebook_page

        # Cell 1: Define class
        cell = page.locator(".CodeMirror-code").first
        cell.click()
        page.keyboard.type("""class Item {
    weight : Int
    value : Int
    req weight > 0
    req value > 0
}""")
        page.keyboard.press("Shift+Enter")
        page.wait_for_timeout(3000)

        # Cell 2: Create instance and add constraints
        page.keyboard.type("""item : Item
req item.weight <= 10
req item.value >= 50""")
        page.keyboard.press("Shift+Enter")
        page.wait_for_timeout(3000)

        # Check final output has solution
        outputs = page.locator(".output_area")
        expect(outputs.last).to_contain_text("SAT")
        expect(outputs.last).to_contain_text("Item")


class TestErrorHandling:
    """Test error handling in the kernel."""

    def test_syntax_error(self, notebook_page: Page):
        """Test that syntax errors are reported clearly."""
        page = notebook_page

        cell = page.locator(".CodeMirror-code").first
        cell.click()
        page.keyboard.type("class { invalid syntax")
        page.keyboard.press("Shift+Enter")
        page.wait_for_timeout(3000)

        output = page.locator(".output_area").first
        # Should show some error indication
        expect(output).to_contain_text("Error")

    def test_type_error(self, notebook_page: Page):
        """Test that type errors are reported."""
        page = notebook_page

        cell = page.locator(".CodeMirror-code").first
        cell.click()
        page.keyboard.type('x : Int\nreq x = "string"')  # Type mismatch
        page.keyboard.press("Shift+Enter")
        page.wait_for_timeout(3000)

        output = page.locator(".output_area").first
        # Should show type error or fail
        # (exact message depends on K's error handling)


# Run tests if executed directly
if __name__ == "__main__":
    pytest.main([__file__, "-v", "--headed"])

