-- @expected SAT
-- With auto-sharing of diamond ancestors, only explicit share D is needed
-- (F and E are auto-shared as ancestors of D)
package b

import a
import c
import e

class BB extends A, C, E {
 share D
 y : Int
}

b1 : BB
b2 : BB

req b1.freal != b2.freal
