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

    // First part might be a known package OR an imported class name
    if (knownPackages.contains(parts.head)) {
      true
    } else if (importMap.contains(parts.head)) {
      // The first part is an imported class name (e.g., "Math" from "import java.lang.Math")
      true
    } else {
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
   * Resolve a qualified name by expanding imported class names.
   * E.g., "Math.sqrt" -> "java.lang.Math.sqrt" if Math was imported from java.lang.Math
   */
  def resolveQualifiedName(qualifiedName: String): String = {
    val parts = qualifiedName.split("\\.")
    if (parts.length < 1) return qualifiedName

    importMap.get(parts.head) match {
      case Some(fullClassName) =>
        // Replace the short class name with the full qualified name
        (fullClassName +: parts.tail).mkString(".")
      case None =>
        qualifiedName
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
    // Resolve imported class names to full qualified names
    val resolvedName = resolveQualifiedName(qualifiedName)

    // Check cache first
    evaluationCache.get((resolvedName, args)) match {
      case Some(result) => return Some(result)
      case None => // continue
    }

    parseQualifiedName(resolvedName) match {
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
    externalCallsInModel.clear()
  }

  // ============================================================================
  // CEGAR Refinement Loop
  // ============================================================================

  /** Track external function calls encountered during SMT generation */
  private val externalCallsInModel: MMap[String, ExternalCallInfo] = MMap()

  /** Info about an external call for CEGAR refinement */
  case class ExternalCallInfo(
    smtFuncName: String,        // e.g., "java_lang_Math_sqrt"
    qualifiedName: String,      // e.g., "java.lang.Math.sqrt"
    argVarNames: List[String],  // SMT variable names for arguments
    resultVarName: Option[String] // SMT variable name for result (if assigned)
  )

  /**
   * Register an external call during SMT generation for later CEGAR verification
   */
  def registerExternalCall(smtFuncName: String, qualifiedName: String,
                           argVarNames: List[String], resultVarName: Option[String] = None): Unit = {
    externalCallsInModel += (smtFuncName -> ExternalCallInfo(smtFuncName, qualifiedName, argVarNames, resultVarName))
    if (logCalls) {
      println(s"[CEGAR] Registered external call: $qualifiedName as $smtFuncName")
    }
  }

  /**
   * Get all registered external calls
   */
  def getExternalCalls: Map[String, ExternalCallInfo] = externalCallsInModel.toMap

  /**
   * Verify a Z3 solution against actual external function evaluations.
   * Returns either None (all verified) or Some(constraints) for refinement.
   *
   * @param getVarValue Function to extract variable value from Z3 model
   * @return None if all external calls verified, Some(list of refinement constraints) otherwise
   */
  def verifyAndRefine(getVarValue: String => Option[Any]): Option[List[String]] = {
    var refinements: List[String] = Nil
    var allVerified = true

    for ((smtFuncName, callInfo) <- externalCallsInModel) {
      // Try to get concrete values for arguments
      val argValues: List[Option[Any]] = callInfo.argVarNames.map { varName =>
        getVarValue(varName)
      }

      // Only verify if all arguments are concrete
      if (argValues.forall(_.isDefined)) {
        val concreteArgs = argValues.map(_.get)

        // Evaluate the actual function
        tryEvaluate(callInfo.qualifiedName, concreteArgs) match {
          case Some(actualResult) =>
            // Check if Z3's assumed result matches
            // We need to get what Z3 assumed for the function output
            // This is the value of smtFuncName(args) in the model

            // For now, add a refinement constraint that this specific input
            // maps to this specific output
            val refinement = generateRefinementConstraint(smtFuncName, concreteArgs, actualResult)
            refinements = refinement :: refinements

            if (logCalls) {
              println(s"[CEGAR] Verified: ${callInfo.qualifiedName}(${concreteArgs.mkString(", ")}) = $actualResult")
            }

          case None =>
            if (logCalls) {
              println(s"[CEGAR] Could not evaluate: ${callInfo.qualifiedName}(${concreteArgs.mkString(", ")})")
            }
        }
      }
    }

    if (refinements.isEmpty) None else Some(refinements)
  }

  /**
   * Generate an SMT assertion that constrains the uninterpreted function
   * to return the correct value for the given concrete inputs.
   */
  def generateRefinementConstraint(smtFuncName: String, args: List[Any], result: Any): String = {
    val argsSMT = args.map(anyToSMT).mkString(" ")
    val resultSMT = anyToSMT(result)

    if (args.isEmpty) {
      s"(assert (= $smtFuncName $resultSMT))"
    } else {
      s"(assert (= ($smtFuncName $argsSMT) $resultSMT))"
    }
  }

  /**
   * Convert a Scala/Java value to SMT-LIB2 format
   */
  def anyToSMT(value: Any): String = value match {
    case i: Int => i.toString
    case l: Long => l.toString
    case d: Double =>
      if (d == d.toLong) s"${d.toLong}.0"
      else d.toString
    case f: Float => f.toString
    case b: Boolean => b.toString
    case s: String => s""""$s""""
    case bi: BigInt => bi.toString
    case bd: BigDecimal => bd.toString
    case bd: java.math.BigDecimal => bd.toString
    case other => other.toString
  }

  /**
   * Get all refinement constraints as SMT assertions
   */
  def getAllRefinementConstraints: List[String] = {
    (for {
      (funcName, refinements) <- refinementConstraints
      (args, result) <- refinements
    } yield generateRefinementConstraint(funcName, args, result)).toList
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
