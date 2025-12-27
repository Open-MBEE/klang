package k.frontend

import scala.collection.mutable.{ListBuffer, StringBuilder, Map => MMap}

/**
 * K to MiniZinc translator.
 *
 * Translates K models to MiniZinc constraint programs for solving with
 * CP solvers like Gecode, OR-Tools, Chuffed, etc.
 *
 * This enables research comparisons between SMT (Z3/CVC5) and CP backends.
 */
object K2MiniZinc {

  // Configuration
  var defaultIntBound: Int = 1000000     // Default bound for unbounded Int
  var defaultStringMaxLen: Int = 50      // Default max string length
  var defaultMaxObjects: Int = 10        // Default max objects per class
  var verbose: Boolean = false

  // State during translation
  private var warnings: ListBuffer[String] = ListBuffer()
  private var stringVars: MMap[String, Int] = MMap()  // name -> maxLength
  private var seqVars: MMap[String, (Type, Int)] = MMap()  // name -> (elemType, maxLength)
  private var classObjects: MMap[String, Int] = MMap()  // className -> objectCount
  private var varTypes: MMap[String, String] = MMap()  // varName -> className (for object refs)
  private var classFields: MMap[String, List[(String, Type)]] = MMap()  // className -> List[(fieldName, fieldType)]
  private var varCounter: Int = 0
  private var functionDefs: MMap[(String, String), FunDecl] = MMap()  // (className, functionName) -> FunDecl

  def reset(): Unit = {
    warnings.clear()
    stringVars.clear()
    seqVars.clear()
    classObjects.clear()
    varTypes.clear()
    classFields.clear()
    functionDefs.clear()
    varCounter = 0
  }

  def freshVar(prefix: String = "tmp"): String = {
    varCounter += 1
    s"${prefix}_$varCounter"
  }

  /**
   * Register a class and its own fields (not inherited).
   */
  private def registerClassFields(ed: EntityDecl): Unit = {
    val className = ed.ident
    val maxObjects = extractMaxObjects(ed).getOrElse(defaultMaxObjects)
    classObjects(className) = maxObjects

    // Register only own fields initially
    val ownFields = ed.members.collect {
      case pd: PropertyDecl => (pd.name, pd.ty.getOrElse(IntType))
    }
    if (!classFields.contains(className)) {
      classFields(className) = ownFields
    }
  }

  /**
   * Resolve inherited fields for a class.
   */
  private def resolveInheritedFields(ed: EntityDecl): Unit = {
    val className = ed.ident

    // Get parent fields
    val parentFields = ed.extending.flatMap {
      case IdentType(QualifiedName(List(parentName)), _) =>
        val fields = classFields.getOrElse(parentName, Nil)
        fields
      case _ => Nil
    }

    // Own fields
    val ownFields = ed.members.collect {
      case pd: PropertyDecl => (pd.name, pd.ty.getOrElse(IntType))
    }

    // Combine: parent fields first, then own fields (own fields can override)
    val combinedFields = parentFields ++ ownFields
    val allFieldNames = combinedFields.map(_._1).distinct
    val allFields = allFieldNames.map { name =>
      // Take the last definition (own overrides parent)
      combinedFields.filter(_._1 == name).last
    }

    classFields(className) = allFields
  }

  /**
   * Main entry point: translate a K model to MiniZinc.
   */
  def translate(model: Model): MiniZincResult = {
    reset()

    val mzn = new StringBuilder()

    // Header
    mzn ++= "% Auto-generated MiniZinc model from K\n"
    mzn ++= s"% Generated: ${java.time.LocalDateTime.now()}\n"
    mzn ++= "\n"

    // First pass: analyze model to determine bounds
    val analysis = analyzeModel(model)

    // Note: We don't include globals.mzn to avoid version compatibility issues
    // Our generated code is self-contained

    // Generate constants for bounds
    mzn ++= "% === Bounds ===\n"
    mzn ++= s"int: MAX_INT = $defaultIntBound;\n"
    mzn ++= s"int: MIN_INT = -$defaultIntBound;\n"
    mzn ++= s"int: MAX_STRING_LEN = $defaultStringMaxLen;\n"
    mzn ++= "\n"

    // Generate string helper predicates if needed
    if (analysis.usesStrings) {
      mzn ++= generateStringHelpers()
    }

    // Collect all declarations
    val allDecls = model.decls ++ model.packages.flatMap(_.model.decls)

    // First pass: register all classes and their fields (needed for inheritance)
    val entityDecls = allDecls.collect { case ed: EntityDecl if ed.keyword != Some("assoc") => ed }
    for (ed <- entityDecls) {
      registerClassFields(ed)
    }

    // Second pass: resolve inheritance (may need multiple iterations for deep hierarchies)
    var changed = true
    var iterations = 0
    while (changed && iterations < 10) {
      changed = false
      iterations += 1
      for (ed <- entityDecls) {
        val oldSize = classFields.getOrElse(ed.ident, Nil).size
        resolveInheritedFields(ed)
        val newSize = classFields.getOrElse(ed.ident, Nil).size
        if (newSize != oldSize) {
          changed = true
          if (verbose) println(s"[MZN] Iteration $iterations: ${ed.ident} fields changed from $oldSize to $newSize")
        }
      }
    }
    if (verbose) {
      println(s"[MZN] Inheritance resolved in $iterations iterations")
      for ((name, fields) <- classFields) {
        println(s"[MZN] Class $name has fields: ${fields.map(_._1).mkString(", ")}")
      }
    }

    // Collect function definitions from all classes (needed for predicate generation)
    for (ed <- entityDecls) {
      for (member <- ed.members) member match {
        case fd: FunDecl =>
          functionDefs((ed.ident, fd.ident)) = fd
        case _ =>
      }
      // Also collect inherited functions
      val allFunDecls = ed.getAllFunDecls
      for (fd <- allFunDecls) {
        functionDefs((ed.ident, fd.ident)) = fd
      }
    }

    // Generate class definitions (as bounded object pools)
    mzn ++= "% === Classes ===\n"
    for (ed <- entityDecls) {
      mzn ++= translateEntityDecl(ed)
    }
    mzn ++= "\n"

    // Generate predicates for temporal operators and other functions
    // NOTE: This must happen AFTER classFields are fully populated
    mzn ++= "% === Predicates (Temporal Operators) ===\n"
    mzn ++= generateFunctionPredicates(entityDecls)
    mzn ++= "\n"

    // Generate variable declarations
    mzn ++= "% === Variables ===\n"
    for (decl <- allDecls) decl match {
      case pd: PropertyDecl =>
        mzn ++= translatePropertyDecl(pd, "TopLevel")
      case _ =>
    }
    // Also handle properties inside classes
    for (decl <- allDecls) decl match {
      case ed: EntityDecl =>
        for (member <- ed.members) member match {
          case pd: PropertyDecl =>
            mzn ++= translatePropertyDecl(pd, ed.ident)
          case _ =>
        }
      case _ =>
    }
    mzn ++= "\n"

    // Generate constraints
    mzn ++= "% === Constraints ===\n"
    for (decl <- allDecls) decl match {
      case cd: ConstraintDecl =>
        mzn ++= translateConstraint(cd, "TopLevel")
      case _ =>
    }
    // Also handle constraints inside classes
    for (decl <- allDecls) decl match {
      case ed: EntityDecl =>
        for (member <- ed.members) member match {
          case cd: ConstraintDecl =>
            mzn ++= translateConstraint(cd, ed.ident)
          case _ =>
        }
      case _ =>
    }
    mzn ++= "\n"

    // Generate optimization objective if present
    var hasOptimize = false
    for (decl <- allDecls) decl match {
      case od: OptimizeDecl =>
        mzn ++= translateOptimize(od, "TopLevel")
        hasOptimize = true
      case _ =>
    }
    // Also inside classes - optimize sum over all live objects
    for (decl <- allDecls) decl match {
      case ed: EntityDecl =>
        for (member <- ed.members) member match {
          case od: OptimizeDecl =>
            mzn ++= translateOptimizeInClass(od, ed.ident)
            hasOptimize = true
          case _ =>
        }
      case _ =>
    }

    // Default solve statement if no optimization
    if (!hasOptimize) {
      mzn ++= "solve satisfy;\n"
    }

    // Output section
    mzn ++= "\n% === Output ===\n"
    mzn ++= generateOutput(allDecls)

    MiniZincResult(mzn.toString(), warnings.toList)
  }

  /**
   * Analyze model to determine bounds and features used.
   */
  private def analyzeModel(model: Model): ModelAnalysis = {
    var usesStrings = false
    var usesReals = false
    var usesClasses = false
    var usesBitvectors = false

    def analyzeType(ty: Type): Unit = ty match {
      case StringType => usesStrings = true
      case RealType => usesReals = true
      case _: BitVecType => usesBitvectors = true
      case _: SignedIntType => usesBitvectors = true
      case _: UnsignedIntType => usesBitvectors = true
      case IdentType(name, args) if !Misc.isCollection(name.names.head) =>
        usesClasses = true
        args.foreach(analyzeType)
      case IdentType(_, args) =>
        args.foreach(analyzeType)
      case CartesianType(types) =>
        types.foreach(analyzeType)
      case FunctionType(from, to) =>
        analyzeType(from)
        analyzeType(to)
      case _ =>
    }

    def analyzeDecl(decl: TopDecl): Unit = decl match {
      case pd: PropertyDecl =>
        pd.ty.foreach(analyzeType)
      case ed: EntityDecl =>
        usesClasses = true
        ed.members.foreach {
          case pd: PropertyDecl => pd.ty.foreach(analyzeType)
          case _ =>
        }
      case _ =>
    }

    model.decls.foreach(analyzeDecl)
    model.packages.foreach(_.model.decls.foreach(analyzeDecl))

    ModelAnalysis(usesStrings, usesReals, usesClasses, usesBitvectors)
  }

  /**
   * Generate MiniZinc helpers for string operations.
   */
  private def generateStringHelpers(): String = {
    val sb = new StringBuilder()
    sb ++= "% === String Helpers ===\n"
    sb ++= "% Strings are encoded as arrays of ASCII codes with a length variable\n"
    sb ++= "\n"

    // String equality predicate
    sb ++= """% String equality
predicate str_eq(array[int] of var int: s1, var int: len1,
                 array[int] of var int: s2, var int: len2) =
  len1 = len2 /\
  forall(i in 1..MAX_STRING_LEN)(i <= len1 -> s1[i] = s2[i]);

"""

    // String prefix predicate
    sb ++= """% String prefix (s1 starts with s2)
predicate str_prefix(array[int] of var int: s1, var int: len1,
                     array[int] of var int: s2, var int: len2) =
  len2 <= len1 /\
  forall(i in 1..MAX_STRING_LEN)(i <= len2 -> s1[i] = s2[i]);

"""

    // String suffix predicate
    sb ++= """% String suffix (s1 ends with s2)
predicate str_suffix(array[int] of var int: s1, var int: len1,
                     array[int] of var int: s2, var int: len2) =
  len2 <= len1 /\
  forall(i in 1..MAX_STRING_LEN)(i <= len2 -> s1[len1 - len2 + i] = s2[i]);

"""

    // String contains predicate
    sb ++= """% String contains (s1 contains s2)
predicate str_contains(array[int] of var int: s1, var int: len1,
                       array[int] of var int: s2, var int: len2) =
  len2 <= len1 /\
  exists(j in 0..MAX_STRING_LEN-1)(
    j + len2 <= len1 /\
    forall(i in 1..MAX_STRING_LEN)(i <= len2 -> s1[j + i] = s2[i])
  );

"""

    // String concatenation predicate
    sb ++= """% String concatenation (result = s1 ++ s2)
predicate str_concat(array[int] of var int: s1, var int: len1,
                     array[int] of var int: s2, var int: len2,
                     array[int] of var int: result, var int: result_len) =
  result_len = len1 + len2 /\
  forall(i in 1..MAX_STRING_LEN)(i <= len1 -> result[i] = s1[i]) /\
  forall(i in 1..MAX_STRING_LEN)(i <= len2 -> result[len1 + i] = s2[i]);

"""

    sb.toString()
  }

  /**
   * Translate an entity (class) declaration to MiniZinc.
   * Uses bounded object pool encoding.
   */
  private def translateEntityDecl(ed: EntityDecl): String = {
    val sb = new StringBuilder()
    val className = ed.ident
    val maxObjects = classObjects(className)

    // Fields are already resolved (including inherited)
    val fieldTypes = classFields(className)

    // Get property initializers (need to track which fields have init expressions)
    val ownFieldsWithInit = ed.members.collect {
      case pd: PropertyDecl => (pd.name, pd.ty.getOrElse(IntType), pd.expr)
    }

    // Get parent initializers too
    val parentInits = ed.extending.flatMap {
      case IdentType(QualifiedName(List(parentName)), _) =>
        // For now, we don't propagate parent initializers - they're handled in parent class
        Nil
      case _ => Nil
    }

    sb ++= s"% Class: $className (max $maxObjects objects)\n"
    if (ed.extending.nonEmpty) {
      sb ++= s"% Extends: ${ed.extending.mkString(", ")}\n"
    }
    sb ++= s"int: MAX_${className} = $maxObjects;\n"
    sb ++= s"set of int: ${className}ID = 1..MAX_${className};\n"
    sb ++= s"int: ${className}_NULL = 0;\n"
    sb ++= s"array[${className}ID] of var bool: ${className}_alive;\n"
    sb ++= "\n"

    // Generate arrays for each field (including inherited)
    for ((fieldName, fieldType) <- fieldTypes) {
      val mznType = typeToMiniZincForField(fieldType, className)
      sb ++= s"array[${className}ID] of $mznType: ${className}_$fieldName;\n"
    }

    // Generate property initializers for own fields that have init expressions
    val propInitializers = ownFieldsWithInit.collect {
      case (name, _, Some(initExp)) => (name, initExp)
    }
    if (propInitializers.nonEmpty) {
      sb ++= s"\n% Property initializers for $className\n"
      for ((fieldName, initExp) <- propInitializers) {
        val initStr = translateClassConstraint(initExp, className)
        sb ++= s"constraint forall(obj in ${className}ID where ${className}_alive[obj])(${className}_$fieldName[obj] = $initStr);\n"
      }
    }

    // Generate class invariants (constraints that apply to all live objects)
    val classConstraints = ed.members.collect { case cd: ConstraintDecl => cd }
    if (classConstraints.nonEmpty) {
      sb ++= s"\n% Class invariants for $className\n"
      for (cd <- classConstraints) {
        val constraintExp = translateClassConstraint(cd.exp, className)
        val comment = cd.name.map(n => s"  % $n").getOrElse("")
        sb ++= s"constraint forall(obj in ${className}ID where ${className}_alive[obj])($constraintExp);$comment\n"
      }
    }

    sb ++= "\n"
    sb.toString()
  }

  /**
   * Convert a type to MiniZinc type for class field arrays.
   */
  private def typeToMiniZincForField(ty: Type, ownerClass: String): String = ty match {
    case BoolType => "var bool"
    case IntType => s"var MIN_INT..MAX_INT"
    case RealType => s"var -1000000.0..1000000.0"  // Bounded float
    case StringType => "var int"  // String fields need special handling
    case IdentType(QualifiedName(List(refClass)), _) if classObjects.contains(refClass) || refClass == ownerClass =>
      s"var 0..MAX_$refClass"  // Reference to another object
    case _ => "var int"
  }

  /**
   * Translate a constraint expression within a class context.
   * Field references become array accesses with 'obj' as the index.
   */
  private def translateClassConstraint(exp: Exp, className: String): String = {
    exp match {
      case IdentExp(name) if classFields.get(className).exists(_.exists(_._1 == name)) =>
        // Field reference within class - access via obj index
        s"${className}_$name[obj]"

      // Handle field access on another field: parent1.age -> Person_age[Family_parent1[obj]]
      case DotExp(IdentExp(fieldName), accessedField)
          if classFields.get(className).exists(_.exists(_._1 == fieldName)) =>
        // Find the type of the field
        classFields.get(className).flatMap(_.find(_._1 == fieldName)) match {
          case Some((_, IdentType(QualifiedName(List(refClassName)), _))) if classObjects.contains(refClassName) =>
            // This is a reference to another class - access its field
            s"${refClassName}_$accessedField[${className}_$fieldName[obj]]"
          case Some((_, StringType)) =>
            // String field - handle string methods
            accessedField match {
              case "length" => s"${className}_${fieldName}_len[obj]"
              case _ => s"${className}_${fieldName}_$accessedField[obj]"
            }
          case _ =>
            warnings += s"Cannot resolve field access $fieldName.$accessedField in class $className"
            s"${className}_${fieldName}_$accessedField[obj]"
        }

      // Handle method calls on fields: text.startsWith(prefix)
      case FunApplExp(DotExp(IdentExp(fieldName), method), args)
          if classFields.get(className).exists(_.exists(_._1 == fieldName)) =>
        val fieldType = classFields(className).find(_._1 == fieldName).map(_._2)
        fieldType match {
          case Some(StringType) =>
            translateStringMethodInClass(fieldName, method, args, className)
          case _ =>
              // Check if this is a temporal operator that we have a predicate for
              // Note: "equal" has a complex body that needs special handling, skip for now
              val temporalOps = Set("meets", "during", "starts", "finishes", "overlaps", "before")
              if (temporalOps.contains(method) && args.length == 1) {
                // Determine the actual type of the field (not the context class)
                // e.g., DSN_Pass_1 is of type DSN_Pass, not Schedule
                // For temporal operators, they're defined in Event, so use Event_meets
                val fieldType = classFields(className).find(_._1 == fieldName).map(_._2)
                val actualClassName = fieldType match {
                  case Some(IdentType(QualifiedName(List(refClass)), _)) if classObjects.contains(refClass) =>
                    // Temporal operators are defined in Event class
                    // If refClass extends Event, use Event_meets (since meets is inherited)
                    "Event"  // Always use Event for temporal operators
                  case _ =>
                    "Event"  // Default to Event for temporal operators
                }
                val argExpr = args.collect { case PositionalArgument(e) => translateClassConstraint(e, className) }.head
                val thisObj = s"${className}_$fieldName[obj]"
                s"${actualClassName}_$method($thisObj, $argExpr)"
            } else {
              // Generic method call on field
              val argExprs = args.collect { case PositionalArgument(e) => translateClassConstraint(e, className) }
              s"${className}_${fieldName}_$method[obj](${argExprs.mkString(", ")})"
            }
        }
      
      // Handle method calls on object references: e1.meets(e2) where e1 is an object ref
      case FunApplExp(DotExp(objExp, method), args) =>
        val temporalOps = Set("meets", "during", "starts", "finishes", "overlaps", "before", "equal")
        if (temporalOps.contains(method) && args.length == 1) {
          // Determine the actual type of the object expression
          // For field access like DSN_Pass_1, we need to find its type
          val actualClassName = objExp match {
            case IdentExp(fieldName) if classFields.get(className).exists(_.exists(_._1 == fieldName)) =>
              // This is a field - get its type
              classFields(className).find(_._1 == fieldName).flatMap(_._2 match {
                case IdentType(QualifiedName(List(refClass)), _) if classObjects.contains(refClass) =>
                  Some(refClass)
                case _ => None
              }).getOrElse(className)
            case _ =>
              className  // Fallback
          }
          val thisObj = translateClassConstraint(objExp, className)
          val argExpr = args.collect { case PositionalArgument(e) => translateClassConstraint(e, className) }.head
          s"${actualClassName}_$method($thisObj, $argExpr)"
        } else {
          // Generic method call - try to expand or use predicate
          val objStr = translateClassConstraint(objExp, className)
          val argExprs = args.collect { case PositionalArgument(e) => translateClassConstraint(e, className) }
          s"${className}_$method($objStr, ${argExprs.mkString(", ")})"
        }

      // Type cast - for MiniZinc, just translate the inner expression
      case TypeCastCheckExp(true, e, _) =>
        translateClassConstraint(e, className)

      case BinExp(e1, op, e2) =>
        val left = translateClassConstraint(e1, className)
        val right = translateClassConstraint(e2, className)
        val opStr = op match {
          case ADD => "+"
          case SUB => "-"
          case MUL => "*"
          case DIV => "div"
          case REM => "mod"
          case LT => "<"
          case LTE => "<="
          case GT => ">"
          case GTE => ">="
          case EQ => "="
          case NEQ => "!="
          case AND => "/\\"
          case OR => "\\/"
          case IMPL => "->"
          case IFF => "<->"
          case _ => op.toString
        }
        s"($left $opStr $right)"

      case UnaryExp(NOT, e) =>
        s"(not ${translateClassConstraint(e, className)})"

      case UnaryExp(NEG, e) =>
        s"(-${translateClassConstraint(e, className)})"

      case IntegerLiteral(v) => v.toString
      case RealLiteral(v) => v.toString
      case BooleanLiteral(b) => if (b) "true" else "false"

      case ParenExp(e) =>
        s"(${translateClassConstraint(e, className)})"

      case _ =>
        // Fall back to regular translation
        translateExp(exp, className)
    }
  }

  /**
   * Translate a property declaration to MiniZinc.
   */
  private def translatePropertyDecl(pd: PropertyDecl, context: String): String = {
    // Skip if this is a class member (already handled in translateEntityDecl)
    if (context != "TopLevel" && classObjects.contains(context)) {
      return ""
    }

    val sb = new StringBuilder()
    val name = pd.name
    val ty = pd.ty.getOrElse(IntType)

    // Check for annotations
    val maxLen = extractMaxStringLength(pd)

    ty match {
      case StringType =>
        val len = maxLen.getOrElse(defaultStringMaxLen)
        stringVars(name) = len
        sb ++= s"% String variable: $name (max length $len)\n"
        sb ++= s"array[1..$len] of var 0..127: ${name}_chars;\n"
        sb ++= s"var 0..$len: ${name}_len;\n"
        sb ++= s"constraint forall(i in 1..$len)(i > ${name}_len -> ${name}_chars[i] = 0);\n"

      case IdentType(QualifiedName(List(collName)), List(elemType)) if Misc.isCollection(collName) =>
        collName match {
          case "Seq" =>
            val maxSeqLen = maxLen.getOrElse(20)
            seqVars(name) = (elemType, maxSeqLen)
            val elemMzn = typeToMiniZinc(elemType, None)
            sb ++= s"% Seq variable: $name\n"
            sb ++= s"array[1..$maxSeqLen] of $elemMzn: ${name}_elems;\n"
            sb ++= s"var 0..$maxSeqLen: ${name}_len;\n"

          case "Set" =>
            val elemMzn = typeToMiniZincSetElem(elemType)
            sb ++= s"% Set variable: $name\n"
            sb ++= s"var set of $elemMzn: $name;\n"

          case _ =>
            warnings += s"Unsupported collection type: $collName"
            sb ++= s"% WARNING: Unsupported collection $collName\n"
        }

      case IdentType(QualifiedName(List(className)), _) if classObjects.contains(className) =>
        // Reference to a class object - track the type
        varTypes(name) = className
        sb ++= s"var 1..MAX_$className: $name;  % Reference to $className (must be valid)\n"
        // Ensure the referenced object is alive
        sb ++= s"constraint ${className}_alive[$name];\n"

      case _ =>
        val mznType = typeToMiniZinc(ty, None)
        sb ++= s"$mznType: $name;\n"
    }

    // Handle initializer if present
    pd.expr.foreach { initExp =>
      sb ++= s"constraint $name = ${translateExp(initExp, context)};\n"
    }

    sb.toString()
  }

  /**
   * Translate a constraint declaration.
   */
  private def translateConstraint(cd: ConstraintDecl, context: String): String = {
    // Skip if this is a class member constraint (already handled in translateEntityDecl)
    if (context != "TopLevel" && classObjects.contains(context)) {
      return ""
    }

    val sb = new StringBuilder()

    if (cd.soft) {
      // Soft constraints need to be reified
      val violationVar = freshVar("soft_violation")
      val comment = cd.name.map(n => s"  % soft: $n").getOrElse("  % soft constraint")
      sb ++= s"var bool: $violationVar = not (${translateExp(cd.exp, context)});$comment\n"
      // The objective should minimize violations - handled separately
    } else {
      val comment = cd.name.map(n => s"  % $n").getOrElse("")
      sb ++= s"constraint ${translateExp(cd.exp, context)};$comment\n"
    }

    sb.toString()
  }

  /**
   * Translate an optimization declaration.
   */
  private def translateOptimize(od: OptimizeDecl, context: String): String = {
    val direction = od.kind match {
      case MinimizeKind => "minimize"
      case MaximizeKind => "maximize"
    }
    val exp = translateExp(od.exp, context)
    s"solve $direction $exp;\n"
  }

  /**
   * Translate an optimization declaration inside a class.
   * Uses sum over all live objects.
   */
  private def translateOptimizeInClass(od: OptimizeDecl, className: String): String = {
    val direction = od.kind match {
      case MinimizeKind => "minimize"
      case MaximizeKind => "maximize"
    }
    // For optimization inside a class, we optimize the sum/first over all live objects
    val expStr = translateClassConstraint(od.exp, className)
    // Use sum for aggregating over all live objects
    s"solve $direction sum(obj in ${className}ID where ${className}_alive[obj])($expStr);\n"
  }

  /**
   * Translate an expression to MiniZinc.
   */
  private def translateExp(exp: Exp, context: String): String = exp match {
    // Literals
    case IntegerLiteral(v) => v.toString
    case RealLiteral(v) => v.toString
    case BooleanLiteral(b) => if (b) "true" else "false"
    case StringLiteral(s) => translateStringLiteral(s)
    case CharacterLiteral(c) => c.toInt.toString
    case NullLiteral => "0"  // Null is 0 in our encoding

    // Variables
    case IdentExp(name) =>
      if (stringVars.contains(name)) {
        // String variables are represented differently
        s"${name}_chars"  // Return the char array; context determines usage
      } else {
        name
      }

    // Binary operations
    case BinExp(e1, op, e2) => translateBinExp(e1, op, e2, context)

    // Unary operations
    case UnaryExp(op, e) => translateUnaryExp(op, e, context)

    // Comparisons
    case e: BinExp => translateBinExp(e.exp1, e.op, e.exp2, context)

    // Quantified expressions
    case QuantifiedExp(quant, bindings, body) =>
      val qStr = quant match {
        case Forall => "forall"
        case Exists => "exists"
      }
      val bindingsStr = bindings.map(translateRngBinding).mkString(", ")
      s"$qStr($bindingsStr)(${translateExp(body, context)})"

    // If-then-else
    case IfExp(cond, thenExp, elseExp) =>
      val condStr = translateExp(cond, context)
      val thenStr = translateExp(thenExp, context)
      val elseStr = elseExp.map(e => translateExp(e, context)).getOrElse("true")
      s"if $condStr then $thenStr else $elseStr endif"

    // Collection literals
    case CollectionEnumExp(kind, exps) =>
      kind match {
        case SetKind =>
          val elemsStr = exps.map(e => translateExp(e, context)).mkString(", ")
          s"{$elemsStr}"
        case SeqKind =>
          val elemsStr = exps.map(e => translateExp(e, context)).mkString(", ")
          s"[$elemsStr]"
        case _ =>
          warnings += s"Unsupported collection kind: $kind"
          "{}"
      }

    // Collection range
    case CollectionRangeExp(kind, e1, e2) =>
      val start = translateExp(e1, context)
      val end = translateExp(e2, context)
      kind match {
        case SetKind => s"$start..$end"
        case SeqKind => s"[$start..$end]"
        case _ => s"$start..$end"
      }

    // Dot expressions (field access)
    case DotExp(obj, field) =>
      translateDotExp(obj, field, context)

    // Function/method application
    case FunApplExp(fun, args) =>
      translateFunAppl(fun, args, context)

    // Type cast
    case TypeCastCheckExp(true, e, ty) =>
      // Cast - just translate the expression, MiniZinc will handle coercion
      translateExp(e, context)

    // Type check
    case TypeCastCheckExp(false, e, ty) =>
      // is-check - for class types, check if reference is valid
      ty match {
        case IdentType(QualifiedName(List(className)), _) if classObjects.contains(className) =>
          val eStr = translateExp(e, context)
          s"($eStr > 0 /\\ ${className}_alive[$eStr])"
        case _ =>
          "true"  // Other type checks not supported
      }

    // Parenthesized expression
    case ParenExp(e) =>
      s"(${translateExp(e, context)})"

    // Tuple
    case TupleExp(exps) =>
      val elemsStr = exps.map(e => translateExp(e, context)).mkString(", ")
      s"($elemsStr)"

    // Lambda - limited support
    case LambdaExp(pat, body) =>
      warnings += "Lambda expressions have limited support in MiniZinc"
      translateExp(body, context)

    // Default fallback
    case _ =>
      warnings += s"Unsupported expression: ${exp.getClass.getSimpleName}"
      "0 /* unsupported */"
  }

  /**
   * Translate a binary expression.
   */
  private def translateBinExp(e1: Exp, op: BinaryOp, e2: Exp, context: String): String = {
    val left = translateExp(e1, context)
    val right = translateExp(e2, context)

    op match {
      // Arithmetic
      case ADD => s"($left + $right)"
      case SUB => s"($left - $right)"
      case MUL => s"($left * $right)"
      case DIV => s"($left div $right)"
      case REM => s"($left mod $right)"

      // Comparison
      case LT => s"($left < $right)"
      case LTE => s"($left <= $right)"
      case GT => s"($left > $right)"
      case GTE => s"($left >= $right)"
      case EQ => translateEquality(e1, e2, context)
      case NEQ => s"(${translateEquality(e1, e2, context)} = false)"

      // Logical
      case AND => s"($left /\\ $right)"
      case OR => s"($left \\/ $right)"
      case IMPL => s"($left -> $right)"
      case IFF => s"($left <-> $right)"

      // Set operations
      case ISIN => s"($left in $right)"
      case NOTISIN => s"(not ($left in $right))"
      case SUBSET => s"($left subset $right)"
      case PSUBSET => s"($left subset $right /\\ $left != $right)"
      case SETUNION => s"($left union $right)"
      case SETINTER => s"($left intersect $right)"
      case SETDIFF => s"($left diff $right)"

      // String concatenation
      case TUPLEINDEX if isStringExp(e1) =>
        // This is actually string concat in K: s ++ t
        translateStringConcat(e1, e2, context)

      case _ =>
        warnings += s"Unsupported binary operator: $op"
        s"($left /* $op */ $right)"
    }
  }

  /**
   * Translate equality, handling special cases like strings.
   */
  private def translateEquality(e1: Exp, e2: Exp, context: String): String = {
    if (isStringExp(e1) || isStringExp(e2)) {
      translateStringEquality(e1, e2, context)
    } else {
      val left = translateExp(e1, context)
      val right = translateExp(e2, context)
      s"($left = $right)"
    }
  }

  /**
   * Translate string equality.
   */
  private def translateStringEquality(e1: Exp, e2: Exp, context: String): String = {
    (e1, e2) match {
      case (IdentExp(name), StringLiteral(s)) if stringVars.contains(name) =>
        // Variable = literal - strip any surrounding quotes from the literal value
        val cleanStr = s.stripPrefix("\"").stripSuffix("\"")
        val chars = cleanStr.getBytes("UTF-8")
        if (chars.isEmpty) {
          s"(${name}_len = 0)"
        } else {
          val constraints = chars.zipWithIndex.map { case (c, i) =>
            s"${name}_chars[${i+1}] = $c"
          }.mkString(" /\\ ")
          s"(${name}_len = ${chars.length} /\\ $constraints)"
        }

      case (StringLiteral(s), IdentExp(name)) if stringVars.contains(name) =>
        translateStringEquality(e2, e1, context)  // Swap

      case (IdentExp(n1), IdentExp(n2)) if stringVars.contains(n1) && stringVars.contains(n2) =>
        s"str_eq(${n1}_chars, ${n1}_len, ${n2}_chars, ${n2}_len)"

      case _ =>
        val left = translateExp(e1, context)
        val right = translateExp(e2, context)
        s"($left = $right)"
    }
  }

  /**
   * Translate string concatenation.
   */
  private def translateStringConcat(e1: Exp, e2: Exp, context: String): String = {
    // For now, generate a constraint using str_concat predicate
    // This is complex because we need to create a result variable
    warnings += "String concatenation requires auxiliary variables - may not work in all contexts"

    (e1, e2) match {
      case (IdentExp(n1), IdentExp(n2)) if stringVars.contains(n1) && stringVars.contains(n2) =>
        val resultName = freshVar("concat_result")
        val maxLen = stringVars(n1) + stringVars(n2)
        // Note: This doesn't actually declare the variable - that's a limitation
        s"str_concat(${n1}_chars, ${n1}_len, ${n2}_chars, ${n2}_len, ${resultName}_chars, ${resultName}_len)"
      case _ =>
        val left = translateExp(e1, context)
        val right = translateExp(e2, context)
        s"($left ++ $right)"
    }
  }

  /**
   * Translate a unary expression.
   */
  private def translateUnaryExp(op: UnaryOp, e: Exp, context: String): String = {
    val operand = translateExp(e, context)
    op match {
      case NOT => s"(not $operand)"
      case NEG => s"(-$operand)"
      case _ =>
        warnings += s"Unsupported unary operator: $op"
        operand
    }
  }

  /**
   * Translate a dot expression (field access).
   */
  private def translateDotExp(obj: Exp, field: String, context: String): String = {
    obj match {
      // String method calls
      case IdentExp(name) if stringVars.contains(name) =>
        field match {
          case "length" => s"${name}_len"
          case _ =>
            warnings += s"Unsupported string method: $field"
            s"${name}_$field"
        }

      // Sequence method calls
      case IdentExp(name) if seqVars.contains(name) =>
        field match {
          case "length" | "size" => s"${name}_len"
          case "head" => s"${name}_elems[1]"
          case "last" => s"${name}_elems[${name}_len]"
          case _ =>
            warnings += s"Unsupported sequence method: $field"
            s"${name}_$field"
        }

      // Object field access - look up the class from varTypes
      case IdentExp(name) if varTypes.contains(name) =>
        val className = varTypes(name)
        s"${className}_$field[$name]"

      // Object field access within a class context
      case IdentExp(name) if classObjects.contains(context) && classFields.get(context).exists(_.exists(_._1 == name)) =>
        // This is a field of the current class being accessed
        s"${context}_$field[$name]"

      // Nested field access: a.b.c
      case DotExp(innerObj, innerField) =>
        // First resolve the inner access
        val innerResult = translateDotExp(innerObj, innerField, context)
        // Now we need to figure out what type innerResult is
        // For now, try to find the class from the innerField
        val possibleClass = classFields.find { case (cls, fields) =>
          fields.exists(_._1 == innerField)
        }
        possibleClass match {
          case Some((className, fields)) =>
            // Find the type of innerField to determine class of result
            fields.find(_._1 == innerField) match {
              case Some((_, IdentType(QualifiedName(List(refClass)), _))) if classObjects.contains(refClass) =>
                s"${refClass}_$field[$innerResult]"
              case _ =>
                s"${className}_$field[$innerResult]"
            }
          case None =>
            warnings += s"Cannot resolve nested field access: $obj.$field"
            s"${context}_$field[$innerResult]"
        }

      case _ =>
        val objStr = translateExp(obj, context)
        warnings += s"Unresolved field access: $obj.$field"
        s"$objStr.$field"
    }
  }

  /**
   * Translate a function application.
   */
  private def translateFunAppl(fun: Exp, args: List[Argument], context: String): String = {
    val argExprs = args.collect { case PositionalArgument(e) => e }

    fun match {
      // String method calls
      case DotExp(IdentExp(strName), method) if stringVars.contains(strName) =>
        translateStringMethod(strName, method, argExprs, context)

      // Sequence method calls
      case DotExp(IdentExp(seqName), method) if seqVars.contains(seqName) =>
        translateSeqMethod(seqName, method, argExprs, context)

      // Built-in functions
      case IdentExp("abs") if argExprs.length == 1 =>
        s"abs(${translateExp(argExprs.head, context)})"

      case IdentExp("min") if argExprs.length == 2 =>
        s"min(${translateExp(argExprs(0), context)}, ${translateExp(argExprs(1), context)})"

      case IdentExp("max") if argExprs.length == 2 =>
        s"max(${translateExp(argExprs(0), context)}, ${translateExp(argExprs(1), context)})"

      // Array/sequence indexing
      case IdentExp(name) if seqVars.contains(name) && argExprs.length == 1 =>
        val idx = translateExp(argExprs.head, context)
        s"${name}_elems[$idx + 1]"  // K uses 0-indexing, MiniZinc uses 1-indexing

      // Generic function call
      case IdentExp(funName) =>
        val argsStr = argExprs.map(e => translateExp(e, context)).mkString(", ")
        s"$funName($argsStr)"

      // Method calls on object references: e1.meets(e2)
      case DotExp(objExp, method) =>
        val temporalOps = Set("meets", "during", "starts", "finishes", "overlaps", "before", "equal")
        if (temporalOps.contains(method) && argExprs.length == 1) {
          // Determine the class name from context or object expression
          val className = determineClassName(objExp, context)
          val thisObj = translateExp(objExp, context)
          val argObj = translateExp(argExprs.head, context)
          s"${className}_$method($thisObj, $argObj)"
        } else {
          // Generic method call
          val objStr = translateExp(objExp, context)
          val argsStr = argExprs.map(e => translateExp(e, context)).mkString(", ")
          s"$objStr.$method($argsStr)"
        }
        
      case _ =>
        val funStr = translateExp(fun, context)
        val argsStr = argExprs.map(e => translateExp(e, context)).mkString(", ")
        s"$funStr($argsStr)"
    }
  }

  /**
   * Determine the class name from an expression (for method calls).
   */
  private def determineClassName(exp: Exp, context: String): String = {
    exp match {
      case IdentExp(name) if varTypes.contains(name) =>
        varTypes(name)
      case IdentExp(name) if classObjects.contains(context) =>
        context
      case DotExp(inner, _) =>
        determineClassName(inner, context)
      case _ =>
        // Default to Event for temporal operators
        "Event"
    }
  }

  /**
   * Translate a string method call.
   */
  private def translateStringMethod(strName: String, method: String, args: List[Exp], context: String): String = {
    method match {
      case "length" =>
        s"${strName}_len"

      case "charAt" if args.length == 1 =>
        val idx = translateExp(args.head, context)
        s"${strName}_chars[$idx + 1]"  // Convert 0-indexed to 1-indexed

      case "startsWith" if args.length == 1 =>
        args.head match {
          case IdentExp(argName) if stringVars.contains(argName) =>
            s"str_prefix(${strName}_chars, ${strName}_len, ${argName}_chars, ${argName}_len)"
          case StringLiteral(s) =>
            val chars = s.getBytes("UTF-8")
            val checks = chars.zipWithIndex.map { case (c, i) =>
              s"${strName}_chars[${i+1}] = $c"
            }.mkString(" /\\ ")
            s"(${strName}_len >= ${chars.length} /\\ $checks)"
          case _ =>
            warnings += "startsWith with non-literal argument not fully supported"
            "true"
        }

      case "endsWith" if args.length == 1 =>
        args.head match {
          case IdentExp(argName) if stringVars.contains(argName) =>
            s"str_suffix(${strName}_chars, ${strName}_len, ${argName}_chars, ${argName}_len)"
          case StringLiteral(s) =>
            val chars = s.getBytes("UTF-8")
            val checks = chars.zipWithIndex.map { case (c, i) =>
              s"${strName}_chars[${strName}_len - ${chars.length} + ${i+1}] = $c"
            }.mkString(" /\\ ")
            s"(${strName}_len >= ${chars.length} /\\ $checks)"
          case _ =>
            warnings += "endsWith with non-literal argument not fully supported"
            "true"
        }

      case "contains" if args.length == 1 =>
        args.head match {
          case IdentExp(argName) if stringVars.contains(argName) =>
            s"str_contains(${strName}_chars, ${strName}_len, ${argName}_chars, ${argName}_len)"
          case StringLiteral(s) =>
            val chars = s.getBytes("UTF-8")
            val len = chars.length
            // Existential search for the substring
            s"exists(j in 0..${strName}_len-$len)(${chars.zipWithIndex.map { case (c, i) => s"${strName}_chars[j+${i+1}] = $c" }.mkString(" /\\ ")})"
          case _ =>
            warnings += "contains with non-literal argument not fully supported"
            "true"
        }

      case "substring" if args.length == 2 =>
        // This is complex - would need to create a result variable
        warnings += "substring operation requires auxiliary variables"
        s"${strName}_chars  % substring not fully supported"

      case "toInt" =>
        // String to int conversion - complex in MiniZinc
        warnings += "toInt operation has limited support"
        s"0  % toInt not fully supported"

      case _ =>
        warnings += s"Unsupported string method: $method"
        s"${strName}_$method"
    }
  }

  /**
   * Translate a string method call within a class context.
   * String fields are stored as ClassName_fieldName_chars[obj] and ClassName_fieldName_len[obj]
   */
  private def translateStringMethodInClass(fieldName: String, method: String, args: List[Argument], className: String): String = {
    val argExprs = args.collect { case PositionalArgument(e) => e }
    val strChars = s"${className}_${fieldName}_chars[obj]"
    val strLen = s"${className}_${fieldName}_len[obj]"

    method match {
      case "length" =>
        strLen

      case "startsWith" if argExprs.length == 1 =>
        argExprs.head match {
          case IdentExp(argField) if classFields.get(className).exists(_.exists(_._1 == argField)) =>
            val argChars = s"${className}_${argField}_chars[obj]"
            val argLen = s"${className}_${argField}_len[obj]"
            s"str_prefix_arrays($strChars, $strLen, $argChars, $argLen)"
          case StringLiteral(s) =>
            val cleanStr = s.stripPrefix("\"").stripSuffix("\"")
            val chars = cleanStr.getBytes("UTF-8")
            if (chars.isEmpty) "true"
            else {
              val checks = chars.zipWithIndex.map { case (c, i) =>
                s"${className}_${fieldName}_chars[obj, ${i+1}] = $c"
              }.mkString(" /\\ ")
              s"($strLen >= ${chars.length} /\\ $checks)"
            }
          case _ =>
            warnings += s"startsWith with unsupported argument in class $className"
            "true"
        }

      case "endsWith" if argExprs.length == 1 =>
        argExprs.head match {
          case IdentExp(argField) if classFields.get(className).exists(_.exists(_._1 == argField)) =>
            val argChars = s"${className}_${argField}_chars[obj]"
            val argLen = s"${className}_${argField}_len[obj]"
            s"str_suffix_arrays($strChars, $strLen, $argChars, $argLen)"
          case StringLiteral(s) =>
            val cleanStr = s.stripPrefix("\"").stripSuffix("\"")
            val chars = cleanStr.getBytes("UTF-8")
            if (chars.isEmpty) "true"
            else {
              val checks = chars.zipWithIndex.map { case (c, i) =>
                s"${className}_${fieldName}_chars[obj, $strLen - ${chars.length} + ${i+1}] = $c"
              }.mkString(" /\\ ")
              s"($strLen >= ${chars.length} /\\ $checks)"
            }
          case _ =>
            warnings += s"endsWith with unsupported argument in class $className"
            "true"
        }

      case "contains" if argExprs.length == 1 =>
        argExprs.head match {
          case IdentExp(argField) if classFields.get(className).exists(_.exists(_._1 == argField)) =>
            val argChars = s"${className}_${argField}_chars[obj]"
            val argLen = s"${className}_${argField}_len[obj]"
            s"str_contains_arrays($strChars, $strLen, $argChars, $argLen)"
          case StringLiteral(s) =>
            val cleanStr = s.stripPrefix("\"").stripSuffix("\"")
            val chars = cleanStr.getBytes("UTF-8")
            if (chars.isEmpty) "true"
            else {
              val len = chars.length
              s"exists(j in 0..$strLen-$len)(${chars.zipWithIndex.map { case (c, i) => s"${className}_${fieldName}_chars[obj, j+${i+1}] = $c" }.mkString(" /\\ ")})"
            }
          case _ =>
            warnings += s"contains with unsupported argument in class $className"
            "true"
        }

      case _ =>
        warnings += s"Unsupported string method in class: $method"
        "true"
    }
  }

  /**
   * Translate a sequence method call.
   */
  private def translateSeqMethod(seqName: String, method: String, args: List[Exp], context: String): String = {
    method match {
      case "length" | "size" =>
        s"${seqName}_len"

      case "head" =>
        s"${seqName}_elems[1]"

      case "last" =>
        s"${seqName}_elems[${seqName}_len]"

      case "at" | "apply" if args.length == 1 =>
        val idx = translateExp(args.head, context)
        s"${seqName}_elems[$idx + 1]"  // K uses 0-indexing, MiniZinc uses 1-indexing

      case "tail" =>
        warnings += "tail operation requires auxiliary variables"
        s"${seqName}_elems  % tail not fully supported"

      case "contains" if args.length == 1 =>
        val elem = translateExp(args.head, context)
        s"exists(i in 1..${seqName}_len)(${seqName}_elems[i] = $elem)"

      case "sum" =>
        s"sum(i in 1..${seqName}_len)(${seqName}_elems[i])"

      case _ =>
        warnings += s"Unsupported sequence method: $method"
        s"${seqName}_$method"
    }
  }

  /**
   * Translate a range binding for quantifiers.
   */
  private def translateRngBinding(binding: RngBinding): String = {
    val names = binding.patterns.collect { case IdentPattern(name) => name }
    val range = binding.collection match {
      case TypeCollection(IdentType(QualifiedName(List("Int")), _)) =>
        "MIN_INT..MAX_INT"
      case TypeCollection(IdentType(QualifiedName(List(className)), _)) if classObjects.contains(className) =>
        s"${className}ID"
      case TypeCollection(ty) =>
        typeToMiniZincDomain(ty)
      case ExpCollection(e) =>
        translateExp(e, "")
    }
    names.map(n => s"$n in $range").mkString(", ")
  }

  /**
   * Convert a K type to MiniZinc type syntax.
   */
  private def typeToMiniZinc(ty: Type, context: Option[String]): String = ty match {
    case BoolType => "var bool"
    case IntType => s"var MIN_INT..MAX_INT"
    case RealType => s"var -1000000.0..1000000.0"  // Bounded float
    case StringType => "var int"  // Strings are complex - handled specially
    case CharType => "var 0..127"

    case IdentType(QualifiedName(List("Set")), List(elemType)) =>
      val elemMzn = typeToMiniZincSetElem(elemType)
      s"var set of $elemMzn"

    case IdentType(QualifiedName(List("Seq")), List(elemType)) =>
      s"var int"  // Sequences need special handling

    case IdentType(QualifiedName(List(className)), _) if classObjects.contains(className) =>
      s"var 0..MAX_$className"

    case SignedIntType(8) => "var -128..127"
    case SignedIntType(16) => "var -32768..32767"
    case SignedIntType(32) => "var -2147483648..2147483647"
    case SignedIntType(64) => "var MIN_INT..MAX_INT"  // Approximation

    case UnsignedIntType(8) => "var 0..255"
    case UnsignedIntType(16) => "var 0..65535"
    case UnsignedIntType(32) => "var 0..4294967295"
    case UnsignedIntType(64) => "var 0..MAX_INT"  // Approximation

    case _ =>
      warnings += s"Unsupported type: $ty"
      "var int"
  }

  /**
   * Convert a type to a MiniZinc set element type.
   */
  private def typeToMiniZincSetElem(ty: Type): String = ty match {
    case IntType => "MIN_INT..MAX_INT"
    case BoolType => "bool"
    case _ => "int"
  }

  /**
   * Convert a type to a MiniZinc domain for quantifiers.
   */
  private def typeToMiniZincDomain(ty: Type): String = ty match {
    case IntType => "MIN_INT..MAX_INT"
    case BoolType => "bool"
    case IdentType(QualifiedName(List(className)), _) if classObjects.contains(className) =>
      s"${className}ID"
    case _ => "int"
  }

  /**
   * Translate a string literal.
   */
  private def translateStringLiteral(s: String): String = {
    // For a string literal, we need to return it in a context-appropriate way
    // Usually this will be part of an equality constraint
    s""""$s""""  // Just quote it - will be handled specially in equality
  }

  /**
   * Check if an expression is a string expression.
   */
  private def isStringExp(exp: Exp): Boolean = exp match {
    case StringLiteral(_) => true
    case IdentExp(name) => stringVars.contains(name)
    case DotExp(e, _) => isStringExp(e)  // String method result
    case _ => false
  }

  /**
   * Extract @maxStringLength annotation value.
   */
  private def extractMaxStringLength(pd: PropertyDecl): Option[Int] = {
    pd.annotations.collectFirst {
      case Annotation("maxStringLength", IntegerLiteral(n)) => n.toInt
    }
  }

  /**
   * Generate MiniZinc predicates for temporal operators and other functions.
   * These are defined as predicates that can be called in constraints.
   */
  private def generateFunctionPredicates(entityDecls: List[EntityDecl]): String = {
    val sb = new StringBuilder()
    // Note: "equal" has a complex body that needs special handling, skip for now
    val temporalOps = Set("meets", "during", "starts", "finishes", "overlaps", "before")
    
    for (ed <- entityDecls) {
      val className = ed.ident
      // Only process functions defined in this class (not inherited ones)
      // Inherited functions will use the parent class predicate
      val ownFunDecls = ed.getFunDecls
      
      for (fd <- ownFunDecls if temporalOps.contains(fd.ident)) {
        // Only generate predicates for functions defined in this class (not inherited)
        // Inherited functions will use the parent class predicate
        val isOwnFunction = ed.getFunDecls.contains(fd)
        if (!isOwnFunction) {
          // Skip - will use parent class predicate
          // But we still need to generate it if parent class doesn't have it
          // For now, generate for all to avoid missing predicates
        }
        
        // Generate predicate for this temporal operator
        // Format: predicate Event_meets(var int: obj1, var int: obj2) = ...;
        val predicateName = s"${className}_${fd.ident}"
        
        // Get function body - should be a single expression
        // Function bodies are stored as List[MemberDecl]
        // For simple functions like "fun meets(e: Event): Bool { t2 = e.t1 }"
        // The body contains a PropertyDecl with assignment=true and expr=e.t1
        // We need to construct: t2 = e.t1 as a BinExp
        val bodyExp = fd.body match {
          case ExpressionDecl(e) :: Nil => 
            Some(e)
          case PropertyDecl(_, name, _, _, Some(_), Some(expr)) :: Nil =>
            // Property assignment: name = expr (e.g., t2 = e.t1)
            // assignment can be Some(true) or Some(false) - both mean assignment
            // Create: this.name = expr
            Some(BinExp(DotExp(ThisLiteral, name), EQ, expr))
          case PropertyDecl(_, name, _, _, None, Some(expr)) :: Nil =>
            // Property with expression but no assignment - might be a return value
            Some(expr)
          case _ =>
            // Always print debug info for temporal operators to understand structure
            if (fd.body.nonEmpty) {
              warnings += s"[DEBUG] Function ${className}.${fd.ident} body structure: ${fd.body.map(_.getClass.getSimpleName).mkString(", ")}"
              fd.body.foreach { m =>
                m match {
                  case pd: PropertyDecl => 
                    warnings += s"  PropertyDecl: name=${pd.name}, assignment=${pd.assignment}, expr=${pd.expr.map(_.getClass.getSimpleName).getOrElse("None")}, expr.isDefined=${pd.expr.isDefined}, assignment.isDefined=${pd.assignment.isDefined}"
                  case ed: ExpressionDecl => 
                    warnings += s"  ExpressionDecl: ${ed.exp.getClass.getSimpleName}"
                  case _ => 
                    warnings += s"  Other: ${m.getClass.getSimpleName} = $m"
                }
              }
            } else {
              warnings += s"[DEBUG] Function ${className}.${fd.ident} has empty body"
            }
            None
        }
        
        bodyExp match {
          case Some(exp) =>
            // Translate the function body, replacing 'this' with obj1 and parameter with obj2
            // For temporal operators: e1.meets(e2) -> Event_meets(obj1, obj2)
            // where obj1 is 'this' and obj2 is the parameter
            val paramName = if (fd.params.nonEmpty) fd.params.head.name else "e"
            
            // Create a context for translating the body
            // We need to map:
            // - 'this' fields -> className_field[obj1]
            // - paramName fields -> className_field[obj2]
            val bodyStr = translateFunctionBody(exp, className, "obj1", paramName, "obj2")
            
            sb ++= s"predicate $predicateName(var int: obj1, var int: obj2) = $bodyStr;\n"
            
          case None =>
            warnings += s"Function ${className}.${fd.ident} has no body, cannot generate predicate"
        }
      }
    }
    
    if (sb.isEmpty) {
      sb ++= "% No temporal operators found\n"
    }
    
    sb.toString()
  }

  /**
   * Translate a function body expression, replacing 'this' references with obj1 and parameter references with obj2.
   */
  private def translateFunctionBody(exp: Exp, className: String, thisObj: String, paramName: String, paramObj: String): String = {
    exp match {
      case BinExp(e1, op, e2) =>
        val left = translateFunctionBody(e1, className, thisObj, paramName, paramObj)
        val right = translateFunctionBody(e2, className, thisObj, paramName, paramObj)
        val opStr = op match {
          case ADD => "+"
          case SUB => "-"
          case MUL => "*"
          case DIV => "div"
          case REM => "mod"
          case LT => "<"
          case LTE => "<="
          case GT => ">"
          case GTE => ">="
          case EQ => "="
          case NEQ => "!="
          case AND => "/\\"
          case OR => "\\/"
          case IMPL => "->"
          case IFF => "<->"
          case _ => op.toString
        }
        s"($left $opStr $right)"
        
      case UnaryExp(NOT, e) =>
        s"(not ${translateFunctionBody(e, className, thisObj, paramName, paramObj)})"
        
      case UnaryExp(NEG, e) =>
        s"(-${translateFunctionBody(e, className, thisObj, paramName, paramObj)})"
        
      // Field access on 'this': t2 -> className_t2[thisObj]
      // In K, bare field names like "t2" are implicitly "this.t2"
      // Check if this identifier is a field in the class
      case IdentExp(name) =>
        // Check if it's the parameter name first
        if (name == paramName) {
          // This is the parameter name itself, not a field access
          paramObj
        } else {
          // For Event class temporal operators, fields are: t1, t2, duration, success
          // Always treat these as fields for Event class
          val commonEventFields = Set("t1", "t2", "duration", "success")
          if (className == "Event" && commonEventFields.contains(name)) {
            s"${className}_$name[$thisObj]"
          } else {
            // Check if it's a field - use simpler lookup
            val fields = classFields.getOrElse(className, Nil)
            val isField = fields.exists(_._1 == name)
            
            if (isField) {
              // Field access on 'this' (implicit)
              s"${className}_$name[$thisObj]"
            } else {
              // Assume it's a field (better than returning "true" which breaks predicates)
              s"${className}_$name[$thisObj]"
            }
          }
        }
        
      // Field access on 'this' (explicit): this.t1 -> className_t1[thisObj]
      case DotExp(ThisLiteral, field) =>
        s"${className}_$field[$thisObj]"
        
      // Field access on parameter: e.t1 -> className_t1[paramObj]
      case DotExp(IdentExp(name), field) if name == paramName =>
        s"${className}_$field[$paramObj]"
        
      // Literals
      case IntegerLiteral(v) => v.toString
      case RealLiteral(v) => v.toString
      case BooleanLiteral(b) => if (b) "true" else "false"
      
      case ParenExp(e) =>
        s"(${translateFunctionBody(e, className, thisObj, paramName, paramObj)})"
        
      case _ =>
        warnings += s"Unsupported expression in function body: ${exp.getClass.getSimpleName}"
        "true"
    }
  }

  /**
   * Extract @maxObjects annotation value from entity.
   */
  private def extractMaxObjects(ed: EntityDecl): Option[Int] = {
    ed.annotations.collectFirst {
      case Annotation("maxObjects", IntegerLiteral(n)) => n.toInt
    }
  }

  /**
   * Generate output section for printing solutions.
   */
  private def generateOutput(decls: Seq[TopDecl]): String = {
    val sb = new StringBuilder()
    sb ++= "output [\n"

    var first = true
    for (decl <- decls) decl match {
      case pd: PropertyDecl =>
        if (!first) sb ++= ",\n"
        first = false
        val name = pd.name
        if (stringVars.contains(name)) {
          // Show string length and first N chars
          sb ++= s"""  "$name = (len=", show(${name}_len), ") chars: ", show([${name}_chars[i] | i in 1..min(${name}_len, 20)]), "\\n""""
        } else if (seqVars.contains(name)) {
          sb ++= s"""  "$name = (len=", show(${name}_len), ") elems: ", show(${name}_elems), "\\n""""
        } else if (varTypes.contains(name)) {
          // Object reference - just show the reference ID
          sb ++= s"""  "$name = ", show($name), "\\n""""
        } else {
          sb ++= s"""  "$name = ", show($name), "\\n""""
        }
      case _ =>
    }

    // Also output class alive arrays for debugging
    if (classObjects.nonEmpty) {
      if (!first) sb ++= ",\n"
      sb ++= """  "\n% Objects alive:\n""""
      for ((className, _) <- classObjects) {
        sb ++= s""",\n  "$className alive: ", show(${className}_alive), "\\n""""
      }
    }

    sb ++= "\n];\n"
    sb.toString()
  }
}

/**
 * Result of MiniZinc translation.
 */
case class MiniZincResult(
  mznCode: String,
  warnings: List[String]
)

/**
 * Analysis results for a model.
 */
case class ModelAnalysis(
  usesStrings: Boolean,
  usesReals: Boolean,
  usesClasses: Boolean,
  usesBitvectors: Boolean
)

