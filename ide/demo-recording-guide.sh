#!/bin/bash
# K Language IDE Demo Recording Script
#
# This script helps you record a demo video of the K Language IDE features.
# It will guide you through each feature with on-screen prompts.
#
# Prerequisites:
# - VS Code with K Language extension installed
# - A K file open (e.g., src/examples/Shapes.k or src/tests/mixed_external1.k)
# - Screen recording software (QuickTime, OBS, or use macOS: Cmd+Shift+5)
#
# Usage:
#   ./demo-recording-guide.sh
#
# The script will display prompts in the terminal. Follow along while recording.

set -e

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

clear

echo -e "${BOLD}${CYAN}"
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║           K LANGUAGE IDE - FEATURE DEMO GUIDE                   ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo ""
echo -e "${YELLOW}Before starting:${NC}"
echo "  1. Start your screen recording (Cmd+Shift+5 on macOS)"
echo "  2. Open VS Code with the K Language extension"
echo "  3. Have a K file ready (e.g., src/examples/Shapes.k)"
echo ""
echo -e "${GREEN}Press ENTER when ready to begin the demo...${NC}"
read

# ============================================================================
# DEMO SECTION 1: Basic Syntax Highlighting
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 1: SYNTAX HIGHLIGHTING${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} Open src/examples/Shapes.k"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"K Language provides rich syntax highlighting for constraint-based"
echo "   specifications. Notice how keywords like 'class', 'req', and 'fun'"
echo "   are highlighted. Types like Int, Real, and Bool have distinct colors."
echo "   Comments starting with -- are also highlighted.\""
echo ""
echo -e "${GREEN}ACTION: Scroll through the file to show different syntax elements${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO SECTION 2: Document Symbols & Navigation
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 2: DOCUMENT SYMBOLS & NAVIGATION${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} Press Cmd+Shift+O to open document symbols"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"The K Language extension provides document symbols for quick navigation."
echo "   You can see all classes defined in the file - Shape, Angle, TAngle,"
echo "   Triangle, Equilateral, and Obtuse. Click on any symbol to jump to it.\""
echo ""
echo -e "${GREEN}ACTION: Click on 'Triangle' in the symbol list to navigate to it${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO SECTION 3: Go to Definition
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 3: GO TO DEFINITION${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} In Triangle class, Cmd+Click on 'TAngle' in 'a : TAngle'"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"Go to Definition lets you navigate from type references to their"
echo "   declarations. Here, clicking on TAngle takes us to where that class"
echo "   is defined. This works for all class references in the file.\""
echo ""
echo -e "${GREEN}ACTION: Cmd+Click on TAngle, then Cmd+Click on Shape in 'extends Shape'${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO SECTION 4: Hover Documentation
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 4: HOVER DOCUMENTATION${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} Hover over class names and properties"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"Hovering over elements shows documentation. For classes, you see"
echo "   the class definition with its properties and constraints."
echo "   For properties, you see their types. Comments above declarations"
echo "   are included in the documentation.\""
echo ""
echo -e "${GREEN}ACTION: Hover over Triangle, then hover over 'sides' property${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO SECTION 5: Run K File
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 5: RUNNING K FILES${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} Right-click in editor → 'Run K File' OR use Command Palette"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"You can run K files directly from VS Code. Right-click and select"
echo "   'Run K File', or use the Command Palette (Cmd+Shift+P) and type"
echo "   'K: Run File'. The output shows the Z3 solver finding a solution"
echo "   that satisfies all constraints.\""
echo ""
echo -e "${GREEN}ACTION: Run the file and show the output panel with solution${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO SECTION 6: Unified Debug Panel
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 6: UNIFIED DEBUG PANEL${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} Command Palette → 'K: Open Debug Panel'"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"The Unified Debug Panel provides a rich debugging experience."
echo "   At the top, you see session tabs - like IntelliJ, you can debug"
echo "   multiple K files simultaneously. The status indicator shows"
echo "   SAT (satisfiable) or UNSAT (unsatisfiable).\""
echo ""
echo -e "${GREEN}ACTION: Open the debug panel and point out session tabs and status${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO SECTION 7: Constraint Stepping
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 7: CONSTRAINT STEPPING${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} Use the Prev/Next buttons in the debug panel"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"You can step through constraints one at a time. Each step adds"
echo "   constraints incrementally and re-solves. The progress bar shows"
echo "   how many constraints have been processed. Constraints are highlighted"
echo "   in the editor as you step through them.\""
echo ""
echo -e "${GREEN}ACTION: Click 'Next' several times, showing constraints being added${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO SECTION 8: Constraint Breakpoints
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 8: CONSTRAINT BREAKPOINTS${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} Click the red dot next to a constraint to toggle breakpoint"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"Just like code breakpoints, you can set breakpoints on constraints."
echo "   Click the red dot to the left of any constraint. The constraint"
echo "   gets a red border indicating a breakpoint is set. When stepping,"
echo "   the solver will pause when it reaches this constraint.\""
echo ""
echo -e "${GREEN}ACTION: Set a breakpoint on a constraint, show the red indicator${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO SECTION 9: Solution Objects
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 9: SOLUTION VISUALIZATION${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} Scroll to 'Solution Objects' section in debug panel"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"When the solver finds a solution, the objects are displayed"
echo "   in a structured format. You can see each object's class,"
echo "   its properties, and their values. References between objects"
echo "   are shown inline - notice how Triangle references TAngle objects.\""
echo ""
echo -e "${GREEN}ACTION: Expand and examine the solution objects${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO SECTION 10: External Function Support
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 10: EXTERNAL JAVA/PYTHON FUNCTIONS${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} Open src/tests/mixed_external1.k"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"K can import and call external Java and Python functions!"
echo "   This file imports both java.lang.Math and Python's math module."
echo "   The debug panel shows external imports with language badges -"
echo "   coffee cup for Java, snake for Python.\""
echo ""
echo -e "${GREEN}ACTION: Show the External Imports section with Java/Python badges${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO SECTION 11: CEGAR Iterations
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 11: CEGAR REFINEMENT LOOP${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} CEGAR Iterations section (if visible)"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"When external functions are involved, K uses CEGAR -"
echo "   Counter-Example Guided Abstraction Refinement. The debug panel"
echo "   shows each iteration: candidate solutions, counterexamples when"
echo "   the external function disagrees, and refinements added.\""
echo ""
echo -e "${GREEN}ACTION: Click on a CEGAR iteration to see details${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO SECTION 12: Auto-Solve Mode
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 12: AUTO-SOLVE MODE${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} Toggle the Auto-solve checkbox in the debug panel"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"Enable Auto-solve to have the solver run automatically after"
echo "   every edit. This gives you immediate feedback as you write"
echo "   constraints - great for iterative development. The status"
echo "   updates in real-time as you type.\""
echo ""
echo -e "${GREEN}ACTION: Enable auto-solve, make an edit, show automatic re-solve${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO SECTION 13: Inline Value Decorations
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 13: INLINE VALUE DECORATIONS${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} Look at the editor with solution values shown inline"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"Like a Java debugger, K shows variable values inline in the editor."
echo "   After solving, you see '= value' next to each property declaration."
echo "   Satisfied constraints show green checkmarks. If UNSAT, conflicting"
echo "   constraints are highlighted in red.\""
echo ""
echo -e "${GREEN}ACTION: Point out inline values and constraint status indicators${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO SECTION 14: Debugging External Functions
# ============================================================================
clear
echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}DEMO 14: STEPPING INTO EXTERNAL CODE${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}SHOW:${NC} The step-into (⏎) and go-to (→) buttons on external calls"
echo ""
echo -e "${CYAN}NARRATION:${NC}"
echo "  \"For constraints that call external functions, you see buttons"
echo "   to step into the debugger or navigate to source. Click ⏎ to"
echo "   launch the Java or Python debugger. Click → to jump to the"
echo "   source file. This integrates K debugging with traditional debuggers.\""
echo ""
echo -e "${GREEN}ACTION: Show the external call buttons in a constraint${NC}"
echo ""
echo -e "Press ENTER for next section..."
read

# ============================================================================
# DEMO COMPLETE
# ============================================================================
clear
echo -e "${BOLD}${GREEN}"
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║                    DEMO COMPLETE! 🎉                             ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo ""
echo -e "${CYAN}Features Demonstrated:${NC}"
echo "  ✓ Syntax Highlighting"
echo "  ✓ Document Symbols & Navigation"
echo "  ✓ Go to Definition"
echo "  ✓ Hover Documentation"
echo "  ✓ Running K Files"
echo "  ✓ Unified Debug Panel"
echo "  ✓ Constraint Stepping"
echo "  ✓ Constraint Breakpoints"
echo "  ✓ Solution Visualization"
echo "  ✓ External Java/Python Functions"
echo "  ✓ CEGAR Refinement Loop"
echo "  ✓ Auto-Solve Mode"
echo "  ✓ Inline Value Decorations"
echo "  ✓ External Function Debugging"
echo ""
echo -e "${YELLOW}Don't forget to stop your screen recording!${NC}"
echo ""
echo -e "${GREEN}Thank you for watching the K Language IDE demo!${NC}"
echo ""

