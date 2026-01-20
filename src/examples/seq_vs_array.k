// @expected DISPLAY_FIX_NEEDED
// BUG: Model output shows raw Z3 expressions instead of readable values
// ============================================================
// Seq vs Array: Understanding the Differences
// ============================================================
//
// K provides two collection types for indexed data:
//
//   Seq[T]          - Finite sequences with length
//   Array[K,V]      - Total functions (maps) without length
//
// This file illustrates the key differences.

// ============================================================
// Seq[T] - Finite Sequences
// ============================================================
//
// Seq[T] represents a finite ordered collection of elements.
// Key characteristics:
//   - Has a definite length (.size)
//   - Elements accessed via .at(index)
//   - Supports slicing, concatenation
//   - Can be empty
//
// Use Seq when you need:
//   - A list with a specific length
//   - Length constraints (e.g., "list has exactly 5 elements")
//   - Iteration patterns

class SeqExample {
  // A sequence of integers
  numbers : Seq[Int]
  
  // Sequences have a length
  req numbers.size = 5
  
  // Access elements by index (0-based)
  req numbers.at(0) = 10
  req numbers.at(4) = 50
  
  // Elements can reference other elements
  req numbers.at(1) = numbers.at(0) + 5  // 15
}

// ============================================================
// Array[K,V] - Total Functions (Maps)
// ============================================================
//
// Array[K,V] represents a total function from keys to values.
// Key characteristics:
//   - No concept of length - every key has a value
//   - Elements accessed via arr[key]
//   - Unconstrained indices have arbitrary values
//   - Cannot be "empty" - all keys map to something
//
// Use Array when you need:
//   - A lookup table / dictionary
//   - Multi-dimensional matrices
//   - No length constraints
//   - String keys (like JSON objects)

class ArrayExample {
  // An integer-keyed array
  lookup : Array[Int, Int]
  
  // Arrays have no .size - every integer has a value
  // We can only constrain specific elements
  req lookup[0] = 10
  req lookup[100] = 200
  
  // Unmentioned indices have arbitrary values
  // lookup[1], lookup[2], etc. are unconstrained
}

// ============================================================
// Practical Differences
// ============================================================

// Example 1: When length matters - use Seq
class LengthConstraint {
  // "I need exactly 3 positive numbers"
  items : Seq[Int]
  req items.size = 3
  req items.at(0) > 0
  req items.at(1) > 0
  req items.at(2) > 0
}

// Example 2: Sparse data - use Array  
class SparseData {
  // "Map from user IDs to scores (users 1000-9999)"
  scores : Array[Int, Int]
  req scores[1000] = 95
  req scores[5000] = 88
  req scores[9999] = 72
  // No need to specify all 9000 entries!
}

// Example 3: String-keyed config - use Array
class Configuration {
  // Like a JSON object: {"host": "...", "port": 8080}
  settings : Array[String, Int]
  req settings["port"] = 8080
  req settings["timeout"] = 30
}

// Example 4: 2D Grid/Matrix - use nested Array
class Grid2D {
  // 2D array accessed as grid[row][col]
  cells : Array[Int, Array[Int, Int]]
  
  // Use parentheses for chained access: (grid[row])[col]
  req (cells[0])[0] = 1
  req (cells[0])[1] = 2
  req (cells[1])[0] = 3
  req (cells[1])[1] = 4
}

// ============================================================
// Summary
// ============================================================
//
// Feature             | Seq[T]              | Array[K,V]
// --------------------|---------------------|--------------------
// Length              | Has .size           | No length concept
// Access syntax       | seq.at(i)           | arr[key]
// Key type            | Int only (0-based)  | Any type (K)
// Undefined indices   | Error if out of     | Have arbitrary
//                     | bounds              | values
// Use case            | Lists, vectors      | Maps, lookup tables
// Multi-dimensional   | Seq[Seq[T]]         | Array[K, Array[...]]
// SMT Theory          | Z3 Sequences        | Z3 Arrays
//
// Rule of thumb:
//   - Need length? Use Seq
//   - Need string keys? Use Array
//   - Need sparse data? Use Array
//   - Need bounds checking? Use Seq
