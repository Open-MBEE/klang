// @expected SAT
package pkg2

import pkg1

class Container {
  helper : Helper  // cross-package member type
  name : String
}
