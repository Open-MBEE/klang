// Test file for multi-dimensional arrays
// Demonstrates nested Array types

// Multi-dimensional array: Array[Int, Array[Int, Int]] (2D array)
// Note: Use parentheses for chained array access: (arr[i])[j]
class Matrix2DArrayTest {
  grid : Array[Int, Array[Int, Int]]
  
  // Constraints on specific elements using parentheses for clarity
  req (grid[0])[0] = 42
  req (grid[0])[1] = 100
  req (grid[1])[0] = (grid[0])[0] + (grid[0])[1]  // 142
}

// 3D Array: Array[Int, Array[Int, Array[Int, Int]]]
class Array3DTest {
  cube : Array[Int, Array[Int, Array[Int, Int]]]
  
  // Access deep element with nested parentheses
  req ((cube[0])[0])[0] = 1
  req ((cube[1])[1])[1] = 8
}

// Array with String keys (like a dictionary of dictionaries)
class NestedDictTest {
  config : Array[String, Array[String, Int]]
  
  req (config["server"])["port"] = 8080
  req (config["server"])["timeout"] = 30
  req (config["client"])["retries"] = 3
}
