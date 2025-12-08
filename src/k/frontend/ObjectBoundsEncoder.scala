package k.frontend

import scala.collection.mutable.{ListBuffer, Map => MMap}

/**
 * ObjectBoundsEncoder - Generates SMT with existence variables for dynamic object creation
 *
 * When a K model contains collections (Seq, Set, List) of objects, we don't know
 * upfront how many objects are needed. This encoder:
 *
 * 1. Creates "potential" objects with existence flags
 * 2. Encodes constraints that only apply when objects exist
 * 3. Tracks collection membership via existence variables
 *
 * Example for `items : Seq[Item]` with max bound 3:
 * ```smt2
 * ; Potential Item instances
 * (declare-const Item_0_exists Bool)
 * (declare-const Item_0_value Int)
 * (declare-const Item_1_exists Bool)
 * (declare-const Item_1_value Int)
 * (declare-const Item_2_exists Bool)
 * (declare-const Item_2_value Int)
 *
 * ; Constraints only apply to existing items
 * (assert (=> Item_0_exists (> Item_0_value 0)))
 * (assert (=> Item_1_exists (> Item_1_value 0)))
 * (assert (=> Item_2_exists (> Item_2_value 0)))
 *
 * ; Collection size = count of existing items
 * (define-fun items_size () Int
 *   (+ (ite Item_0_exists 1 0)
 *      (ite Item_1_exists 1 0)
 *      (ite Item_2_exists 1 0)))
 * ```
 */
object ObjectBoundsEncoder {

  /** Configuration */
  var debug: Boolean = false

  /**
   * Information about a potential object instance
   */
  case class PotentialObject(
    className: String,
    index: Int,
    existsVar: String,
    properties: Map[String, String]  // property name -> SMT variable name
  ) {
    def prefix: String = s"${className}_$index"
  }

  /**
   * Information about a collection with dynamic size
   */
  case class DynamicCollection(
    ownerClass: String,
    propertyName: String,
    elementClass: String,
    collectionKind: String,  // Seq, Set, List
    maxBound: Int
  )

  /**
   * Generate SMT declarations for potential objects
   */
  def generatePotentialObjects(
    className: String,
    classDecl: EntityDecl,
    maxBound: Int
  ): (String, List[PotentialObject]) = {
    val sb = new StringBuilder
    val objects = ListBuffer[PotentialObject]()

    sb.append(s"\n; === Potential $className instances (max $maxBound) ===\n")

    for (i <- 0 until maxBound) {
      val prefix = s"${className}_$i"
      val existsVar = s"${prefix}_exists"

      // Declare existence variable
      sb.append(s"(declare-const $existsVar Bool)\n")

      // Declare property variables
      val properties = MMap[String, String]()
      for (prop <- classDecl.getAllPropertyDecls) {
        val propType = prop.getType
        propType match {
          case Some(ty) if TypeChecker.isPrimitiveType(ty) =>
            val smtType = ty.toSMT
            val propVar = s"${prefix}_${prop.name}"
            sb.append(s"(declare-const $propVar $smtType)\n")
            properties += (prop.name -> propVar)
          case _ =>
            // For non-primitive types, we'd need recursive handling
            // For now, just track the reference
            val propVar = s"${prefix}_${prop.name}"
            sb.append(s"(declare-const $propVar Int) ; reference\n")
            properties += (prop.name -> propVar)
        }
      }

      objects += PotentialObject(className, i, existsVar, properties.toMap)
      sb.append("\n")
    }

    (sb.toString, objects.toList)
  }

  /**
   * Generate constraints that apply only when objects exist
   */
  def generateExistenceGuardedConstraints(
    classDecl: EntityDecl,
    potentialObjects: List[PotentialObject]
  ): String = {
    val sb = new StringBuilder
    val className = classDecl.ident

    sb.append(s"\n; === Constraints for $className (guarded by existence) ===\n")

    for (obj <- potentialObjects) {
      // Generate constraints from class constraints
      for (constraint <- classDecl.getAllConstraintDecls) {
        val smtConstraint = rewriteConstraintForObject(constraint.exp, obj)
        sb.append(s"(assert (=> ${obj.existsVar} $smtConstraint))\n")
      }
    }

    sb.toString
  }

  /**
   * Rewrite a constraint expression for a specific potential object
   * Replaces property references with the object's specific variables
   */
  private def rewriteConstraintForObject(exp: Exp, obj: PotentialObject): String = {
    // Simple rewriting - replace property names with object-specific variables
    // This is a simplified version; full implementation would need AST traversal
    var smt = exp.toSMT("", false)

    for ((propName, propVar) <- obj.properties) {
      // Replace references to property with object-specific variable
      // This is a simple string replacement; proper implementation would
      // use AST transformation
      smt = smt.replace(s"(${obj.className}!$propName this)", propVar)
      smt = smt.replace(propName, propVar)
    }

    smt
  }

  /**
   * Generate collection size function
   */
  def generateCollectionSizeFunction(
    collectionName: String,
    potentialObjects: List[PotentialObject]
  ): String = {
    val sb = new StringBuilder

    sb.append(s"\n; Collection size for $collectionName\n")
    sb.append(s"(define-fun ${collectionName}_size () Int\n")
    sb.append("  (+")
    for (obj <- potentialObjects) {
      sb.append(s"\n    (ite ${obj.existsVar} 1 0)")
    }
    sb.append("))\n")

    sb.toString
  }

  /**
   * Generate forall constraints over collection elements
   * Transforms `forall x : collection . P(x)` into conjunction over existing elements
   */
  def generateForallConstraint(
    collectionName: String,
    potentialObjects: List[PotentialObject],
    bodyTemplate: PotentialObject => String
  ): String = {
    val sb = new StringBuilder

    sb.append(s"\n; Forall over $collectionName\n")
    for (obj <- potentialObjects) {
      val body = bodyTemplate(obj)
      sb.append(s"(assert (=> ${obj.existsVar} $body))\n")
    }

    sb.toString
  }

  /**
   * Generate exists constraints over collection elements
   * Transforms `exists x : collection . P(x)` into disjunction over existing elements
   */
  def generateExistsConstraint(
    collectionName: String,
    potentialObjects: List[PotentialObject],
    bodyTemplate: PotentialObject => String
  ): String = {
    val sb = new StringBuilder

    sb.append(s"\n; Exists over $collectionName\n")
    sb.append("(assert (or")
    for (obj <- potentialObjects) {
      val body = bodyTemplate(obj)
      sb.append(s"\n  (and ${obj.existsVar} $body)")
    }
    sb.append("))\n")

    sb.toString
  }

  /**
   * Generate ordering constraints for Seq (ordered collection)
   * Ensures that if Item_2_exists, then Item_1_exists and Item_0_exists
   */
  def generateSequenceOrderingConstraints(
    potentialObjects: List[PotentialObject]
  ): String = {
    if (potentialObjects.length <= 1) return ""

    val sb = new StringBuilder
    sb.append("\n; Sequence ordering: no gaps in existence\n")

    for (i <- 1 until potentialObjects.length) {
      val current = potentialObjects(i)
      val previous = potentialObjects(i - 1)
      // If current exists, previous must exist
      sb.append(s"(assert (=> ${current.existsVar} ${previous.existsVar}))\n")
    }

    sb.toString
  }

  /**
   * Generate uniqueness constraints for Set
   * Ensures all existing elements are distinct
   */
  def generateSetUniquenessConstraints(
    potentialObjects: List[PotentialObject],
    keyProperty: String
  ): String = {
    if (potentialObjects.length <= 1) return ""

    val sb = new StringBuilder
    sb.append("\n; Set uniqueness: all existing elements are distinct\n")

    for (i <- 0 until potentialObjects.length) {
      for (j <- (i + 1) until potentialObjects.length) {
        val obj1 = potentialObjects(i)
        val obj2 = potentialObjects(j)
        val key1 = obj1.properties.getOrElse(keyProperty, obj1.existsVar)
        val key2 = obj2.properties.getOrElse(keyProperty, obj2.existsVar)

        // If both exist, they must be different
        sb.append(s"(assert (=> (and ${obj1.existsVar} ${obj2.existsVar}) (not (= $key1 $key2))))\n")
      }
    }

    sb.toString
  }

  /**
   * Encode a complete model with object bounds
   */
  def encodeModelWithBounds(
    model: Model,
    objectBounds: Map[String, Int]
  ): String = {
    val sb = new StringBuilder
    val allPotentialObjects = MMap[String, List[PotentialObject]]()

    sb.append("; === Object Bounds Encoding ===\n")
    sb.append(s"; Bounds: ${objectBounds.mkString(", ")}\n\n")

    // Find all class declarations
    val classDecls = model.decls.collect {
      case ed: EntityDecl if ed.keyword == ClassToken => ed
    }

    // Generate potential objects for classes with bounds > 0
    for (classDecl <- classDecls) {
      val className = classDecl.ident
      val bound = objectBounds.getOrElse(className, 0)

      if (bound > 0) {
        val (decls, objects) = generatePotentialObjects(className, classDecl, bound)
        sb.append(decls)
        allPotentialObjects += (className -> objects)

        // Add existence-guarded constraints
        sb.append(generateExistenceGuardedConstraints(classDecl, objects))
      }
    }

    // Generate collection-related constraints
    // (This would need more context about which collections exist)

    sb.toString
  }

  private def log(msg: String): Unit = {
    if (debug) {
      println(s"[ObjectBoundsEncoder] $msg")
    }
  }
}

