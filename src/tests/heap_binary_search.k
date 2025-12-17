// Binary search tree with BST property
// Requires CEGAR to discover sufficient heap size

class BSTNode {
    var value: Int
    var left: BSTNode
    var right: BSTNode
}

// Tree structure
var root: BSTNode

// BST property constraints
req root.value = 10
req root.left.value = 5
req root.right.value = 15

// BST ordering
req root.left.value < root.value
req root.right.value > root.value

// All nodes distinct
req root != root.left
req root != root.right
req root.left != root.right
