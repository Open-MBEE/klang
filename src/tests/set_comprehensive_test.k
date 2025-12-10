// Comprehensive test for Set operations in K
class SetComprehensiveTest {
  intSet1 : Set[Int]
  req intSet1 = Set{1, 2, 3, 4, 5}
  intSet2 : Set[Int]
  req intSet2 = Set{4, 5, 6, 7, 8}
  unionSet : Set[Int]
  req unionSet = intSet1 union intSet2
  req 6 isin unionSet
  req 1 isin unionSet
  interSet : Set[Int]
  req interSet = intSet1 inter intSet2
  req 4 isin interSet
  req 5 isin interSet
  smallSet : Set[Int]
  req smallSet = Set{1, 2}
  req smallSet subset intSet1
  stringSet1 : Set[String]
  req stringSet1 = Set{"hello", "world"}
  stringSet2 : Set[String]
  req stringSet2 = Set{"world", "there"}
  stringUnion : Set[String]
  req stringUnion = stringSet1 union stringSet2
  req "hello" isin stringUnion
  req "there" isin stringUnion
  boolSet : Set[Bool]
  req boolSet = Set{x | x : Bool :- true}
  req true isin boolSet
  req false isin boolSet
}
main : SetComprehensiveTest
