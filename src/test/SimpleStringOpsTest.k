class SimpleStringOpsTest {

  text : String
  len : Int
  first : String

  req text = "Hello"
  req len = text.length
  req first = text.substring(0, 1)

}

