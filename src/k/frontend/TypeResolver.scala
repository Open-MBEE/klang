package k.frontend

import k.frontend._
import java.util.{IdentityHashMap => IMap}

/**
 * TypeResolver builds the type information needed for SMT generation.
 * This is a minimal replacement for the full TypeChecker that only resolves types
 * without performing validation (which is now done by KTypeChecker via Z3).
 *
 * Eventually, this could also be expressed as a K program for full self-hosting.
 */
object TypeResolver {

  def log(msg: String) = Misc.log("TypeResolver", msg)

  /**
   * Resolve types for the given model, populating TypeChecker's global state
   * that is used by toSMT methods in AbstractSyntax.scala.
   */
  def resolve(model: Model): Unit = {
    // Reset shared state
    TypeChecker.reset()

    // Build basic maps first
    buildTypeDeclarations(model)
    buildClassMap(model)
    buildClassHierarchy(model)

    // Build type environments for each class
    buildTypeEnvironments(model)

    // Resolve expression types
    resolveExpressionTypes(model)
  }

  /**
   * Build type2Decl and keywords maps from model declarations.
   */
  private def buildTypeDeclarations(model: Model): Unit = {
    def processDecls(decls: List[TopDecl]): Unit = {
      for (decl <- decls) {
        decl match {
          case ed @ EntityDecl(_, _, _, ident, _, _, _, _) =>
            val ty = IdentType(QualifiedName(List(ident)), Nil)
            TypeChecker.type2Decl += (ty -> ed)
            TypeChecker.keywords += (ident -> ty)
          case td @ TypeDecl(ident, _, body) =>
            val ty = IdentType(QualifiedName(List(ident)), Nil)
            TypeChecker.type2Decl += (ty -> td)
            TypeChecker.keywords += (ident -> ty)
          case _ =>
        }
      }
    }

    def processModel(m: Model): Unit = {
      processDecls(m.decls)
      m.packages.foreach(pkg => processModel(pkg.model))
    }

    processModel(model)
  }

  /**
   * Build the classes map from class name to EntityDecl.
   */
  private def buildClassMap(model: Model): Unit = {
    def processDecls(decls: List[TopDecl], parentClass: Option[String]): Unit = {
      for (decl <- decls) {
        decl match {
          case ed @ EntityDecl(_, _, _, ident, _, _, _, members) =>
            TypeChecker.classes += (ident -> ed)
            // Track nested class parent
            parentClass.foreach { parent =>
              TypeChecker.nestedClassParent += (ident -> parent)
            }
            // Process nested classes
            for (member <- members) {
              member match {
                case nested @ EntityDecl(_, _, _, nestedIdent, _, _, _, _) =>
                  TypeChecker.classes += (nestedIdent -> nested)
                  TypeChecker.nestedClassParent += (nestedIdent -> ident)
                case _ =>
              }
            }
          case _ =>
        }
      }
    }

    def processModel(m: Model): Unit = {
      processDecls(m.decls, None)
      m.packages.foreach(pkg => processModel(pkg.model))
    }

    processModel(model)
  }

  /**
   * Build class hierarchy (parents/children relationships).
   */
  private def buildClassHierarchy(model: Model): Unit = {
    ClassHierarchy.buildHierarchy(model)
  }

  /**
   * Build type environments for each class (property name -> type mappings).
   */
  private def buildTypeEnvironments(model: Model): Unit = {
    def getPropertyType(pd: PropertyDecl): Type = {
      val baseType = pd.ty.getOrElse {
        // Infer type from expression if no explicit type
        pd.expr.map(e => inferExprType(e)).getOrElse(AnyType)
      }
      // Handle multiplicity
      if (!pd.multiplicity.isEmpty) {
        if (pd.modifiers.contains(Unique))
          IdentType(QualifiedName(List("Set")), List(baseType))
        else if (pd.modifiers.contains(Ordered))
          IdentType(QualifiedName(List("Seq")), List(baseType))
        else
          IdentType(QualifiedName(List("Bag")), List(baseType))
      } else baseType
    }

    def processEntityDecl(ed: EntityDecl, parentEnv: TypeEnv): Unit = {
      var envMap: Map[String, TypeInfo] = parentEnv.map

      // Add properties to type environment
      for (member <- ed.members) {
        member match {
          case pd: PropertyDecl =>
            // PropertyTypeInfo: (decl, global, classMember, owner)
            envMap = envMap + (pd.name -> PropertyTypeInfo(pd, false, true, ed))
          case fd: FunDecl =>
            envMap = envMap + (fd.ident -> FunctionTypeInfo(fd, ed))
          case nested @ EntityDecl(_, _, _, nestedIdent, _, _, _, _) =>
            envMap = envMap + (nestedIdent -> ClassTypeInfo(nested))
            processEntityDecl(nested, TypeEnv(ed, envMap))
          case _ =>
        }
      }

      // Add inherited properties
      for (parentType <- ed.extending) {
        TypeChecker.type2Decl.get(parentType).foreach {
          case parent: EntityDecl =>
            val pEnv = TypeChecker.decl2TypeEnvi.getOrElse(parent, TypeEnv(null, Map()))
            for ((name, info) <- pEnv.map if !envMap.contains(name)) {
              envMap = envMap + (name -> info)
            }
          case _ =>
        }
      }

      TypeChecker.decl2TypeEnvi += (ed -> TypeEnv(ed, envMap))
    }

    def processDecls(decls: List[TopDecl]): Unit = {
      // First pass: create empty environments
      for (decl <- decls) {
        decl match {
          case ed @ EntityDecl(_, _, _, _, _, _, _, _) =>
            if (!TypeChecker.decl2TypeEnvi.contains(ed)) {
              TypeChecker.decl2TypeEnvi += (ed -> TypeEnv(ed, Map()))
            }
          case _ =>
        }
      }
      // Second pass: populate with properties
      for (decl <- decls) {
        decl match {
          case ed @ EntityDecl(_, _, _, _, _, _, _, _) =>
            processEntityDecl(ed, TypeChecker.globalTypeEnv)
          case _ =>
        }
      }
    }

    def processModel(m: Model): Unit = {
      processDecls(m.decls)
      m.packages.foreach(pkg => processModel(pkg.model))
    }

    // Build global type env first
    var globalMap: Map[String, TypeInfo] = Map()
    for (decl <- model.decls) {
      decl match {
        case pd: PropertyDecl =>
          // PropertyTypeInfo: (decl, global, classMember, owner)
          globalMap = globalMap + (pd.name -> PropertyTypeInfo(pd, true, false, null))
        case fd: FunDecl =>
          globalMap = globalMap + (fd.ident -> FunctionTypeInfo(fd, null))
        case ed @ EntityDecl(_, _, _, ident, _, _, _, _) =>
          globalMap = globalMap + (ident -> ClassTypeInfo(ed))
        case _ =>
      }
    }
    TypeChecker.globalTypeEnv = TypeEnv(null, globalMap)

    processModel(model)
  }

  /**
   * Resolve types for all expressions in the model, populating exp2Type.
   * This recursively traverses all sub-expressions.
   */
  private def resolveExpressionTypes(model: Model): Unit = {
    /**
     * Recursively resolve types for an expression and all its sub-expressions.
     * Stores the type in exp2Type for each expression.
     */
    def resolveExp(exp: Exp, typeEnv: TypeEnv, owner: EntityDecl): Type = {
      // First resolve all sub-expressions, then compute type for this expression
      val result: Type = exp match {
        case ParenExp(e) =>
          resolveExp(e, typeEnv, owner)

        case IdentExp(i) =>
          if (typeEnv.contains(i)) {
            typeEnv(i) match {
              case PropertyTypeInfo(decl, _, _, _) => getPropertyDeclType(decl)
              case ParamTypeInfo(p) => p.ty
              case FunctionTypeInfo(decl, _) => decl.getReturnTypeOrUnit
              case ClassTypeInfo(decl) => IdentType(QualifiedName(List(decl.ident)), List())
              case PatternTypeInfo(_, t) => t
              case _ => AnyType
            }
          } else AnyType

        case DotExp(e, i) =>
          val baseType = resolveExp(e, typeEnv, owner)
          resolveDotType(baseType, i)

        case BinExp(exp1, op, exp2) =>
          val ty1 = resolveExp(exp1, typeEnv, owner)
          val ty2 = resolveExp(exp2, typeEnv, owner)
          op match {
            case LT | LTE | GT | GTE | AND | IMPL | OR | IFF | NEQ | EQ | ISIN | NOTISIN | SUBSET | PSUBSET =>
              BoolType
            case MUL | DIV | ADD | SUB | REM =>
              ty1
            case ASSIGN =>
              UnitType
            case SETUNION | SETDIFF | SETINTER =>
              ty1
            case TUPLEINDEX =>
              ty1 match {
                case CartesianType(types) =>
                  val idx = exp2.toString.toIntOption.getOrElse(1) - 1
                  if (idx >= 0 && idx < types.length) types(idx) else AnyType
                case _ => AnyType
              }
            case BITAND | BITOR | BITXOR =>
              (ty1, ty2) match {
                case (bv: BitVecType, _) => bv
                case (_, bv: BitVecType) => bv
                case _ => IntType
              }
            case BITSHL | BITSHR | BITASHR =>
              ty1
            case _ => AnyType
          }

        case UnaryExp(op, e) =>
          val eType = resolveExp(e, typeEnv, owner)
          op match {
            case NOT => BoolType
            case NEG => eType
            case BITNOT => eType
            case PREV => eType
          }

        case IfExp(cond, thenExp, elseExp) =>
          resolveExp(cond, typeEnv, owner)
          val thenType = resolveExp(thenExp, typeEnv, owner)
          elseExp.foreach(e => resolveExp(e, typeEnv, owner))
          thenType

        case FunApplExp(fexp, args) =>
          val funType = resolveExp(fexp, typeEnv, owner)
          // Resolve argument types
          args.foreach {
            case PositionalArgument(e) => resolveExp(e, typeEnv, owner)
            case NamedArgument(_, e) => resolveExp(e, typeEnv, owner)
          }
          funType match {
            case FunctionType(_, result) => result
            case IdentType(QualifiedName(List(name)), _) if TypeChecker.classes.contains(name) =>
              funType // Constructor call
            case _ =>
              fexp match {
                case DotExp(_, method) => resolveMethodReturnType(funType, method, args)
                case IdentExp(name) =>
                  TypeChecker.globalTypeEnv.map.get(name).map {
                    case FunctionTypeInfo(fd, _) => fd.getReturnTypeOrUnit
                    case _ => AnyType
                  }.getOrElse(AnyType)
                case _ => AnyType
              }
          }

        case CtorApplExp(ty, args) =>
          // Resolve argument types
          args.foreach {
            case PositionalArgument(e) => resolveExp(e, typeEnv, owner)
            case NamedArgument(_, e) => resolveExp(e, typeEnv, owner)
          }
          ty

        case IntegerLiteral(_) => IntType
        case RealLiteral(_) => RealType
        case CharacterLiteral(_) => CharType
        case StringLiteral(_) => StringType
        case BooleanLiteral(_) => BoolType
        case NullLiteral => NullType

        case TupleExp(exps) =>
          CartesianType(exps.map(e => resolveExp(e, typeEnv, owner)))

        case CollectionEnumExp(kind, exps) =>
          val elemTypes = exps.map(e => resolveExp(e, typeEnv, owner))
          val elemType = elemTypes.headOption.getOrElse(AnyType)
          IdentType(QualifiedName(List(kind.toString)), List(elemType))

        case CollectionRangeExp(kind, exp1, exp2) =>
          val ty1 = resolveExp(exp1, typeEnv, owner)
          resolveExp(exp2, typeEnv, owner)
          IdentType(QualifiedName(List(kind.toString)), List(ty1))

        case LambdaExp(pat, body) =>
          val resultType = resolveExp(body, typeEnv, owner)
          FunctionType(AnyType, resultType)

        case QuantifiedExp(_, bindings, body) =>
          // Resolve type for the range expressions in bindings
          bindings.foreach {
            case RngBinding(_, collection) =>
              collection match {
                case ExpCollection(e) => resolveExp(e, typeEnv, owner)
                case _ =>
              }
          }
          resolveExp(body, typeEnv, owner)
          BoolType

        case StarExp => AnyType
        case ResultExp => AnyType

        case _ => AnyType
      }

      // Store the resolved type
      TypeChecker.exp2Type.put(exp, result)
      TypeChecker.exp2TypeEnv.put(exp, typeEnv)
      result
    }

    def processMembers(members: List[MemberDecl], owner: EntityDecl): Unit = {
      val typeEnv = TypeChecker.decl2TypeEnvi.getOrElse(owner, TypeChecker.globalTypeEnv)
      for (member <- members) {
        member match {
          case pd: PropertyDecl =>
            pd.expr.foreach(e => resolveExp(e, typeEnv, owner))
          case fd: FunDecl =>
            // FunDecl.body is List[MemberDecl], process each member
            for (bodyMember <- fd.body) {
              bodyMember match {
                case exprDecl: ExpressionDecl => resolveExp(exprDecl.exp, typeEnv, owner)
                case cd: ConstraintDecl => resolveExp(cd.exp, typeEnv, owner)
                case _ =>
              }
            }
          case cd: ConstraintDecl =>
            resolveExp(cd.exp, typeEnv, owner)
          case nested @ EntityDecl(_, _, _, _, _, _, _, nestedMembers) =>
            processMembers(nestedMembers, nested)
          case ed: ExpressionDecl =>
            resolveExp(ed.exp, typeEnv, owner)
          case _ =>
        }
      }
    }

    def processDecls(decls: List[TopDecl]): Unit = {
      for (decl <- decls) {
        decl match {
          case ed @ EntityDecl(_, _, _, _, _, _, _, members) =>
            processMembers(members, ed)
          case pd: PropertyDecl =>
            pd.expr.foreach(e => resolveExp(e, TypeChecker.globalTypeEnv, null))
          case cd: ConstraintDecl =>
            resolveExp(cd.exp, TypeChecker.globalTypeEnv, null)
          case exprDecl: ExpressionDecl =>
            resolveExp(exprDecl.exp, TypeChecker.globalTypeEnv, null)
          case _ =>
        }
      }
    }

    def processModel(m: Model): Unit = {
      processDecls(m.decls)
      m.packages.foreach(pkg => processModel(pkg.model))
    }

    processModel(model)
  }

  /**
   * Simple type inference for property initializers.
   * Used during buildTypeEnvironments before resolveExpressionTypes runs.
   */
  private def inferExprType(exp: Exp): Type = {
    exp match {
      case IntegerLiteral(_) => IntType
      case RealLiteral(_) => RealType
      case CharacterLiteral(_) => CharType
      case StringLiteral(_) => StringType
      case BooleanLiteral(_) => BoolType
      case NullLiteral => NullType
      case ParenExp(e) => inferExprType(e)
      case _ => AnyType
    }
  }

  /**
   * Resolve the type of a dot expression given base type and member name.
   */
  private def resolveDotType(baseType: Type, member: String): Type = {
    baseType match {
      case it @ IdentType(_, _) =>
        if (Misc.isCollection(it)) {
          member match {
            case "size" | "length" => IntType
            case "isEmpty" => BoolType
            case "head" | "first" | "last" | "get" | "apply" | "at" =>
              if (it.args.nonEmpty) it.args.head else IntType
            case "tail" | "subList" => it
            case "sum" => SumType(it.args)
            case "collect" => CollectType(it.args)
            case _ => AnyType
          }
        } else {
          // Look up in class type environment
          val className = it.ident.toString
          TypeChecker.classes.get(className).flatMap { classDecl =>
            TypeChecker.decl2TypeEnvi.get(classDecl).flatMap { typeEnv =>
              typeEnv.map.get(member).map {
                case PropertyTypeInfo(pd, _, _, _) => getPropertyDeclType(pd)
                case ParamTypeInfo(p) => p.ty
                case FunctionTypeInfo(fd, _) => fd.getReturnTypeOrUnit
                case _ => AnyType
              }
            }
          }.getOrElse {
            // Built-in methods
            member match {
              case "size" | "length" => IntType
              case "toString" => StringType
              case _ => AnyType
            }
          }
        }

      case StringType =>
        member match {
          case "length" => IntType
          case "toString" => StringType
          case _ => AnyType
        }

      case ExternalType(qname) =>
        ExternalType(qname + "." + member)

      case PythonExternalType(qname) =>
        PythonExternalType(qname + "." + member)

      case _ =>
        member match {
          case "toString" => StringType
          case "size" | "length" => IntType
          case _ => AnyType
        }
    }
  }

  /**
   * Resolve return type of a method call.
   */
  private def resolveMethodReturnType(baseType: Type, method: String, args: List[Argument]): Type = {
    baseType match {
      case it @ IdentType(_, _) if Misc.isCollection(it) =>
        method match {
          case "at" | "get" | "apply" | "head" | "first" | "last" =>
            if (it.args.nonEmpty) it.args.head else IntType
          case "size" | "length" => IntType
          case "isEmpty" | "contains" => BoolType
          case "filter" | "take" | "drop" | "subList" => it
          case "map" => it
          case _ => AnyType
        }

      case StringType =>
        method match {
          case "length" => IntType
          case "substring" | "toLowerCase" | "toUpperCase" | "trim" => StringType
          case "charAt" => CharType
          case "contains" | "startsWith" | "endsWith" | "isEmpty" => BoolType
          case _ => AnyType
        }

      case _ => AnyType
    }
  }

  /**
   * Get the type of a property declaration, accounting for multiplicity.
   */
  private def getPropertyDeclType(decl: PropertyDecl): Type = {
    val baseType = decl.ty.getOrElse(AnyType)
    if (!decl.multiplicity.isEmpty) {
      if (decl.modifiers.contains(Unique))
        IdentType(QualifiedName(List("Set")), List(baseType))
      else if (decl.modifiers.contains(Ordered))
        IdentType(QualifiedName(List("Seq")), List(baseType))
      else if (decl.modifiers.contains(Ordered) && decl.modifiers.contains(Unique))
        IdentType(QualifiedName(List("OSet")), List(baseType))
      else
        IdentType(QualifiedName(List("Bag")), List(baseType))
    } else baseType
  }
}
