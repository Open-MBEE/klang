// @preferred_options -unified -timeout 60000
// @expected SAT
package timelinechain

-- Regression/perf test for timelineValue + heap scalarization (see docs/TIMELINES.md).
-- A chain of dependent lookups (like an iterate-until-converged scan-center search) written as FIELDS of a class instance.
-- Before heap scalarization + pinned constructor references this shape was ~10-25x slower than the same chain as top-level
-- definitions; with the default settings it should solve in a couple of seconds (try -no-scalarize to compare).
--
-- demo timeline: 361 points, 10 s apart from 2031-01-01T00:00:00Z, value (seconds) = 100 + 0.01 s + 5 sin(s/500)

t0 : Time = 2031-01-01T00:00:00.000
pad : Duration = 0:01:00.000

fun f(t : Time) : Duration { timelineValue("src/test/data/timeline_demo.csv", t, "ms") }

class Chain {
  anchor : Time
  c0 : Time
  c1 : Time
  c2 : Time
  c3 : Time
  c4 : Time
  c5 : Time
  c6 : Time
  req c0 = anchor
  req c1 = anchor + (f(c0) + pad)
  req c2 = anchor + (f(c1) + pad)
  req c3 = anchor + (f(c2) + pad)
  req c4 = anchor + (f(c3) + pad)
  req c5 = anchor + (f(c4) + pad)
  req c6 = anchor + (f(c5) + pad)
}

ch : Chain = Chain()
req ch.anchor = t0 + 0:10:00.000
