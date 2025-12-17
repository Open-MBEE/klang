// Test: Linked list that requires exactly 3 nodes
// This tests the CEGAR loop's ability to find the right heap size

class Node {
    var value: Int
    var next: Node
}

// Create a linked list: head -> n1 -> n2 -> null
var head: Node

// Constraints to form a 3-node linked list
req head != null
req head.next != null               // head has a successor
req head.next.next != null          // head.next has a successor
req head.next.next.next = null      // chain ends at 3rd node

// Values should be positive and distinct
req head.value = 1
req head.next.value = 2
req head.next.next.value = 3

