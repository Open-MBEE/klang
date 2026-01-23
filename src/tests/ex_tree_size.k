// @expected SAT
// Tree with computed size property
// Require a tree with 7 nodes to study instance counting

class Node {
    var size : Int
}

class Leaf extends Node {
    req size = 1
}

class Branch extends Node {
    var left  : Node
    var right : Node
    
    // Size is 1 + left size + right size
    req size = 1 + left.size + right.size
    
    // Every Node must be either a Leaf or a Branch
    req left is Leaf || left is Branch
    req right is Leaf || right is Branch
    
    // No self-reference (acyclicity) and children must be distinct
    req left != this
    req right != this
    req left != right
    
    // Children must have smaller size (ensures termination/acyclicity)
    req left.size < size
    req right.size < size
}

// Just one root, require total size of 31 (full binary tree of depth 4)
// Needs 15 branches + 16 leaves = 31 nodes
var root : Branch
req root.size = 31
