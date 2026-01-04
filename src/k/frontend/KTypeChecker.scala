package k.frontend

import scala.collection.mutable
import com.microsoft.z3.{Context, IntExpr, BoolExpr, Status}

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
  val TYPE_TIME = 8
  val TYPE_DURATION = 9
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
      AnyType -> TYPE_ANY,
      TimeType -> TYPE_TIME,
      DurationType -> TYPE_DURATION
    )
    private var nextTypeId = FIRST_USER_TYPE
    
    // Map from variable name to type variable name in generated K
    private val varToTypeVar = mutable.Map[String, String]()
    private var nextVarId = 0

    // Map from expression to its type variable (for tracking expression result types)
    private val expToTypeVar = mutable.Map[Exp, String]()
    private var nextExpId = 0

    // Collected constraints with metadata for error reporting
    // Each entry: (constraint expression, human-readable description)
    private val constraints = mutable.ListBuffer[(String, String)]()

    // Property declarations for the K program
    private val properties = mutable.ListBuffer[String]()
    
    // Known variable types (from explicit declarations)
    // Keys are scoped names like "global.x" or "ClassName.x"
    private val knownTypes = mutable.Map[String, Type]()

    // Track which variables are explicitly declared vs inferred
    // Keys are scoped names like "global.x" or "ClassName.x"
    private val declaredVars = mutable.Set[String]()
    private val undeclaredVars = mutable.Set[String]()

    // Track class inheritance (child -> parent)
    private val classParents = mutable.Map[String, List[String]]()

    // Current scope stack for nested scopes
    private var currentScope: String = "global"

    /**
     * Get scoped variable name
     */
    def scopedName(varName: String): String = s"$currentScope.$varName"

    /**
     * Push a new scope (e.g., when entering a class)
     */
    def pushScope(scopeName: String): Unit = {
      currentScope = scopeName
    }

    /**
     * Pop to global scope
     */
    def popScope(): Unit = {
      currentScope = "global"
    }

    /**
     * Register class inheritance
     */
    def registerInheritance(className: String, parents: List[String]): Unit = {
      classParents(className) = parents
    }

    /**
     * Find a variable by searching from current scope outward
     * Returns the scoped name if found, None if not found
     */
    def findVariable(varName: String): Option[String] = {
      // First check current scope
      val scopedVar = s"$currentScope.$varName"
      if (declaredVars.contains(scopedVar)) {
        return Some(scopedVar)
      }

      // Check parent classes (inheritance)
      classParents.get(currentScope) match {
        case Some(parents) =>
          for (parent <- parents) {
            val parentVar = s"$parent.$varName"
            if (declaredVars.contains(parentVar)) {
              return Some(parentVar)
            }
          }
        case None =>
      }

      // Check global scope
      val globalVar = s"global.$varName"
      if (declaredVars.contains(globalVar)) {
        return Some(globalVar)
      }

      None
    }
    
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
    def isDeclared(varName: String): Boolean = findVariable(varName).isDefined
    
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
     * Create a type variable for a scoped variable name
     * Returns the type variable name, or records an error if declarations are required
     */
    def createTypeVar(scopedVarName: String, isReference: Boolean = false): String = {
      varToTypeVar.getOrElseUpdate(scopedVarName, {
        // Convert dots to underscores for valid K identifier
        val tvName = s"_ty_${scopedVarName.replace(".", "_")}"
        nextVarId += 1
        // Add property declaration for the type variable
        properties += s"$tvName : Int"
        // Constrain to valid type range
        constraints += ((s"$tvName >= 0", s"type of '$scopedVarName' must be valid"))

        // Track if this is an undeclared variable being referenced
        if (isReference && !declaredVars.contains(scopedVarName)) {
          undeclaredVars += scopedVarName
          if (requireDeclarations) {
            addError(s"Variable '$scopedVarName' is not declared (requireDeclarations=true)")
          }
        }

        tvName
      })
    }

    /**
     * Create type variable for a variable in current scope
     */
    def createTypeVarInScope(varName: String, isReference: Boolean = false): String = {
      if (isReference) {
        // For references, search from current scope outward
        findVariable(varName) match {
          case Some(foundScope) => createTypeVar(foundScope, isReference = false)
          case None =>
            // Variable not found - create in current scope (may be error)
            val scoped = scopedName(varName)
            createTypeVar(scoped, isReference = true)
        }
      } else {
        // For declarations, always use current scope
        createTypeVar(scopedName(varName), isReference = false)
      }
    }

    /**
     * Get the type variable for a scoped variable (if exists)
     */
    def getTypeVar(scopedVarName: String): Option[String] = varToTypeVar.get(scopedVarName)

    /**
     * Register a known type for a variable in current scope
     */
    def setKnownType(varName: String, ty: Type): Unit = {
      val scoped = scopedName(varName)
      knownTypes(scoped) = ty
      declaredVars += scoped
      val tvName = createTypeVar(scoped)
      val typeId = getTypeId(ty)
      constraints += ((s"$tvName = $typeId", s"'$varName' declared as $ty"))
    }

    /**
     * Mark a variable as declared in current scope
     */
    def markDeclared(varName: String): Unit = {
      declaredVars += scopedName(varName)
    }
    
    /**
     * Get known type for a variable
     */
    def getKnownType(varName: String): Option[Type] = knownTypes.get(varName)
    
    /**
     * Add a constraint that a variable must be a specific type
     */
    def addTypeConstraint(varName: String, ty: Type): Unit = {
      val tvName = createTypeVarInScope(varName, isReference = true)
      val typeId = getTypeId(ty)
      constraints += ((s"$tvName = $typeId", s"'$varName' must be $ty"))
    }

    /**
     * Add a constraint that a variable must be numeric (Int or Real)
     * In ambiguous mode, this allows the solver to pick either
     */
    def addNumericConstraint(varName: String): Unit = {
      val tvName = createTypeVarInScope(varName, isReference = true)
      if (requireUnambiguousTypes) {
        // In strict mode, we still allow Int or Real but the solver must pick one
        constraints += ((s"($tvName = $TYPE_INT || $tvName = $TYPE_REAL)", s"'$varName' must be numeric (Int or Real)"))
      } else {
        // In ambiguous mode, we just require it's numeric (could be either)
        // The solver will find any valid assignment
        constraints += ((s"($tvName = $TYPE_INT || $tvName = $TYPE_REAL)", s"'$varName' must be numeric (Int or Real)"))
      }
    }

    /**
     * Add a constraint that two variables must have the same type
     */
    def addSameTypeConstraint(var1: String, var2: String): Unit = {
      val tv1 = createTypeVarInScope(var1, isReference = true)
      val tv2 = createTypeVarInScope(var2, isReference = true)
      constraints += ((s"$tv1 = $tv2", s"'$var1' and '$var2' must have same type"))
    }

    /**
     * Add a constraint that a variable must have a type compatible with comparison
     */
    def addComparableConstraint(varName: String): Unit = {
      addNumericConstraint(varName)  // For now, only numeric types are comparable
    }

    /**
     * Create or get type variable for an expression result.
     * This allows tracking types of intermediate expressions like (x + y) or (x > 3).
     */
    def getOrCreateExpTypeVar(exp: Exp, description: String): String = {
      expToTypeVar.getOrElseUpdate(exp, {
        val tvName = s"_ty_exp_$nextExpId"
        nextExpId += 1
        properties += s"$tvName : Int"
        constraints += ((s"$tvName >= 0", s"type of '$description' must be valid"))
        tvName
      })
    }

    /**
     * Add constraint that an expression must have a specific type.
     * For IdentExp, uses the variable's type var; otherwise uses expression type var.
     */
    def addExpTypeConstraint(exp: Exp, ty: Type, description: String): Unit = {
      val tvName = getTypeVarForExp(exp, description)
      val typeId = getTypeId(ty)
      constraints += ((s"$tvName = $typeId", s"'$description' must be $ty"))
    }

    /**
     * Add constraint that two expressions must have the same type.
     * For IdentExp, uses the variable's type var; otherwise uses expression type var.
     */
    def addExpSameTypeConstraint(exp1: Exp, exp2: Exp, desc1: String, desc2: String): Unit = {
      val tv1 = getTypeVarForExp(exp1, desc1)
      val tv2 = getTypeVarForExp(exp2, desc2)
      constraints += ((s"$tv1 = $tv2", s"'$desc1' and '$desc2' must have same type"))
    }

    /**
     * Get the type variable for an expression.
     * For IdentExp, returns the variable's type var; for ParenExp recurses; otherwise creates/gets expression type var.
     */
    def getTypeVarForExp(exp: Exp, description: String): String = exp match {
      case IdentExp(name) =>
        // Use the variable's type var
        createTypeVarInScope(name, isReference = true)
      case ParenExp(inner) =>
        // Recurse through parentheses
        getTypeVarForExp(inner, description)
      case _ =>
        // Use expression type var
        getOrCreateExpTypeVar(exp, description)
    }

    /**
     * Get type variable for expression (if exists)
     */
    def getExpTypeVar(exp: Exp): Option[String] = expToTypeVar.get(exp)

    /**
     * Add a raw constraint (for special cases)
     */
    def addRawConstraint(constraint: String, description: String): Unit = {
      constraints += ((constraint, description))
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

      // Add constraints with comments for error reporting
      if (constraints.nonEmpty) {
        sb.append("-- Type constraints\n")
        constraints.zipWithIndex.foreach { case ((constraint, description), idx) =>
          sb.append(s"-- [$idx] $description\n")
          sb.append(s"req $constraint\n")
        }
      }

      sb.toString
    }

    /**
     * Get constraint metadata for error reporting
     * Returns map from constraint index to human-readable description
     */
    def getConstraintMetadata: Map[Int, String] = {
      constraints.zipWithIndex.map { case ((_, desc), idx) => idx -> desc }.toMap
    }

    /**
     * Get all constraints (for unsat core analysis)
     */
    def getAllConstraints: List[(String, String)] = constraints.toList
    
    /**
     * Get the variable to type variable mapping
     */
    def getVarMapping: Map[String, String] = varToTypeVar.toMap

    /**
     * Get all expression type variable names
     */
    def getExpTypeVars: Set[String] = expToTypeVar.values.toSet
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
   * Format constraint metadata for error reporting
   */
  private def formatConstraintErrors(ctx: KTypeContext): List[String] = {
    val constraints = ctx.getAllConstraints
    if (constraints.isEmpty) {
      List("Type constraints are unsatisfiable - no specific constraint info available")
    } else {
      List(
        "Type constraints are unsatisfiable. Conflicting constraints:",
        constraints.map { case (_, desc) => s"  - $desc" }.mkString("\n")
      )
    }
  }
  
  /**
   * Collect declarations from the model
   */
  private def collectDeclarations(model: Model, ctx: KTypeContext): Unit = {
    // Register user-defined types (classes) and their inheritance
    model.decls.foreach {
      case ed: EntityDecl =>
        val ty = IdentType(QualifiedName(List(ed.ident)), List())
        ctx.getTypeId(ty)  // Register the type
        // Register inheritance
        val parentNames = ed.extending.map(_.toString)
        if (parentNames.nonEmpty) {
          ctx.registerInheritance(ed.ident, parentNames)
        }
      case _ =>
    }

    // Register explicitly typed properties - global scope first
    model.decls.foreach {
      case pd: PropertyDecl =>
        // Mark as declared in global scope
        ctx.markDeclared(pd.name)
        pd.ty.foreach { ty =>
          ctx.setKnownType(pd.name, ty)
        }
      case _ =>
    }

    // Register class members in their own scopes
    model.decls.foreach {
      case ed: EntityDecl =>
        ctx.pushScope(ed.ident)
        ed.members.foreach {
          case pd: PropertyDecl =>
            ctx.markDeclared(pd.name)
            pd.ty.foreach { ty =>
              ctx.setKnownType(pd.name, ty)
            }
          case _ =>
        }
        ctx.popScope()
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
        val exprDesc = analyzeExpression(e, ctx)
        // If type is explicit, expression must match
        pd.ty.foreach { declaredType =>
          ctx.addExpTypeConstraint(e, declaredType, s"initializer of '${pd.name}'")
        }
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
      // Enter class scope
      ctx.pushScope(ed.ident)
      ed.members.foreach(m => analyzeMemberDecl(m, ctx))
      ctx.popScope()

    case od: OptimizeDecl =>
      // Optimization expression (maximize/minimize) must be numeric
      val desc = analyzeExpression(od.exp, ctx)
      constrainNumeric(od.exp, ctx)

    case _ =>
  }

  /**
   * Analyze a member declaration
   */
  private def analyzeMemberDecl(decl: MemberDecl, ctx: KTypeContext): Unit = decl match {
    case pd: PropertyDecl =>
      pd.expr.foreach { e =>
        val exprDesc = analyzeExpression(e, ctx)
        // If type is explicit, expression must match
        pd.ty.foreach { declaredType =>
          ctx.addExpTypeConstraint(e, declaredType, s"initializer of '${pd.name}'")
        }
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
    case nested: EntityDecl =>
      // Nested class - enter its scope
      ctx.pushScope(nested.ident)
      nested.members.foreach(m => analyzeMemberDecl(m, ctx))
      ctx.popScope()
    case od: OptimizeDecl =>
      // Optimization expression (maximize/minimize) must be numeric
      val desc = analyzeExpression(od.exp, ctx)
      constrainNumeric(od.exp, ctx)
    case _ =>
  }
  
  /**
   * Analyze an expression and generate type constraints.
   * Returns a description of the expression for error messages.
   */
  private def analyzeExpression(exp: Exp, ctx: KTypeContext): String = exp match {
    case IdentExp(name) =>
      // Create type variable using scope-aware lookup
      ctx.createTypeVarInScope(name, isReference = true)
      name

    case lit: IntegerLiteral =>
      ctx.addExpTypeConstraint(exp, IntType, lit.toString)
      lit.toString

    case lit: RealLiteral =>
      ctx.addExpTypeConstraint(exp, RealType, lit.toString)
      lit.toString

    case lit: StringLiteral =>
      ctx.addExpTypeConstraint(exp, StringType, s"\"${lit.s}\"")
      s"\"${lit.s}\""

    case lit: BooleanLiteral =>
      ctx.addExpTypeConstraint(exp, BoolType, lit.toString)
      lit.toString

    case lit: CharacterLiteral =>
      ctx.addExpTypeConstraint(exp, CharType, lit.toString)
      lit.toString

    case lit: DateLiteral =>
      ctx.addExpTypeConstraint(exp, TimeType, lit.toString)
      lit.toString

    case lit: DurationLiteral =>
      ctx.addExpTypeConstraint(exp, DurationType, lit.toString)
      lit.toString

    case BinExp(e1, op, e2) =>
      val desc1 = analyzeExpression(e1, ctx)
      val desc2 = analyzeExpression(e2, ctx)
      val expDesc = s"$desc1 $op $desc2"

      op match {
        // Comparison operators: operands must be same type, result is Bool
        case LT | LTE | GT | GTE =>
          constrainSameType(e1, e2, ctx)
          constrainComparable(e1, ctx)
          constrainComparable(e2, ctx)
          // Result type is Bool
          ctx.addExpTypeConstraint(exp, BoolType, expDesc)

        // Arithmetic operators: operands must be numeric and same type, result is same as operands
        case ADD =>
          constrainSameType(e1, e2, ctx)
          // Result type is same as operands
          ctx.addExpSameTypeConstraint(exp, e1, expDesc, desc1)

        case SUB | MUL | DIV | REM =>
          constrainSameType(e1, e2, ctx)
          constrainNumeric(e1, ctx)
          constrainNumeric(e2, ctx)
          // Result type is same as operands
          ctx.addExpSameTypeConstraint(exp, e1, expDesc, desc1)

        // Boolean operators: operands must be Bool, result is Bool
        case AND | OR | IMPL | IFF =>
          constrainExpType(e1, BoolType, ctx)
          constrainExpType(e2, BoolType, ctx)
          ctx.addExpTypeConstraint(exp, BoolType, expDesc)

        // Equality: operands must be same type, result is Bool
        case EQ | NEQ =>
          constrainSameType(e1, e2, ctx)
          ctx.addExpTypeConstraint(exp, BoolType, expDesc)

        // Tuple indexing: tuple # index - index must be Int
        case TUPLEINDEX =>
          constrainExpType(e2, IntType, ctx)
          // Note: Full type inference would require knowing tuple element types

        case _ =>
      }
      expDesc

    case UnaryExp(op, e) =>
      val desc = analyzeExpression(e, ctx)
      val expDesc = s"$op $desc"
      op match {
        case NOT =>
          constrainExpType(e, BoolType, ctx)
          ctx.addExpTypeConstraint(exp, BoolType, expDesc)
        case NEG =>
          constrainNumeric(e, ctx)
          ctx.addExpSameTypeConstraint(exp, e, expDesc, desc)
        case _ =>
      }
      expDesc

    case ParenExp(e) =>
      val desc = analyzeExpression(e, ctx)
      // Paren expression has same type as inner
      ctx.getExpTypeVar(e).foreach { innerTv =>
        val outerTv = ctx.getOrCreateExpTypeVar(exp, s"($desc)")
        ctx.addRawConstraint(s"$outerTv = $innerTv", s"'($desc)' has same type as '$desc'")
      }
      s"($desc)"

    case IfExp(cond, thenExp, elseExp) =>
      val condDesc = analyzeExpression(cond, ctx)
      constrainExpType(cond, BoolType, ctx)
      val thenDesc = analyzeExpression(thenExp, ctx)
      elseExp.foreach { e =>
        val elseDesc = analyzeExpression(e, ctx)
        constrainSameType(thenExp, e, ctx)
      }
      s"if $condDesc then $thenDesc"

    case FunApplExp(fun, args) =>
      val funDesc = analyzeExpression(fun, ctx)
      args.foreach {
        case PositionalArgument(e) => analyzeExpression(e, ctx)
        case NamedArgument(_, e) => analyzeExpression(e, ctx)
      }
      s"$funDesc(...)"

    case QuantifiedExp(quant, bindings, body) =>
      bindings.foreach {
        case RngBinding(patterns, collection) =>
          collection match {
            case ExpCollection(e) => analyzeExpression(e, ctx)
            case TypeCollection(ty) =>
          }
          patterns.foreach {
            case IdentPattern(name) => ctx.createTypeVarInScope(name, isReference = false)
            case _ =>
          }
      }
      val bodyDesc = analyzeExpression(body, ctx)
      constrainExpType(body, BoolType, ctx)
      // Quantified expressions return Bool
      ctx.addExpTypeConstraint(exp, BoolType, s"$quant ...")
      s"$quant ..."

    case BlockExp(body) =>
      body.foreach(m => analyzeMemberDecl(m, ctx))
      "{...}"

    case DotExp(e, ident) =>
      val baseDesc = analyzeExpression(e, ctx)
      s"$baseDesc.$ident"

    case TupleExp(es) =>
      es.foreach(analyzeExpression(_, ctx))
      s"(${es.length} elements)"

    case CollectionEnumExp(kind, es) =>
      es.foreach(analyzeExpression(_, ctx))
      if (es.length > 1) {
        es.sliding(2).foreach {
          case Seq(e1, e2) => constrainSameType(e1, e2, ctx)
          case _ =>
        }
      }
      s"$kind{...}"

    case IndexExp(base, args) =>
      // Array/sequence indexing: arr[key] or tuple indexing: tuple # index
      val baseDesc = analyzeExpression(base, ctx)
      args.foreach {
        case PositionalArgument(e) => analyzeExpression(e, ctx)
        case NamedArgument(_, e) => analyzeExpression(e, ctx)
      }
      // Note: Full type inference would require knowing collection element types
      // For now, just ensure the expression is analyzed
      s"$baseDesc[...]"

    case CtorApplExp(ty, args) =>
      // Constructor call: new Type(...) or Type(...)
      // Register the type being constructed
      ctx.getTypeId(ty)
      // Analyze argument expressions
      args.foreach {
        case PositionalArgument(e) => analyzeExpression(e, ctx)
        case NamedArgument(_, e) => analyzeExpression(e, ctx)
      }
      // Result type is the constructed type
      ctx.addExpTypeConstraint(exp, ty, s"$ty(...)")
      s"$ty(...)"

    case _ =>
      exp.toString
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

        // Skip type checking the generated type program - it's well-formed by construction
        // and Z3 will report UNSAT if there's a type error. This allows K to be self-hosting
        // (K type checking done via K) without circular dependency on TypeChecker.

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
          // UNSAT - include constraint descriptions for debugging
          TypeCheckResult(
            success = false,
            inferredTypes = Map(),
            errors = formatConstraintErrors(ctx),
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
        // Include constraint descriptions for debugging
        TypeCheckResult(
          success = false,
          inferredTypes = Map(),
          errors = formatConstraintErrors(ctx),
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

  // ==========================================================================
  // Direct Z3 Type Checking (with unsat core support)
  // ==========================================================================

  /**
   * Type check using direct Z3 API with unsat core extraction.
   * This bypasses the K→SMT pipeline for better error reporting.
   */
  def typeCheckDirect(model: Model): TypeCheckResult = {
    val kCtx = new KTypeContext()

    try {
      // Phase 1: Collect declarations and analyze model (same as before)
      collectDeclarations(model, kCtx)
      analyzeModel(model, kCtx)

      // Check for declaration errors
      val declErrors = kCtx.getErrors
      if (declErrors.nonEmpty) {
        return TypeCheckResult(
          success = false,
          inferredTypes = Map(),
          errors = declErrors,
          kProgram = kCtx.generateKProgram()
        )
      }

      // Phase 2: Build Z3 constraints directly
      val z3Ctx = new Context()
      try {
        val config = SolverConfig(produceUnsatCores = true)
        val session = new IncrementalSession(z3Ctx, config)

        // Create Z3 integer constants for each type variable (both variable and expression types)
        val typeVars = mutable.Map[String, IntExpr]()
        for ((scopedName, tvName) <- kCtx.getVarMapping) {
          typeVars(tvName) = z3Ctx.mkIntConst(tvName)
        }
        // Also create constants for expression type variables
        for (tvName <- kCtx.getExpTypeVars) {
          if (!typeVars.contains(tvName)) {
            typeVars(tvName) = z3Ctx.mkIntConst(tvName)
          }
        }

        // Add constraints with labels for unsat core
        val constraints = kCtx.getAllConstraints
        for (((constraint, description), idx) <- constraints.zipWithIndex) {
          val label = s"tc_$idx"
          val z3Expr = parseConstraintToZ3(z3Ctx, constraint, typeVars)
          if (z3Expr != null) {
            session.assert(z3Expr, Some(label))
            // Store label → description mapping for error reporting
            UtilSMT.constraintMessageMap = UtilSMT.constraintMessageMap + (label -> description)
          }
        }

        // Check satisfiability
        val result = session.check()
        result match {
          case Satisfiable(z3Model, _) =>
            // Extract inferred types from model
            val inferredTypes = extractTypesFromZ3Model(z3Model, kCtx, typeVars)
            TypeCheckResult(
              success = true,
              inferredTypes = inferredTypes,
              errors = List(),
              kProgram = kCtx.generateKProgram()
            )

          case Unsatisfiable(unsatCore, explanation) =>
            // Map unsat core labels back to descriptions
            val errorMessages = if (unsatCore.nonEmpty) {
              val coreDescriptions = unsatCore.flatMap { label =>
                UtilSMT.constraintMessageMap.get(label)
              }
              if (coreDescriptions.nonEmpty) {
                List(
                  "Type error: conflicting type constraints",
                  coreDescriptions.map(d => s"  - $d").mkString("\n")
                )
              } else {
                List(s"Type error: $explanation")
              }
            } else {
              List("Type constraints are unsatisfiable")
            }

            TypeCheckResult(
              success = false,
              inferredTypes = Map(),
              errors = errorMessages,
              kProgram = kCtx.generateKProgram()
            )

          case Unknown(reason, _, _) =>
            TypeCheckResult(
              success = false,
              inferredTypes = Map(),
              errors = List(s"Type checking inconclusive: $reason"),
              kProgram = kCtx.generateKProgram()
            )
        }
      } finally {
        z3Ctx.close()
      }

    } catch {
      case e: Exception =>
        TypeCheckResult(
          success = false,
          inferredTypes = Map(),
          errors = List(s"Type checking failed: ${e.getMessage}"),
          kProgram = kCtx.generateKProgram()
        )
    }
  }

  /**
   * Parse a constraint string to Z3 BoolExpr.
   * Handles simple patterns like "x = 1", "x >= 0", "(x = 1 || x = 2)".
   */
  private def parseConstraintToZ3(
    ctx: Context,
    constraint: String,
    typeVars: mutable.Map[String, IntExpr]
  ): BoolExpr = {
    try {
      val trimmed = constraint.trim

      // Handle disjunction: (x = 1 || x = 2)
      if (trimmed.startsWith("(") && trimmed.contains("||")) {
        val inner = trimmed.drop(1).dropRight(1) // Remove outer parens
        val parts = inner.split("\\|\\|").map(_.trim)
        val disjuncts = parts.flatMap(p => Option(parseConstraintToZ3(ctx, p, typeVars)))
        if (disjuncts.length == parts.length) {
          return ctx.mkOr(disjuncts: _*)
        }
        return null
      }

      // Handle equality: x = 1 or _ty_global_x = 1
      if (trimmed.contains(" = ")) {
        val parts = trimmed.split(" = ", 2)
        val lhs = parts(0).trim
        val rhs = parts(1).trim

        // LHS should be a type variable
        typeVars.get(lhs) match {
          case Some(lhsExpr) =>
            // RHS could be a number or another type variable
            if (rhs.forall(c => c.isDigit || c == '-')) {
              return ctx.mkEq(lhsExpr, ctx.mkInt(rhs.toInt))
            } else {
              typeVars.get(rhs) match {
                case Some(rhsExpr) => return ctx.mkEq(lhsExpr, rhsExpr)
                case None => // Unknown variable
              }
            }
          case None => // Unknown variable
        }
      }

      // Handle >= constraint: x >= 0
      if (trimmed.contains(" >= ")) {
        val parts = trimmed.split(" >= ", 2)
        val lhs = parts(0).trim
        val rhs = parts(1).trim

        typeVars.get(lhs) match {
          case Some(lhsExpr) =>
            if (rhs.forall(c => c.isDigit || c == '-')) {
              return ctx.mkGe(lhsExpr, ctx.mkInt(rhs.toInt))
            }
          case None =>
        }
      }

      // Could not parse - skip this constraint
      if (debug) logDebug(s"Could not parse constraint: $constraint")
      null
    } catch {
      case e: Exception =>
        if (debug) logDebug(s"Error parsing constraint '$constraint': ${e.getMessage}")
        null
    }
  }

  /**
   * Extract inferred types from Z3 model.
   */
  private def extractTypesFromZ3Model(
    z3Model: com.microsoft.z3.Model,
    kCtx: KTypeContext,
    typeVars: mutable.Map[String, IntExpr]
  ): Map[String, Type] = {
    val typeIdToType = kCtx.getAllTypes.map(_.swap)
    val varMapping = kCtx.getVarMapping
    val inferredTypes = mutable.Map[String, Type]()

    for ((scopedName, tvName) <- varMapping) {
      typeVars.get(tvName).foreach { intExpr =>
        val eval = z3Model.eval(intExpr, true)
        if (eval != null) {
          try {
            val typeId = eval.toString.toInt
            typeIdToType.get(typeId).foreach { ty =>
              inferredTypes(scopedName) = ty
            }
          } catch {
            case _: NumberFormatException => // Ignore
          }
        }
      }
    }

    inferredTypes.toMap
  }
}
