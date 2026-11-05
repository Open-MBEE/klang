// @expected SAT
// Test: objective in imported base class (expect x=50, y=51)
package opt_import1
import opt_import_base
class ImpDerived extends ImpBase {
  y : Int
  req y = x + 1
}
d : ImpDerived
