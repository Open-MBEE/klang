-- Test: duration_microseconds1.k
-- Tests microsecond precision in Duration literals
-- Expected: PASSED

class PrecisionTest {
  -- Microsecond precision using HH:MM:SS.ffffff format
  microDuration : Duration

  -- 1 microsecond = 0:00:00.000001
  req microDuration >= 0:00:00.000001
  req microDuration <= 0:00:00.001000  -- 1 millisecond max
}

class HighPrecisionEvent {
  -- Sub-millisecond timing
  startOffset : Duration
  endOffset : Duration

  req startOffset = 0:00:00.000500   -- 500 microseconds
  req endOffset = 0:00:00.001500     -- 1500 microseconds = 1.5ms
  req endOffset > startOffset
}

-- Test specific microsecond values
class MicrosecondValues {
  oneMicro : Duration      -- 1 microsecond
  hundredMicros : Duration -- 100 microseconds
  oneMillis : Duration     -- 1000 microseconds = 1 millisecond

  req oneMicro = 0:00:00.000001
  req hundredMicros = 0:00:00.000100
  req oneMillis = 0:00:00.001000

  -- Verify ordering
  req oneMicro < hundredMicros
  req hundredMicros < oneMillis
}

test1 : PrecisionTest
test2 : HighPrecisionEvent
test3 : MicrosecondValues

