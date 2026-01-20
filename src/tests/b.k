-- @expected SAT
-- (multiple inheritance with auto-shared diamond ancestors)
package b

import a
import c
import e

class B extends A, C, E {
 y : Int
}