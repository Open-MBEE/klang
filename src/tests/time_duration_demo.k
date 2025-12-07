// Test: Time and Duration primitive types with ISO 8601 literals
// K supports Time and Duration as primitive types
// Times/Durations are converted to milliseconds internally for SMT solving

class Meeting {
  // Use Time type for timestamps
  startTime : Time
  endTime : Time

  // Use Duration type for time spans
  minDuration : Duration

  req endTime > startTime
  // Note: Time/Duration are represented as Int (milliseconds) in SMT
  // so arithmetic operations work naturally
}

// Test with specific time values
meeting1 : Meeting
req meeting1.startTime = 2025-12-07T09:00:00
req meeting1.endTime = 2025-12-07T10:30:00
req meeting1.minDuration = PT1H  // 1 hour minimum

// Test duration arithmetic
class Task {
  estimatedDuration : Duration
  actualDuration : Duration

  // Durations should be non-negative
  req estimatedDuration >= PT0S
  req actualDuration >= PT0S

  // Task should not take longer than 2x estimated
  req actualDuration <= estimatedDuration + estimatedDuration
}

task1 : Task
req task1.estimatedDuration = PT2H30M  // 2.5 hours
// Solver finds actualDuration that satisfies constraint

// Test the HH:MM:SS format
class VideoClip {
  length : Duration

  req length >= PT0S
  req length <= PT10M  // Max 10 minutes
}

clip1 : VideoClip
// Solver finds a valid clip length
