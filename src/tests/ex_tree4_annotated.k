// @preferred_options -timeout 60000
// @expected SAT
// Full binary tree of depth 4 - with explicit @instances annotations
// This is a variant of ex_tree4.k that provides instance hints to the solver
//
// Structure needed:
//   Depth 0: 1 Branch (root)
//   Depth 1: 2 Branches (root.left, root.right)
//   Depth 2: 4 Branches
//   Depth 3: 8 Branches
//   Depth 4: 16 Leaves
// Total: 15 Branches + 16 Leaves = 31 nodes

// Node is abstract - no direct instances needed
@instances(0)
class Node {
    var depth : Int
}

// Need exactly 16 leaves for depth 4
@instances(16)
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

// Build a full binary tree by declaring all branches explicitly
var root : Branch
req root.depth = 0

// Depth 1
var b1L : Branch
var b1R : Branch
req root.left = b1L
req root.right = b1R
req b1L != b1R

// Depth 2
var b2LL : Branch
var b2LR : Branch
var b2RL : Branch
var b2RR : Branch
req b1L.left = b2LL
req b1L.right = b2LR
req b1R.left = b2RL
req b1R.right = b2RR

// Depth 3
var b3LLL : Branch
var b3LLR : Branch
var b3LRL : Branch
var b3LRR : Branch
var b3RLL : Branch
var b3RLR : Branch
var b3RRL : Branch
var b3RRR : Branch
req b2LL.left = b3LLL
req b2LL.right = b3LLR
req b2LR.left = b3LRL
req b2LR.right = b3LRR
req b2RL.left = b3RLL
req b2RL.right = b3RLR
req b2RR.left = b3RRL
req b2RR.right = b3RRR

// Depth 4 - all leaves
req b3LLL.left is Leaf && b3LLL.right is Leaf
req b3LLR.left is Leaf && b3LLR.right is Leaf
req b3LRL.left is Leaf && b3LRL.right is Leaf
req b3LRR.left is Leaf && b3LRR.right is Leaf
req b3RLL.left is Leaf && b3RLL.right is Leaf
req b3RLR.left is Leaf && b3RLR.right is Leaf
req b3RRL.left is Leaf && b3RRL.right is Leaf
req b3RRR.left is Leaf && b3RRR.right is Leaf
