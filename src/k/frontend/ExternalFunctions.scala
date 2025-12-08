package k.frontend

import java.lang.reflect.{Method, Modifier}
import scala.util.{Try, Success, Failure}
import scala.collection.mutable.{Map => MMap}

/**
 * ExternalFunctions - Support for calling external JVM functions from K
 *
 * This module implements CEGAR-style (Counter-Example Guided Abstraction Refinement)
 * support for opaque/external function calls in K models.
 *
 * The approach:
 * 1. During SMT generation, external calls are treated as uninterpreted functions
 * 2. After Z3 finds a candidate solution, we evaluate the actual JVM call
 * 3. If the result mismatches, we add a refinement constraint and re-solve
 * 4. Repeat until consistent or max iterations reached
 *
 * Syntax (kservices style - no annotations needed):
 *   - Direct qualified names: `java.lang.Math.sqrt(100.0)`
 *   - Import + simple name: `import java.io.File; File(path)`
 *   - Abstract functions: `fun f(x: Int): Int` (no body = uninterpreted)
 *
 * @author Generated for K language opaque function support
 */
object ExternalFunctions {

  /** Maximum number of CEGAR refinement iterations */
  var maxRefinements: Int = 100

  /** Whether to log external function calls */
  var logCalls: Boolean = false

  /** Cache of evaluated function calls: (qualifiedName, args) -> result */
  private val evaluationCache: MMap[(String, List[Any]), Any] = MMap()

  /** Pending refinement constraints from mismatches */
  private val refinementConstraints: MMap[String, List[(List[Any], Any)]] = MMap()

  /** Import map: simple name -> fully qualified name */
  private var importMap: Map[String, String] = Map()

  /**
   * Check if a qualified name refers to an external Java class/method
   */
  def isExternalReference(qualifiedName: String): Boolean = {
    val parts = qualifiedName.split("\\.")
    if (parts.length < 2) return false

    // Check for known Java packages
    val knownPackages = Set("java", "javax", "scala", "com", "org", "gov", "edu", "net")
    knownPackages.contains(parts.head) || {
      // Try to load the class
      try {
        val className = parts.dropRight(1).mkString(".")
        Class.forName(className)
        true
      } catch {
        case _: ClassNotFoundException => false
      }
    }
  }

  /**
   * Parse a qualified name into class name and method name
   */
  def parseQualifiedName(qualifiedName: String): Option[(String, String)] = {
    val parts = qualifiedName.split("\\.")
    if (parts.length < 2) return None

    // Try different splits to find a valid class
    for (i <- parts.length - 1 to 1 by -1) {
      val className = parts.take(i).mkString(".")
      val memberName = parts.drop(i).mkString(".")
      try {
        Class.forName(className)
        return Some((className, memberName))
      } catch {
        case _: ClassNotFoundException => // continue
      }
    }
    None
  }

  /**
   * Register an import declaration
   */
  def registerImport(qualifiedName: String, star: Boolean): Unit = {
    if (star) {
      // import pkg.* - we'd need to scan the package, skip for now
      if (logCalls) println(s"[ExternalFunctions] Wildcard import not fully supported: $qualifiedName.*")
    } else {
      val simpleName = qualifiedName.split("\\.").last
      importMap += (simpleName -> qualifiedName)
      if (logCalls) println(s"[ExternalFunctions] Registered import: $simpleName -> $qualifiedName")
    }
  }

  /**
   * Resolve a simple name using imports
   */
  def resolveSimpleName(name: String): String = {
    importMap.getOrElse(name, name)
  }

  /**
   * Evaluate an external static method call with concrete arguments
   */
  def evaluateStaticMethod(className: String, methodName: String, args: List[Any]): Try[Any] = Try {
    val clazz = Class.forName(className)

    // Find a matching method
    val methods = clazz.getMethods.filter { m =>
      m.getName == methodName &&
      Modifier.isStatic(m.getModifiers) &&
      m.getParameterCount == args.length
    }

    if (methods.isEmpty) {
      throw new NoSuchMethodException(s"No static method $methodName in $className with ${args.length} args")
    }

    // Try to find best match based on argument types
    val method = methods.find { m =>
      val paramTypes = m.getParameterTypes
      (args zip paramTypes).forall { case (arg, paramType) =>
        arg == null || paramType.isInstance(arg) || isBoxedMatch(arg, paramType)
      }
    }.getOrElse(methods.head)  // fallback to first if no exact match

    // Convert arguments if needed
    val convertedArgs = (args zip method.getParameterTypes).map { case (arg, paramType) =>
      convertArg(arg, paramType)
    }

    val result = method.invoke(null, convertedArgs.map(_.asInstanceOf[Object]): _*)

    if (logCalls) {
      println(s"[ExternalFunctions] $className.$methodName(${args.mkString(", ")}) = $result")
    }

    result
  }

  /**
   * Evaluate an external instance method call
   */
  def evaluateInstanceMethod(instance: Any, methodName: String, args: List[Any]): Try[Any] = Try {
    val clazz = instance.getClass

    val methods = clazz.getMethods.filter { m =>
      m.getName == methodName &&
      !Modifier.isStatic(m.getModifiers) &&
      m.getParameterCount == args.length
    }

    if (methods.isEmpty) {
      throw new NoSuchMethodException(s"No instance method $methodName in ${clazz.getName} with ${args.length} args")
    }

    val method = methods.head
    val convertedArgs = (args zip method.getParameterTypes).map { case (arg, paramType) =>
      convertArg(arg, paramType)
    }

    val result = method.invoke(instance, convertedArgs.map(_.asInstanceOf[Object]): _*)

    if (logCalls) {
      println(s"[ExternalFunctions] ${clazz.getName}.$methodName(${args.mkString(", ")}) = $result")
    }

    result
  }

  /**
   * Evaluate a constructor call
   */
  def evaluateConstructor(className: String, args: List[Any]): Try[Any] = Try {
    val clazz = Class.forName(className)

    val constructors = clazz.getConstructors.filter(_.getParameterCount == args.length)

    if (constructors.isEmpty) {
      throw new NoSuchMethodException(s"No constructor for $className with ${args.length} args")
    }

    val constructor = constructors.head
    val convertedArgs = (args zip constructor.getParameterTypes).map { case (arg, paramType) =>
      convertArg(arg, paramType)
    }

    val result = constructor.newInstance(convertedArgs.map(_.asInstanceOf[Object]): _*)

    if (logCalls) {
      println(s"[ExternalFunctions] new $className(${args.mkString(", ")}) = $result")
    }

    result
  }

  /**
   * Try to evaluate an external call, returning either a concrete result or None
   */
  def tryEvaluate(qualifiedName: String, args: List[Any]): Option[Any] = {
    // Check cache first
    evaluationCache.get((qualifiedName, args)) match {
      case Some(result) => return Some(result)
      case None => // continue
    }

    parseQualifiedName(qualifiedName) match {
      case Some((className, methodName)) =>
        if (methodName.isEmpty) {
          // Constructor call
          evaluateConstructor(className, args) match {
            case Success(result) =>
              evaluationCache += ((qualifiedName, args) -> result)
              Some(result)
            case Failure(e) =>
              if (logCalls) println(s"[ExternalFunctions] Constructor failed: $e")
              None
          }
        } else {
          // Static method call
          evaluateStaticMethod(className, methodName, args) match {
            case Success(result) =>
              evaluationCache += ((qualifiedName, args) -> result)
              Some(result)
            case Failure(e) =>
              if (logCalls) println(s"[ExternalFunctions] Static method failed: $e")
              None
          }
        }
      case None =>
        if (logCalls) println(s"[ExternalFunctions] Could not parse: $qualifiedName")
        None
    }
  }

  /**
   * Check if all arguments are concrete (not symbolic)
   */
  def areArgsConcrete(args: List[Any]): Boolean = {
    args.forall {
      case _: Int | _: Long | _: Double | _: Float | _: String | _: Boolean | null => true
      case _: java.lang.Number => true
      case _ => false  // Symbolic or unknown
    }
  }

  /**
   * Add a refinement constraint: given these inputs, the output must be this value
   */
  def addRefinement(funcName: String, args: List[Any], result: Any): Unit = {
    val existing = refinementConstraints.getOrElse(funcName, Nil)
    refinementConstraints += (funcName -> ((args, result) :: existing))
  }

  /**
   * Get all refinement constraints for a function
   */
  def getRefinements(funcName: String): List[(List[Any], Any)] = {
    refinementConstraints.getOrElse(funcName, Nil)
  }

  /**
   * Clear all state (for new model)
   */
  def reset(): Unit = {
    evaluationCache.clear()
    refinementConstraints.clear()
    importMap = Map()
  }

  // ============================================================================
  // Helper methods
  // ============================================================================

  private def isBoxedMatch(arg: Any, paramType: Class[_]): Boolean = {
    (arg, paramType) match {
      case (_: java.lang.Integer, t) if t == classOf[Int] || t == java.lang.Integer.TYPE => true
      case (_: java.lang.Long, t) if t == classOf[Long] || t == java.lang.Long.TYPE => true
      case (_: java.lang.Double, t) if t == classOf[Double] || t == java.lang.Double.TYPE => true
      case (_: java.lang.Float, t) if t == classOf[Float] || t == java.lang.Float.TYPE => true
      case (_: java.lang.Boolean, t) if t == classOf[Boolean] || t == java.lang.Boolean.TYPE => true
      case _ => false
    }
  }

  private def convertArg(arg: Any, targetType: Class[_]): Any = {
    (arg, targetType) match {
      // Handle BigInt/BigDecimal to primitive conversions
      case (bi: BigInt, t) if t == java.lang.Integer.TYPE || t == classOf[Int] => bi.intValue
      case (bi: BigInt, t) if t == java.lang.Long.TYPE || t == classOf[Long] => bi.longValue
      case (bi: BigInt, t) if t == java.lang.Double.TYPE || t == classOf[Double] => bi.doubleValue
      case (bd: BigDecimal, t) if t == java.lang.Double.TYPE || t == classOf[Double] => bd.doubleValue
      case (bd: BigDecimal, t) if t == java.lang.Float.TYPE || t == classOf[Float] => bd.floatValue

      // Handle Int to Double conversion
      case (i: Int, t) if t == java.lang.Double.TYPE || t == classOf[Double] => i.toDouble
      case (l: Long, t) if t == java.lang.Double.TYPE || t == classOf[Double] => l.toDouble

      case _ => arg
    }
  }
}

/**
 * Represents an external function call in the K AST that needs special handling
 */
case class ExternalCall(
  className: String,
  methodName: String,  // empty for constructors
  args: List[Exp],
  isStatic: Boolean
) {
  def qualifiedName: String = if (methodName.isEmpty) className else s"$className.$methodName"
}

/**
 * Result of attempting to evaluate an external call
 */
sealed trait ExternalEvalResult
case class ConcreteResult(value: Any) extends ExternalEvalResult
case class SymbolicResult(funcName: String) extends ExternalEvalResult  // Use uninterpreted function
case class EvalError(message: String) extends ExternalEvalResult

