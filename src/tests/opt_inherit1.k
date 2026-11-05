// @expected SAT
// Test: inherited fields in objective (expect z=20)
class OptBase { x : Int req x >= 0 req x <= 10 }
class OptDerived extends OptBase {
  z : Int
  req z >= x
  req z <= 20
  maximize z
}
d : OptDerived
