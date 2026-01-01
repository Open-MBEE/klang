class Fruit {
  mass : Real
}
 
class CitrusFruit extends Fruit {
  vitC : Real
}
 
class Legume extends Fruit {
  protein : Real  
}
 
class Berry extends Fruit
 
class SeedyFruit extends Fruit, CitrusFruit {
  share Fruit;  -- Resolve diamond (Fruit inherited via both direct and CitrusFruit paths)
  seeds : Int
}
 
apple : CitrusFruit
peanut : Legume
 
req apple is SeedyFruit


