// Test: Binary tree that requires exactly 3 nodes
// This tests the CEGAR loop's ability to find the right heap size
// Using a complete binary tree avoids null reference issues

class TreeNode {
    var value: Int
    var left: TreeNode
    var right: TreeNode
}

// Root of the tree
var root: TreeNode

// Root has value 1
req root.value = 1

// Root's children have values 2 and 3
req root.left.value = 2
req root.right.value = 3

// All nodes are distinct
req root != root.left
req root != root.right
req root.left != root.right

