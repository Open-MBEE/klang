-- @expected ERROR
-- (multiple inheritance without share declarations)
package b

import a
import c
import e

class B extends A, C, E {
 y : Int
}