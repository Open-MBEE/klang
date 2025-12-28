package k.frontend

import scala.collection.mutable

/**
 * Properties of a K model that affect solving strategy selection.
 */
case class ProblemProperties(
  // Heap properties
  hasDynamicHeap: Boolean,              // Has recursive types or collections needing dynamic allocation
  dynamicClasses: Set[String],          // Classes that need dynamic bounds
  fixedObjectCount: Int,                // Number of pre-declared object instances
  
  // Structure properties
  hasScenarios: Boolean,                // Has top-level disjunctive structure
  scenarioCount: Int,                   // Number of detected scenarios
  
  // Complexity metrics
  classCount: Int,                      // Number of class declarations
  constraintCount: Int,                 // Number of constraint declarations
  propertyCount: Int,                   // Number of property declarations
  functionCount: Int,                   // Number of function declarations
  
  // Special features
  hasExternalFunctions: Boolean,        // Has opaque/external function calls
  hasCollections: Boolean,              // Uses Set, List, Seq types
  hasQuantifiers: Boolean,              // Uses forall/exists
  hasRecursion: Boolean,                // Has recursive type references
  
  // Estimated complexity (heuristic)
  estimatedComplexity: ComplexityLevel
)

sealed trait ComplexityLevel
object ComplexityLevel {
  case object Simple extends ComplexityLevel      // < 10 constraints, no dynamic heap
  case object Medium extends ComplexityLevel      // 10-50 constraints or dynamic heap
  case object Complex extends ComplexityLevel     // > 50 constraints or scenarios
  case object VeryComplex extends ComplexityLevel // > 100 constraints with scenarios
}

/**
 * Analyzes a K model to determine its properties for strategy selection.
 */
object ProblemAnalyzer {
  
  /**
   * Analyze a model and return its properties.
   */
  def analyze(model: Model): ProblemProperties = {
    val classes = collectClasses(model)
    val constraints = collectConstraints(model)
    val properties = collectProperties(model)
    val functions = collectFunctions(model)
    
    // Detect dynamic classes (recursive references, collections)
    val dynamicClasses = detectDynamicClasses(model, classes)
    val hasDynamicHeap = dynamicClasses.nonEmpty
    
    // Detect scenarios (top-level disjunctions)
    val scenarios = detectScenarios(model)
    val hasScenarios = scenarios.nonEmpty
    
    // Detect special features
    val hasExternalFunctions = detectExternalFunctions(model, functions)
    val hasCollections = detectCollections(model)
    val hasQuantifiers = detectQuantifiers(model)
    val hasRecursion = dynamicClasses.nonEmpty
    
    // Count fixed object instances
    val fixedObjectCount = countFixedObjects(model, classes)
    
    // Estimate complexity
    val complexity = estimateComplexity(
      constraintCount = constraints.size,
      hasDynamicHeap = hasDynamicHeap,
      hasScenarios = hasScenarios,
      hasQuantifiers = hasQuantifiers
    )
    
    ProblemProperties(
      hasDynamicHeap = hasDynamicHeap,
      dynamicClasses = dynamicClasses,
      fixedObjectCount = fixedObjectCount,
      hasScenarios = hasScenarios,
      scenarioCount = scenarios.size,
      classCount = classes.size,
      constraintCount = constraints.size,
      propertyCount = properties.size,
      functionCount = functions.size,
      hasExternalFunctions = hasExternalFunctions,
      hasCollections = hasCollections,
      hasQuantifiers = hasQuantifiers,
      hasRecursion = hasRecursion,
      estimatedComplexity = complexity
    )
  }
  
  /**
   * Select optimal configuration based on problem properties.
   */
  def selectConfig(props: ProblemProperties, userConfig: SolveConfig): SolveConfig = {
    var config = userConfig
    
    // Auto-select heap strategy
    if (config.heapStrategy == HeapStrategy.Auto) {
      config = if (props.hasDynamicHeap) {
        config.copy(heapStrategy = HeapStrategy.CEGAR)
      } else {
        config.copy(heapStrategy = HeapStrategy.Fixed)
      }
    }
    
    // Auto-select incremental mode
    if (config.incrementalMode == IncrementalMode.Auto) {
      config = if (props.hasScenarios) {
        config.copy(incrementalMode = IncrementalMode.Scenarios)
      } else if (props.constraintCount > 50) {
        config.copy(incrementalMode = IncrementalMode.PushPop)
      } else {
        config.copy(incrementalMode = IncrementalMode.None)
      }
    }
    
    // Set initial heap bounds for dynamic classes
    if (props.hasDynamicHeap && config.initialHeapBounds.isEmpty) {
      val initialBounds = props.dynamicClasses.map(cls => cls -> 1).toMap
      val maxBounds = props.dynamicClasses.map(cls => cls -> 100).toMap
      config = config.copy(
        initialHeapBounds = initialBounds,
        maxHeapBounds = maxBounds
      )
    }
    
    // Adjust initial timeout based on complexity
    config = props.estimatedComplexity match {
      case ComplexityLevel.Simple => 
        config.copy(initialTimeoutMs = 500)
      case ComplexityLevel.Medium => 
        config.copy(initialTimeoutMs = 2000)
      case ComplexityLevel.Complex => 
        config.copy(initialTimeoutMs = 5000)
      case ComplexityLevel.VeryComplex => 
        config.copy(initialTimeoutMs = 10000)
    }
    
    // Enable soft constraint fallback for complex problems
    if (props.estimatedComplexity == ComplexityLevel.Complex || 
        props.estimatedComplexity == ComplexityLevel.VeryComplex) {
      config = config.copy(useSoftConstraintFallback = true)
    }
    
    config
  }
  
  // ============= Helper methods =============
  
  /**
   * Collect all class declarations from model and packages.
   */
  private def collectClasses(model: Model): Set[EntityDecl] = {
    val classes = mutable.Set[EntityDecl]()
    
    // Top-level classes
    model.decls.foreach {
      case e: EntityDecl if e.entityToken == ClassToken => classes += e
      case _ =>
    }
    
    // Classes in packages
    model.packages.foreach { pkg =>
      if (pkg.model != null) {
        pkg.model.decls.foreach {
          case e: EntityDecl if e.entityToken == ClassToken => classes += e
          case _ =>
        }
      }
    }
    
    classes.toSet
  }
  
  /**
   * Collect all constraint declarations.
   */
  private def collectConstraints(model: Model): List[ConstraintDecl] = {
    val constraints = mutable.ListBuffer[ConstraintDecl]()
    
    // Top-level constraints
    model.decls.foreach {
      case c: ConstraintDecl => constraints += c
      case e: EntityDecl => 
        e.members.foreach {
          case c: ConstraintDecl => constraints += c
          case _ =>
        }
      case _ =>
    }
    
    // Constraints in packages
    model.packages.foreach { pkg =>
      if (pkg.model != null) {
        pkg.model.decls.foreach {
          case c: ConstraintDecl => constraints += c
          case e: EntityDecl =>
            e.members.foreach {
              case c: ConstraintDecl => constraints += c
              case _ =>
            }
          case _ =>
        }
      }
    }
    
    constraints.toList
  }
  
  /**
   * Collect all property declarations.
   */
  private def collectProperties(model: Model): List[PropertyDecl] = {
    val properties = mutable.ListBuffer[PropertyDecl]()
    
    def collectFromDecls(decls: List[TopDecl]): Unit = {
      decls.foreach {
        case p: PropertyDecl => properties += p
        case e: EntityDecl =>
          e.members.foreach {
            case p: PropertyDecl => properties += p
            case _ =>
          }
        case _ =>
      }
    }
    
    collectFromDecls(model.decls)
    model.packages.foreach { pkg =>
      if (pkg.model != null) collectFromDecls(pkg.model.decls)
    }
    
    properties.toList
  }
  
  /**
   * Collect all function declarations.
   */
  private def collectFunctions(model: Model): List[FunDecl] = {
    val functions = mutable.ListBuffer[FunDecl]()
    
    def collectFromDecls(decls: List[TopDecl]): Unit = {
      decls.foreach {
        case f: FunDecl => functions += f
        case e: EntityDecl =>
          e.members.foreach {
            case f: FunDecl => functions += f
            case _ =>
          }
        case _ =>
      }
    }
    
    collectFromDecls(model.decls)
    model.packages.foreach { pkg =>
      if (pkg.model != null) collectFromDecls(pkg.model.decls)
    }
    
    functions.toList
  }
  
  /**
   * Detect classes that need dynamic heap bounds.
   * A class is dynamic if:
   * - It has a property whose type references itself or a superclass/subclass (true recursion)
   * - It is used in a collection type (Set[A], List[A])
   * - It has a top-level var without being fully "contained" in another class
   * 
   * A class is NOT dynamic if:
   * - All its instances are pre-declared as properties in a container class
   *   (like Schedule { DSN_Pass_1: DSN_Pass; DSN_Pass_2: DSN_Pass; ... })
   */
  private def detectDynamicClasses(model: Model, classes: Set[EntityDecl]): Set[String] = {
    val classNames = classes.map(_.ident).toSet
    val dynamic = mutable.Set[String]()
    
    // Build inheritance graph (both directions)
    val superclasses = mutable.Map[String, Set[String]]()
    val subclasses = mutable.Map[String, Set[String]]()
    classes.foreach { cls =>
      val supers = cls.extending.flatMap {
        case IdentType(QualifiedName(names), _) => names.lastOption
        case _ => None
      }.toSet
      superclasses(cls.ident) = supers
      supers.foreach { sup =>
        subclasses(sup) = subclasses.getOrElse(sup, Set.empty) + cls.ident
      }
    }
    
    // Get all classes in the same inheritance hierarchy
    def getHierarchy(cls: String): Set[String] = {
      val visited = mutable.Set[String]()
      val queue = mutable.Queue(cls)
      while (queue.nonEmpty) {
        val current = queue.dequeue()
        if (!visited.contains(current)) {
          visited += current
          queue ++= superclasses.getOrElse(current, Set.empty)
          queue ++= subclasses.getOrElse(current, Set.empty)
        }
      }
      visited.toSet
    }
    
    // Check for TRUE recursive references (class references itself or its hierarchy)
    classes.foreach { cls =>
      val referencedTypes = collectReferencedTypes(cls)
      val hierarchy = getHierarchy(cls.ident)
      
      // Only mark as dynamic if it references types in its OWN hierarchy
      // (e.g., ListExp referencing S_Exp, which is its superclass)
      val selfRefs = referencedTypes.intersect(hierarchy)
      
      if (selfRefs.nonEmpty) {
        // This is true recursion
        dynamic ++= hierarchy.intersect(classNames)
      }
    }
    
    // Check for collection usage
    collectAllTypes(model).foreach {
      case IdentType(QualifiedName(List("Set")), args) =>
        args.foreach {
          case IdentType(QualifiedName(names), _) =>
            names.lastOption.foreach(dynamic += _)
          case _ =>
        }
      case IdentType(QualifiedName(List("List")), args) =>
        args.foreach {
          case IdentType(QualifiedName(names), _) =>
            names.lastOption.foreach(dynamic += _)
          case _ =>
        }
      case IdentType(QualifiedName(List("Seq")), args) =>
        args.foreach {
          case IdentType(QualifiedName(names), _) =>
            names.lastOption.foreach(dynamic += _)
          case _ =>
        }
      case _ =>
    }
    
    dynamic.toSet.intersect(classNames)
  }
  
  /**
   * Collect all types referenced in a class's properties.
   */
  private def collectReferencedTypes(cls: EntityDecl): Set[String] = {
    val types = mutable.Set[String]()
    
    cls.members.foreach {
      case PropertyDecl(_, _, Some(ty), _, _, _) =>
        collectTypeNames(ty, types)
      case _ =>
    }
    
    types.toSet
  }
  
  /**
   * Recursively collect type names from a type.
   */
  private def collectTypeNames(ty: Type, names: mutable.Set[String]): Unit = {
    ty match {
      case IdentType(QualifiedName(ns), args) =>
        ns.lastOption.foreach(names += _)
        args.foreach(collectTypeNames(_, names))
      case CartesianType(types) =>
        types.foreach(collectTypeNames(_, names))
      case FunctionType(from, to) =>
        collectTypeNames(from, names)
        collectTypeNames(to, names)
      case ParenType(t) =>
        collectTypeNames(t, names)
      case SubType(_, t, _) =>
        collectTypeNames(t, names)
      case _ =>
    }
  }
  
  /**
   * Collect all types used in the model.
   */
  private def collectAllTypes(model: Model): List[Type] = {
    val types = mutable.ListBuffer[Type]()
    
    def collectFromExp(exp: Exp): Unit = {
      exp match {
        case TypeCastCheckExp(_, e, t) =>
          collectFromExp(e)
          types += t
        case BinExp(e1, _, e2) =>
          collectFromExp(e1)
          collectFromExp(e2)
        case UnaryExp(_, e) =>
          collectFromExp(e)
        case IfExp(c, t, f) =>
          collectFromExp(c)
          collectFromExp(t)
          f.foreach(collectFromExp)
        case FunApplExp(e, args) =>
          collectFromExp(e)
          args.foreach {
            case PositionalArgument(e) => collectFromExp(e)
            case NamedArgument(_, e) => collectFromExp(e)
          }
        case _ =>
      }
    }
    
    def collectFromDecls(decls: List[TopDecl]): Unit = {
      decls.foreach {
        case PropertyDecl(_, _, Some(t), _, _, _) => types += t
        case e: EntityDecl =>
          e.members.foreach {
            case PropertyDecl(_, _, Some(t), _, _, _) => types += t
            case FunDecl(_, _, params, retTy, _, _) =>
              params.foreach(p => types += p.ty)
              retTy.foreach(types += _)
            case ConstraintDecl(_, exp, _) => collectFromExp(exp)
            case _ =>
          }
        case ConstraintDecl(_, exp, _) => collectFromExp(exp)
        case _ =>
      }
    }
    
    collectFromDecls(model.decls)
    model.packages.foreach { pkg =>
      if (pkg.model != null) collectFromDecls(pkg.model.decls)
    }
    
    types.toList
  }
  
  /**
   * Detect scenarios from top-level if-then-else structures.
   */
  private def detectScenarios(model: Model): List[String] = {
    val scenarios = mutable.ListBuffer[String]()
    
    // Look for top-level if-then-else in constraints
    model.decls.foreach {
      case ConstraintDecl(name, IfExp(cond, _, Some(_)), _) =>
        // This is a scenario-creating constraint
        scenarios += name.getOrElse("scenario")
      case _ =>
    }
    
    // Look for comments indicating scenarios (like DSN_Pass.k)
    // This is a heuristic - we look for "Nominal", "Anomalous", etc. patterns
    
    scenarios.toList
  }
  
  /**
   * Detect if model uses external/opaque functions.
   */
  private def detectExternalFunctions(model: Model, functions: List[FunDecl]): Boolean = {
    functions.exists { f =>
      // A function with no body is external/opaque
      f.body.isEmpty
    }
  }
  
  /**
   * Detect if model uses collection types.
   */
  private def detectCollections(model: Model): Boolean = {
    collectAllTypes(model).exists {
      case IdentType(QualifiedName(List("Set")), _) => true
      case IdentType(QualifiedName(List("List")), _) => true
      case IdentType(QualifiedName(List("Seq")), _) => true
      case IdentType(QualifiedName(List("Map")), _) => true
      case _ => false
    }
  }
  
  /**
   * Detect if model uses quantifiers (forall, exists).
   */
  private def detectQuantifiers(model: Model): Boolean = {
    var hasQuantifiers = false
    
    def checkExp(exp: Exp): Unit = {
      exp match {
        case QuantifiedExp(_, _, _) => hasQuantifiers = true
        case BinExp(e1, _, e2) => checkExp(e1); checkExp(e2)
        case UnaryExp(_, e) => checkExp(e)
        case IfExp(c, t, f) => checkExp(c); checkExp(t); f.foreach(checkExp)
        case ParenExp(e) => checkExp(e)
        case _ =>
      }
    }
    
    def checkDecls(decls: List[TopDecl]): Unit = {
      decls.foreach {
        case ConstraintDecl(_, exp, _) => checkExp(exp)
        case e: EntityDecl =>
          e.members.foreach {
            case ConstraintDecl(_, exp, _) => checkExp(exp)
            case _ =>
          }
        case _ =>
      }
    }
    
    checkDecls(model.decls)
    model.packages.foreach { pkg =>
      if (pkg.model != null) checkDecls(pkg.model.decls)
    }
    
    hasQuantifiers
  }
  
  /**
   * Count fixed object instances (top-level var declarations with class types).
   */
  private def countFixedObjects(model: Model, classes: Set[EntityDecl]): Int = {
    val classNames = classes.map(_.ident).toSet
    var count = 0
    
    def checkDecls(decls: List[TopDecl]): Unit = {
      decls.foreach {
        case PropertyDecl(_, _, Some(IdentType(QualifiedName(names), _)), _, _, _) =>
          if (names.lastOption.exists(classNames.contains)) count += 1
        case e: EntityDecl =>
          e.members.foreach {
            case PropertyDecl(_, _, Some(IdentType(QualifiedName(names), _)), _, _, _) =>
              if (names.lastOption.exists(classNames.contains)) count += 1
            case _ =>
          }
        case _ =>
      }
    }
    
    checkDecls(model.decls)
    model.packages.foreach { pkg =>
      if (pkg.model != null) checkDecls(pkg.model.decls)
    }
    
    count
  }
  
  /**
   * Estimate complexity level based on problem properties.
   */
  private def estimateComplexity(
    constraintCount: Int,
    hasDynamicHeap: Boolean,
    hasScenarios: Boolean,
    hasQuantifiers: Boolean
  ): ComplexityLevel = {
    val baseScore = constraintCount match {
      case n if n < 10 => 1
      case n if n < 50 => 2
      case n if n < 100 => 3
      case _ => 4
    }
    
    val modifiers = 
      (if (hasDynamicHeap) 1 else 0) +
      (if (hasScenarios) 1 else 0) +
      (if (hasQuantifiers) 1 else 0)
    
    val totalScore = baseScore + modifiers
    
    totalScore match {
      case n if n <= 2 => ComplexityLevel.Simple
      case n if n <= 3 => ComplexityLevel.Medium
      case n if n <= 5 => ComplexityLevel.Complex
      case _ => ComplexityLevel.VeryComplex
    }
  }
}

