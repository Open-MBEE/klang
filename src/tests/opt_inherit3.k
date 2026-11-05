// @expected SAT
// Test: three-level chain, soft in middle (expect z=7, y=7, x=7)
class OI1 { x : Int req x >= 0 req x <= 20 }
class OI2 extends OI1 { y : Int req y = x soft req y = 7 weight 2 }
class OI3 extends OI2 { z : Int req z = y maximize z weight 1 }
q : OI3
