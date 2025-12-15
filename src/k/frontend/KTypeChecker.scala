package k.frontend

import scala.collection.mutable

/**
 * K-based Type Checker
 *
 * This module encodes type checking as a K constraint satisfaction problem.
 * Instead of using a traditional type checking algorithm implemented in Scala,
 * we generate a K program where:
 * - Type variables are represented as K properties with type TypeId (Int)
 * - Type constraints are K constraints (req statements)
 * - Running the K program solves the type constraints
 * - The solution gives us the inferred types
 *
 * Benefits:
 * - Uses K's constraint solver (Z3) for type inference
 * - Unified approach: types and values can be solved together
 * - More flexible inference than traditional algorithms
 * - Simpler codebase: leverage existing K infrastructure
 *
 * The generated K program uses integer IDs to represent types:
 * - 0 = Bool, 1 = Int, 2 = Real, 3 = String, 4 = Char, 5+ = user-defined types
 *
 * Type Checking Modes (based on two boolean options):
 * 
 * | Mode            | requireDeclarations | requireUnambiguousTypes | Description |
 * |-----------------|---------------------|-------------------------|-------------|
 * | Strict          | true                | true                    | Traditional - all vars must be declared, types fully determined |
 * | InferredDecls   | false               | true                    | Variables can be undeclared but types must be unambiguous |
 * | AmbiguousTypes  | true                | false                   | Declarations required but types can be ambiguous (e.g., x could be Int or Real) |
 * | FullyFlexible   | false               | false                   | No declarations needed, types can be ambiguous |
 */
object KTypeChecker {
  var debug = false
  var silent = false
  
  // Type checking mode options
  var requireDeclarations = false      // If true, all variables must be explicitly declared
  var requireUnambiguousTypes = true   // If true, types must be fully determined (no ambiguity)
  
  def log(msg: String): Unit = if (!silent) println(s"[KTypeChecker] $msg")
  def logDebug(msg: String): Unit = if (debug && !silent) println(s"[KTypeChecker.DEBUG] $msg")
  
  /**
   * Set type checking mode
   */
  def setMode(reqDecl: Boolean, reqUnambiguous: Boolean): Unit = {
    requireDeclarations = reqDecl
    requireUnambiguousTypes = reqUnambiguous
    val modeName = (reqDecl, reqUnambiguous) match {
      case (true, true) => "Strict"
      case (false, true) => "InferredDecls"
      case (true, false) => "AmbiguousTypes"
      case (false, false) => "FullyFlexible"
    }
    logDebug(s"Type checking mode: $modeName (requireDeclarations=$reqDecl, requireUnambiguousTypes=$reqUnambiguous)")
  }
  
  // Type ID constants - primitive types have fixed IDs
  val TYPE_BOOL = 0
  val TYPE_INT = 1
  val TYPE_REAL = 2
  val TYPE_STRING = 3
  val TYPE_CHAR = 4
  val TYPE_UNIT = 5
  val TYPE_ANY = 6
  val TYPE_NUMERIC = 7  // Special: represents "Int or Real" for ambiguous mode
  val FIRST_USER_TYPE = 10  // User-defined types start here
  
  /**
   * Result of type checking via K
   */
  case class TypeCheckResult(
    success: Boolean,
    inferredTypes: Map[String, Type],
    errors: List[String],
    kProgram: String  // The generated K program (for debugging)
  )
  
  /**
   * Context for K type program generation
   */
  class KTypeContext {
    // Map from type to its integer ID
    private val typeToId = mutable.Map[Type, Int](
      BoolType -> TYPE_BOOL,
      IntType -> TYPE_INT,
      RealType -> TYPE_REAL,
      StringType -> TYPE_STRING,
      CharType -> TYPE_CHAR,
      UnitType -> TYPE_UNIT,
      AnyType -> TYPE_ANY
    )
    private var nextTypeId = FIRST_USER_TYPE
    
    // Map from variable name to type variable name in generated K
    private val varToTypeVar = mutable.Map[String, String]()
    private var nextVarId = 0
    
    // Collected constraints (K req statements)
    private val constraints = mutable.ListBuffer[String]()
    
    // Property declarations for the K program
    private val properties = mutable.ListBuffer[String]()
    
    // Known variable types (from explicit declarations)
    private val knownTypes = mutable.Map[String, Type]()
    
    // Track which variables are explicitly declared vs inferred
    private val declaredVars = mutable.Set[String]()
    private val undeclaredVars = mutable.Set[String]()
    
    // Errors accumulated during analysis
    private val errors = mutable.ListBuffer[String]()
    
    /**
     * Get or create a type ID for a type
     */
    def getTypeId(ty: Type): Int = {
      typeToId.getOrElseUpdate(ty, {
        val id = nextTypeId
        nextTypeId += 1
        id
      })
    }
    
    /**
     * Get all registered types
     */
    def getAllTypes: Map[Type, Int] = typeToId.toMap
    
    /**
     * Check if a variable is declared
     */
    def isDeclared(varName: String): Boolean = declaredVars.contains(varName)
    
    /**
     * Get undeclared variables
     */
    def getUndeclaredVars: Set[String] = undeclaredVars.toSet
    
    /**
     * Get accumulated errors
     */
    def getErrors: List[String] = errors.toList
    
    /**
     * Add an error
     */
    def addError(msg: String): Unit = errors += msg
    
    /**
     * Create a type variable for a variable name
     * Returns the type variable name, or records an error if declarations are required
     */
    def createTypeVar(varName: String, isReference: Boolean = false): String = {
      varToTypeVar.getOrElseUpdate(varName, {
        val tvName = s"_ty_$varName"
        nextVarId += 1
        // Add property declaration for the type variable
        properties += s"$tvName : Int"
        // Constrain to valid type range
        constraints += s"$tvName >= 0"
        
        // Track if this is an undeclared variable being referenced
        if (isReference && !declaredVars.contains(varName)) {
          undeclaredVars += varName
          if (requireDeclarations) {
            addError(s"Variable '$varName' is not declared (requireDeclarations=true)")
          }
        }
        
        tvName
      })
    }
    
    /**
     * Get the type variable for a variable (if exists)
     */
    def getTypeVar(varName: String): Option[String] = varToTypeVar.get(varName)
    
    /**
     * Register a known type for a variable (from explicit declaration)
     */
    def setKnownType(varName: String, ty: Type): Unit = {
      knownTypes(varName) = ty
      declaredVars += varName
      val tvName = createTypeVar(varName)
      val typeId = getTypeId(ty)
      constraints += s"$tvName = $typeId"
    }
    
    /**
     * Mark a variable as declared (even without explicit type)
     */
    def markDeclared(varName: String): Unit = {
      declaredVars += varName
    }
    
    /**
     * Get known type for a variable
     */
    def getKnownType(varName: String): Option[Type] = knownTypes.get(varName)
    
    /**
     * Add a constraint that a variable must be a specific type
     */
    def addTypeConstraint(varName: String, ty: Type): Unit = {
      val tvName = createTypeVar(varName)
      val typeId = getTypeId(ty)
      constraints += s"$tvName = $typeId"
    }
    
    /**
     * Add a constraint that a variable must be numeric (Int or Real)
     * In ambiguous mode, this allows the solver to pick either
     */
    def addNumericConstraint(varName: String): Unit = {
      val tvName = createTypeVar(varName)
      if (requireUnambiguousTypes) {
        // In strict mode, we still allow Int or Real but the solver must pick one
        constraints += s"($tvName = $TYPE_INT || $tvName = $TYPE_REAL)"
      } else {
        // In ambiguous mode, we just require it's numeric (could be either)
        // The solver will find any valid assignment
        constraints += s"($tvName = $TYPE_INT || $tvName = $TYPE_REAL)"
      }
    }
    
    /**
     * Add a constraint that two variables must have the same type
     */
    def addSameTypeConstraint(var1: String, var2: String): Unit = {
      val tv1 = createTypeVar(var1)
      val tv2 = createTypeVar(var2)
      constraints += s"$tv1 = $tv2"
    }
    
    /**
     * Add a constraint that a variable must have a type compatible with comparison
     */
    def addComparableConstraint(varName: String): Unit = {
      addNumericConstraint(varName)  // For now, only numeric types are comparable
    }
    
    /**
     * Generate the K program source
     */
    def generateKProgram(): String = {
      val sb = new StringBuilder
      sb.append("-- Auto-generated K program for type checking\n")
      sb.append("-- Type IDs: Bool=0, Int=1, Real=2, String=3, Char=4, Unit=5, Any=6\n\n")
      
      // Add type constants as comments for readability
      sb.append("-- Type constants\n")
      sb.append(s"TYPE_BOOL : Int = $TYPE_BOOL\n")
      sb.append(s"TYPE_INT : Int = $TYPE_INT\n")
      sb.append(s"TYPE_REAL : Int = $TYPE_REAL\n")
      sb.append(s"TYPE_STRING : Int = $TYPE_STRING\n")
      sb.append(s"TYPE_CHAR : Int = $TYPE_CHAR\n")
      sb.append(s"TYPE_UNIT : Int = $TYPE_UNIT\n")
      sb.append(s"TYPE_ANY : Int = $TYPE_ANY\n\n")
      
      // Add user-defined type constants
      val userTypes = typeToId.filter(_._2 >= FIRST_USER_TYPE)
      if (userTypes.nonEmpty) {
        sb.append("-- User-defined type IDs\n")
        userTypes.foreach { case (ty, id) =>
          sb.append(s"TYPE_${ty.toString.toUpperCase.replaceAll("[^A-Z0-9]", "_")} : Int = $id\n")
        }
        sb.append("\n")
      }
      
      // Add type variable properties
      if (properties.nonEmpty) {
        sb.append("-- Type variables\n")
        properties.foreach { p =>
          sb.append(s"$p\n")
        }
        sb.append("\n")
      }
      
      // Add constraints
      if (constraints.nonEmpty) {
        sb.append("-- Type constraints\n")
        constraints.foreach { c =>
          sb.append(s"req $c\n")
        }
      }
      
      sb.toString
    }
    
    /**
     * Get the variable to type variable mapping
     */
    def getVarMapping: Map[String, String] = varToTypeVar.toMap
  }
  
  /**
   * Main entry point: type check a K model using K constraints
   */
  def typeCheck(model: Model): TypeCheckResult = {
    val ctx = new KTypeContext()
    
    try {
      // Phase 1: Collect all declarations and their explicit types
      collectDeclarations(model, ctx)
      
      // Phase 2: Analyze expressions and generate type constraints
      analyzeModel(model, ctx)
      
      // Check for errors from requireDeclarations mode
      val declErrors = ctx.getErrors
      if (declErrors.nonEmpty) {
        return TypeCheckResult(
          success = false,
          inferredTypes = Map(),
          errors = declErrors,
          kProgram = ctx.generateKProgram()
        )
      }
      
      // Log undeclared variables if any (informational in non-strict mode)
      val undeclared = ctx.getUndeclaredVars
      if (undeclared.nonEmpty && !requireDeclarations) {
        logDebug(s"Undeclared variables (will be inferred): ${undeclared.mkString(", ")}")
      }
      
      // Phase 3: Generate K program
      val kProgram = ctx.generateKProgram()
      logDebug(s"Generated K program:\n$kProgram")
      
      // Phase 4: Run K to solve constraints
      val result = runKTypeCheck(kProgram, ctx)
      
      result.copy(kProgram = kProgram)
      
    } catch {
      case e: Exception =>
        TypeCheckResult(
          success = false,
          inferredTypes = Map(),
          errors = List(s"Type checking failed: ${e.getMessage}"),
          kProgram = ctx.generateKProgram()
        )
    }
  }
  
  /**
   * Collect declarations from the model
   */
  private def collectDeclarations(model: Model, ctx: KTypeContext): Unit = {
    // Register user-defined types (classes)
    model.decls.foreach {
      case ed: EntityDecl =>
        val ty = IdentType(QualifiedName(List(ed.ident)), List())
        ctx.getTypeId(ty)  // Register the type
      case _ =>
    }
    
    // Register explicitly typed properties and mark all properties as declared
    model.decls.foreach {
      case pd: PropertyDecl =>
        // Mark as declared (even without explicit type - it's a property declaration)
        ctx.markDeclared(pd.name)
        pd.ty.foreach { ty =>
          ctx.setKnownType(pd.name, ty)
        }
      case ed: EntityDecl =>
        ed.members.foreach {
          case pd: PropertyDecl =>
            ctx.markDeclared(pd.name)
            pd.ty.foreach { ty =>
              ctx.setKnownType(pd.name, ty)
            }
          case _ =>
        }
      case _ =>
    }
    
    // Process packages recursively
    model.packages.foreach { pkg =>
      collectDeclarations(pkg.model, ctx)
    }
  }
  
  /**
   * Analyze model and generate type constraints
   */
  private def analyzeModel(model: Model, ctx: KTypeContext): Unit = {
    model.decls.foreach(d => analyzeDecl(d, ctx))
    model.packages.foreach { pkg =>
      analyzeModel(pkg.model, ctx)
    }
  }
  
  /**
   * Analyze a declaration
   */
  private def analyzeDecl(decl: TopDecl, ctx: KTypeContext): Unit = decl match {
    case pd: PropertyDecl =>
      // If property has initialization, analyze it
      pd.expr.foreach { e =>
        analyzeExpression(e, ctx)
        // If type is not explicit, infer from expression
        if (pd.ty.isEmpty) {
          inferTypeFromExp(pd.name, e, ctx)
        }
      }
      
    case cd: ConstraintDecl =>
      analyzeExpression(cd.exp, ctx)
      // Constraint expressions should be Bool
      constrainExpType(cd.exp, BoolType, ctx)
      
    case ed: ExpressionDecl =>
      analyzeExpression(ed.exp, ctx)
      
    case fd: FunDecl =>
      // Register parameter types
      fd.params.foreach { p =>
        ctx.setKnownType(p.name, p.ty)
      }
      // Analyze function body
      fd.body.foreach(m => analyzeMemberDecl(m, ctx))
      
    case ed: EntityDecl =>
      ed.members.foreach(m => analyzeMemberDecl(m, ctx))
      
    case _ =>
  }
  
  /**
   * Analyze a member declaration
   */
  private def analyzeMemberDecl(decl: MemberDecl, ctx: KTypeContext): Unit = decl match {
    case pd: PropertyDecl =>
      pd.expr.foreach { e =>
        analyzeExpression(e, ctx)
        if (pd.ty.isEmpty) {
          inferTypeFromExp(pd.name, e, ctx)
        }
      }
    case cd: ConstraintDecl =>
      analyzeExpression(cd.exp, ctx)
      constrainExpType(cd.exp, BoolType, ctx)
    case ed: ExpressionDecl =>
      analyzeExpression(ed.exp, ctx)
    case fd: FunDecl =>
      fd.params.foreach { p =>
        ctx.setKnownType(p.name, p.ty)
      }
      fd.body.foreach(m => analyzeMemberDecl(m, ctx))
    case _ =>
  }
  
  /**
   * Analyze an expression and generate type constraints
   */
  private def analyzeExpression(exp: Exp, ctx: KTypeContext): Unit = exp match {
    case IdentExp(name) =>
      // Create type variable and mark as referenced (to detect undeclared variables)
      ctx.createTypeVar(name, isReference = true)
      
    case BinExp(e1, op, e2) =>
      analyzeExpression(e1, ctx)
      analyzeExpression(e2, ctx)
      
      op match {
        // Comparison operators: operands must be same type and comparable
        case LT | LTE | GT | GTE =>
          constrainSameType(e1, e2, ctx)
          constrainComparable(e1, ctx)
          constrainComparable(e2, ctx)
          
        // Arithmetic operators: operands must be numeric and same type (unless string concat)
        case ADD =>
          // ADD can be either numeric addition or string concatenation
          // We need to handle this specially - if either operand is known to be String,
          // treat as concatenation; otherwise assume numeric
          // For now, just require same type (could be Int, Real, or String)
          constrainSameType(e1, e2, ctx)
          // Note: We don't constrain numeric here to allow string concatenation
          
        case SUB | MUL | DIV | REM =>
          constrainSameType(e1, e2, ctx)
          constrainNumeric(e1, ctx)
          constrainNumeric(e2, ctx)
          
        // Boolean operators: operands must be Bool
        case AND | OR | IMPL | IFF =>
          constrainExpType(e1, BoolType, ctx)
          constrainExpType(e2, BoolType, ctx)
          
        // Equality: operands must be same type
        case EQ | NEQ =>
          constrainSameType(e1, e2, ctx)
          
        case _ =>
      }
      
    case UnaryExp(op, e) =>
      analyzeExpression(e, ctx)
      op match {
        case NOT => constrainExpType(e, BoolType, ctx)
        case NEG => constrainNumeric(e, ctx)
        case _ =>
      }
      
    case ParenExp(e) =>
      analyzeExpression(e, ctx)
      
    case IfExp(cond, thenExp, elseExp) =>
      analyzeExpression(cond, ctx)
      constrainExpType(cond, BoolType, ctx)
      analyzeExpression(thenExp, ctx)
      elseExp.foreach { e =>
        analyzeExpression(e, ctx)
        constrainSameType(thenExp, e, ctx)
      }
      
    case FunApplExp(fun, args) =>
      analyzeExpression(fun, ctx)
      args.foreach {
        case PositionalArgument(e) => analyzeExpression(e, ctx)
        case NamedArgument(_, e) => analyzeExpression(e, ctx)
      }
      
    case QuantifiedExp(_, bindings, body) =>
      bindings.foreach {
        case RngBinding(patterns, collection) =>
          // Collection can be ExpCollection wrapping an expression
          collection match {
            case ExpCollection(e) => analyzeExpression(e, ctx)
            case TypeCollection(ty) => // Type collection, no expression to analyze
          }
          patterns.foreach {
            case IdentPattern(name) => ctx.createTypeVar(name)
            case _ =>
          }
      }
      analyzeExpression(body, ctx)
      constrainExpType(body, BoolType, ctx)
      
    case BlockExp(body) =>
      body.foreach(m => analyzeMemberDecl(m, ctx))
      
    case DotExp(e, _) =>
      analyzeExpression(e, ctx)
      
    case TupleExp(es) =>
      es.foreach(analyzeExpression(_, ctx))
      
    case CollectionEnumExp(_, es) =>
      es.foreach(analyzeExpression(_, ctx))
      // All elements should have same type
      if (es.length > 1) {
        es.sliding(2).foreach {
          case Seq(e1, e2) => constrainSameType(e1, e2, ctx)
          case _ =>
        }
      }
      
    case _ => // Literals and other expressions don't need constraints
  }
  
  /**
   * Infer type of variable from an expression
   */
  private def inferTypeFromExp(varName: String, exp: Exp, ctx: KTypeContext): Unit = exp match {
    case IntegerLiteral(_) => ctx.addTypeConstraint(varName, IntType)
    case RealLiteral(_) => ctx.addTypeConstraint(varName, RealType)
    case StringLiteral(_) => ctx.addTypeConstraint(varName, StringType)
    case BooleanLiteral(_) => ctx.addTypeConstraint(varName, BoolType)
    case CharacterLiteral(_) => ctx.addTypeConstraint(varName, CharType)
    case IdentExp(name) => ctx.addSameTypeConstraint(varName, name)
    case BinExp(e1, op, _) =>
      op match {
        case LT | LTE | GT | GTE | AND | OR | IMPL | IFF | EQ | NEQ =>
          ctx.addTypeConstraint(varName, BoolType)
        case ADD | SUB | MUL | DIV | REM =>
          // Result type is same as operand type
          e1 match {
            case IdentExp(n) => ctx.addSameTypeConstraint(varName, n)
            case IntegerLiteral(_) => ctx.addTypeConstraint(varName, IntType)
            case RealLiteral(_) => ctx.addTypeConstraint(varName, RealType)
            case _ => ctx.addNumericConstraint(varName)
          }
        case _ =>
      }
    case UnaryExp(NOT, _) => ctx.addTypeConstraint(varName, BoolType)
    case UnaryExp(NEG, _) => ctx.addNumericConstraint(varName)
    case ParenExp(e) => inferTypeFromExp(varName, e, ctx)
    case _ =>
  }
  
  /**
   * Add constraint that expression must be a specific type
   */
  private def constrainExpType(exp: Exp, ty: Type, ctx: KTypeContext): Unit = exp match {
    case IdentExp(name) => ctx.addTypeConstraint(name, ty)
    case ParenExp(e) => constrainExpType(e, ty, ctx)
    case _ => // Literals are already typed
  }
  
  /**
   * Add constraint that expression must be numeric
   */
  private def constrainNumeric(exp: Exp, ctx: KTypeContext): Unit = exp match {
    case IdentExp(name) => ctx.addNumericConstraint(name)
    case ParenExp(e) => constrainNumeric(e, ctx)
    case _ => // Literals are already typed
  }
  
  /**
   * Add constraint that expression must be comparable
   */
  private def constrainComparable(exp: Exp, ctx: KTypeContext): Unit = exp match {
    case IdentExp(name) => ctx.addComparableConstraint(name)
    case ParenExp(e) => constrainComparable(e, ctx)
    case _ =>
  }
  
  /**
   * Add constraint that two expressions must have same type
   */
  private def constrainSameType(e1: Exp, e2: Exp, ctx: KTypeContext): Unit = (e1, e2) match {
    case (IdentExp(n1), IdentExp(n2)) => ctx.addSameTypeConstraint(n1, n2)
    case (IdentExp(n), lit) if isLiteral(lit) => ctx.addTypeConstraint(n, getLiteralType(lit))
    case (lit, IdentExp(n)) if isLiteral(lit) => ctx.addTypeConstraint(n, getLiteralType(lit))
    case (ParenExp(e), other) => constrainSameType(e, other, ctx)
    case (other, ParenExp(e)) => constrainSameType(other, e, ctx)
    case _ =>
  }
  
  private def isLiteral(e: Exp): Boolean = e match {
    case IntegerLiteral(_) | RealLiteral(_) | StringLiteral(_) |
         BooleanLiteral(_) | CharacterLiteral(_) => true
    case _ => false
  }
  
  private def getLiteralType(e: Exp): Type = e match {
    case IntegerLiteral(_) => IntType
    case RealLiteral(_) => RealType
    case StringLiteral(_) => StringType
    case BooleanLiteral(_) => BoolType
    case CharacterLiteral(_) => CharType
    case _ => AnyType
  }
  
  /**
   * Run K to solve type constraints (in-process, no subprocess)
   */
  private def runKTypeCheck(kProgram: String, ctx: KTypeContext): TypeCheckResult = {
    try {
      // Save current state to restore later
      val savedSilent = TypeChecker.silent
      val savedK2Z3Silent = K2Z3.silent
      
      // Suppress output during type checking
      TypeChecker.silent = true
      K2Z3.silent = true
      
      try {
        // Reset state for fresh type checking
        TypeChecker.reset()
        UtilSMT.reset
        K2Z3.reset()
        
        // Parse the generated K type program
        val typeModel = Frontend.getModelFromString(kProgram)
        
        if (typeModel == null) {
          return TypeCheckResult(
            success = false,
            inferredTypes = Map(),
            errors = List("Failed to parse type checking K program"),
            kProgram = kProgram
          )
        }
        
        // Type check the type program (using traditional type checker)
        val tc = new TypeChecker(typeModel)
        tc.smtCheck
        
        // Generate SMT and solve
        val smtModel = typeModel.toSMT
        K2Z3.solveSMT(typeModel, smtModel, printModel = false)
        
        // Extract results
        val allValues = K2Z3.getAllVariableValues()
        val inferredTypes = parseKResultFromValues(allValues, ctx)
        
        // Check if satisfiable by looking at z3Model
        if (K2Z3.z3Model != null) {
          TypeCheckResult(
            success = true,
            inferredTypes = inferredTypes,
            errors = List(),
            kProgram = kProgram
          )
        } else {
          TypeCheckResult(
            success = false,
            inferredTypes = Map(),
            errors = List("Type constraints are unsatisfiable - type error in program"),
            kProgram = kProgram
          )
        }
      } finally {
        // Restore state
        TypeChecker.silent = savedSilent
        K2Z3.silent = savedK2Z3Silent
      }
    } catch {
      case e: TypeCheckException.type =>
        TypeCheckResult(
          success = false,
          inferredTypes = Map(),
          errors = List("Type checking of type constraints failed"),
          kProgram = kProgram
        )
      case e: K2Z3Exception.type =>
        // UNSAT or solver error - likely a type error
        TypeCheckResult(
          success = false,
          inferredTypes = Map(),
          errors = List("Type constraints are unsatisfiable - type error in program"),
          kProgram = kProgram
        )
      case e: Exception =>
        TypeCheckResult(
          success = false,
          inferredTypes = Map(),
          errors = List(s"Type checking failed: ${e.getMessage}"),
          kProgram = kProgram
        )
    }
  }
  
  /**
   * Parse type results from K2Z3 variable values
   */
  private def parseKResultFromValues(values: Map[String, String], ctx: KTypeContext): Map[String, Type] = {
    val typeIdToType = ctx.getAllTypes.map(_.swap)
    val varMapping = ctx.getVarMapping
    val inferredTypes = mutable.Map[String, Type]()
    
    values.foreach { case (varName, value) =>
      if (varName.startsWith("_ty_")) {
        try {
          val typeId = value.toInt
          varMapping.find(_._2 == varName).foreach { case (origName, _) =>
            typeIdToType.get(typeId).foreach { ty =>
              inferredTypes(origName) = ty
            }
          }
        } catch {
          case _: NumberFormatException => // Ignore non-integer values
        }
      }
    }
    
    inferredTypes.toMap
  }
  
  /**
   * Generate K program for type checking (for external use)
   */
  def generateTypeCheckProgram(model: Model): String = {
    val ctx = new KTypeContext()
    collectDeclarations(model, ctx)
    analyzeModel(model, ctx)
    ctx.generateKProgram()
  }
}
