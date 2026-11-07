// @expected SAT
// Test: inherited Bool softs with different weights (expect p=false, q=true)
class SoftBoolBase { p : Bool soft req p weight 1 }
class SoftBoolDerived extends SoftBoolBase { q : Bool req !(p && q) soft req q weight 2 }
d : SoftBoolDerived
