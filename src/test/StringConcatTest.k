class StringConcatTest {

  greeting : String
  name : String
  message : String

  req greeting = "Hello"
  req name = "World"
  req message = greeting + " " + name

}

