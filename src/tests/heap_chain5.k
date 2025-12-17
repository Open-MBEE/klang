// Test: Chain of objects that requires exactly 5 objects
// This tests CEGAR loop refinement across iterations
// The default instance count may be too low

class Chain {
    var id: Int
    var next: Chain
}

// Create a 5-object chain: c1 -> c2 -> c3 -> c4 -> c5 (loops back to c1)
var c1: Chain

// Define the chain structure
req c1.id = 1
req c1.next.id = 2
req c1.next.next.id = 3
req c1.next.next.next.id = 4
req c1.next.next.next.next.id = 5

// Make it circular (c5.next = c1)
req c1.next.next.next.next.next = c1

// All 5 objects must be distinct
req c1 != c1.next
req c1 != c1.next.next
req c1 != c1.next.next.next
req c1 != c1.next.next.next.next
req c1.next != c1.next.next
req c1.next != c1.next.next.next
req c1.next != c1.next.next.next.next
req c1.next.next != c1.next.next.next
req c1.next.next != c1.next.next.next.next
req c1.next.next.next != c1.next.next.next.next

