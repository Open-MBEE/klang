-- Test: bitvec_test.k
-- Tests bit vector type declarations
-- Expected: PASSED (tests basic bitvector operations)

class BitVecBasicTest {
  -- Basic bit vector declarations with decimal literals
  flags : BitVec[8] = 255
  mask : BitVec[8] = 15

  -- Bitwise AND
  andResult : BitVec[8]
  req andResult = flags band mask
  req andResult = 15  -- 255 & 15 = 15

  -- Bitwise OR
  orResult : BitVec[8]
  req orResult = 16 bor 1
  req orResult = 17

  -- Bitwise XOR
  xorResult : BitVec[8]
  req xorResult = 255 bxor 15
  req xorResult = 240  -- 255 ^ 15 = 240
}

main : BitVecBasicTest
