// @expected SAT
// Full binary tree of depth 2
// Structure:
//   Depth 0: 1 Branch (root)
//   Depth 1: 2 Branches
//   Depth 2: 4 Leaves
// Total: 3 Branches + 4 Leaves = 7 nodes

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

// Build full binary tree of depth 2
var root : Branch
req root.depth = 0

// Depth 1 - two branches
var b1L : Branch
var b1R : Branch
req root.left = b1L
req root.right = b1R
req b1L != b1R

// Depth 2 - four leaves (require leaves explicitly)
req b1L.left is Leaf
req b1L.right is Leaf
req b1R.left is Leaf
req b1R.right is Leaf

// All leaves must be distinct
req b1L.left != b1L.right
req b1R.left != b1R.right
req b1L.left != b1R.left
req b1L.left != b1R.right
req b1L.right != b1R.left
req b1L.right != b1R.right
