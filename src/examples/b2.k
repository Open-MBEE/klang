package b

import a
import c
import e

class BB extends A, C, E {
 share F
 share E
 share D
 y : Int
}

b1 : BB
b2 : BB

req b1.freal != b2.freal
