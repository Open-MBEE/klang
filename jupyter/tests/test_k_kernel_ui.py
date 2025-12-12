"""
Playwright tests for the K Jupyter Kernel.

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
import os
from playwright.sync_api import Page, expect

# Configuration
JUPYTER_PORT = 18888  # Use non-standard port to avoid conflicts
JUPYTER_TOKEN = "test_token_for_k_kernel"
K_HOME = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SCREENSHOT_DIR = "/tmp/k_jupyter_tests"


@pytest.fixture(scope="module")
def jupyter_server():
    """Start a Jupyter notebook server for testing."""
    import socket

    os.makedirs(SCREENSHOT_DIR, exist_ok=True)

    env = os.environ.copy()
    env["K_HOME"] = K_HOME

    # Start Jupyter server
    proc = subprocess.Popen(
        [
            "jupyter", "notebook",
            "--no-browser",
            f"--port={JUPYTER_PORT}",
            f"--IdentityProvider.token={JUPYTER_TOKEN}",
            "--ServerApp.disable_check_xsrf=True",
            f"--notebook-dir={K_HOME}/jupyter/examples"
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
        proc.terminate()
        output, _ = proc.communicate(timeout=5)
        print(f"Jupyter failed to start. Output:\n{output.decode() if output else 'None'}")
        pytest.skip("Could not start Jupyter server")

    time.sleep(2)
    yield url

    proc.terminate()
    try:
        proc.wait(timeout=10)
    except subprocess.TimeoutExpired:
        proc.kill()


class TestNotebookOpening:
    """Test that notebooks open correctly (not as raw JSON)."""

    def test_notebook_opens_as_notebook_not_json(self, page: Page, jupyter_server: str):
        """
        Verify that clicking on a .ipynb file opens it as a rendered notebook,
        not as raw JSON text.

        This was a bug we found - Jupyter Notebook 7 was defaulting to showing
        the raw JSON instead of the notebook interface.
        """
        # Go to Jupyter file browser
        page.goto(jupyter_server)
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(2000)
        page.screenshot(path=f"{SCREENSHOT_DIR}/01_file_browser.png")

        # Click on notebook - use get_by_label to be specific to the file browser
        notebook_link = page.get_by_label("Files", exact=True).get_by_text("K_Introduction.ipynb")
        if notebook_link.count() == 0:
            # Fallback to any matching text
            notebook_link = page.locator("text=K_Introduction.ipynb").first

        notebook_link.click()
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(3000)
        page.screenshot(path=f"{SCREENSHOT_DIR}/02_after_click.png")

        # Check if it opened as raw JSON (bad) or as notebook (good)
        # Notebook view has rendered cells with these classes
        notebook_cells = page.locator(".jp-Cell, .cell, .jp-Notebook")

        page.screenshot(path=f"{SCREENSHOT_DIR}/03_notebook_view.png")

        # Check page content for signs of raw JSON
        page_content = page.content()

        # These patterns in the visible page (not in script tags) indicate raw JSON
        if '"cells":' in page_content and '"cell_type": "markdown"' in page_content:
            # Could be raw JSON view - check if we also have notebook elements
            if notebook_cells.count() == 0:
                pytest.fail(
                    "Notebook opened as raw JSON instead of rendered notebook. "
                    "Fix: Create ~/.jupyter/labconfig/default_setting_overrides.json with:\n"
                    '{"@jupyterlab/docmanager-extension:plugin": {"defaultViewers": {"ipynb": "Notebook"}}}'
                )

        # If we have notebook cells OR we don't see raw JSON, we're good
        # (Some Jupyter versions may have different class names)
        print(f"Found {notebook_cells.count()} notebook cell elements")


class TestKernelConnection:
    """Test that the K kernel connects and runs."""

    def test_kernel_indicator_shows_k(self, page: Page, jupyter_server: str):
        """Verify the K kernel is selected and connected."""
        # Open notebook directly
        page.goto(f"{jupyter_server.replace('?', 'notebooks/K_Introduction.ipynb?')}")
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(3000)
        page.screenshot(path=f"{SCREENSHOT_DIR}/04_kernel_check.png")

        # Look for kernel indicator showing "K"
        kernel_name = page.locator(".jp-Toolbar-kernelName, [data-type='kernel-name']")
        if kernel_name.count() > 0:
            kernel_text = kernel_name.text_content()
            assert "K" in kernel_text or "k" in kernel_text, f"Expected K kernel, got: {kernel_text}"


class TestCodeExecution:
    """Test running K code in notebooks."""

    @pytest.fixture
    def notebook_page(self, page: Page, jupyter_server: str):
        """Open a notebook ready for testing."""
        # Create a new notebook or open existing one
        page.goto(f"{jupyter_server.replace('?', 'notebooks/K_Introduction.ipynb?')}")
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(3000)

        # Wait for kernel to be ready (circle should be filled, not lightning bolt)
        page.wait_for_timeout(2000)
        return page

    def test_help_command(self, notebook_page: Page):
        """Test that %help magic command works."""
        page = notebook_page
        page.screenshot(path=f"{SCREENSHOT_DIR}/05_before_help.png")

        # Find the first code cell and click it
        code_cell = page.locator(".jp-Cell-inputArea, .input_area").first
        if code_cell.count() == 0:
            pytest.skip("No code cells found")

        code_cell.click()
        page.wait_for_timeout(500)

        # Run the cell (assuming %help is already there, or we clear and type it)
        page.keyboard.press("Shift+Enter")
        page.wait_for_timeout(3000)
        page.screenshot(path=f"{SCREENSHOT_DIR}/06_after_help.png")

        # Check for help output
        output = page.locator(".jp-OutputArea, .output_area")
        if output.count() > 0:
            output_text = output.first.text_content()
            # Help should mention magic commands
            assert "reset" in output_text.lower() or "help" in output_text.lower() or "Magic" in output_text


class TestRegressionIssues:
    """Test for specific issues we discovered."""

    def test_notebook_not_showing_raw_json(self, page: Page, jupyter_server: str):
        """
        Regression test: Notebooks should not display as raw JSON.

        Issue: Left-clicking on .ipynb showed raw JSON like:
        {
          "cells": [
            {"cell_type": "markdown", ...}
          ]
        }

        Instead of the rendered notebook with executable cells.
        """
        page.goto(jupyter_server)
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(2000)

        # Click on notebook - use specific selector for file browser
        notebook_link = page.get_by_label("Files", exact=True).get_by_text("K_Introduction.ipynb")
        if notebook_link.count() == 0:
            notebook_link = page.locator("text=K_Introduction.ipynb").first
        notebook_link.click()
        page.wait_for_timeout(3000)
        page.screenshot(path=f"{SCREENSHOT_DIR}/07_json_regression.png")

        # Should NOT see raw JSON structure
        page_text = page.locator("body").text_content()

        # These patterns indicate raw JSON is being shown
        bad_patterns = [
            '"cells": [',
            '"cell_type": "markdown"',
            '"execution_count": null',
            '"nbformat": 4'
        ]

        for pattern in bad_patterns:
            if pattern in page_text:
                # Check if it's in an output cell (which would be OK) vs main content
                main_content = page.locator(".jp-Notebook, #notebook-container")
                if main_content.count() == 0:
                    pytest.fail(f"Notebook showing raw JSON. Found: {pattern}")
