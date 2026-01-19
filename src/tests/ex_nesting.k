// @expected SAT
// KNOWN LIMITATION: Nested classes (Inner, Very_Inner) are not currently
// instantiated or checked. Only Outer is found. The constraints req x = y
// and req z = x are silently ignored. This returns SAT because only the
// outer class constraints are checked (none in this case).
package nesting

class Outer {
  var x : Int

  class Inner {
    var y : Int

    req x = y

    class Very_Inner {
      var z : Int

      req z = x
    }
  }
}