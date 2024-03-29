package k.frontend

import java.util.HashMap
import com.microsoft.z3._
import com.microsoft.z3.{ Symbol => Z3Symbol }
import collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.{ HashMap => MMap }
import sys.process._
import java.io._

object Util {
  def ??? : Nothing = null.asInstanceOf[Nothing]
}
import Util._

object K2Z3Exception extends Exception

class DataTypes(ctx: Context) {
  type TypeName = String
  type FieldName = String

  private var datatypes: Map[Type, DataType] = Map()

  def addDataType(ty: Type, datatype: DataType) {
    datatypes += (ty -> datatype)
  }

  def addDataType(name: String, argTypes: List[Type], fields: List[(String, Type)]) {
    val fieldNames: Array[FieldName] = fields.toArray map { case (n, _) => n }
    val fieldSorts: Array[Sort] = fields.toArray map { case (_, t) => getDataType(t).sort }
    val mkDatatype: Constructor = ctx.mkConstructor(s"mk$name", s"is$name", fieldNames, fieldSorts, null)
    val datatypeSort: DatatypeSort = ctx.mkDatatypeSort(name, Array(mkDatatype))
    val constructor: FuncDecl = datatypeSort.getConstructors.apply(0)
    val selectors: Array[FuncDecl] = datatypeSort.getAccessors.apply(0)
    val fieldDecls: Map[FieldName, FuncDecl] = (fieldNames zip selectors).toMap
    val dataType = DataType(datatypeSort, constructor, fieldDecls)
    addDataType(IdentType(QualifiedName(List(name)), argTypes), dataType)
  }

  //  Array < String > argnames = new Array < String > ("first", "second");
  //  Array < z3.Sort > argsorts = new Array < z3.Sort > (ctx.getIntSort(), ctx.getIntSort());
  //  z3.Constructor mkpair = ctx.mkConstructor("mkpair", "ispair", argnames, argsorts, null);
  //  z3.DatatypeSort pair = ctx.mkDatatypeSort("pair", new Array < z3.Constructor > (mkpair));

  def addTupleType(fieldTypes: List[Type]) {
    val tupleSize: Int = fieldTypes.length
    val constructorSymbol: StringSymbol = ctx.mkSymbol(s"mkTuple")
    val fieldNames: Array[FieldName] = (for (i <- 1 to tupleSize) yield s"sel_$i").toArray
    val fieldSymbols: Array[Z3Symbol] = fieldNames map (ctx.mkSymbol(_))
    val fieldSorts: Array[Sort] = fieldTypes.toArray map (getDataType(_).sort)
    val tupleSort: TupleSort = ctx.mkTupleSort(constructorSymbol, fieldSymbols, fieldSorts)
    val tupleConstructor: FuncDecl = tupleSort.mkDecl()
    val fieldDecls: Map[FieldName, FuncDecl] = (fieldNames zip tupleSort.getFieldDecls).toMap
    val dataType = DataType(tupleSort, tupleConstructor, fieldDecls)
    addDataType(CartesianType(fieldTypes), dataType)
  }

  def getDataType(ty: Type): DataType = datatypes(ty)

  def getSort(ty: Type): Sort = getDataType(ty).sort

  addDataType(RealType, DataType(ctx.getRealSort(), null, null))
  addDataType(BoolType, DataType(ctx.getBoolSort(), null, null))
  addDataType(RealType, DataType(ctx.getRealSort(), null, null))
}

case class DataType(sort: Sort, constructor: FuncDecl, selectors: Map[String, FuncDecl])

object K2Z3 {

  var debug: Boolean = false
  var debugRawModel: Boolean = false
  val printExtraEntries: Boolean = true
  var silent: Boolean = false
  var cfg: Map[String, String] = Map(
    "model" -> "true",
    "auto-config" -> "true",
    "unsat_core" -> "true")
  var ctx: Context = new Context(cfg)
  var solver: Solver = ctx.mkSolver()
  var idents: MMap[String, (Expr, com.microsoft.z3.StringSymbol)] = MMap()
  var z3Model: com.microsoft.z3.Model = null
  val tc: TypeChecker = new TypeChecker(null)
  var datatypes: DataTypes = null
  var params = ctx.mkParams
  params.add("unsat_core", true)

  def error(msg: String) = {
    if (silent) Misc.silentErrorThrow("K2Z3", msg, K2Z3Exception)
    else Misc.errorThrow("K2Z3", msg, K2Z3Exception)
  }
  def log(msg: String = "") = if (!silent) Misc.log("K2Z3", msg)
  def logDebug(msg: String) = if (debug && !silent) Misc.log("K2Z3", s"DEBUG $msg")
  def warning(msg: String) = Misc.log("K2Z3", s"Warning $msg")

  def reset() {
    z3Model = null
    idents = new MMap
    ctx = new Context(cfg)
    params = ctx.mkParams
    params.add("unsat_core", true)
    solver = ctx.mkSolver
    solver.setParameters(params)
  }

  def getStringForSets(setValue: FuncDecl, ty: Type): String = {
    "Set(" +
      z3Model.getFuncInterp(setValue).getEntries.foldLeft(List[String]()) {
        (res, x) =>
          if (x.getValue.getBoolValue.toInt > 0) {
            val arg = x.getArgs.mkString.toString
            if (arg.contains("array")) {
              val setName = arg.split(" ").last.split("!").last.replace(")", "")
              val decl = z3Model.getFuncDecls.find { x => x.getName.toString == setName }
              val result = getStringForSets(decl.get, Misc.getInnerTypeFromCollectionType(ty))
              result :: res
            } else {
              if (TypeChecker.isPrimitiveType(Misc.getInnerTypeFromCollectionType(ty)))
                x.getArgs.mkString :: res
              else
                x.getArgs.map { x => s"Ref $x" }.mkString :: res
            }
          } else res
      }.mkString(",") +
      ")"
  }

  def printObjectValue(name: String, heap: Map[String, String],
                       v: String, visited: Set[String],
                       refNum: String, force: Boolean): (Set[String], List[List[String]]) = {

    if (visited.contains(name)) return (visited, Nil)

    val value = v.trim.replace("- ", "-")
    if (value.indexOf("mk-") < 0) return (visited + name, Nil)
    val className = value.subSequence(1, value.indexOf(' ', 1)).toString.replace("lift-", "").trim

    val classDecl = TypeChecker.classes(className)
    val noInstancesForClass =
      classDecl.annotations.foldLeft(false)((res, a) => if (a.name.equals("noInstances")) true else res)
    if (noInstancesForClass && !force) return (visited, Nil)

    val objectValuesString = value.subSequence(value.indexOf("mk-"), value.length - 2).toString
    val objectValuesOrig = objectValuesString.split(' ').map(_.trim).filterNot { _.isEmpty }.drop(1)
    var objectValues = List[String]()
    var printList = List[String]()
    var toPrint = List[String]()

    var i = 0
    while (i < objectValuesOrig.length) {
      var value = objectValuesOrig(i)
      if (objectValuesOrig(i).contains("Tuple2")) {
        i = i + 1
        value += " " + objectValuesOrig(i)
        i = i + 1
        value += " " + objectValuesOrig(i)
      } else if (objectValuesOrig(i).contains("Tuple3")) {
        i = i + 1
        value += " " + objectValuesOrig(i)
        i = i + 1
        value += " " + objectValuesOrig(i)
        i = i + 1
        value += " " + objectValuesOrig(i)
      } else if (objectValuesOrig(i).contains("(_")) {
        i = i + 1
        value += " " + objectValuesOrig(i)
        i = i + 1
        value += " " + objectValuesOrig(i)
      }
      objectValues = value :: objectValues
      i = i + 1
    }
    objectValues = objectValues.reverse

    if (className == "TopLevelDeclarations") return (visited, List(List(name, " - top level -")))

    val properties = classDecl.getAllPropertyDecls
    printList =
      (properties zip objectValues).map {
        x =>
          if (Misc.isCollection(x._1.ty)) {
            val setName = x._2.split("!").last.replace(")", "")
            val setValue = z3Model.getFuncDecls.find { x => x.getName.toString == setName }
            if (setValue.isEmpty) x._1.name + ":: [Empty Seq]"
            else x._1.name + ":: " + getStringForSets(setValue.get, x._1.ty)
          } else if (!TypeChecker.isPrimitiveType(x._1.ty)) {
            toPrint = x._2 :: toPrint
            (x._1.name + ":: Ref " + x._2)
          } else {
            (x._1.name + "::" + x._2)
          }
      }.toList

    var all =
      if (name.startsWith("Ref")) List("", name, s"$className(" + printList.mkString(", ") + ")")
      else List(name, s"Ref $refNum", s"$className(" + printList.mkString(", ") + ")")

    var result = toPrint.foldLeft((visited, List(all))) { (res, x) =>
      if (heap.contains(x)) {
        val downRes = printObjectValue("Ref " + x, heap, heap(x), res._1 + name, x, true)
        ((downRes._1 + name) ++ res._1, downRes._2 ++ res._2)
      } else {
        val downRes = printObjectValue("else " + x, heap, heap("else"), res._1 + name, x, true)
        ((downRes._1 + name) ++ res._1, downRes._2 ++ res._2)
      }
    }
    (result._1 + name, result._2)
  }
  
  def PrintModel(model: Model) {

    if (z3Model != null) {

      logDebug(z3Model.toString)

      // log("<<++")

      var rows: List[List[String]] = List(List("Variable", "Ref", "Value"))
      var extraRows: List[List[String]] = List(List("Variable", "Ref", "Value"))
      var heapDecl = z3Model.getDecls.find { _.getName.toString.equals("heap") }

      if (heapDecl.isEmpty) {
        error(s"FATAL INTERNAL ERROR! Could not find a heap declaration for printing the model.")
      }

      var heapMap =
        z3Model.getFuncInterp(heapDecl.get).getEntries.
          foldLeft(Map[String, String]()) { (res, x) =>
            x.getValue.getArgs.foreach { x =>
            }
            res + (x.getArgs.last.toString -> x.getValue.toString)
          }
      
      val elseK = z3Model.getFuncInterp(heapDecl.get).getElse
      heapMap += ("else" -> elseK.toString)

      var visited = Set[String]()
      
      // walk through heap and  print entries
      heapMap.foreach { kv =>

        val key = kv._1

        val value = kv._2.replace("- ", "-")
        if (value != "null") {
          val className = value.subSequence(1, value.indexOf(' ', 1)).toString.replace("lift-", "").trim
          if (value.contains("mk-")) {
            val objectValues = value.subSequence(value.indexOf("mk-"), value.length - 2).toString
              .split(' ').map(_.trim).filterNot { _.isEmpty }

            className == "TopLevelDeclarations" match {
              case true =>
                var topLevelVariables =
                  model.decls.foldLeft(List[(String, Boolean)]()) { (res, d) =>
                    d match {
                      case pd @ PropertyDecl(_, _, _, _, _, _) => (new Tuple2(pd.name, TypeChecker.isPrimitiveType(pd.ty))) :: res
                      case _                                   => res
                    }
                  }
                var i = 1
                topLevelVariables.reverse.foreach { k =>
                  if (k._2) {
                    rows = (List(k._1, "-", objectValues(i))) :: rows
                  } else {
                    val res = printObjectValue(k._1, heapMap, heapMap.getOrElse(objectValues(i), heapMap("else")), visited, objectValues(i), false)
                    rows = res._2 ++ rows
                    visited = res._1 + ("Ref " + objectValues(i))
                  }
                  i = i + 1
                }
              case _ => ()
            }
          }
        }
      }
            
      // walk through heap and  print EXTRA entries
      if (printExtraEntries) {
        heapMap.foreach { kv =>

          val key = kv._1

          val value = kv._2.replace("- ", "-") 
          if (value != "null") {
            val className = value.subSequence(1, value.indexOf(' ', 1)).toString.replace("lift-", "").trim
            if (value.contains("mk-")) {
              val objectValues = value.subSequence(value.indexOf("mk-"), value.length - 2).toString
                .split(' ').map(_.trim).filterNot { _.isEmpty }
              className == "TopLevelDeclarations" match {
                case true => ()
                case _ => {
                  val res = printObjectValue("Ref " + key, heapMap, value, visited, key, false)
                  extraRows = res._2 ++ extraRows
                  visited = res._1 + ("Ref " + key)
                }
              }
            }
          }
        }
      }
            
      println()
      if (rows.length > 1) {
        println("\tTop level objects created:")
        println()
        println(Tabulator.format(rows.reverse))
      }
      else println("\tNo instance variables were declared at the top level.")

      println()
      if (extraRows.length > 1) {
        println("\tExtra objects created during analysis:")
        println()
        println(Tabulator.format(extraRows.reverse))
      }
      else println("\tNo extra objects.")
      println()

      // log("-->>")
    }
  }
  
  def solveSMT(model: Model, smtModel: String, printModel: Boolean) {
    try {
      reset()

      val boolExp = ctx.parseSMTLIB2String(smtModel, null, null, null, null)
      z3Model = SolveExp(boolExp, smtModel)
      
      if (debugRawModel) {
        println
        println("--- BEGIN RAW SMT MODEL: ---")
        println(z3Model)
        println("--- END RAW SMT MODEL ---")
        println
      }
            
      if (printModel) PrintModel(model)
    } catch {
      case e: Throwable =>
        if (debug) e.printStackTrace()
        throw K2Z3Exception
    }
  }

  def SolveExp(e: Exp): com.microsoft.z3.Model = {
    reset()
    val boolExpr = Expr2Z3(e).asInstanceOf[BoolExpr];
    SolveExp(boolExpr, "")
  }

  def SolveExp(e: BoolExpr, smtModel: String): com.microsoft.z3.Model = {

    solver.add(e)

    val status = solver.check

    if (Status.SATISFIABLE == status) {
      z3Model = solver.getModel
    } else if (status == Status.UNSATISFIABLE) {
      log()
      log(s"The given model is NOT satisfiable. ")
      val smt2 = ("(set-option :produce-unsat-cores true)\n") + (smtModel + "(check-sat) (get-unsat-core) (exit)")
      val file = new File("t.smt2")
      val tf = new PrintWriter(file)
      tf.write(smt2)
      tf.close
      val res = (("z3 -smt2 t.smt2")).!!
      val lines = res.split("\\r?\\n")
      val assertionNames = lines(1).replace("(", "").replace(")", "").split("\\s")
        .filter { !_.equals("xTOP") }
        .map { UtilSMT.constraintMessageMap(_) }.toSet
      log("UNSAT due to the following reasons: ")
      println
      for (
        an <- assertionNames.filter { !_.equals("_k_ignore_") }
      ) {
        println(s"\t$an")
      }
      println
      log()
      file.delete()
    } else {
      log()
      log("Model could not be solved successfully.")
      log("Reason: " + solver.getReasonUnknown)
      log()
      z3Model = null
    }

    z3Model
  }

  def Expr2Z3(e: Exp): com.microsoft.z3.Expr = {
    e match {

      //case FunApplExp(exp, args) =>
      //  var function = getZ3Function(exp).asInstanceOf[FuncDecl]
      //  var argsZ3: Array[Expr] = args.map(a => Expr2Z3(a)).toArray
      //  ctx.mkApp(function, argsZ3: _*)
      case ParenExp(e) =>
        Expr2Z3(e)
      case TupleExp(es) =>
        val vs = es map Expr2Z3
        val tupleType = tc.inferTypeFrom("es", CartesianType(List(RealType, BoolType)))
        val mkTuple = datatypes.getDataType(tupleType).constructor
        mkTuple(vs(0), vs(1))
      case IdentExp(i) =>
        idents.get(i) match {
          case None =>
            var s = ctx.mkSymbol(i)
            var ie = ctx.mkRealConst(s)
            idents.put(i, (ie, s))
            ie
          case Some(x) =>
            x._1
        }
      case DotExp(exp, ident) =>
        exp match {
          case IdentExp(id) => Expr2Z3(IdentExp(id + "." + ident))
          case _ =>
            var obj: Expr = Expr2Z3(exp)
            val theType = tc.inferTypeFrom("exp", IdentType(QualifiedName(List("A")), Nil))
            val datatype: DataType = datatypes.getDataType(theType)
            val selector: FuncDecl = datatype.selectors(ident)
            selector(obj)
        }
      case FunApplExp(exp, args) =>
        var obj: Expr = Expr2Z3(exp)
        val theType = tc.inferTypeFrom("exp", IdentType(QualifiedName(List("A")), Nil))
        val isConstructor = false // TODO: for constructor make this true
        if (isConstructor) {
          // constructor application
          val datatype: DataType = datatypes.getDataType(theType)
          val constructor: FuncDecl = datatype.constructor
          val arguments: List[Expr] = args map (Expr2Z3(_))
          constructor(arguments: _*)
        } else {
          // normal function application

          // 1. create an uninterpreted function

          val functionDecl = ctx.mkFuncDecl(exp.toString, args.map(a => ctx.getRealSort).toArray[Sort], ctx.getRealSort)

          // 2. apply the uninterpreted function
          ctx.mkApp(functionDecl, args.map(Expr2Z3(_)): _*)

        }
      case PositionalArgument(exp) =>
        Expr2Z3(exp)
      case BinExp(e1, o, e2) =>
        o match {
          case LT =>
            var v1: ArithExpr = Expr2Z3(e1).asInstanceOf[ArithExpr]
            var v2: ArithExpr = Expr2Z3(e2).asInstanceOf[ArithExpr]
            ctx.mkLt(v1, v2)
          case LTE =>
            var v1: ArithExpr = Expr2Z3(e1).asInstanceOf[ArithExpr]
            var v2: ArithExpr = Expr2Z3(e2).asInstanceOf[ArithExpr]
            ctx.mkLe(v1, v2)
          case GT =>
            var v1: ArithExpr = Expr2Z3(e1).asInstanceOf[ArithExpr]
            var v2: ArithExpr = Expr2Z3(e2).asInstanceOf[ArithExpr]
            ctx.mkGt(v1, v2)
          case GTE =>
            var v1: ArithExpr = Expr2Z3(e1).asInstanceOf[ArithExpr]
            var v2: ArithExpr = Expr2Z3(e2).asInstanceOf[ArithExpr]
            ctx.mkGe(v1, v2)
          case AND =>
            var v1: BoolExpr = Expr2Z3(e1).asInstanceOf[BoolExpr]
            var v2: BoolExpr = Expr2Z3(e2).asInstanceOf[BoolExpr]
            ctx.mkAnd(v1, v2)
          case OR =>
            var v1: BoolExpr = Expr2Z3(e1).asInstanceOf[BoolExpr]
            var v2: BoolExpr = Expr2Z3(e2).asInstanceOf[BoolExpr]
            ctx.mkOr(v1, v2)
          case IMPL =>
            var v1: BoolExpr = Expr2Z3(e1).asInstanceOf[BoolExpr]
            var v2: BoolExpr = Expr2Z3(e2).asInstanceOf[BoolExpr]
            ctx.mkImplies(v1, v2)
          case IFF =>
            var v1: BoolExpr = Expr2Z3(e1).asInstanceOf[BoolExpr]
            var v2: BoolExpr = Expr2Z3(e2).asInstanceOf[BoolExpr]
            ctx.mkIff(v1, v2)
          case EQ =>
            var v1: Expr = Expr2Z3(e1).asInstanceOf[Expr]
            var v2: Expr = Expr2Z3(e2).asInstanceOf[Expr]
            ctx.mkEq(v1, v2)
          case NEQ =>
            var v1 = Expr2Z3(e1)
            var v2 = Expr2Z3(e2)
            ctx.mkNot(ctx.mkEq(v1, v2))
          case MUL =>
            var v1: ArithExpr = Expr2Z3(e1).asInstanceOf[ArithExpr]
            var v2: ArithExpr = Expr2Z3(e2).asInstanceOf[ArithExpr]
            ctx.mkMul(v1, v2)
          case DIV =>
            var v1: ArithExpr = Expr2Z3(e1).asInstanceOf[ArithExpr]
            var v2: ArithExpr = Expr2Z3(e2).asInstanceOf[ArithExpr]
            ctx.mkDiv(v1, v2)
          case REM =>
            var v1: IntExpr = Expr2Z3(e1).asInstanceOf[IntExpr]
            var v2: IntExpr = Expr2Z3(e2).asInstanceOf[IntExpr]
            ctx.mkRem(v1, v2)
          case ADD =>
            var v1: ArithExpr = Expr2Z3(e1).asInstanceOf[ArithExpr]
            var v2: ArithExpr = Expr2Z3(e2).asInstanceOf[ArithExpr]
            ctx.mkAdd(v1, v2)
          case SUB =>
            var v1: ArithExpr = Expr2Z3(e1).asInstanceOf[ArithExpr]
            var v2: ArithExpr = Expr2Z3(e2).asInstanceOf[ArithExpr]
            ctx.mkSub(v1, v2)
          case ASSIGN =>
            var v1: Expr = Expr2Z3(e1).asInstanceOf[Expr]
            var v2: Expr = Expr2Z3(e2).asInstanceOf[Expr]
            ctx.mkEq(v1, v2)
          case TUPLEINDEX =>
            var v1: Expr = Expr2Z3(e1).asInstanceOf[Expr]
            var v2: Expr = Expr2Z3(e2).asInstanceOf[Expr]
            val tupleType = tc.inferTypeFrom("e1", CartesianType(List(RealType, BoolType)))
            val datatype = datatypes.getDataType(tupleType)
            if (v2 == ctx.mkReal(1))
              datatype.selectors("sel_1").apply(v1)
            else
              datatype.selectors("sel_2").apply(v1)
        }
      case UnaryExp(o, e) =>
        o match {
          case NOT =>
            var v: BoolExpr = {
              val ev = Expr2Z3(e)
              if (ev.isInstanceOf[RealExpr]) {
                ctx.mkNot(ctx.mkEq(ev, ctx.mkReal(0)))
              } else {
                ev.asInstanceOf[BoolExpr]
              }
            }

            ctx.mkNot(v)
          case NEG =>
            var v: ArithExpr = Expr2Z3(e).asInstanceOf[ArithExpr]
            ctx.mkMul(ctx.mkReal(-1), v)
        }
      case IntegerLiteral(i) =>
        ctx.mkReal(i)
      case BooleanLiteral(b) =>
        ctx.mkBool(b)
      case RealLiteral(r) =>
        ctx.mkReal(r.toString)
      case QuantifiedExp(quantifier, bindings, expression) =>
        var qtypes = new ListBuffer[com.microsoft.z3.Sort]()
        var names = new ListBuffer[com.microsoft.z3.Symbol]()
        var patterns = new ListBuffer[com.microsoft.z3.Pattern]() // not used
        var ies = new ListBuffer[Expr]()
        for (b <- bindings) {
          for (p <- b.patterns) {
            p match {
              case IdentPattern(x) =>
                idents.get(x) match {
                  case None =>
                    val xSym = ctx.mkSymbol(x)
                    var ie = ctx.mkConst(xSym, ctx.getRealSort)
                    idents.put(x, (ie, xSym))
                    names.add(xSym)
                    ies.add(ie)
                    b.collection match {
                      case TypeCollection(ty) =>
                        ty match {
                          case BoolType => qtypes.add(ctx.getBoolSort())
                          case IntType =>
                            qtypes.add(ctx.getIntSort())
                            val pattern = ctx.mkPattern(ie)
                            patterns.add(ctx.mkPattern(ie)) // use pattern, but not used anyway
                          case RealType =>
                            qtypes.add(ctx.getRealSort())
                            val pattern = ctx.mkPattern(ie)
                            patterns.add(ctx.mkPattern(ie)) // use pattern, but not used anyway
                          case _ =>
                            error("Only bool, int, and real primitive types are supported for quantified expressions in Z3." + expression)
                        }
                      case _ =>
                        error("Only type collections are supported for quantified expressions in Z3." +
                          "\nPlease check expression " + e)
                    }
                  case Some(x) => () // so you can't quantify over an existing variable?
                }

              case _ =>
                error("Only literal and ident patterns are supported for quantified expressions in Z3." +
                  "Please check expression " + expression)
            }
          }
        }

        var body: Expr = Expr2Z3(expression)

        // There are two ways to construct quantified expressions in Z3
        // one is by using named constants
        // the other is by de-Brujin indexed variables.
        // We have to be careful, because there are no checks for actually 
        // checking if you are mixing the two and doing it incorrectly
        // The following uses de-Brujin indexed variables for forall
        // and named constants for exists. 
        // This probably can be cleaned up, but it is the only way I got
        // it to work.
        // KH: I think that it does not work as expected for existential quantification.
        // since variables are all de-Brujin variables, and since symbols are used for
        // existential, it does not work. It looks like it works, but I think it is
        // not working the way it is intended to.
        quantifier match {
          case Forall =>
            ctx.mkForall(ies.toArray, body, 0, null,
              null, null, null)
          case Exists =>
            ctx.mkExists(ies.toArray, body, 0, null,
              null, null, null)
          //            ctx.mkExists(qtypes.toArray, names.toArray,
          //              body, 1, null, null, null, null)
          //ctx.mkExists(ies.toArray, body, 0, null, null, null, null)
        }
    }
  }

}
