// Minimal test for float conversion
class FloatMinimal {
  intVal : Int = 42
  f32 : Float32 = intVal as Float32
  realBack : Real = f32 as Real
  req realBack > 41.0
  req realBack < 43.0
}

main : FloatMinimal
