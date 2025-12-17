// Doubly linked list with 3 nodes
// Tests bidirectional pointer constraints

class DLLNode {
    var value: Int
    var prev: DLLNode
    var next: DLLNode
}

// List pointers
var head: DLLNode
var middle: DLLNode
var tail: DLLNode

// All distinct
req head != middle
req middle != tail
req head != tail

// Values
req head.value = 1
req middle.value = 2
req tail.value = 3

// Forward links
req head.next = middle
req middle.next = tail

// Backward links
req middle.prev = head
req tail.prev = middle
