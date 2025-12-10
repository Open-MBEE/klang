// filepath: /Users/bclement/git/klang/src/tests/set_time_test.k
// Test: Sets containing Time and Duration types
// Time and Duration are primitive types that map to Int in SMT

class TimeSchedule {
  // Set of times (supported since Time is a primitive type)
  availableTimes : Set[Time]

  meetingTime : Time
  req meetingTime isin availableTimes
  req meetingTime >= 2025-12-08T09:00:00
  req meetingTime <= 2025-12-08T17:00:00
}

class DurationSet {
  // Set of durations (supported since Duration is a primitive type)
  allowedDurations : Set[Duration]

  taskDuration : Duration
  req taskDuration isin allowedDurations
  req taskDuration >= PT30M  // At least 30 minutes
  req taskDuration <= PT2H   // At most 2 hours
}

// Test constraints on set of times
schedule : TimeSchedule
req schedule.availableTimes = Set{2025-12-08T09:00:00, 2025-12-08T10:00:00, 2025-12-08T14:00:00}

// Test constraints on set of durations
durations : DurationSet
req durations.allowedDurations = Set{PT30M, PT1H, PT1H30M, PT2H}

