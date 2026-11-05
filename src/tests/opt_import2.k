// @expected SAT
// Test: derived class adds soft over imported base (expect x=5 via soft, no base objective conflict)
package opt_import2
import opt_import_base
class ImpDerived2 extends ImpBase {
  soft req x = 5 weight 1000
}
d : ImpDerived2
