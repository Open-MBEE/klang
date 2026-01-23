// @expected SAT
// Simple binary tree - no strings, fast to solve
// Used to study heap instance counting

class Node {
    var depth : Int
}

class Leaf extends Node {
    // Leaf has no children
}

class Branch extends Node {
    var left  : Node
    var right : Node
    
    // Children are one level deeper
    req left.depth = depth + 1
    req right.depth = depth + 1
    
    // Every Node must be either a Leaf or a Branch
    req left is Leaf || left is Branch
    req right is Leaf || right is Branch
}

// Create a tree of depth 4 (full binary tree)
// Depth 0: 1 Branch (root)
// Depth 1: 2 Branches
// Depth 2: 4 Branches  
// Depth 3: 8 Branches
// Depth 4: 16 Leaves
// Total: 15 Branches + 16 Leaves = 31 nodes

var root : Branch
req root.depth = 0

// Force full tree by requiring all depth-3 nodes have Leaf children
// We need to express this recursively - every Branch at depth < 4 must have Branch children
// and every Branch at depth = 3 must have Leaf children

// For now, just require the root and check iteration behavior
req root.left is Branch || root.left is Leaf
req root.right is Branch || root.right is Leaf
req root.left != root.right
