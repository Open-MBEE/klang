#!/bin/bash
# Simple timeout wrapper for testing
# Usage: ./test-with-timeout.sh <seconds> <command>

TIMEOUT_SEC=$1
shift
COMMAND="$@"

# Run command in background
$COMMAND &
PID=$!

# Wait for timeout or completion
for i in $(seq 1 $TIMEOUT_SEC); do
    if ! kill -0 $PID 2>/dev/null; then
        # Process finished
        wait $PID
        exit $?
    fi
    sleep 1
done

# Timeout reached
echo "TIMEOUT: Command exceeded ${TIMEOUT_SEC}s, killing process $PID"
kill $PID 2>/dev/null
wait $PID 2>/dev/null
exit 124

