// @expected SAT
// Test: weighted soft constraints over object references (expect pick = good)
class SoftRefItem {
  ok : Bool
}
class SoftRef1 {
  good : SoftRefItem
  bad : SoftRefItem
  pick : SoftRefItem
  req good.ok
  req !bad.ok
  req pick = good || pick = bad
  soft req pick = bad weight 1
  soft req pick.ok weight 3
}
main : SoftRef1
