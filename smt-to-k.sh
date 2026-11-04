#!/bin/bash
# Convert smtModel to K constraints for existing baseline files

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Setup classpath
if [ -d "$SCRIPT_DIR/target/classes" ]; then
    CLASSPATH="$SCRIPT_DIR/target/classes"
else
    CLASSPATH="$SCRIPT_DIR/bin"
fi
CLASSPATH="$CLASSPATH:$SCRIPT_DIR/src/grammar/antlr-4.7-complete.jar"
CLASSPATH="$CLASSPATH:$SCRIPT_DIR/export/lib/*"
CLASSPATH="$CLASSPATH:$SCRIPT_DIR/export/lib/scalalib/*"

# Note: We don't need Z3 native lib for this tool (avoids K2Z3 initialization)
# But we may need the jar for other imports
if [ -f "$SCRIPT_DIR/export/lib/com.microsoft.z3.osx.jar" ]; then
    CLASSPATH="$CLASSPATH:$SCRIPT_DIR/export/lib/com.microsoft.z3.osx.jar"
elif [ -f "$SCRIPT_DIR/lib/com.microsoft.z3.jar" ]; then
    CLASSPATH="$CLASSPATH:$SCRIPT_DIR/lib/com.microsoft.z3.jar"
fi

java -classpath "$CLASSPATH" k.frontend.SmtModelToK "$@"
