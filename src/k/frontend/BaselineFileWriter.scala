package k.frontend

import java.io._
import org.json.{JSONObject, JSONArray}
import scala.collection.mutable.ListBuffer

/**
 * Utility for writing baseline files in expanded formats.
 * 
 * Given a baseline JSON file like `sm.k.json`, this creates additional files:
 * - `sm.k.smt` - The SMT-LIB2 formula from "smt" field
 * - `sm.k.model.smt` - The Z3 model output from "smtModel" field  
 * - `sm.baseline.k` - The original K model text from "model" field
 * - `sm.k.1.json` - The parsed AST from "json1" field
 * - `sm.k.2.json` - The transformed AST from "json2" field
 * - `sm.model.k` - K constraints representing the solution (for verification)
 * - `sm.k.output.txt` - Console output from test execution (optional, from "output" field)
 */
object BaselineFileWriter {
  
  /**
   * Write all expanded baseline files for a given baseline JSON.
   * 
   * @param baselineDir The directory containing baseline files
   * @param kFileName The name of the K file (e.g., "sm.k")
   * @param baseline The parsed baseline JSONObject
   */
  def writeExpandedFiles(baselineDir: File, kFileName: String, baseline: JSONObject): Unit = {
    // Strip the .k extension for base name
    val baseName = if (kFileName.endsWith(".k")) kFileName.dropRight(2) else kFileName
    
    // Write SMT file (smt field)
    if (baseline.has("smt") && !baseline.isNull("smt")) {
      val smtContent = baseline.getString("smt")
      writeFile(new File(baselineDir, s"$kFileName.smt"), smtContent)
    }
    
    // Write SMT model file (smtModel field)
    if (baseline.has("smtModel") && !baseline.isNull("smtModel")) {
      val smtModelContent = baseline.getString("smtModel")
      writeFile(new File(baselineDir, s"$kFileName.model.smt"), smtModelContent)
    }
    
    // Write original K model (model field)
    if (baseline.has("model") && !baseline.isNull("model")) {
      val modelContent = baseline.getString("model")
      writeFile(new File(baselineDir, s"$baseName.baseline.k"), modelContent)
    }
    
    // Write json1 (parsed AST)
    if (baseline.has("json1") && !baseline.isNull("json1")) {
      val json1 = baseline.get("json1")
      val json1Content = if (json1.isInstanceOf[JSONObject]) {
        json1.asInstanceOf[JSONObject].toString(2)
      } else if (json1.isInstanceOf[JSONArray]) {
        json1.asInstanceOf[JSONArray].toString(2)
      } else {
        json1.toString
      }
      writeFile(new File(baselineDir, s"$kFileName.1.json"), json1Content)
    }
    
    // Write json2 (transformed AST)
    if (baseline.has("json2") && !baseline.isNull("json2")) {
      val json2 = baseline.get("json2")
      val json2Content = if (json2.isInstanceOf[JSONObject]) {
        json2.asInstanceOf[JSONObject].toString(2)
      } else if (json2.isInstanceOf[JSONArray]) {
        json2.asInstanceOf[JSONArray].toString(2)
      } else {
        json2.toString
      }
      writeFile(new File(baselineDir, s"$kFileName.2.json"), json2Content)
    }
    
    // Write K model constraints (derived from smtModel)
    if (baseline.has("smtModel") && !baseline.isNull("smtModel")) {
      val smtModelContent = baseline.getString("smtModel")
      val kConstraints = smtModelToKConstraints(smtModelContent)
      writeFile(new File(baselineDir, s"$baseName.model.k"), kConstraints)
    }
    
    // Write output file (console output from test execution)
    if (baseline.has("output") && !baseline.isNull("output")) {
      val outputContent = baseline.getString("output")
      writeFile(new File(baselineDir, s"$kFileName.output.txt"), outputContent)
    }
  }
  
  /**
   * Compare expanded baseline files against the baseline JSON.
   * Returns a list of (fieldName, matches, message) tuples.
   * This provides redundant checking - comparing file contents against embedded data.
   * 
   * @param baselineDir The directory containing baseline files
   * @param kFileName The name of the K file (e.g., "sm.k")
   * @param baseline The parsed baseline JSONObject
   * @return List of comparison results: (field, matches, message)
   */
  def compareExpandedFiles(baselineDir: File, kFileName: String, baseline: JSONObject): List[(String, Boolean, String)] = {
    val baseName = if (kFileName.endsWith(".k")) kFileName.dropRight(2) else kFileName
    val results = ListBuffer[(String, Boolean, String)]()
    
    // Compare SMT file
    val smtFile = new File(baselineDir, s"$kFileName.smt")
    if (smtFile.exists && baseline.has("smt") && !baseline.isNull("smt")) {
      val fileContent = readFile(smtFile)
      val jsonContent = baseline.getString("smt")
      val matches = fileContent.trim == jsonContent.trim
      results += (("smt", matches, if (matches) "OK" else "MISMATCH: .smt file differs from JSON"))
    }
    
    // Compare SMT model file
    val smtModelFile = new File(baselineDir, s"$kFileName.model.smt")
    if (smtModelFile.exists && baseline.has("smtModel") && !baseline.isNull("smtModel")) {
      val fileContent = readFile(smtModelFile)
      val jsonContent = baseline.getString("smtModel")
      val matches = fileContent.trim == jsonContent.trim
      results += (("smtModel", matches, if (matches) "OK" else "MISMATCH: .model.smt file differs from JSON"))
    }
    
    // Compare baseline.k file (model field)
    val baselineKFile = new File(baselineDir, s"$baseName.baseline.k")
    if (baselineKFile.exists && baseline.has("model") && !baseline.isNull("model")) {
      val fileContent = readFile(baselineKFile)
      val jsonContent = baseline.getString("model")
      val matches = fileContent.trim == jsonContent.trim
      results += (("model", matches, if (matches) "OK" else "MISMATCH: .baseline.k file differs from JSON"))
    }
    
    // Compare json1 file
    val json1File = new File(baselineDir, s"$kFileName.1.json")
    if (json1File.exists && baseline.has("json1") && !baseline.isNull("json1")) {
      val fileContent = readFile(json1File)
      val json1 = baseline.get("json1")
      val jsonContent = if (json1.isInstanceOf[JSONObject]) {
        json1.asInstanceOf[JSONObject].toString(2)
      } else if (json1.isInstanceOf[JSONArray]) {
        json1.asInstanceOf[JSONArray].toString(2)
      } else {
        json1.toString
      }
      val matches = fileContent.trim == jsonContent.trim
      results += (("json1", matches, if (matches) "OK" else "MISMATCH: .1.json file differs from JSON"))
    }
    
    // Compare json2 file
    val json2File = new File(baselineDir, s"$kFileName.2.json")
    if (json2File.exists && baseline.has("json2") && !baseline.isNull("json2")) {
      val fileContent = readFile(json2File)
      val json2 = baseline.get("json2")
      val jsonContent = if (json2.isInstanceOf[JSONObject]) {
        json2.asInstanceOf[JSONObject].toString(2)
      } else if (json2.isInstanceOf[JSONArray]) {
        json2.asInstanceOf[JSONArray].toString(2)
      } else {
        json2.toString
      }
      val matches = fileContent.trim == jsonContent.trim
      results += (("json2", matches, if (matches) "OK" else "MISMATCH: .2.json file differs from JSON"))
    }
    
    results.toList
  }
  
  /**
   * Read file contents as a string.
   */
  private def readFile(file: File): String = {
    scala.io.Source.fromFile(file).mkString
  }
  
  /**
   * Convert an SMT model string to K constraints.
   * 
   * This parses the Z3 model output (which uses define-fun) and converts
   * it into K req statements that can be added to the original K file
   * to verify the solution is correct.
   * 
   * @param smtModel The SMT-LIB2 model string from Z3
   * @return K constraint syntax that can be appended to the original K file
   */
  def smtModelToKConstraints(smtModel: String): String = {
    if (smtModel == null || smtModel.isEmpty || smtModel == "()" || smtModel == "()\\n") {
      return "-- UNSAT: No model available\n"
    }
    
    val sb = new StringBuilder
    sb.append("-- K constraints derived from solver model\n")
    sb.append("-- These can be combined with the original K file to verify the solution\n")
    sb.append("-- Generated by BaselineFileWriter\n\n")
    
    val constraints = ListBuffer[String]()
    
    // Parse define-fun statements
    // Format: (define-fun name () Type value)
    // For constants (arity 0), this is straightforward
    val defineFunPattern = """\(define-fun\s+(\S+)\s+\(\)\s+(\S+)\s+""".r
    
    // Also handle heap entries which show object field values
    val heapEntryPattern = """\(mk-(\w+)\s+([^)]+)\)""".r
    
    // Extract simple constant definitions
    val lines = smtModel.split('\n')
    var i = 0
    while (i < lines.length) {
      val line = lines(i).trim
      
      // Look for constant definitions: (define-fun name () Type value)
      if (line.startsWith("(define-fun") && line.contains("()")) {
        val content = extractBalancedSExpr(smtModel, smtModel.indexOf(line))
        if (content.nonEmpty) {
          parseDefineFunConstant(content) match {
            case Some((name, typeName, value)) =>
              // Skip internal names and complex types
              if (!isInternalName(name) && isSimpleValue(value)) {
                val kValue = formatValueAsK(value, typeName)
                if (kValue.nonEmpty) {
                  constraints += s"req $name = $kValue"
                }
              }
            case None => // Skip
          }
        }
      }
      
      i += 1
    }
    
    // Parse heap to extract object values
    val heapConstraints = parseHeapForConstraints(smtModel)
    constraints ++= heapConstraints
    
    if (constraints.isEmpty) {
      sb.append("-- No simple constraints could be extracted from the model\n")
      sb.append("-- The model may contain only complex expressions or heap structures\n")
    } else {
      sb.append(s"-- ${constraints.size} constraints extracted\n\n")
      constraints.foreach { c =>
        sb.append(c)
        sb.append("\n")
      }
    }
    
    sb.toString()
  }
  
  /**
   * Parse a (define-fun name () Type value) expression for a constant.
   */
  private def parseDefineFunConstant(sexpr: String): Option[(String, String, String)] = {
    // Remove outer parens and split
    val content = sexpr.trim.stripPrefix("(").stripSuffix(")")
    val tokens = tokenizeSExpr(content)
    
    if (tokens.length >= 4 && tokens(0) == "define-fun") {
      val name = tokens(1)
      // tokens(2) should be "()" for constants
      if (tokens(2) == "()") {
        val typeName = tokens(3)
        // Value is everything after the type
        val valueStart = sexpr.indexOf(typeName) + typeName.length
        val value = sexpr.substring(valueStart).trim.stripSuffix(")").trim
        Some((name, typeName, value))
      } else {
        None // This is a function with arguments, not a constant
      }
    } else {
      None
    }
  }
  
  /**
   * Tokenize an S-expression, treating nested parens as single tokens.
   */
  private def tokenizeSExpr(s: String): List[String] = {
    val tokens = ListBuffer[String]()
    var i = 0
    while (i < s.length) {
      while (i < s.length && s(i).isWhitespace) i += 1
      if (i < s.length) {
        if (s(i) == '(') {
          // Find matching close paren
          var depth = 1
          var j = i + 1
          while (j < s.length && depth > 0) {
            if (s(j) == '(') depth += 1
            else if (s(j) == ')') depth -= 1
            j += 1
          }
          tokens += s.substring(i, j)
          i = j
        } else if (s(i) == '"') {
          // String literal
          var j = i + 1
          while (j < s.length && s(j) != '"') j += 1
          tokens += s.substring(i, j + 1)
          i = j + 1
        } else {
          // Regular token
          var j = i
          while (j < s.length && !s(j).isWhitespace && s(j) != '(' && s(j) != ')') j += 1
          tokens += s.substring(i, j)
          i = j
        }
      }
    }
    tokens.toList
  }
  
  /**
   * Extract a balanced S-expression starting at the given position.
   */
  private def extractBalancedSExpr(s: String, start: Int): String = {
    if (start < 0 || start >= s.length || s(start) != '(') return ""
    
    var depth = 0
    var i = start
    while (i < s.length) {
      if (s(i) == '(') depth += 1
      else if (s(i) == ')') {
        depth -= 1
        if (depth == 0) {
          return s.substring(start, i + 1)
        }
      }
      i += 1
    }
    ""
  }
  
  /**
   * Check if a name is internal/synthetic (should be skipped).
   */
  private def isInternalName(name: String): Boolean = {
    name.startsWith("k!") ||
    name.startsWith("_xk") ||
    name.contains("!val!") ||
    name == "heap" ||
    name == "NULL$" ||
    name == "NULL$Ref" ||
    name.startsWith("const-") ||
    name.startsWith("lift-") ||
    name.startsWith("mk-") ||
    name.startsWith("deref")
  }
  
  /**
   * Check if a value is simple enough to convert to K.
   */
  private def isSimpleValue(value: String): Boolean = {
    val trimmed = value.trim
    // Simple values: integers, booleans, strings, simple negative numbers
    trimmed.matches("-?\\d+") ||
    trimmed == "true" || trimmed == "false" ||
    trimmed.startsWith("\"") ||
    trimmed.matches("-?\\d+\\.\\d+") ||  // Decimals
    trimmed.matches("#x[0-9a-fA-F]+") ||  // Hex bitvectors
    (trimmed.startsWith("(- ") && trimmed.endsWith(")") && trimmed.drop(3).dropRight(1).trim.matches("\\d+"))  // (- n)
  }
  
  /**
   * Format a value from SMT to K syntax.
   */
  private def formatValueAsK(value: String, typeName: String): String = {
    val trimmed = value.trim
    
    // Handle negative numbers in SMT format: (- n)
    if (trimmed.startsWith("(- ") && trimmed.endsWith(")")) {
      val num = trimmed.drop(3).dropRight(1).trim
      return s"-$num"
    }
    
    // Handle rationals: (/ num denom) - skip these for now
    if (trimmed.startsWith("(/")) {
      return ""
    }
    
    // Handle hex bitvectors: #xNN
    if (trimmed.startsWith("#x")) {
      return trimmed  // K can use hex format directly
    }
    
    // Booleans
    if (trimmed == "true" || trimmed == "false") {
      return trimmed
    }
    
    // Integers and decimals
    if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
      return trimmed
    }
    
    // Strings
    if (trimmed.startsWith("\"")) {
      return trimmed
    }
    
    // Complex values - return empty to skip
    ""
  }
  
  /**
   * Parse heap definition to extract object field constraints.
   */
  private def parseHeapForConstraints(smtModel: String): List[String] = {
    val constraints = ListBuffer[String]()
    
    // Look for heap definition with store expressions
    // These contain (mk-ClassName field1 field2 ...) which give us object field values
    
    // For now, we extract information from const-N-ClassName definitions
    // These define the actual object instances
    val constPattern = """\(define-fun\s+(const-\d+-(\w+))\s+\(\)\s+\w+""".r
    
    for (m <- constPattern.findAllMatchIn(smtModel)) {
      val constName = m.group(1)
      val className = m.group(2)
      
      // Find the full define-fun for this const
      val startIdx = smtModel.indexOf(s"(define-fun $constName")
      if (startIdx >= 0) {
        val expr = extractBalancedSExpr(smtModel, startIdx)
        // The mk-ClassName part contains field values
        val mkPattern = s"\\(mk-$className\\s+([^)]+)\\)".r
        for (mkMatch <- mkPattern.findFirstMatchIn(expr)) {
          val fieldValues = mkMatch.group(1).trim.split("\\s+")
          // We'd need class metadata to map these to field names
          // For now, just add a comment showing the values
          if (fieldValues.nonEmpty && fieldValues.exists(_.nonEmpty)) {
            constraints += s"-- $className instance: ${fieldValues.mkString(", ")}"
          }
        }
      }
    }
    
    constraints.toList
  }
  
  /**
   * Write content to a file.
   */
  private def writeFile(file: File, content: String): Unit = {
    val writer = new FileWriter(file)
    try {
      writer.write(content)
    } finally {
      writer.close()
    }
  }
}
