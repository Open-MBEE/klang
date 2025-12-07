// Test: Duration in HH:MM:SS.mmm format
// This tests the new HH:MM:SS duration literal syntax

class VideoClip {
  // Duration using HH:MM:SS format
  length : Duration

  req length >= 0:00:00
  req length <= 0:10:00  // Max 10 minutes
}

class Movie {
  // Longer duration
  runtime : Duration

  req runtime >= 1:00:00  // At least 1 hour
  req runtime <= 3:00:00  // At most 3 hours
}

// Test with milliseconds
class AudioClip {
  duration : Duration

  req duration >= 0:00:00.000
  req duration <= 0:00:30.500  // Max 30.5 seconds
}

clip1 : VideoClip
movie1 : Movie
audio1 : AudioClip

// Specific values
req clip1.length = 0:05:30      // 5 minutes 30 seconds
req movie1.runtime = 2:15:00    // 2 hours 15 minutes
req audio1.duration = 0:00:15.250  // 15.25 seconds

