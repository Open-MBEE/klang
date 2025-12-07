#!/bin/bash
cd /Users/bclement/git/klang
echo "=== BRANCH ==="
git branch
echo ""
echo "=== STATUS ==="
git status --short
echo ""
echo "=== LOG ==="
git log --oneline -5

