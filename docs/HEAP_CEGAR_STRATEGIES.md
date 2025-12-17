# Heap & CEGAR Strategy Comparison

This document summarizes the heap and CEGAR solving strategies available in K.

## Available Strategies

### 1. Unbounded (`-` no flag)
- No heap bounds applied
- Objects allocated as needed
- May timeout on complex problems

### 2. Bounded (`-heap`)
- Fixed heap bounds from `HeapLayout`
- Hard constraint on number of objects
- May return UNSAT if bounds too small

### 3. CEGAR Bounded (`-heapcegar`)
- Counter-Example Guided Abstraction Refinement
- Starts with small bounds, increases on UNSAT
- Uses `k!heap_full!ClassName` flags to detect tight bounds
- Generally fastest for heap problems

### 4. Soft Bounded (`-heapsoftbounded`)
- Uses Z3's optimization with soft constraints
- Tries to minimize heap usage
- Falls back to regular solver if optimization fails

## Test Files

Located in `src/tests/`:

| File | Description | Strategy |
|------|-------------|----------|
| `cegar1.k` | External function with CEGAR | default |
| `heap_chain5.k` | 5-element chain of objects | heapcegar |
| `heap_linked_list.k` | Linked list structure | heapcegar |
| `heap_tree.k` | Binary tree (3 nodes) | heapcegar |
| `heap_binary_search.k` | BST with ordering constraints | heapcegar |
| `heap_doubly_linked.k` | Doubly-linked list (3 nodes) | heapcegar |
| `heap_cyclic_graph.k` | Cyclic graph (3 nodes) | heapcegar |

## Running Strategy Comparison

Use the Python script to compare strategies:

```bash
# Compare default tests
python3 compare-heap-strategies.py

# Compare specific files
python3 compare-heap-strategies.py src/tests/heap_tree.k src/tests/heap_chain5.k
```

## Example Output

```
==================================================
 Heap Strategy Comparison
==================================================

Files: cegar1.k, heap_chain5.k, heap_linked_list.k, heap_tree.k

File                      unbounded    bounded      cegar        softbounded 
-----------------------------------------------------------------------------
cegar1.k                  SAT      514ms SAT      501ms SAT      508ms SAT      504ms
heap_chain5.k             SAT      532ms SAT      544ms SAT      539ms SAT      542ms
heap_linked_list.k        SAT      531ms SAT      531ms SAT      527ms SAT      529ms
heap_tree.k               SAT      530ms SAT      533ms SAT      534ms SAT      527ms
```

## Implementation Notes

- CEGAR declares `k!heap_full!ClassName` flags in SMT for each dynamic class
- These flags indicate when all allocated objects of a class are used
- When `heap_full` is true, bounds are increased in the next iteration
- Refinement doubles bounds (up to `maxBound`, default 256)
- See `UnifiedSolver.scala` for implementation details

