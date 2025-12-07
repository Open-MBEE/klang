ERROR : Int req ERROR < 0
fun CHECK(b:Bool):Int {if b then 42 else ERROR}

class TimeSpan {
  t1 : Int
  t2 : Int

  fun before(other: TimeSpan): Bool {
    t2 < other.t1
  }

  req t1 < t2

  RESULT : Int = CHECK(t1 < t2)
}

class Event {
  duration : TimeSpan
  min: Int

  req duration.t2-duration.t1 >= min

  RESULT : Int = CHECK(duration.t2-duration.t1 >= min)
}

a : Event
b : Event

req a.min = 10
req b.min = 20
req a.duration.before(b.duration)


RESULT: Int = 
  CHECK(
    a.min = 10 && 
    b.min = 20 &&
    a.duration.t2 < b.duration.t1)
