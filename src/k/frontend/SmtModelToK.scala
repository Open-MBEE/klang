package k.frontend

import java.io.{File, PrintWriter}
import org.json.JSONObject
import scala.io.Source
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.{Map => MutableMap}

/**
 * Tool to convert Z3 SMT model output to K constraints.
 * 
 * This allows extracting K constraints from existing baseline JSON files
 * without re-running the solver. It parses the K source file to get variable
 * names and types, then uses those to interpret the smtModel output.
 * 
 * NOTE: This tool is designed to run WITHOUT loading Z3 native libraries.
 * It uses ANTLR directly to parse K files instead of going through K2Z3.
 * 
 * Usage: 
 *   SmtModelToK <file.k>              - Process single K file
 *   SmtModelToK --all-examples        - Process all examples
 *   SmtModelToK --all-tests           - Process all tests  
 *   SmtModelToK --all                 - Process all baselines
 */
object SmtModelToK {
  
  var debug = false
  
  // Local class map - built from parsing without TypeChecker
  var localClasses: MutableMap[String, EntityDecl] = MutableMap()
  
  /**
   * Check if a type is primitive without using TypeChecker.
   */
  def isPrimitiveType(t: Type): Boolean = {
    t.isInstanceOf[PrimitiveType] ||
      (t match {
        case CartesianType(types) => types.forall { isPrimitiveType(_) }
        case ParenType(ty)        => isPrimitiveType(ty)
        case UnitType             => true
        case _                    => false
      })
  }
  
  /**
   * Build class map from Model AST directly.
   */
  def buildClassMap(model: Model): Unit = {
    def collectClasses(decls: List[TopDecl]): Unit = {
      for (decl <- decls) {
        decl match {
          case ed: EntityDecl =>
            localClasses(ed.ident) = ed
            // Also collect nested classes
            for (member <- ed.members) {
              member match {
                case nested: EntityDecl =>
                  localClasses(nested.ident) = nested
                case _ =>
              }
            }
          case _ =>
        }
      }
    }
    
    localClasses.clear()
    collectClasses(model.decls)
    for (pkg <- model.packages) {
      collectClasses(pkg.model.decls)
    }
    log(s"Built class map with ${localClasses.size} classes: ${localClasses.keys.mkString(", ")}")
  }
  
  /**
   * Simplified transformModel that doesn't use TypeChecker.
   * Wraps top-level declarations into TopLevelDeclarations class for SMT model parsing.
   */
  def simpleTransformModel(model: Model): Model = {
    val Model(packageName, packages, imports, annotations, decls) = model
    
    // Extract member declarations, but EXCLUDE EntityDecl
    var memberDecls: List[MemberDecl] =
      for (decl <- decls if decl.isInstanceOf[MemberDecl] && !decl.isInstanceOf[EntityDecl]) 
        yield decl.asInstanceOf[MemberDecl]
    
    // Convert ExpressionDecl to ConstraintDecl
    memberDecls = memberDecls.map {
      case ExpressionDecl(exp) => ConstraintDecl(None, exp, false)
      case other => other
    }
    
    var entityDecls: List[EntityDecl] =
      for (decl <- decls if decl.isInstanceOf[EntityDecl]) yield decl.asInstanceOf[EntityDecl]
    
    // Create mainClass (TopLevelDeclarations) if there are member declarations
    val entityDeclsWithMain = 
      if (memberDecls.nonEmpty) {
        val mainClass = EntityDecl(Nil, ClassToken, None, UtilSMT.Names.mainClass, null, Nil, Nil, memberDecls)
        mainClass :: entityDecls
      } else {
        entityDecls
      }

    var newPackages = ListBuffer[PackageDecl]()
    for (pd <- packages) {
      var m: Model = simpleTransformModel(pd.model)
      var newPackage = PackageDecl(pd.name, m)
      newPackages += newPackage
    }

    Model(packageName, newPackages.toList, imports, annotations, entityDeclsWithMain)
  }
  
  def log(msg: String): Unit = if (debug) println(s"[SmtModelToK] $msg")
  
  /**
   * Parse smtModel string to extract heap mappings.
   * Returns a map from ref number to value string.
   */
  def parseSmtModelHeap(smtModel: String): Map[String, String] = {
    if (smtModel == null || smtModel.trim.isEmpty || smtModel.trim == "()") {
      return Map()
    }
    
    val heapMap = scala.collection.mutable.Map[String, String]()
    val normalized = smtModel.replaceAll("\\s+", " ")
    
    // Pattern to find define-fun entries for const-N-ClassName
    // e.g. (define-fun const-2-Account () Account (mk-Account 0 0))
    val constPattern = """\(define-fun\s+const-(\d+)-(\w+)\s+\(\)\s+\w+\s+""".r
    
    for (m <- constPattern.findAllMatchIn(normalized)) {
      val refNum = m.group(1)
      val className = m.group(2)
      val startIdx = m.end
      
      // Find the balanced value expression
      var depth = 0
      var endIdx = startIdx
      var i = startIdx
      while (i < normalized.length && endIdx == startIdx) {
        normalized(i) match {
          case '(' => depth += 1
          case ')' => 
            if (depth == 0) endIdx = i  // Found closing paren of define-fun
            else depth -= 1
          case _ =>
        }
        i += 1
      }
      
      if (endIdx > startIdx) {
        val valueExpr = normalized.substring(startIdx, endIdx).trim
        heapMap(refNum) = s"(lift-$className $valueExpr)"
        log(s"Found ref $refNum -> $className: ${valueExpr.take(60)}...")
      }
    }
    
    heapMap.toMap
  }
  
  /**
   * Extract let bindings from SMT model string for resolving references.
   */
  def extractLetBindings(modelStr: String): Map[String, String] = {
    var bindings = Map[String, String]()
    val normalized = modelStr.replaceAll("\\s+", " ")
    
    val bindingNamePattern = """\(([a-zA-Z]!\d+)\s+""".r
    
    for (m <- bindingNamePattern.findAllMatchIn(normalized)) {
      val bindingName = m.group(1)
      val startIdx = m.end
      
      var depth = 1
      var endIdx = startIdx
      var i = startIdx
      while (i < normalized.length && depth > 0) {
        normalized(i) match {
          case '(' => depth += 1
          case ')' => 
            depth -= 1
            if (depth == 0) endIdx = i
          case _ =>
        }
        i += 1
      }
      
      if (endIdx > startIdx) {
        bindings += (bindingName -> normalized.substring(startIdx, endIdx).trim)
      }
    }
    
    bindings
  }
  
  /**
   * Parse object values from a mk-ClassName expression.
   * Returns list of field values in order.
   */
  def parseObjectValues(valueString: String): List[String] = {
    def tokenize(s: String): List[String] = {
      var tokens = List[String]()
      var i = 0
      while (i < s.length) {
        while (i < s.length && s(i).isWhitespace) i += 1
        if (i >= s.length) return tokens.reverse

        val start = i
        s(i) match {
          case '"' =>
            i += 1
            while (i < s.length && s(i) != '"') {
              if (s(i) == '\\' && i + 1 < s.length) i += 2
              else i += 1
            }
            if (i < s.length) i += 1
            tokens = s.substring(start, i) :: tokens
          case '(' =>
            var depth = 1
            i += 1
            while (i < s.length && depth > 0) {
              s(i) match {
                case '"' =>
                  i += 1
                  while (i < s.length && s(i) != '"') {
                    if (s(i) == '\\' && i + 1 < s.length) i += 2
                    else i += 1
                  }
                  if (i < s.length) i += 1
                case '(' => depth += 1; i += 1
                case ')' => depth -= 1; i += 1
                case _ => i += 1
              }
            }
            tokens = s.substring(start, i) :: tokens
          case ')' =>
            i += 1
          case _ =>
            while (i < s.length && !s(i).isWhitespace && s(i) != '(' && s(i) != ')' && s(i) != '"') {
              i += 1
            }
            if (i > start) tokens = s.substring(start, i) :: tokens
        }
      }
      tokens.reverse
    }

    val allTokens = tokenize(valueString)
    if (allTokens.nonEmpty) allTokens.drop(1) else Nil  // Drop mk-ClassName
  }
  
  /**
   * Format a primitive value from SMT to K syntax.
   */
  def formatPrimitiveValue(v: String): String = {
    val trimmed = v.trim
    if (trimmed.startsWith("(/") || trimmed.startsWith("(/ ")) {
      val parts = trimmed.stripPrefix("(/").stripPrefix("(/ ").stripSuffix(")").trim.split("\\s+")
      if (parts.length == 2) {
        try {
          val num = parts(0).toDouble
          val denom = parts(1).toDouble
          if (denom != 0) f"${num / denom}%.6f" else trimmed
        } catch {
          case _: NumberFormatException => trimmed
        }
      } else trimmed
    } else if (trimmed.startsWith("(-") || trimmed.startsWith("(- ")) {
      val inner = trimmed.stripPrefix("(-").stripPrefix("(- ").stripSuffix(")").trim
      s"-$inner"
    } else trimmed
  }
  
  /**
   * Format a sequence value from SMT to K syntax.
   */
  def formatSequenceValue(seqStr: String): String = {
    val unitPattern = """\(seq\.unit\s+(-?\d+)\)""".r
    val elements = unitPattern.findAllMatchIn(seqStr).map(_.group(1)).toList
    if (elements.nonEmpty) {
      "Seq(" + elements.mkString(", ") + ")"
    } else if (seqStr.contains("seq.empty")) {
      "Seq()"
    } else {
      seqStr
    }
  }
  
  /**
   * Collect top-level property declarations from a Model AST.
   * Returns list of (name, type, isPrimitive, isCollection).
   */
  def collectTopLevelProperties(model: Model): List[(String, Type, Boolean, Boolean)] = {
    val props = ListBuffer[(String, Type, Boolean, Boolean)]()
    
    def processDecls(decls: List[TopDecl]): Unit = {
      for (decl <- decls) {
        decl match {
          case ed: EntityDecl if ed.ident == UtilSMT.Names.mainClass =>
            for (member <- ed.members) {
              member match {
                case pd: PropertyDecl =>
                  val ty = pd.getTypeOrError
                  val isPrim = isPrimitiveType(ty)
                  val isColl = Misc.isCollection(ty)
                  props += ((pd.name, ty, isPrim, isColl))
                case _ =>
              }
            }
          case pd: PropertyDecl =>
            val ty = pd.getTypeOrError
            val isPrim = isPrimitiveType(ty)
            val isColl = Misc.isCollection(ty)
            props += ((pd.name, ty, isPrim, isColl))
          case _ =>
        }
      }
    }
    
    processDecls(model.decls)
    for (pkg <- model.packages) {
      processDecls(pkg.model.decls)
    }
    
    props.toList
  }
  
  /**
   * Get class properties for formatting object values as K constraints.
   */
  def getClassProperties(className: String): List[(String, Type)] = {
    localClasses.get(className) match {
      case Some(classDecl) =>
        classDecl.getAllPropertyDecls.map(pd => (pd.name, pd.getTypeOrError))
      case None =>
        log(s"Warning: Unknown class $className")
        Nil
    }
  }
  
  /**
   * Convert Z3 model output to K constraints.
   * 
   * @param model The parsed K Model AST (for variable names and types)
   * @param smtModel The raw smtModel string from Z3
   * @return K constraint text that can be appended to the original .k file
   */
  def smtModelToKConstraints(model: Model, smtModel: String): String = {
    if (smtModel == null || smtModel.trim.isEmpty || smtModel.trim == "()") {
      return "-- UNSAT: No model available\n"
    }
    
    val constraints = ListBuffer[String]()
    constraints += "-- K constraints derived from solver model"
    constraints += "-- These can be added to the original .k file for validation"
    constraints += ""
    
    // Parse the heap structure from smtModel
    val heapMap = parseSmtModelHeap(smtModel)
    if (heapMap.isEmpty) {
      constraints += "-- Could not parse heap from smtModel"
      return constraints.mkString("\n")
    }
    
    // Get top-level variable names from AST
    val topLevelVars = collectTopLevelProperties(model)
    log(s"Found ${topLevelVars.size} top-level variables: ${topLevelVars.map(_._1).mkString(", ")}")
    
    // Track which refs have been output (to avoid duplicates)
    val outputRefs = scala.collection.mutable.Set[String]()
    
    // Get the TopLevelDeclarations value (ref 0)
    val topLevelValue = heapMap.get("0")
    
    if (topLevelValue.isDefined && topLevelVars.nonEmpty) {
      // Parse the TopLevelDeclarations value to get the field values
      val tlValue = topLevelValue.get
      val mkIdx = tlValue.indexOf("mk-")
      if (mkIdx >= 0) {
        // Extract balanced expression starting from mk-
        var depth = 0
        var endIdx = mkIdx
        var i = mkIdx
        val normalized = tlValue
        while (i < normalized.length) {
          normalized(i) match {
            case '(' => depth += 1
            case ')' => 
              depth -= 1
              if (depth == 0) { endIdx = i + 1; i = normalized.length }
            case _ =>
          }
          i += 1
        }
        if (endIdx <= mkIdx) endIdx = normalized.length - 1
        
        val mkExpr = normalized.substring(mkIdx, endIdx)
        val fieldValues = parseObjectValues(mkExpr)
        log(s"Field values: ${fieldValues.mkString(", ")}")
        
        // Match variables to values
        for (((varName, varType, isPrim, isColl), idx) <- topLevelVars.zipWithIndex) {
          if (idx < fieldValues.length) {
            val rawValue = fieldValues(idx)
            
            if (isPrim) {
              val formattedValue = formatPrimitiveValue(rawValue)
              constraints += s"req $varName = $formattedValue"
            } else if (isColl) {
              val formattedSeq = formatSequenceValue(rawValue)
              constraints += s"req $varName = $formattedSeq"
            } else {
              // Object reference - need to expand
              val refNum = rawValue.trim
              outputRefs += refNum
              outputObjectConstraints(varName, refNum, heapMap, constraints, outputRefs)
            }
          }
        }
      }
    }
    
    // Now output extra objects that weren't referenced from TopLevelDeclarations
    // These are objects at refs 1, 2, 3, etc. that are created during analysis
    val extraConstraints = ListBuffer[String]()
    
    for ((refNum, objValue) <- heapMap.toSeq.sortBy(_._1.toIntOption.getOrElse(999))) {
      if (refNum != "0" && !outputRefs.contains(refNum)) {
        // Extract class name from lift-ClassName
        val liftIdx = objValue.indexOf("lift-")
        if (liftIdx >= 0) {
          val spaceIdx = objValue.indexOf(' ', liftIdx)
          if (spaceIdx > liftIdx) {
            val className = objValue.substring(liftIdx + 5, spaceIdx)
            
            // Skip TopLevelDeclarations
            if (className != "TopLevelDeclarations") {
              // Generate a name for this extra object
              val objName = s"obj${refNum}_$className"
              
              // Add declaration comment
              extraConstraints += s"-- Extra object at ref $refNum"
              extraConstraints += s"$objName : $className"
              
              outputRefs += refNum
              outputObjectConstraints(objName, refNum, heapMap, extraConstraints, outputRefs)
              extraConstraints += ""
            }
          }
        }
      }
    }
    
    if (extraConstraints.nonEmpty) {
      constraints += ""
      constraints += "-- Extra objects (generated instances)"
      constraints ++= extraConstraints
    }
    
    constraints += ""
    constraints.mkString("\n")
  }
  
  /**
   * Output constraints for an object at a given heap reference.
   */
  private def outputObjectConstraints(
    varName: String, 
    refNum: String, 
    heapMap: Map[String, String], 
    constraints: ListBuffer[String],
    outputRefs: scala.collection.mutable.Set[String]
  ): Unit = {
    heapMap.get(refNum) match {
      case Some(objValue) =>
        // Get the class name
        val liftIdx = objValue.indexOf("lift-")
        val spaceIdx = objValue.indexOf(' ', liftIdx)
        if (liftIdx >= 0 && spaceIdx > liftIdx) {
          val className = objValue.substring(liftIdx + 5, spaceIdx)
          
          // Get class properties
          val classProps = getClassProperties(className)
          
          // Parse the mk- expression
          val objMkIdx = objValue.indexOf("mk-")
          if (objMkIdx >= 0) {
            var depth2 = 0
            var endIdx2 = objMkIdx
            var j = objMkIdx
            while (j < objValue.length) {
              objValue(j) match {
                case '(' => depth2 += 1
                case ')' => 
                  depth2 -= 1
                  if (depth2 == 0) { endIdx2 = j + 1; j = objValue.length }
                case _ =>
              }
              j += 1
            }
            if (endIdx2 <= objMkIdx) endIdx2 = objValue.length - 1
            
            val objFieldValues = parseObjectValues(objValue.substring(objMkIdx, endIdx2))
            
            // Generate constraints for each field
            for (((propName, propType), propIdx) <- classProps.zipWithIndex) {
              if (propIdx < objFieldValues.length) {
                val propValue = objFieldValues(propIdx)
                val isPropPrim = isPrimitiveType(propType)
                
                if (isPropPrim) {
                  val formattedValue = formatPrimitiveValue(propValue)
                  constraints += s"req $varName.$propName = $formattedValue"
                }
                // Skip non-primitive nested objects for now
              }
            }
          }
        }
      case None =>
        constraints += s"-- $varName: ref $refNum not found in heap"
    }
  }
  
  /**
   * Process a single K file: parse it, read baseline, generate constraints.
   */
  def processKFile(kFilePath: String): Option[String] = {
    val kFile = new File(kFilePath)
    if (!kFile.exists()) {
      println(s"K file not found: $kFilePath")
      return None
    }
    
    // Find baseline JSON
    val baselineDir = new File(kFile.getParentFile, "baseline")
    val baselineFile = new File(baselineDir, kFile.getName + ".json")
    
    if (!baselineFile.exists()) {
      log(s"No baseline for: $kFilePath")
      return None
    }
    
    // Read baseline JSON
    val jsonStr = Source.fromFile(baselineFile).mkString
    val json = new JSONObject(jsonStr)
    
    val smtModel = if (json.has("smtModel")) json.getString("smtModel") else ""
    if (smtModel.isEmpty || smtModel == "()" || smtModel == "()\n") {
      log(s"No smtModel in baseline for: $kFilePath")
      return None
    }
    
    // Parse K file to get AST
    try {
      // Parse the K file using ANTLR directly (avoid K2Z3 which loads Z3 native lib)
      val source = Source.fromFile(kFile).mkString
      val input = new org.antlr.v4.runtime.ANTLRInputStream(source)
      val lexer = new ModelLexer(input)
      val tokens = new org.antlr.v4.runtime.CommonTokenStream(lexer)
      val parser = new ModelParser(tokens)
      parser.setBuildParseTree(true)
      val tree = parser.model()
      val ksv = new KScalaVisitor()
      val model = ksv.visit(tree).asInstanceOf[Model]
      
      if (model == null) {
        println(s"Failed to parse: $kFilePath")
        return None
      }
      
      // Build class map from Model directly (avoids TypeChecker which may load Z3)
      buildClassMap(model)
      
      // Transform model (using our local version that doesn't need TypeChecker)
      val transformedModel = simpleTransformModel(model)
      
      // Generate K constraints
      val constraints = smtModelToKConstraints(transformedModel, smtModel)
      
      Some(constraints)
    } catch {
      case e: Exception =>
        log(s"Error processing $kFilePath: ${e.getMessage}")
        if (debug) e.printStackTrace()
        None
    }
  }
  
  /**
   * Process a K file and write the .model.k output file.
   */
  def processAndWriteModelK(kFilePath: String): Boolean = {
    val kFile = new File(kFilePath)
    val baselineDir = new File(kFile.getParentFile, "baseline")
    val modelKFile = new File(baselineDir, kFile.getName.stripSuffix(".k") + ".model.k")
    
    processKFile(kFilePath) match {
      case Some(constraints) =>
        val writer = new PrintWriter(modelKFile)
        writer.write(constraints)
        writer.close()
        println(s"Generated: ${modelKFile.getPath}")
        true
      case None =>
        false
    }
  }
  
  /**
   * Process all K files in a directory.
   */
  def processDirectory(dirPath: String): Int = {
    val dir = new File(dirPath)
    if (!dir.exists() || !dir.isDirectory) {
      println(s"Directory not found: $dirPath")
      return 0
    }
    
    var count = 0
    for (file <- dir.listFiles() if file.getName.endsWith(".k")) {
      if (processAndWriteModelK(file.getPath)) {
        count += 1
      }
    }
    count
  }
  
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      println("Usage: SmtModelToK <file.k>")
      println("       SmtModelToK --all-examples")
      println("       SmtModelToK --all-tests")
      println("       SmtModelToK --all")
      println("       SmtModelToK --debug <file.k>")
      return
    }
    
    val argList = args.toList
    debug = argList.contains("--debug")
    val filteredArgs = argList.filterNot(_ == "--debug")
    
    filteredArgs match {
      case List("--all-examples") =>
        val count = processDirectory("src/examples")
        println(s"\nGenerated $count .model.k files in src/examples/baseline/")
        
      case List("--all-tests") =>
        val count = processDirectory("src/tests")
        println(s"\nGenerated $count .model.k files in src/tests/baseline/")
        
      case List("--all") =>
        val count1 = processDirectory("src/examples")
        val count2 = processDirectory("src/tests")
        println(s"\nGenerated ${count1 + count2} .model.k files total")
        
      case List(kFile) if kFile.endsWith(".k") =>
        processKFile(kFile) match {
          case Some(constraints) =>
            println(constraints)
          case None =>
            println("Failed to generate constraints")
        }
        
      case _ =>
        println(s"Unknown arguments: ${filteredArgs.mkString(" ")}")
    }
  }
}
