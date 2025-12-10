package k.frontend

import k.frontend._
import java.util.{IdentityHashMap => IMap}

import gov.nasa.jpl.mbee.util.ClassUtils

object TypeCheckException extends Exception

case object TypeChecker {
  var debug = false
  var silent = false
  def error(msg: String) = {
    if (silent) Misc.silentErrorThrow("TypeChecker", msg, TypeCheckException)
    else Misc.errorThrow("TypeChecker", msg, TypeCheckException)
  }
  def log(msg: String) = if (!silent) Misc.log("TypeChecker", msg)
  def logDebug(msg: String) = if (debug && !silent) Misc.log("TypeChecker", s"DEBUG $msg")
  def warning(msg: String) = Misc.log("TypeChecker", s"Warning $msg")

  var globalTypeEnv: TypeEnv = TypeEnv(null, Map())
  var decl2TypeEnvi: Map[TopDecl, TypeEnv] = Map()
  var origTypeEnvironments: Map[TopDecl, TypeEnv] = Map()
  var exp2Type: IMap[Exp, Type] = new IMap()
  var exp2TypeEnv: IMap[Exp, TypeEnv] = new IMap()
  var keywords: Map[String, Type] = Map[String, Type]()
  var type2Decl = Map[Type, TopDecl]()
  var annotations = Map[String, AnnotationDecl]()
  var classes = Map[String, EntityDecl]()

  /** Map from simple name to fully qualified Java class name (for imports) */
  var javaImports: Map[String, String] = Map()

  /**
   * PropertyDecls that should be interpreted as equality expressions (constraints).
   * This happens when a PropertyDecl has no explicit type but the name is already
   * declared in an enclosing scope. In this case, `name = expr` is interpreted as
   * `name == expr` (equality/constraint) rather than a new property declaration.
   */
  var propertyAsConstraint: IMap[PropertyDecl, BinExp] = new IMap()

  def reset(): Unit = {
    globalTypeEnv = TypeEnv(null, Map())
    decl2TypeEnvi = Map()
    origTypeEnvironments = Map()
    exp2Type = new IMap()
    exp2TypeEnv = new IMap()
    keywords = Map[String, Type]()
    type2Decl = Map[Type, TopDecl]()
    annotations = Map[String, AnnotationDecl]()
    classes = Map[String, EntityDecl]()
    javaImports = Map[String, String]()
    propertyAsConstraint = new IMap()
    ClassHierarchy.parents = Map[EntityDecl, Set[Type]]()
    ClassHierarchy.children = Map[EntityDecl, Set[Type]]()
  }

  def getEntityDecl(className: String): EntityDecl = {
    assert(classes contains className, s"$className does not exist in 'classes'")
    classes(className)
  }

  def getPropertyDeclType(decl: PropertyDecl): Type = {
    val baseType = decl.getTypeOrError
    if (!decl.multiplicity.isEmpty) {
      if (decl.modifiers.contains(Unique)) 
        IdentType(QualifiedName(List("Set")), List(baseType))
      else if (decl.modifiers.contains(Ordered))
        IdentType(QualifiedName(List("Seq")), List(baseType))
      else if (decl.modifiers.contains(Ordered) && decl.modifiers.contains(Unique))
        IdentType(QualifiedName(List("OSet")), List(baseType))
      else IdentType(QualifiedName(List("Bag")), List(baseType))
    } else baseType
  }

  def isPrimitiveType(t: Type): Boolean = {
    t.isInstanceOf[PrimitiveType] ||
      (t match {
        case CartesianType(types) => types.forall { isPrimitiveType(_) }
        case ParenType(ty)        => isPrimitiveType(ty)
        case UnitType             => true
        case _                    => false
      })
  }

  // assming that exp is an ident exp...
  def getOwningEntityDecl(exp: Exp): EntityDecl = {
    if (exp2TypeEnv.containsKey(exp)) {
      val te = exp2TypeEnv.get(exp)
      te(exp.toString) match {
        case PropertyTypeInfo(_, _, _, o) => o
        case FunctionTypeInfo(_, o)       => o
        case i @ _                        => error(s"Unexpected type info for given expression. Cannot retrieve owning decl for $exp ${i.getClass}")
      }
    } else null
  }

  def areTypesEqual(ty1: Type, ty2: Type, compatibility: Boolean): Boolean = {
    (ty1, ty2) match {
      case (i1 @ IdentType(it1, it2), i2 @ IdentType(it3, it4)) if !Misc.isCollection(i1) && !Misc.isCollection(i2) =>
        val it1Parents = ClassHierarchy.parentsTransitive(type2Decl(ty1).asInstanceOf[EntityDecl])
        val it2Parents = ClassHierarchy.parentsTransitive(type2Decl(ty2).asInstanceOf[EntityDecl])
        val same = (it1.equals(it3) && (it2 zip it4).forall { t => areTypesEqual(t._1, t._2, compatibility) })
        val inheritanceSame = !((it1Parents.intersect(it2Parents)).isEmpty) || it1Parents.contains(ty2) || it2Parents.contains(ty1)
        return same || inheritanceSame
      case _ => Misc.areTypesEqual(ty1, ty2, compatibility)
    }
  }

  def isConstructorCall(te: TypeEnv, exp: Exp): Option[Type] = {
    return exp match {
      case IdentExp(i) =>
        if (te.map.keySet.contains(i)) {
          te.map(i) match {
            case ClassTypeInfo(d) => Some(type2Decl.map(_.swap).asInstanceOf[Map[EntityDecl, Type]](d))
            case _                => None
          }
        } else None
      case _ => None
    }
  }

  def isConstructor(exp: Exp): Boolean = !isConstructorCall(globalTypeEnv, exp).isEmpty

  def isConstructorAppl(exp: Exp): Boolean =
    exp match {
      case FunApplExp(exp1, _) => isConstructor(exp1)
      case _                   => false
    }

  def getDirectSubClasses(className: String): List[String] =
    if (className == "TopLevelDeclarations") Nil
    else if (ClassHierarchy.children.contains(classes(className)))
      ClassHierarchy.children(classes(className)).map(_.toString).toList
    else Nil

  def getDirectSuperClasses(className: String): List[String] =
    if (className == "TopLevelDeclarations") Nil
    else ClassHierarchy.parents(classes(className)).map(_.toString).toList

  def getSuperClasses(className: String): List[String] =
    if (className == "TopLevelDeclarations") Nil
    else ClassHierarchy.parentsTransitive(classes(className)).map(_.toString).toList

  def getSubClasses(className: String): List[String] =
    if (className == "TopLevelDeclarations") Nil
    else ClassHierarchy.childrenTransitive(classes(className)).map(_.toString).toList

  def isLocal(exp: IdentExp): Boolean = {
    val res = try {
      exp2TypeEnv.get(exp)(exp.ident) match {
        case ParamTypeInfo(_)             => true
        case PatternTypeInfo(_, _)        => true
        case PropertyTypeInfo(_, g, c, _) => !g && !c
        case _                            => false
      }
    } catch {
      case _: Throwable => false
    }
    logDebug(s"isLocal $exp $res")
    res
  }

}

import TypeChecker._

case class TypeEnv(decl: TopDecl, map: Map[String, TypeInfo]) {
  def overwrite(kv: (String, TypeInfo)) = TypeEnv(decl, map + kv)
  def union(kv: (String, TypeInfo)): TypeEnv = {
    if (map.contains(kv._1)) {
      error(s"${kv._1} already defined. Please check. Exiting.")
    } else {
      TypeEnv(decl, map + kv)
    }
  }

  /*
   * Overwrites globals with locals and member variables
   * Does not allow function overloading
   * Uses decl from this 
   */
  def union2(te: TypeEnv): TypeEnv = {
    var newMap = Map[String, TypeInfo]()
    map.foreach { kv => newMap += (kv._1 -> kv._2) }
    te.map.foreach {
      kv =>
        (kv._1, kv._2) match {
          case (functionName, FunctionTypeInfo(fdecl, fowner)) =>
            if (map.contains(functionName)) {
              val ofdecl = map(functionName).asInstanceOf[FunctionTypeInfo].decl
              val areReturnTypesEqual = areTypesEqual(fdecl.ty.getOrElse(UnitType), ofdecl.ty.getOrElse(UnitType), false)
              val areParamsEqual = ofdecl.params.length == fdecl.params.length && (ofdecl.params zip fdecl.params).forall { p => areTypesEqual(p._1.ty, p._2.ty, false) }
              val onlySecondHasBody = !fdecl.body.isEmpty
              if ((areReturnTypesEqual && areParamsEqual) && fowner != null && onlySecondHasBody) {
                error(s"${fdecl.ident} redefined.")
              }
            }
            newMap += (kv._1 -> kv._2)
          case (pname, pti @ PropertyTypeInfo(pdecl, global, classMember, powner)) =>
            if (map.contains(pname)) {
              if (!map(pname).isInstanceOf[PropertyTypeInfo]) {
                error(s"$pname overloaded. Currently not supported.")
              }
              val opti = map(pname).asInstanceOf[PropertyTypeInfo]
              if (opti.global && pti.global && opti != pti) {
                error(s"$pname has been declared multiple times in the global scope.")
              }
              if (opti.global && !pti.global) {
                newMap += (pname -> pti)
              }
              if (pti.global && !opti.global) {
                newMap += (pname -> opti)
              }
              if (!pti.global && !opti.global) {
                error(s"$pname declared multiple times.")
              }
            } else {
              newMap += (kv._1 -> kv._2)
            }
          case _ => newMap += (kv._1 -> kv._2)
        }
    }
    TypeEnv(decl, newMap)
  }

  def apply(k: String): TypeInfo = {
    if (!map.contains(k)) error(s"Could not find declaration for $k")
    else map(k)
  }
  def contains(k: String): Boolean = map.contains(k)

  override def toString = {
    if (decl != null) s"${decl.asInstanceOf[EntityDecl].ident} : $map"
    else s"Global : $map"
  }
}

trait TypeInfo

case class FunctionTypeInfo(decl: FunDecl, owner: EntityDecl) extends TypeInfo {
  val returnType: Option[Type] = decl.ty

  override def toString() =
    s"""Function: ${decl.ident} : (${decl.params.mkString(",")}) -> ${returnType.getOrElse("")}"""
}
case class ClassTypeInfo(decl: EntityDecl) extends TypeInfo {
  override def toString = s"Class: ${decl.ident}"
}
case class PropertyTypeInfo(decl: PropertyDecl, global: Boolean, classMember: Boolean, owner: EntityDecl) extends TypeInfo {
  override def toString =
    s"Property: ${decl.name} : ${decl.getType.getOrElse("(inferred)")} $global $classMember ${if (owner != null) owner.ident}"
}
case class TypeTypeInfo(decl: TypeDecl) extends TypeInfo
case class ParamTypeInfo(p: Param) extends TypeInfo
case class PatternTypeInfo(p: Pattern, ty: Type) extends TypeInfo

object ClassHierarchy {
  var parents = Map[EntityDecl, Set[Type]]()
  var children = Map[EntityDecl, Set[Type]]()

  def buildHierarchy(model: Model): Unit = {
    // Track processed entity declarations to avoid duplicates from imports
    var processedEntities = Set[EntityDecl]()
    
    def processEntityDecls(decls: List[TopDecl]): Unit = {
      for (ed @ EntityDecl(_, _, _, _, _, _, _, _) <- decls) {
        // Only process if we haven't seen this entity declaration before
        if (!processedEntities.contains(ed)) {
          processedEntities += ed
          var edParents: Set[Type] = Set()
          if (ed.entityToken.isInstanceOf[IdentifierToken]) {
            edParents += keywords(ed.entityToken.asInstanceOf[IdentifierToken].name)
          }
          edParents ++= buildHierarchy(ed, type2Decl, Set())
          parents += (ed -> edParents)
          edParents.foreach { p =>
            val parent = type2Decl(p).asInstanceOf[EntityDecl]
            if (!children.contains(parent)) children = children + (parent -> Set())
            children = children +
              (parent ->
                (children(parent) +
                  (type2Decl.map(_.swap).asInstanceOf[Map[TopDecl, Type]](ed))))
          }
        }
      }
    }
    
    def processModelHierarchy(m: Model): Unit = {
      processEntityDecls(m.decls)
      m.packages.foreach { pkg =>
        processModelHierarchy(pkg.model)
      }
    }
    
    processModelHierarchy(model)

    // ensure that one cannot reach itself in the inheritance scheme
    val decl2Type = type2Decl.map(_.swap).asInstanceOf[Map[EntityDecl, Type]]
    parents.foreach { kv =>
      if (parentsTransitive(kv._1).contains(decl2Type(kv._1)))
        error(s"${kv._1.ident} has a cyclic inheritance structure.")
    }

  }

  def childrenTransitive(e: EntityDecl, visited: Set[Type] = Set()): List[Type] = {
    val declType = type2Decl.map(_.swap).asInstanceOf[Map[EntityDecl, Type]](e)
    if (visited.contains(declType)) return Nil
    val immediateChildren = children.getOrElse(e, Nil).toList
    val subChildren = (for (child <- immediateChildren)
      yield childrenTransitive(type2Decl(child).asInstanceOf[EntityDecl], visited + declType)).flatten
    (immediateChildren ++ subChildren).distinct
  }

  def parentsTransitive(e: EntityDecl, visited: Set[Type] = Set()): List[Type] = {
    val declType = type2Decl.map(_.swap).asInstanceOf[Map[EntityDecl, Type]](e)
    if (visited.contains(declType)) return Nil
    val immediateParent = parents.getOrElse(e, Nil).toList.reverse
    val iParents = immediateParent.foldLeft(List[Type]()) {
      (res, p) =>
        parentsTransitive(type2Decl(p).asInstanceOf[EntityDecl], visited + declType) ++ (p :: res)
    }
    iParents.distinct
  }

  def buildHierarchy(d: EntityDecl, types: Map[Type, TopDecl], visited: Set[EntityDecl]): Set[Type] = {
    d.extending.foldLeft(Set[Type]()) { (res, e) =>
      if (!types.contains(e)) {
        error(s"Could not find ${e.toString}")
      }
      assert(types(e).isInstanceOf[EntityDecl])
      assert(!visited.contains(types(e).asInstanceOf[EntityDecl]))
      res + e
    }
  }

}

class TypeChecker(model: Model) {

  def jvmFunctionToFunctionDecl( cls: java.lang.Class[Object], name: String ) : FunDecl = {
    val field : java.lang.reflect.Field = ClassUtils.getField(cls, name, true)
    if (field == null) return null
    val typeParams : List[TypeParam] = Nil
    var params : List[Param] = Nil
    val extending : List[Type] = Nil
    val members : List[MemberDecl] = Nil
    val functionSpecs : List[FunSpec] = Nil
    val funDecl = FunDecl(name, typeParams, params, None, functionSpecs, members)
    return funDecl
  }

  def jvmPackageToPackageDecl( name: String ) : PackageDecl = {
    if (!ClassUtils.isPackageName(name)) return null
    var names = List(name)
    val pkgDecl = PackageDecl(QualifiedName(names), null)
    return pkgDecl
  }

  def jvmClassToEntityDecl( name: String ) : EntityDecl = {
    val cls = ClassUtils.classForName(name)
    if (cls == null) return null
    val typeParams : List[TypeParam] = Nil
    val extending : List[Type] = Nil
    val members : List[MemberDecl] = Nil
    val entityDecl = EntityDecl(Nil, ClassToken, None, name, null, typeParams, extending, members)
    return entityDecl
  }

  
  private def isExternallyDefined( te: TypeEnv, name: String ) : Boolean = {
    val entityDecl = jvmClassToEntityDecl( name )
    if (entityDecl != Nil && entityDecl != null) {
//      te.map(name) = entityDecl
      return true
    }
    val packageDecl = jvmPackageToPackageDecl( name )
    if (packageDecl != null) {
//      te.map = te.map + ( name -> packageDecl)
      return true
    }
    return false
  }
  
  private def doesTypeExist(te: TypeEnv, ty: Type): Boolean = {
    ty match {
      case it @ IdentType(_, _) =>
        if (Misc.isCollection(it)) return it.args.forall { t => doesTypeExist(te, t) }
        else if (te.contains(it.toString)) return true
        else return isExternallyDefined(te, it.toString)
      case ct @ CartesianType(_) =>
        return ct.types.forall { x => doesTypeExist(te, x) }
      case ft @ FunctionType(_, _) =>
        return (doesTypeExist(te, ft.from) &&
          doesTypeExist(te, ft.to))
      case pt @ ParenType(_) =>
        return doesTypeExist(te, pt.ty)
      case st @ SubType(_, _, _) =>
        return te.contains(st.ident.toString)
      case BoolType   => return true
      case IntType    => return true
      case CharType   => return true
      case StringType => return true
      case UnitType   => return true
      case RealType   => return true
      case TimeType   => return true
      case DurationType => return true
    }
    return false
  }

  private def exprContainsAssignment(e: Exp): Boolean = {
    e match {
      case ParenExp(e) => exprContainsAssignment(e)
      case BinExp(exp1, op, exp2) =>
        if (op == ASSIGN) true
        else exprContainsAssignment(exp1) || exprContainsAssignment(exp2)
      case WhileExp(cond, body)       => true
      case IfExp(cond, tb, eb)        => exprContainsAssignment(tb) || (if (!eb.isEmpty) exprContainsAssignment(eb.get) else false)
      case BlockExp(body)             => body.foldLeft(false) { (res, b) => res || declContainsAssignment(b) }
      case UnaryExp(op, exp)          => exprContainsAssignment(exp)
      case LambdaExp(pat, exp)        => exprContainsAssignment(exp)
      case ReturnExp(exp)             => exprContainsAssignment(exp)
      case ForExp(pattern, exp, body) => true
      case QuantifiedExp(q, b, e)     => exprContainsAssignment(e)
      case _                          => false
    }
  }

  private def declContainsAssignment(d: MemberDecl): Boolean = {
    d match {
      case fd @ FunDecl(_, _, _, _, _, _) => fd.body.foldLeft(false)((res, b) => res || declContainsAssignment(b))
      case ed @ ExpressionDecl(exp)       => exprContainsAssignment(exp)
      case pd @ PropertyDecl(_, _, _, _, _, _) =>
        if (pd.assignment.isEmpty) false
        else pd.assignment.get
      case _ => false
    }
  }

  private def incompleteIfExp(e: Exp): Boolean = {
    e match {
      case IfExp(e, t, f) =>
        f match {
          case None => true
          case _    => false
        }
      case _ => false
    }
  }

  private def functionContainsReq(d: FunDecl): Boolean = {
    d.body.foldLeft(false)((res, b) => res || b.isInstanceOf[ConstraintDecl])
  }

  def smtCheck {
    /*
     * For SMT we disallow statements with side effects and functions returning objects
     */
    model.decls.foreach { d =>
      
      // if property decl, make sure it is not an unsupported collection
      d match {
        case p @ PropertyDecl(_, name, _, _, _, _) =>
          p.getType.foreach { ty =>
            if(Misc.isCollection(ty)){
              val collectionKind = Misc.getCollectionKind(ty)
              // Seq is now supported via Z3 sequence theory
              // Set is supported via Z3 array/set theory
              if(collectionKind == BagKind ||
                  collectionKind == OSetKind){
                error(s"Unsupported collection kind for SMT processing. Currently Set and Seq are supported.")
              }
            }
          }
        case _ => ()
      }
      
      if (d.isInstanceOf[MemberDecl]) {
        if (declContainsAssignment(d.asInstanceOf[MemberDecl])) {
          error(s"Found assignment in declaration $d. SMT mode disallows assignments.")
        }
      }

      if (d.isInstanceOf[FunDecl]) {
        val fd = d.asInstanceOf[FunDecl]
        val lastMemberIsConstructorCall =
          if (fd.body.length == 0) false
          else fd.body.last match {
            case ExpressionDecl(e) => !isConstructorCall(exp2TypeEnv.get(e), e).isEmpty
            case _                 => false
          }
        val incompleteIf =
          if (fd.body.length == 0) false
          else fd.body.last match {
            case ExpressionDecl(e) => incompleteIfExp(e)
            case _                 => false
          }

        if (!isPrimitiveType(d.asInstanceOf[FunDecl].ty.getOrElse(UnitType)) && lastMemberIsConstructorCall) {
          error(s"Function $d does not return a primitive type. SMT mode disallows this.")
        }
        if (functionContainsReq(d.asInstanceOf[FunDecl])) {
          error(s"Function $d contains a constraint. SMT mode disallows this.")
        }
        if (incompleteIf) {
          error(s"Function $d contains an incomplete If statement with no else clause.")
        }

      }

      if (d.isInstanceOf[EntityDecl]) {
        for (fd @ FunDecl(_, _, _, _, _, _) <- d.asInstanceOf[EntityDecl].members) {
          val lastMemberIsConstructorCall =
            if (fd.body.length == 0) false
            else fd.body.last match {
              case ExpressionDecl(e) => !isConstructorCall(exp2TypeEnv.get(e), e).isEmpty
              case _                 => false
            }
          val incompleteIf =
            if (fd.body.length == 0) false
            else fd.body.last match {
              case ExpressionDecl(e) => incompleteIfExp(e)
              case _                 => false
            }

          if (!isPrimitiveType(fd.ty.getOrElse(UnitType)) && lastMemberIsConstructorCall) {
            error(s"Function $fd does not return a primitive type. SMT mode disallows this.")
          }
          if (functionContainsReq(fd)) {
            error(s"Function $fd contains a constraint. SMT mode disallows this.")
          }
          if (incompleteIf) {
            error(s"Function $d contains an incomplete If statement with no else clause.")
          }
        }
      }
    }
  }
  
  
  def hi { typeCheck}

  private def typeCheck: Boolean = {

    if (model == null) return true


    // add the reserved annotations to our available annotations
    ReservedAnnotations.annotations.foreach { a => annotations += (a.name -> a) }

    // get declared annotations and corresponding types
    model.annotations.foreach { d =>
      val ad = d.asInstanceOf[AnnotationDecl]
      if (annotations.contains(ad.name)) error(s"Redefining annotation ${ad.name}.")
      annotations += (ad.name -> ad)
    }

    // Process Java imports - register simple name -> qualified name mappings
    def processJavaImports(m: Model): Unit = {
      m.imports.foreach { imp =>
        val qualifiedName = imp.name.toString
        // Check if this looks like a Java import (starts with known package)
        val javaPackageRoots = Set("java", "javax", "scala", "com", "org", "gov", "edu", "net")
        val firstPart = imp.name.names.headOption.getOrElse("")
        if (javaPackageRoots.contains(firstPart)) {
          if (imp.star) {
            // Wildcard import - we can't resolve these statically without classpath scanning
            logDebug(s"Wildcard Java import not fully supported: $qualifiedName.*")
          } else {
            // Single class import - register the simple name
            val simpleName = imp.name.names.lastOption.getOrElse("")
            if (simpleName.nonEmpty) {
              TypeChecker.javaImports += (simpleName -> qualifiedName)
              logDebug(s"Registered Java import: $simpleName -> $qualifiedName")
              // Also register in ExternalFunctions
              ExternalFunctions.registerImport(qualifiedName, imp.star)
            }
          }
        }
      }
      // Recursively process package imports
      m.packages.foreach { pkg => processJavaImports(pkg.model) }
    }
    processJavaImports(model)

    // get class information - recursively process all declarations including those in packages
    // Track processed entity names to avoid duplicates from imports
    var processedEntityNames = Set[String]()

    def processDecls(decls: List[TopDecl]): Unit = {
      decls.foreach { d =>
        d match {
          case ed @ EntityDecl(_, _, _, ident, _, _, _, _) =>
            // Only process if we haven't seen this entity name before
            // Also check if it's already in the globalTypeEnv to handle parser duplicates
            if (!processedEntityNames.contains(ident) && !globalTypeEnv.map.contains(ident)) {
              processedEntityNames += ident
              val dED = d.asInstanceOf[EntityDecl]
              keywords = ed.keyword match {
                case Some(kw) =>
                  // Don't error if keyword is already registered - packages may reuse keywords
                  if (!keywords.contains(kw)) keywords + (kw -> IdentType(QualifiedName(List(ident)), List()))
                  else keywords
                case _ => keywords
              }
              type2Decl = type2Decl + (IdentType(QualifiedName(List(ident)), List()) -> dED)
              classes = classes + (ident -> dED)
              globalTypeEnv = globalTypeEnv.union(ident -> ClassTypeInfo(ed))
            }
          case td @ TypeDecl(ident, _, _) =>
            if (!processedEntityNames.contains(ident) && !globalTypeEnv.map.contains(ident)) {
              processedEntityNames += ident
              type2Decl = type2Decl + (IdentType(QualifiedName(List(ident)), List()) -> d.asInstanceOf[TypeDecl])
              globalTypeEnv = globalTypeEnv.union(ident -> TypeTypeInfo(td))
            }
          case _ => ()
        }
      }
    }
    
    def processModel(m: Model): Unit = {
      processDecls(m.decls)
      m.packages.foreach { pkg =>
        processModel(pkg.model)
      }
    }
    
    processModel(model)

    // build inheritance model
    ClassHierarchy.buildHierarchy(model)

    // pass: process functions (not bodies of functions) at top level FIRST
    // This must happen before property type inference so that function return types are available
    // Note: Do NOT recursively process packages here because each package gets its own TypeChecker
    globalTypeEnv = model.decls.foldLeft(globalTypeEnv) { (res, d) =>
      d match {
        case fd @ FunDecl(_, _, _, _, _, _) =>
          // check if the type exists
          fd.ty match {
            case Some(t) =>
              if (!doesTypeExist(res, t)) {
                error(s"Specified type $t does not exist. Please check. Exiting.")
              }
            case None => ()
          }
          res.union(fd.ident -> FunctionTypeInfo(fd, null))
        case _ => res
      }
    }

    // pass: get property info on global level - FIRST PASS: only properties with explicit types
    // This adds explicitly typed properties to globalTypeEnv before class processing
    // Note: Do NOT recursively process packages here because each package gets its own TypeChecker
    model.decls.foreach { d =>
      d match {
        case p @ PropertyDecl(_, name, tyOpt, _, _, _) =>
          if ((p.modifiers.contains(Var) && p.modifiers.contains(Val)) ||
            ((p.modifiers.contains(Var) || p.modifiers.contains(Val)) &&
              (p.modifiers.contains(Ordered) || p.modifiers.contains(Unique) ||
                p.modifiers.contains(Source) || p.modifiers.contains(Target))))
            error(s"Property $name has conflicting modifiers: ${p.modifiers.mkString(",")}.")

          // Only process properties with explicit types in this pass
          tyOpt match {
            case Some(ty) =>
              if (!doesTypeExist(globalTypeEnv, ty)) error(s"Specified type $ty does not exist. Please check. Exiting.")
              globalTypeEnv = globalTypeEnv.union(name -> PropertyTypeInfo(p, true, false, null))
            case None =>
              // Will be handled in second pass after class type environments are built
              ()
          }
        case _ => ()
      }
    }

    // pass: build information about properties/functions in classes - FIRST PASS
    // Build class type environments with explicitly typed properties only
    // This must happen before function body processing so that class member types are available
    def processClassPropertiesExplicit(decls: List[TopDecl]): Unit = {
      decls.foreach { d =>
        d match {
          case ed @ EntityDecl(_, token, _, ident, _, _, _, _) if token != AssocToken =>
            // add 'this' to the type env
            var classTypeEnv = TypeEnv(ed, globalTypeEnv.map + ("this" -> ClassTypeInfo(ed)))
            ed.members.foreach { m =>
              m match {
                case pd @ PropertyDecl(_, _, _, _, _, _) =>
                  // Only handle explicitly typed properties in this pass
                  pd.ty match {
                    case Some(explicitType) =>
                      if (!doesTypeExist(classTypeEnv, explicitType))
                        error(s"Specified type $explicitType does not exist. Please check. Exiting.")
                      classTypeEnv = classTypeEnv.overwrite(pd.name -> PropertyTypeInfo(pd, false, true, ed))
                    case None =>
                      // Skip - will be handled in second pass after function bodies are processed
                      ()
                  }
                case fd @ FunDecl(_, _, _, _, _, _) =>
                  classTypeEnv = classTypeEnv.union(fd.ident -> FunctionTypeInfo(fd, ed))
                case _ => ()
              }
            }
            decl2TypeEnvi += (d -> classTypeEnv)
            origTypeEnvironments += (d -> classTypeEnv)
        case ed @ EntityDecl(_, AssocToken, _, ident, _, _, _, _) =>

          // only support 2 members in associations
          if (ed.members.length != 2)
            error(s"$ident contains more than two associations.\n\tCurrently only a source and target are supported for associations.")

          // members must be source and target 
          if (!ed.members.forall { m =>
            m.asInstanceOf[PropertyDecl].modifiers != null &&
              (m.asInstanceOf[PropertyDecl].modifiers.contains(Source) ||
                m.asInstanceOf[PropertyDecl].modifiers.contains(Target))
          })
            error(s"$ident does not define a source or target correctly.")

          // members must be of ident type that is a user defined class
          if (!(ed.members.forall { m =>
            m.asInstanceOf[PropertyDecl].getType.exists(_.isInstanceOf[IdentType])
          })) error(s"$ident association uses a non user defined type as source/target.")
          var classTypeEnv = TypeEnv(ed, globalTypeEnv.map + ("this" -> ClassTypeInfo(ed)))
          classTypeEnv = ed.members.foldLeft(classTypeEnv) {
            (res, m) =>
              m match {
                case pd @ PropertyDecl(_, _, _, _, _, _) =>
                  res.overwrite(pd.name -> PropertyTypeInfo(pd, false, true, ed))
                case _ =>
                  error(s"$ident association contains members besides functions.\n\tCurrently this is unsupported.")
              }
          }

          // also insert the source/target in respective class
          val m1 = ed.members(0).asInstanceOf[PropertyDecl]
          val m2 = ed.members(1).asInstanceOf[PropertyDecl]
          val cte0 =
            decl2TypeEnvi.find(p =>
              p._1 match {
                case ed1 @ EntityDecl(_, t, _, ident, _, _, _, _) if t != AssocToken =>
                  if (ed1.ident.equals(m1.getTypeOrError.toString)) true
                  else false
                case _ =>
                  false
              }).get

          val cte1 =
            decl2TypeEnvi.find(p =>
              p._1 match {
                case ed1 @ EntityDecl(_, t, _, ident, _, _, _, _) if t != AssocToken =>
                  if (ed1.ident.equals(m2.getTypeOrError.toString)) true
                  true
                case _ =>
                  false
              }).get

        /*
          decl2TypeEnvi += (cte0._1 -> (cte0._2.union((m2.name) -> PropertyTypeInfo(m2, false, true, ed))))
          origTypeEnvironments += (cte0._1 -> (cte0._2.union((m2.name) -> PropertyTypeInfo(m2, false, true, ed))))
          decl2TypeEnvi += (cte1._1 -> (cte1._2.union((m1.name) -> PropertyTypeInfo(m1, false, true, ed))))
          origTypeEnvironments += (cte1._1 -> (cte1._2.union((m1.name) -> PropertyTypeInfo(m1, false, true, ed))))
          decl2TypeEnvi += (d -> classTypeEnv)
          origTypeEnvironments += (d -> classTypeEnv)
*/
        case _ => ()
      }
    }
    }

    def processModelClassPropertiesExplicit(m: Model): Unit = {
      processClassPropertiesExplicit(m.decls)
      m.packages.foreach { pkg =>
        processModelClassPropertiesExplicit(pkg.model)
      }
    }

    processModelClassPropertiesExplicit(model)

    // pass: process top-level function BODIES to infer return types
    // This must happen after class type environments are built (so we can access class member types)
    // but before property type inference (so that inferred return types are available)
    model.decls.foreach { d =>
      d match {
        case fd @ FunDecl(_, _, _, _, _, _) =>
          processFunction(fd, globalTypeEnv, null)
        case _ => ()
      }
    }

    // pass: build information about properties/functions in classes - SECOND PASS
    // Now infer types for properties without explicit types (can use function return types)
    def processClassPropertiesInferred(decls: List[TopDecl]): Unit = {
      decls.foreach { d =>
        d match {
          case ed @ EntityDecl(_, token, _, ident, _, _, _, _) if token != AssocToken =>
            var classTypeEnv = decl2TypeEnvi(d)
            ed.members.foreach { m =>
              m match {
                case pd @ PropertyDecl(_, _, _, _, _, _) =>
                  pd.ty match {
                    case Some(_) =>
                      // Already handled in first pass
                      ()
                    case None =>
                      // Infer type from initialization expression
                      pd.expr match {
                        case Some(e) =>
                          val exprType = getExpType(classTypeEnv, e, ed)
                          pd.inferredType = Some(exprType)
                          logDebug(s"Type inference: inferred type $exprType for class property ${pd.name} in $ident")
                          classTypeEnv = classTypeEnv.overwrite(pd.name -> PropertyTypeInfo(pd, false, true, ed))
                        case None =>
                          error(s"Cannot infer type for property '${pd.name}' in class '$ident': no type annotation and no initialization expression.")
                      }
                  }
                case _ => ()
              }
            }
            decl2TypeEnvi += (d -> classTypeEnv)
            origTypeEnvironments += (d -> classTypeEnv)
          case _ => ()
        }
      }
    }
    
    def processModelClassPropertiesInferred(m: Model): Unit = {
      processClassPropertiesInferred(m.decls)
      m.packages.foreach { pkg =>
        processModelClassPropertiesInferred(pkg.model)
      }
    }
    
    processModelClassPropertiesInferred(model)

    // pass: get property info on global level - SECOND PASS: properties requiring type inference
    // Now that class type environments are built, we can infer types from expressions that reference class members
    model.decls.foreach { d =>
      d match {
        case p @ PropertyDecl(_, name, tyOpt, _, _, _) =>
          tyOpt match {
            case Some(_) =>
              // Already processed in first pass
              ()
            case None =>
              // Infer type from initialization expression
              p.expr match {
                case Some(e) =>
                  val exprType = getExpType(globalTypeEnv, e, null)
                  p.inferredType = Some(exprType)
                  logDebug(s"Type inference: inferred type $exprType for top-level property $name")
                case None =>
                  error(s"Cannot infer type for top-level property '$name': no type annotation and no initialization expression.")
              }
              globalTypeEnv = globalTypeEnv.union(name -> PropertyTypeInfo(p, true, false, null))
          }
        case _ => ()
      }
    }

    logDebug(s"GlobalTE: $globalTypeEnv")

    // pass: do inheritance for each class and associations
    def processInheritance(decls: List[TopDecl]): Unit = {
      decls.foreach { d =>
      d match {
        case ed @ EntityDecl(_, t, _, ident, _, _, _, _) if t != AssocToken =>
          logDebug(s"Processing $ident")
          val classTypeEnv = decl2TypeEnvi(d)
          val extending = ClassHierarchy.parentsTransitive(ed)
          val newClassTypeEnv = {
            val extendingEnv = extending.foldLeft(TypeEnv(ed, Map[String, TypeInfo]())) {
              (res, ex) =>
                origTypeEnvironments(classes(ex.asInstanceOf[IdentType].ident.toString)).union2(res)
            }
            classTypeEnv.union2(extendingEnv)
          }
          decl2TypeEnvi += (d -> newClassTypeEnv)
        case _ => ()
      }
    }
    }
    
    def processModelInheritance(m: Model): Unit = {
      processInheritance(m.decls)
      m.packages.foreach { pkg =>
        processModelInheritance(pkg.model)
      }
    }
    
    processModelInheritance(model)

    decl2TypeEnvi.foreach(kv => logDebug(s"${kv._2}"))

    // pass: infer types for undeclared variables from constraints
    // This allows variables to be used without explicit type declarations
    // when their type can be inferred from the constraints they appear in
    def inferUndeclaredTypes(): Unit = {
      import scala.collection.mutable

      // Collect all expressions from constraints and top-level expressions
      val allExpressions = mutable.ListBuffer[Exp]()
      model.decls.foreach {
        case ConstraintDecl(_, exp, _) => allExpressions += exp
        case ExpressionDecl(exp) => allExpressions += exp
        case _ => ()
      }

      // Find all identifiers used in expressions
      val usedIdentifiers = allExpressions.flatMap(TypeConstraints.collectIdentifiers).toSet

      // Find which identifiers are not declared
      val declaredIdentifiers = globalTypeEnv.map.keySet
      val undeclaredIdentifiers = usedIdentifiers -- declaredIdentifiers

      if (undeclaredIdentifiers.nonEmpty) {
        logDebug(s"Found undeclared identifiers: ${undeclaredIdentifiers.mkString(", ")}")

        // Create type variables for undeclared identifiers
        val typeVars = undeclaredIdentifiers.map(name => name -> TypeVar(name)).toMap

        // Collect type constraints from all expressions
        val constraints = allExpressions.flatMap(exp =>
          TypeConstraints.collectConstraints(exp, typeVars)
        ).toList

        logDebug(s"Type constraints: ${constraints.mkString(", ")}")

        // Solve constraints to infer types
        TypeConstraints.solveConstraints(typeVars, constraints) match {
          case Right(inferredTypes) =>
            // Add inferred types to global type environment
            inferredTypes.foreach { case (name, ty) =>
              logDebug(s"Inferred type for $name: $ty")
              // Create a synthetic property declaration for the inferred variable
              val syntheticProp = PropertyDecl(List(), name, Some(ty), None, None, None)
              syntheticProp.inferredType = Some(ty)
              globalTypeEnv = globalTypeEnv.union(name -> PropertyTypeInfo(syntheticProp, true, false, null))
            }
          case Left(errorMsg) =>
            error(s"Type inference failed: $errorMsg")
        }
      }
    }

    inferUndeclaredTypes()

    // pass: build the information for expressions
    // except expressions that are in functions (bodies)
    // Note: Do NOT recursively process packages here because each package gets its own TypeChecker
    model.decls.foreach { d =>
      d match {
        case ExpressionDecl(exp) => exp2Type.put(exp, getExpType(globalTypeEnv, exp, null))
        case ed @ EntityDecl(_, _, _, ident, _, _, _, _) =>
          ed.members.foreach { m =>
            m match {
              case ExpressionDecl(exp) => exp2Type.put(exp, getExpType(decl2TypeEnvi(ed), exp, ed))
              case _                   => ()
            }
          }
        case cd @ ConstraintDecl(name, exp, _) => exp2Type.put(exp, getExpType(globalTypeEnv, exp, null))
        case _                              => ()
      }
    }

    // pass: now process function bodies, property initializations etc. etc.
    // Note: Do NOT recursively process packages here because each package gets its own TypeChecker
    model.decls.foreach { d =>
      d match {
        case cd @ ConstraintDecl(name, exp, _) =>
          val ty = getExpType(globalTypeEnv, exp, null)
          if (ty != BoolType) {
            error(s"Condition $exp is not of type Bool.")
          }
          exp2Type.put(exp, ty)
        case fd @ FunDecl(_, _, _, _, _, _) =>
          processFunction(fd, globalTypeEnv, null)
        case ed @ EntityDecl(_, token, _, ident, _, _, _, _) =>
          val entityTypeEnv = decl2TypeEnvi(ed)

          ed.annotations.foreach { a =>
            val annotationExpType =
              if (a.exp != null) getExpType(entityTypeEnv, a.exp, ed)
              else UnitType
            val annotationType = annotations(a.name).ty
            if (!areTypesEqual(annotationExpType, annotationType, false))
              error(s"Annotation $a does not type check.")
          }

          ed.members.foreach { m =>
            m match {
              case cd @ ConstraintDecl(name, exp, _) =>
                val ty = getExpType(entityTypeEnv, exp, ed)
                if (ty != BoolType && ty != AnyType) {
                  error(s"Condition $exp is not of type Bool.")
                }
                exp2Type.put(exp, ty)
              case od @ OptimizeDecl(kind, exp, weight) =>
                val ty = getExpType(entityTypeEnv, exp, ed)
                if (ty != IntType && ty != RealType && ty != AnyType) {
                  error(s"Optimization expression $exp must be numeric (Int or Real), found $ty.")
                }
                exp2Type.put(exp, ty)
              case fd @ FunDecl(_, _, _, _, _, _) =>
                processFunction(fd, entityTypeEnv, ed)
              case pd @ PropertyDecl(_, _, _, _, _, _) =>
                pd.expr match {
                  case Some(e) =>
                    val exprType = getExpType(entityTypeEnv, e, ed)
                    // Only check type match if explicit type was provided (inference already set inferredType)
                    pd.ty match {
                      case Some(explicitType) =>
                        if (!areTypesEqual(exprType, explicitType, true)) {
                          error(s"Type does not match: ${pd.name}. Expected $explicitType, Found $exprType")
                        }
                      case None => // Type was inferred, already validated
                    }
                    exp2Type.put(e, exprType)
                  case None => ()
                }
              case ExpressionDecl(e) =>
                val exprType = getExpType(entityTypeEnv, e, ed)
                if (exprType != UnitType) {
                  error(s"Expression in class does not have unit type: $e\nMaybe you need to have the 'req' keyword before the expression?")
                }
                exp2Type.put(e, exprType)
              case _ => ()
            }
          }
        case pd @ PropertyDecl(_, _, _, _, _, _) =>
          if (!pd.expr.isEmpty) {
            val exprType = getExpType(globalTypeEnv, pd.expr.get, null)
            // Only check type match if explicit type was provided
            pd.ty match {
              case Some(explicitType) =>
                if (!areTypesEqual(exprType, explicitType, true)) {
                  error(s"Type does not match: ${pd.name}. + Expected $explicitType, Found $exprType")
                }
              case None => // Type was inferred, already validated
            }
            exp2Type.put(pd.expr.get, exprType)
          }
        case _ => ()
      }
    }

    // Recurse into packages
    // Note: When packages are processed, their classes are already in the global scope
    // from the combineModel step, so we don't need to process them again here
    // This prevents "already defined" errors when classes are in packages
    val pkgs = model.packages
    for (p <- pkgs) {
      var m = p.model
      if ( m != null ) {
        // Create a new TypeChecker but don't add classes to global scope again
        // The classes from packages are already processed when the model is combined
        // We only need to type-check the package's internal structure
        var t = new TypeChecker(m)
        // Temporarily save and restore global state to avoid duplicates
        val savedClasses = classes
        val savedGlobalTypeEnv = globalTypeEnv
        classes = Map[String, EntityDecl]()
        globalTypeEnv = TypeEnv(null, Map())
        t.typeCheck
        // Restore - package classes are already in the parent's scope
        classes = savedClasses
        globalTypeEnv = savedGlobalTypeEnv
      }
    }

    true
  }

  def processBody(body: List[MemberDecl], te: TypeEnv, owner: EntityDecl): TypeEnv = {
    var newTe = te
    body.foreach { m =>
      m match {
        case pd @ PropertyDecl(_, _, _, _, _, _) =>
          // Check if this PropertyDecl should be interpreted as an equality constraint
          // This happens when:
          // 1. No explicit type is given (pd.ty.isEmpty)
          // 2. The name is already declared in scope (newTe.contains(pd.name))
          // 3. There is an initialization expression (pd.expr.isDefined)
          // In this case, `name = expr` means `name == expr` (equality/constraint), not property declaration
          val shouldBeConstraint = pd.ty.isEmpty && pd.expr.isDefined && newTe.contains(pd.name)

          if (shouldBeConstraint) {
            // Convert to equality expression: name = expr becomes name == expr (BoolType)
            val identExp = IdentExp(pd.name)
            var equalityExp: BinExp = BinExp(identExp, EQ, pd.expr.get)

            // Handle grammar ambiguity BEFORE type checking:
            // When PropertyDecl captures "t1 = e.t1 && t2 = e.t2", the parser produces:
            //   PropertyDecl("t1", expr = BinExp(e.t1, AND, BinExp(t2, EQ, e.t2)))
            // We need to restructure to: BinExp(BinExp(t1, EQ, e.t1), AND, BinExp(t2, EQ, e.t2))
            // Check the pattern first to avoid type errors from evaluating "Real && Bool"
            pd.expr.get match {
              case BinExp(lhs, op, rhs) if op == AND || op == OR =>
                // Check if lhs has a type compatible with the variable (not Bool from &&/||)
                val varType = getExpType(newTe, identExp, owner)
                val lhsType = getExpType(newTe, lhs, owner)
                if (areTypesEqual(varType, lhsType, true) && lhsType != BoolType) {
                  // Restructure: name = (a && b) becomes (name = a) && b
                  val innerEquality = BinExp(identExp, EQ, lhs)
                  equalityExp = BinExp(innerEquality, op, rhs)
                  logDebug(s"Restructured '${pd.name} = $lhs $op $rhs' to '(${pd.name} = $lhs) $op $rhs'")
                }
              case _ => // No restructuring needed
            }

            // Store the conversion for later use
            propertyAsConstraint.put(pd, equalityExp)
            // Type check the (possibly restructured) equality expression
            val eqType = getExpType(newTe, equalityExp, owner)
            if (eqType != BoolType) {
              error(s"Equality constraint '${pd.name} = ${pd.expr.get}' does not type check to Bool, got $eqType")
            }
            exp2Type.put(equalityExp, BoolType)
            logDebug(s"PropertyDecl '${pd.name} = ...' interpreted as equality constraint (name already in scope)")
            // Don't add to type environment - it's a constraint, not a new declaration
          } else {
            // Normal property declaration handling
            pd.ty match {
              case Some(explicitType) =>
                // Explicit type given - validate it exists
                if (!doesTypeExist(newTe, explicitType)) {
                  error(s"Type $explicitType not found. Exiting.")
                }
                // Validate expression type matches if present
                pd.expr match {
                  case Some(e) =>
                    val exprType = getExpType(newTe, e, owner)
                    if (!areTypesEqual(exprType, explicitType, true)) {
                      error(s"Type does not match: ${pd.name}. Expected $explicitType, Found $exprType")
                    }
                    exp2Type.put(e, exprType)
                  case None => ()
                }
              case None =>
                // No explicit type - infer from initialization expression
                pd.expr match {
                  case Some(e) =>
                    val exprType = getExpType(newTe, e, owner)
                    pd.inferredType = Some(exprType)
                    exp2Type.put(e, exprType)
                    logDebug(s"Type inference: inferred type $exprType for property ${pd.name}")
                  case None =>
                    error(s"Cannot infer type for '${pd.name}': no type annotation and no initialization expression.")
                }
            }
            if (newTe.contains(pd.name)) {
              val typeInfo = newTe(pd.name)
              typeInfo match {
                case PropertyTypeInfo(_, false, false, _) => error(s"Redeclaring variable in block. ${pd.name}")
                case ParamTypeInfo(_)                     => error(s"Redeclaring variable in block. ${pd.name}")
                case _                                    => ()
              }
            }
            newTe = newTe.overwrite(pd.name -> PropertyTypeInfo(pd, false, false, owner))
          }
        case ExpressionDecl(exp @ IfExp(cond, tb, eb)) =>
          if (tb.isInstanceOf[BlockExp]) {
            exp2TypeEnv.put(tb, processBody(tb.asInstanceOf[BlockExp].body, newTe, owner))
          }
          if (!eb.isEmpty) {
            if (eb.get.isInstanceOf[BlockExp]) {
              exp2TypeEnv.put(eb.get, processBody(eb.get.asInstanceOf[BlockExp].body, newTe, owner))
            }
          }
          exp2TypeEnv.put(exp, newTe)
        case ExpressionDecl(exp) if exp.isInstanceOf[BlockExp] =>
          newTe = processBody(exp.asInstanceOf[BlockExp].body, newTe, owner)
          exp2TypeEnv.put(exp, newTe)
        case ExpressionDecl(exp) if !exp.isInstanceOf[BlockExp] =>
          exp2Type.put(exp, getExpType(newTe, exp, owner))
        case _ => ()
      }
    }
    newTe
  }

  def processFunction(fd: FunDecl, entityTypeEnv: TypeEnv, owner: EntityDecl): Unit = {

    // check if return type exists 
    if (!fd.ty.isEmpty) {
      if (!doesTypeExist(entityTypeEnv, fd.ty.get)) {
        error(s"Type ${fd.ty.get} not found. Exiting.")
      }
    }

    // parameters, properties
    var functionTypeEnv = fd.params.foldLeft(entityTypeEnv) {
      (fres, p) =>
        if (!doesTypeExist(fres, p.ty)) {
          error(s"Type ${p.ty} not found. Exiting.")
        }
        fres.overwrite(p.name -> ParamTypeInfo(p))
    }

    // type check spec
    for (s <- fd.spec) {
      getExpType(functionTypeEnv, s.exp, owner)
    }

    functionTypeEnv = processBody(fd.body, functionTypeEnv, owner)

    // process expressions in function
    var lastT: Type = null
    for (i <- Range(0, fd.body.length)) {
      val m = fd.body(i)
      val mType = {
        m match {
          case ExpressionDecl(exp) =>
            lastT = getExpType(functionTypeEnv, exp, owner)
            if (exp.isInstanceOf[ReturnExp] && !fd.ty.isEmpty) {
              if (!areTypesEqual(lastT, fd.ty.get, true))
                error(s"Return type does not match for $exp in function ${fd.ident}")
            }
            exp2Type.put(exp, lastT)
            lastT
          case pd: PropertyDecl if propertyAsConstraint.containsKey(pd) =>
            // This PropertyDecl was interpreted as an equality constraint
            lastT = BoolType
            BoolType
          case _ => UnitType
        }
      }
      if (i < fd.body.length - 1 && mType != UnitType)
        error(s"Non-unit type expression found in function body: $m")
      if (i == fd.body.length - 1 && !fd.ty.isEmpty) {
        if (!areTypesEqual(mType, fd.ty.get, true))
          error(s"Return type does not match for $m in function ${fd.ident}")
      }
    }

    // check return matches last expression type
    if (lastT != null && !fd.ty.isEmpty) {
      if (!areTypesEqual(fd.ty.get, lastT, true)) {
        error(s"Return type does not match body: ${fd.ident}. Expected ${fd.ty.get}, Found $lastT.")
      }
    }
    
    // Infer return type if not explicitly specified
    if (fd.ty.isEmpty && lastT != null) {
      fd.inferredType = Some(lastT)
      logDebug(s"Type inference: inferred return type $lastT for function ${fd.ident}")
    }

    decl2TypeEnvi = decl2TypeEnvi + (fd -> functionTypeEnv)

  }

  def getFunDecl(te: TypeEnv, exp: Exp, owner: EntityDecl): (Boolean, FunDecl) = {
    logDebug(s"getFunDecl: $exp in $te")

    val result: (Boolean, FunDecl) = exp match {
      case ParenExp(e) => getFunDecl(te, e, owner)
      case IdentExp(i) =>
        if (!te.contains(i)) {
          error(s"$i not found in scope.")
        }
        te(i) match {
          case pti @ FunctionTypeInfo(decl, _) => (false, decl)
          case _                               => error(s"Unexpected type found for expression during function application. $exp")
        }
      case DotExp(e, i) =>
        val ti = getExpType(te, e, owner)
        logDebug(i + " " + (i == "collect"))
        if (i == "toString") (true, null)
        else if (i == "collect") (true, null)
        else if (i == "size") (true, null)
        else if (i == "sum") (true, null)
        else if (i == "at") (true, null)
        else if (i == "subList") (true, null)
        else ti match {
          case it @ IdentType(_, _) =>
            if (Misc.isCollection(it)) {
              (true, null)
            } else {
              te(it.toString) match {
                case cti @ ClassTypeInfo(d) =>
                  val classTypeEnv = decl2TypeEnvi(d)
                  classTypeEnv(i) match {
                    case pti @ FunctionTypeInfo(decl, _) => (false, decl)
                    case _                               => error(s"Unknown type info received for expression when discovering function type. $exp")
                  }
              }
            }
          case _ => error(s"Unexpected expression type found in function application. $exp $ti")
        }

      case _ => error(s"Unexpected expression found in function application. $exp")
    }
    logDebug(s"getFunDecl: $exp -> $result")
    return result
  }

  def getExpType(te: TypeEnv, exp: Exp, owner: EntityDecl): Type = {
    logDebug(s"getExpType: $exp in $te")
    val result: Type = exp match {
      case ResultExp   => AnyType //TODO
      case ParenExp(e) => getExpType(te, e, owner)
      case IdentExp(i) =>
        // Check for known Java package roots first
        val javaPackageRoots = Set("java", "javax", "scala", "com", "org", "gov", "edu", "net")
        if (javaPackageRoots.contains(i)) {
          // This is a Java package root - return a special external type
          ExternalType(i)
        } else if (TypeChecker.javaImports.contains(i)) {
          // This is an imported Java class - return ExternalType with full qualified name
          ExternalType(TypeChecker.javaImports(i))
        } else if (!te.contains(i)) {
          error(s"$i not found in scope.")
        } else {
          te(i) match {
            case pti @ PropertyTypeInfo(decl, _, _, _) => getPropertyDeclType(decl)
            case pti @ ParamTypeInfo(p)                => p.ty
            case pti @ FunctionTypeInfo(decl, _) =>
              decl.getReturnTypeOrUnit
            case cti @ ClassTypeInfo(decl) =>
              //ClassType(QualifiedName(List(decl.ident)))
              IdentType(QualifiedName(List(decl.ident)), List())
            case pti @ PatternTypeInfo(p, t) => t
            case tt @ _ =>
              error(s"Type could not be found for $exp." + tt.getClass)
          }
        }
      case DotExp(e, i) =>
        val ti = getExpType(te, e, owner)
        ti match {
          case it @ IdentType(_, _) =>
            if (Misc.isCollection(it)) {
              // Collection operations
              if (i == "collect") CollectType(it.args)
              else if (i == "size" || i == "length") IntType  // size returns Int
              else if (i == "isEmpty") BoolType  // isEmpty returns Bool
              else if (i == "sum") SumType(it.args)
              else if (i == "at" || i == "head" || i == "first" || i == "last" || i == "get" || i == "apply") {
                // Element access returns the element type
                if (it.args.nonEmpty) it.args.head else IntType
              }
              else if (i == "tail") it  // tail returns same collection type
              else if (i == "subList") CollectType(it.args)
              else error(s"getExpType: error, type could not be discovered for $exp.")
            } else {
              if (i == "collect") CollectType(List(it))
              else if (i == "size" || i == "length") IntType  // size returns Int
              else if (i == "sum") SumType(it.args)
              else if (i == "at") SumType(it.args)
              else if (i == "toString") StringType
              else {
                // get class type environment
                val classTypeEnv = decl2TypeEnvi(classes(it.ident.toString))
                logDebug(s"classTypeEnv is $classTypeEnv")
                classTypeEnv(i) match {
                  case pti @ PropertyTypeInfo(decl, _, _, _) => getPropertyDeclType(decl)
                  case pti @ ParamTypeInfo(p)                => p.ty
                  case pti @ FunctionTypeInfo(decl, _)       => decl.ty.get
                  case _                                     => error(s"Given expression does not type check: $exp.")

                }
              }
            }
          case StringType =>
            // String methods
            if (i == "length") IntType
            else if (i == "toString") StringType
            else error(s"Unknown string property: $i")
          case ExternalType(qname) =>
            // Extending external type path - e.g., java.lang -> java.lang.Math
            ExternalType(qname + "." + i)
          case tt @ _ =>
            if (i == "collect") CollectType(List(tt))
            else if (i == "size") SumType(List(tt))
            else if (i == "sum") SumType(List(tt))
            else if (i == "at") SumType(List(tt))
            else if (i == "toString") StringType
            else tt
        }
      case BinExp(exp1, op, exp2) =>
        val ty1 = getExpType(te, exp1, owner)
        val ty2 = getExpType(te, exp2, owner)
        logDebug(s"Types are $exp1:$ty1 and $exp2:$ty2")
        op match {
          case LT | LTE | GT | GTE | AND | IMPL | OR | IFF | NEQ | EQ =>
            if (!areTypesEqual(ty1, ty2, true)) error(s"$exp does not type check. $ty1 and $ty2 are not equivalent.")
            BoolType
          case MUL | DIV | ADD | SUB | REM =>
            if (!areTypesEqual(ty1, ty2, false)) error(s"$exp does not type check. $ty1 and $ty2 are not equivalent.")
            ty1
          case ASSIGN =>
            if (!areTypesEqual(ty1, ty2, false)) error(s"$exp does not type check. $ty1 and $ty2 are not equivalent.")
            UnitType
          case ISIN | NOTISIN | SUBSET | PSUBSET =>
            val (typesCompat, cType) = Misc.typeTypeCollection(ty1, ty2)
            if (!typesCompat) error(s"$exp does not type check. $ty1 and $ty2 are not compatible.")
            BoolType
          case SETUNION | SETDIFF | SETINTER =>
            val (typesCompat, cType) = Misc.typeTypeCollection(ty1, ty2)
            if (!typesCompat) error(s"$exp does not type check. $ty1 and $ty2 are not compatible.")
            cType
          case TUPLEINDEX =>
            if (ty2 != IntType) error("Tuple index is not an integer.")
            ty1 match {
              case CartesianType(types) => types(exp2.toString.toInt - 1)
              case _                    => error(s"Non tuple type found with tuple indexing. $exp")
            }
        }
      case FunApplExp(fexp, args) =>
        // Check if this is a string method call
        fexp match {
          case DotExp(strExp, methodName) if getExpType(te, strExp, owner) == StringType =>
            // String method calls
            methodName match {
              case "startsWith" | "endsWith" | "contains" =>
                // These methods take a String argument and return Bool
                if (args.length != 1) error(s"$methodName requires exactly 1 argument")
                val argType = getExpType(te, args(0).asInstanceOf[PositionalArgument].exp, owner)
                if (argType != StringType) error(s"$methodName argument must be String, got $argType")
                return BoolType
              case "matches" =>
                // matches(pattern) returns Bool - regex matching
                if (args.length != 1) error(s"matches requires exactly 1 argument (regex pattern)")
                val argType = getExpType(te, args(0).asInstanceOf[PositionalArgument].exp, owner)
                if (argType != StringType) error(s"matches argument must be String (regex pattern), got $argType")
                return BoolType
              case "substring" =>
                // substring(start, end) returns String
                if (args.length != 2) error(s"substring requires exactly 2 arguments")
                val arg1Type = getExpType(te, args(0).asInstanceOf[PositionalArgument].exp, owner)
                val arg2Type = getExpType(te, args(1).asInstanceOf[PositionalArgument].exp, owner)
                if (arg1Type != IntType || arg2Type != IntType)
                  error(s"substring arguments must be Int, got $arg1Type and $arg2Type")
                return StringType
              case "charAt" | "at" =>
                // charAt(index) returns String (single character)
                if (args.length != 1) error(s"$methodName requires exactly 1 argument")
                val argType = getExpType(te, args(0).asInstanceOf[PositionalArgument].exp, owner)
                if (argType != IntType) error(s"$methodName argument must be Int, got $argType")
                return StringType
              case "indexOf" | "lastIndexOf" =>
                // indexOf(str) returns Int
                if (args.length != 1) error(s"$methodName requires exactly 1 argument")
                val argType = getExpType(te, args(0).asInstanceOf[PositionalArgument].exp, owner)
                if (argType != StringType) error(s"$methodName argument must be String, got $argType")
                return IntType
              case "replace" =>
                // replace(old, new) returns String
                if (args.length != 2) error(s"replace requires exactly 2 arguments")
                val arg1Type = getExpType(te, args(0).asInstanceOf[PositionalArgument].exp, owner)
                val arg2Type = getExpType(te, args(1).asInstanceOf[PositionalArgument].exp, owner)
                if (arg1Type != StringType || arg2Type != StringType)
                  error(s"replace arguments must be String, got $arg1Type and $arg2Type")
                return StringType
              case "toUpper" | "toLower" =>
                // Case conversion methods take no arguments, return String
                // Note: Requires Z3 4.12+ (current version may not support)
                if (args.length != 0) error(s"$methodName takes no arguments")
                log(s"Warning: $methodName requires Z3 4.12+, current Z3 version may not support it")
                return StringType
              case "toInt" =>
                // toInt() returns Int
                // Note: Requires Z3 4.8+ (current version may not support)
                if (args.length != 0) error(s"toInt takes no arguments")
                log(s"Warning: toInt requires Z3 4.8+, current Z3 version may not support it")
                return IntType
              case _ =>
                // Fall through to regular function handling
            }
          case _ =>
            // Not a string method, continue with regular handling
        }

        // Check for external Java function calls (e.g., java.lang.Math.sqrt)
        val fexpType = getExpType(te, fexp, owner)
        fexpType match {
          case ExternalType(qname) =>
            // This is an external Java function call
            // Type check arguments (all should be valid expressions)
            args.foreach { arg =>
              arg match {
                case PositionalArgument(e) => getExpType(te, e, owner)
                case NamedArgument(_, e) => getExpType(te, e, owner)
              }
            }
            // Return Real by default for external functions
            // TODO: Could use reflection to determine actual return type
            return RealType
          case _ =>
            // Not an external call, continue with regular handling
        }

        val callToConstructor = isConstructorCall(te, fexp)
        if (!callToConstructor.isEmpty) {
          val ty = callToConstructor.get
          val decl = type2Decl(ty)

          assert(decl.isInstanceOf[EntityDecl])

          val declTypeEnvironment = decl2TypeEnvi(decl)

          if (!args.forall { a => a.isInstanceOf[NamedArgument] })
            error(s"Have to use named arguments in a constructor function call: $exp")

          if (!args.forall {
            a =>
              val namedArg = a.asInstanceOf[NamedArgument]
              val lhsType =
                {
                  declTypeEnvironment(namedArg.ident) match {
                    case PropertyTypeInfo(pd, _, _, _) => pd.getTypeOrError
                    case _                             => error(s"Property ${namedArg.ident} could not be found for ${decl.asInstanceOf[EntityDecl].ident}")
                  }
                }
              val rhsType = getExpType(te, namedArg.exp, owner)
              val res = areTypesEqual(lhsType, rhsType, false)
              if (!res) {
                log(s"Types are $lhsType $rhsType.")
              }
              res
          })
            error(s"Incorrect arguments to constructor call: $exp")

          ty
        } else {
          var functionReturnType = getExpType(te, fexp, owner)
          var (collectionFunction, functionDecl) = getFunDecl(te, fexp, owner)

          if (!collectionFunction && functionDecl == null)
            error(s"Could not find function for $exp.")

          // ensure arguments match param types (unless collection function)
          if (!collectionFunction && !args.forall { a => a.isInstanceOf[PositionalArgument] })
            error(s"Cannot use named arguments to a non-constructor function call: $exp")

          if (!collectionFunction && args.length != functionDecl.params.length)
            error(s"Incorrect number of arguments $exp")

          if (!collectionFunction &&
            !((functionDecl.params zip args).forall { pa =>
              val p2Type = getExpType(te, pa._2.asInstanceOf[PositionalArgument].exp, owner)
              areTypesEqual(pa._1.ty, p2Type, false)
            }))
            error(s"Arguments to function seem incorrect: $exp")
          else if (collectionFunction) {
            args.foreach { x => getExpType(te, x.asInstanceOf[PositionalArgument].exp, owner) }
          }

          functionReturnType match {
            case CollectType(t) =>
              if (args.length > 1)
                IdentType(QualifiedName(List("Seq")), t)
              else {
                assert(args.length <= 1)
                // TODO
                // assuming lambda expression as only argument
                // assuming ident pattern in lambda expression
                val lambdaExp = args.last.asInstanceOf[PositionalArgument].exp.asInstanceOf[LambdaExp]
                val lambdaTe = te.overwrite(lambdaExp.pat.asInstanceOf[IdentPattern].ident -> te(t.last.toString))
                // CollectType(List(getExpType(lambdaTe, lambdaExp)))
                IdentType(QualifiedName(List("Seq")), List(getExpType(lambdaTe, lambdaExp, owner)))
              }
            case SumType(t) => IntType
            case _          => functionReturnType
          }
        }
      case WhileExp(cond, body) =>
        if (getExpType(te, cond, owner) != BoolType) {
          error(s"Branch condition $cond does not evaluate to Bool. Please check.")
        }
        getExpType(te, body, owner)
      case IfExp(cond, tb, eb) =>
        if (getExpType(te, cond, owner) != BoolType) {
          error(s"Branch condition $cond does not evaluate to Bool. Please check.")
        }
        val tbType = getExpType(te, tb, owner)
        val ebType = eb match { case Some(ebb) => getExpType(te, ebb, owner) case None => tbType }
        if (!areTypesEqual(tbType, ebType, true)) {
          error("Then and Else branch types are different.")
        }
        tbType
      case BlockExp(body) =>
        logDebug(s"Get type environment for BlockExp $body ${exp2TypeEnv.containsKey(exp)}")
        var blockTe = if (exp2TypeEnv.containsKey(exp)) exp2TypeEnv.get(exp) else processBody(body, te, owner)

        var lastType: Type = UnitType
        val bodyTypes = body.foreach {
          b =>
            b match {
              case ExpressionDecl(e)              => lastType = getExpType(blockTe, e, owner)
              case PropertyDecl(_, _, _, _, _, _) => UnitType
              case _                              => error(s"Unsupported member in block: $b")
            }
        }
        lastType
      case UnaryExp(op, exp)   => getExpType(te, exp, owner)
      case TupleExp(exps)      => CartesianType(exps.map { e => getExpType(te, e, owner) })
      case LambdaExp(pat, exp) => getExpType(te, exp, owner)
      case ReturnExp(exp)      => getExpType(te, exp, owner)
      case ForExp(pattern, exp, body) =>
        val newTe = pattern match {
          case TypedPattern(ident, ty) if ident.isInstanceOf[IdentPattern] =>
            te.overwrite(ident.asInstanceOf[IdentPattern].ident -> PatternTypeInfo(pattern, ty))
          case _ => error(s"Must use a typed pattern in for expressions: $exp")
        }
        if (getExpType(newTe, exp, owner) != BoolType)
          error(s"$exp is not of type Bool")
        getExpType(newTe, body, owner)
      case TypeCastCheckExp(cast, e, ty) =>
        val eType = getExpType(te, e, owner)
        if (cast) ty else BoolType
      case QuantifiedExp(q, b, e) =>

        // process bindings
        val newTe = b.foldLeft(te) { (res, bndg) =>
          bndg.patterns.foldLeft(res) { (res2, p) =>
            val collectionType = bndg.collection match {
              case ExpCollection(collE)   => 
                // Handle case where primitive types are parsed as expressions
                collE match {
                  case IdentExp("Int") => IntType
                  case IdentExp("Real") => RealType
                  case IdentExp("Bool") => BoolType
                  case IdentExp("String") => StringType
                  case IdentExp("Char") => CharType
                  case IdentExp("Unit") => UnitType
                  case _ => getExpType(te, collE, owner)
                }
              case TypeCollection(collTy) => collTy
            }
            val singleType = Misc.removeCollection(collectionType)
            p match {
              case IdentPattern(ident)      => res2.overwrite(ident -> PatternTypeInfo(p, singleType))
              case ProductPattern(patterns) => error(s"Currently only identifier and product patterns are supported. $exp")
              case _                        => error(s"Currently only identifier and product patterns are supported. $exp")
            }
          }
        }
        if (getExpType(newTe, e, owner) != BoolType)
          error(s"$exp does not evaluate to Bool")
        BoolType
      case CollectionEnumExp(kind, exps) =>
        var expsType: Type = UnitType
        for (e <- exps) {
          val eType = getExpType(te, e, owner)
          if (expsType == UnitType || (expsType == eType)) {
            expsType = eType
          } else {
            error(s"CollectionEnumExp: $exp does not type check.")
          }
        }
        IdentType(QualifiedName(List(kind.toString)), List(expsType))
      case CollectionRangeExp(kind, e1, e2) =>
        val e1Type = getExpType(te, e1, owner)
        val e2Type = getExpType(te, e2, owner)
        if (e1Type != e2Type) {
          error(s"CollectionRangeExp: $exp does not type check.")
        }
        IdentType(QualifiedName(List(kind.toString)), List(e1Type))
      case CollectionComprExp(kind, e1, bindings, e2) =>
        val newTe = bindings.foldLeft(te) { (res, bndg) =>
          bndg.patterns.foldLeft(res) { (res2, p) =>
            val collectionType = bndg.collection match {
              case ExpCollection(collE)   =>
                // Handle case where primitive types are parsed as expressions
                collE match {
                  case IdentExp("Int") => IntType
                  case IdentExp("Real") => RealType
                  case IdentExp("Bool") => BoolType
                  case IdentExp("String") => StringType
                  case IdentExp("Char") => CharType
                  case IdentExp("Unit") => UnitType
                  case _ => getExpType(te, collE, owner)
                }
              case TypeCollection(collTy) => collTy
            }
            val singleType = Misc.removeCollection(collectionType)
            p match {
              case IdentPattern(ident)      => res2.overwrite(ident -> PatternTypeInfo(p, singleType))
              case ProductPattern(patterns) => error(s"Currently only identifier and product patterns are supported. $exp")
              case _                        => error(s"Currently only identifier and product patterns are supported. $exp")
            }
          }
        }
        val e1Type = getExpType(newTe, e1, owner)
        val e2Type = getExpType(newTe, e2, owner)
        if (e2Type != BoolType) {
          error(s"CollectionComprExp: $exp does not type check.")
        }
        IdentType(QualifiedName(List(kind.toString)), List(e1Type))
      case IntegerLiteral(_)   => IntType
      case BooleanLiteral(_)   => BoolType
      case CharacterLiteral(_) => CharType
      case StringLiteral(_)    => StringType
      case RealLiteral(_)      => RealType
      case DateLiteral(_)      => TimeType
      case DurationLiteral(_)  => DurationType
      case ThisLiteral =>
        type2Decl.map(_.swap).asInstanceOf[Map[TopDecl, Type]](te("this").asInstanceOf[ClassTypeInfo].decl)
      case _ => error(s"Type checking for ${exp.getClass} not implemented yet!")
    }
    exp2Type.put(exp, result)
    exp2TypeEnv.put(exp, te)

    logDebug(s"getExpType (end): $exp $result")

    return result
  }

  def inferTypeFrom(exp: String, ty: Type) = ty

  typeCheck
}