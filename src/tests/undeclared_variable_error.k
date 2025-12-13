// Regression test: undeclared variables in bare expressions should error
// Previously this was silently ignored (bug)
// See: docs/investigations/TYPE_INFERENCE_VIA_Z3.md

x < y
