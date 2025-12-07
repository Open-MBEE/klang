// Test: Int to String conversion - fromInt
// Expected: SKIP - fromInt requires TypeChecker changes to support as standalone function
// NOTE: This test is a placeholder until fromInt syntax is properly implemented
//       The SMT backend supports str.from_int but K syntax needs work

// For now, test that str.toInt roundtrips (string->int->compare)
class StringFromInt1 {
  // Test toInt works correctly
  numStr1 : String
  numVal1 : Int

  req numStr1 = "42"
  req numVal1 = numStr1.toInt()
  req numVal1 = 42

  // Test another toInt
  numStr2 : String
  numVal2 : Int

  req numStr2 = "12345"
  req numVal2 = numStr2.toInt()
  req numVal2 = 12345

  // Test zero
  numStr3 : String
  numVal3 : Int

  req numStr3 = "0"
  req numVal3 = numStr3.toInt()
  req numVal3 = 0
}

main : StringFromInt1

