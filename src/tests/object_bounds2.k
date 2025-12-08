-- Test: object_bounds2.k
-- Tests nested object creation with constraints
-- Expected: PASSED

class Node {
  num : Int
  req num >= 0
  req num <= 100
}

class Edge {
  fromNode : Node
  toNode : Node
  cost : Int

  req cost > 0
  req cost <= 10
  -- Edge connects different nodes
  req fromNode.num != toNode.num
}

class Graph {
  n1 : Node
  n2 : Node
  n3 : Node
  edge1 : Edge
  edge2 : Edge

  -- Edges connect our nodes
  req edge1.fromNode = n1
  req edge1.toNode = n2
  req edge2.fromNode = n2
  req edge2.toNode = n3

  -- Path constraint: sum of edge costs
  req edge1.cost + edge2.cost <= 15
}

g : Graph

