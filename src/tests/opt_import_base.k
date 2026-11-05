// @expected SAT
package opt_import_base
class ImpBase {
  x : Int
  req x >= 0
  req x <= 50
  maximize x
}
