-- Test: array_comprehensive_test.k
-- Comprehensive tests for Array[K,V] support
-- Expected: PASSED

-- Test 1: Array with different key types
class ArrayStringKeyTest {
  -- String-keyed array (like a dictionary)
  dict : Array[String, Int]
  
  req dict["one"] = 1
  req dict["two"] = 2
  req dict["sum"] = dict["one"] + dict["two"]
}

-- Test 2: Array with Bool values
class ArrayBoolValueTest {
  flags : Array[Int, Bool]
  
  req flags[0] = true
  req flags[1] = false
  req flags[2] = flags[0]  -- Should be true
}

-- Test 3: Array with computed indices (using literals)
class ArrayComputedIndexTest {
  data : Array[Int, Int]
  
  req data[10] = 100
  req data[10 + 1] = 200
  req data[10 + 2] = data[10] + data[11]  -- 300
}

-- Test 4: Array default behavior (unspecified elements)
class ArrayDefaultTest {
  arr : Array[Int, Int]
  
  -- Only constrain one element
  req arr[0] = 42
  -- arr[1], arr[2], etc. are unconstrained - Z3 will pick values
}

-- Test 5: Array arithmetic with fixed indices
class ArrayArithmeticTest {
  scores : Array[Int, Int]
  
  req scores[0] = 85
  req scores[1] = 90
  req scores[2] = 78
  -- Sum of scores
  req scores[3] = scores[0] + scores[1] + scores[2]  -- 253
}
