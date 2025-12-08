-- Test: object_bounds_dynamic1.k
-- Tests dynamic object creation with Seq - requires object bounds
-- Expected: Currently may not fully work (object bounds encoding partial)
-- This test documents the desired behavior

class Task {
  duration : Int
  priority : Int

  req duration > 0
  req duration <= 100
  req priority >= 1
  req priority <= 5
}

class Schedule {
  -- Fixed number of tasks for now (dynamic Seq not yet supported)
  task1 : Task
  task2 : Task
  task3 : Task

  totalDuration : Int

  -- Total duration is sum of all task durations
  req totalDuration = task1.duration + task2.duration + task3.duration

  -- Total duration constraint
  req totalDuration <= 200

  -- At least one high priority task
  req task1.priority = 5 || task2.priority = 5 || task3.priority = 5
}

schedule : Schedule

