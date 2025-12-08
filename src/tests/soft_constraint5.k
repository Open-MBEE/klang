-- Test: soft_constraint5.k
-- Tests soft constraints with Real numbers
-- Expected: PASSED

class RealSoftConstraints {
  temperature : Real
  pressure : Real

  -- Hard safety constraints
  req temperature >= -273.15  -- absolute zero
  req temperature <= 1000.0
  req pressure >= 0.0
  req pressure <= 100.0

  -- Soft preferences for optimal operating conditions
  soft req temperature >= 20.0
  soft req temperature <= 25.0
  soft req pressure >= 1.0
  soft req pressure <= 1.1

  -- Soft constraint relating the two
  soft req temperature * pressure < 30.0
}

sensor : RealSoftConstraints

