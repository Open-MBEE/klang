// Cyclic graph with 3 nodes
// Tests heap bounds with cycles

class GraphNode {
    var id: Int
    var next1: GraphNode
    var next2: GraphNode
}

// Three nodes
var a: GraphNode
var b: GraphNode
var c: GraphNode

// All distinct
req a != b
req b != c
req a != c

// IDs
req a.id = 1
req b.id = 2
req c.id = 3

// Form cycle: a -> b -> c -> a
req a.next1 = b
req b.next1 = c
req c.next1 = a

// Additional edges
req a.next2 = c
req b.next2 = a
req c.next2 = b
