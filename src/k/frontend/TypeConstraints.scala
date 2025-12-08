package k.frontend

import scala.collection.mutable

/**
 * Type constraint-based inference for K language.
 *
 * This module infers types for undeclared variables by collecting constraints
 * from expressions and solving them. For example:
 *   - `x > 0` implies x must be numeric (Int or Real)
 *   - `x = true` implies x must be Bool
 *   - `x = y` implies x and y must have the same type
 *   - `x + 1` implies x must be numeric
 *   - `x + "hello"` implies x must be String (concatenation)
 */

// Type variable representing an unknown type
case class TypeVar(name: String) {
  override def toString = s"?$name"
}

// Type constraints
sealed trait TypeConstraint
case class MustBeType(tv: TypeVar, ty: Type) extends TypeConstraint {
  override def toString = s"$tv : $ty"
}
case class MustBeNumeric(tv: TypeVar) extends TypeConstraint {
  override def toString = s"$tv : Numeric"
}
case class MustBeSameType(tv1: TypeVar, tv2: TypeVar) extends TypeConstraint {
  override def toString = s"$tv1 = $tv2"
}
case class MustBeSameAsType(tv: TypeVar, ty: Type) extends TypeConstraint {
  override def toString = s"$tv : $ty"
}

object TypeConstraints {

  // Collect all identifiers used in an expression
  def collectIdentifiers(exp: Exp): Set[String] = exp match {
    case IdentExp(i) => Set(i)
    case ParenExp(e) => collectIdentifiers(e)
    case DotExp(e, _) => collectIdentifiers(e)
    case BinExp(e1, _, e2) => collectIdentifiers(e1) ++ collectIdentifiers(e2)
    case UnaryExp(_, e) => collectIdentifiers(e)
    case FunApplExp(f, args) =>
      collectIdentifiers(f) ++ args.flatMap(collectIdentifiers).toSet
    case IfExp(c, t, e) =>
      collectIdentifiers(c) ++ collectIdentifiers(t) ++ e.map(collectIdentifiers).getOrElse(Set())
    case WhileExp(c, b) => collectIdentifiers(c) ++ collectIdentifiers(b)
    case ForExp(p, e, b) => collectIdentifiers(e) ++ collectIdentifiers(b)
    case BlockExp(body) => body.flatMap {
      case ExpressionDecl(e) => collectIdentifiers(e)
      case pd: PropertyDecl if pd.expr.isDefined => collectIdentifiers(pd.expr.get)
      case _ => Set[String]()
    }.toSet
    case TupleExp(es) => es.flatMap(collectIdentifiers).toSet
    case CollectionEnumExp(_, es) => es.flatMap(collectIdentifiers).toSet
    case CollectionRangeExp(_, e1, e2) => collectIdentifiers(e1) ++ collectIdentifiers(e2)
    case CollectionComprExp(_, e1, bindings, e2) =>
      collectIdentifiers(e1) ++ bindings.flatMap(b => collectIdentifiers(b.collection.asInstanceOf[Exp])) ++
        collectIdentifiers(e2)
    case LambdaExp(_, e) => collectIdentifiers(e)
    case TypeCastCheckExp(_, e, _) => collectIdentifiers(e)
    case ReturnExp(e) => collectIdentifiers(e)
    case QuantifiedExp(_, _, e) => collectIdentifiers(e)
    case PositionalArgument(e) => collectIdentifiers(e)
    case NamedArgument(_, e) => collectIdentifiers(e)
    case _ => Set()
  }

  // Collect type constraints from an expression
  def collectConstraints(exp: Exp, typeVars: Map[String, TypeVar]): List[TypeConstraint] = {
    val constraints = mutable.ListBuffer[TypeConstraint]()

    def collect(e: Exp, expectedType: Option[Type] = None): Unit = e match {
      case IdentExp(i) =>
        typeVars.get(i).foreach { tv =>
          expectedType.foreach(t => constraints += MustBeSameAsType(tv, t))
        }

      case BinExp(e1, op, e2) =>
        op match {
          // Comparison operators require same numeric type
          case LT | LTE | GT | GTE =>
            collect(e1, Some(RealType)) // Could be Int or Real
            collect(e2, Some(RealType))
            // If both are type vars, constrain them to be equal
            (e1, e2) match {
              case (IdentExp(i1), IdentExp(i2)) =>
                for {
                  tv1 <- typeVars.get(i1)
                  tv2 <- typeVars.get(i2)
                } constraints += MustBeSameType(tv1, tv2)
              case _ =>
            }

          // Arithmetic operators require numeric types
          case ADD | SUB | MUL | DIV | REM =>
            collect(e1, Some(RealType))
            collect(e2, Some(RealType))
            (e1, e2) match {
              case (IdentExp(i1), IdentExp(i2)) =>
                for {
                  tv1 <- typeVars.get(i1)
                  tv2 <- typeVars.get(i2)
                } constraints += MustBeSameType(tv1, tv2)
              case _ =>
            }

          // Boolean operators require Bool
          case AND | OR | IMPL | IFF =>
            collect(e1, Some(BoolType))
            collect(e2, Some(BoolType))

          // Equality - types must match but could be anything
          case EQ | NEQ =>
            (e1, e2) match {
              case (IdentExp(i1), IdentExp(i2)) =>
                for {
                  tv1 <- typeVars.get(i1)
                  tv2 <- typeVars.get(i2)
                } constraints += MustBeSameType(tv1, tv2)
              case (IdentExp(i), lit) if isLiteral(lit) =>
                typeVars.get(i).foreach { tv =>
                  constraints += MustBeSameAsType(tv, getLiteralType(lit))
                }
              case (lit, IdentExp(i)) if isLiteral(lit) =>
                typeVars.get(i).foreach { tv =>
                  constraints += MustBeSameAsType(tv, getLiteralType(lit))
                }
              case _ =>
                collect(e1, None)
                collect(e2, None)
            }

          case _ =>
            collect(e1, None)
            collect(e2, None)
        }

      case UnaryExp(NOT, e1) =>
        collect(e1, Some(BoolType))

      case UnaryExp(NEG, e1) =>
        collect(e1, Some(RealType))

      case ParenExp(e1) => collect(e1, expectedType)

      case _ => // Handle other cases as needed
    }

    collect(exp)
    constraints.toList
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

  // Solve constraints and return inferred types
  def solveConstraints(
    typeVars: Map[String, TypeVar],
    constraints: List[TypeConstraint]
  ): Either[String, Map[String, Type]] = {

    val inferred = mutable.Map[String, Type]()
    val unions = new UnionFind[String]()

    // Initialize union-find with all type variables
    typeVars.keys.foreach(unions.makeSet)

    // First pass: collect direct type assignments and unions
    constraints.foreach {
      case MustBeSameAsType(TypeVar(name), ty) =>
        inferred.get(name) match {
          case Some(existing) if existing != ty && !areCompatible(existing, ty) =>
            return Left(s"Type conflict for $name: $existing vs $ty")
          case _ =>
            inferred(name) = ty
        }

      case MustBeType(TypeVar(name), ty) =>
        inferred.get(name) match {
          case Some(existing) if existing != ty && !areCompatible(existing, ty) =>
            return Left(s"Type conflict for $name: $existing vs $ty")
          case _ =>
            inferred(name) = ty
        }

      case MustBeNumeric(TypeVar(name)) =>
        inferred.get(name) match {
          case Some(IntType) | Some(RealType) | None =>
            if (!inferred.contains(name)) inferred(name) = RealType // Default numeric to Real
          case Some(other) =>
            return Left(s"$name must be numeric but inferred as $other")
        }

      case MustBeSameType(TypeVar(n1), TypeVar(n2)) =>
        unions.union(n1, n2)
    }

    // Second pass: propagate types through unions
    typeVars.keys.foreach { name =>
      val root = unions.find(name)
      if (inferred.contains(root) && !inferred.contains(name)) {
        inferred(name) = inferred(root)
      } else if (inferred.contains(name) && !inferred.contains(root)) {
        inferred(root) = inferred(name)
      }
    }

    // Third pass: propagate to all members of each equivalence class
    typeVars.keys.foreach { name =>
      val root = unions.find(name)
      if (inferred.contains(root)) {
        inferred(name) = inferred(root)
      }
    }

    // Default remaining unresolved types to Int
    typeVars.keys.foreach { name =>
      if (!inferred.contains(name)) {
        inferred(name) = IntType
      }
    }

    Right(inferred.toMap)
  }

  private def areCompatible(t1: Type, t2: Type): Boolean = (t1, t2) match {
    case (IntType, RealType) | (RealType, IntType) => true // Numeric promotion
    case _ => t1 == t2
  }

  // Simple union-find data structure
  class UnionFind[T] {
    private val parent = mutable.Map[T, T]()
    private val rank = mutable.Map[T, Int]()

    def makeSet(x: T): Unit = {
      parent(x) = x
      rank(x) = 0
    }

    def find(x: T): T = {
      if (!parent.contains(x)) {
        makeSet(x)
      }
      if (parent(x) != x) {
        parent(x) = find(parent(x)) // Path compression
      }
      parent(x)
    }

    def union(x: T, y: T): Unit = {
      val xRoot = find(x)
      val yRoot = find(y)
      if (xRoot != yRoot) {
        if (rank(xRoot) < rank(yRoot)) {
          parent(xRoot) = yRoot
        } else if (rank(xRoot) > rank(yRoot)) {
          parent(yRoot) = xRoot
        } else {
          parent(yRoot) = xRoot
          rank(xRoot) = rank(xRoot) + 1
        }
      }
    }
  }
}

