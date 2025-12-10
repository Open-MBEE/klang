package nasa.jpl.klang.ide.psi

import com.intellij.psi.tree.IElementType
import nasa.jpl.klang.ide.KLanguage

/**
 * Token type for K language
 */
class KTokenType(debugName: String) : IElementType(debugName, KLanguage)

/**
 * Element type for K language AST nodes
 */
class KElementType(debugName: String) : IElementType(debugName, KLanguage)

/**
 * Token types for K language lexer
 */
object KTokenTypes {
    // Keywords - declarations
    @JvmField val CLASS = KTokenType("class")
    @JvmField val ASSOC = KTokenType("assoc")
    @JvmField val PACKAGE = KTokenType("package")
    @JvmField val IMPORT = KTokenType("import")
    @JvmField val EXTENDS = KTokenType("extends")
    @JvmField val TYPE = KTokenType("type")
    @JvmField val ANNOTATION_KEYWORD = KTokenType("annotation")

    // Keywords - control flow
    @JvmField val IF = KTokenType("if")
    @JvmField val THEN = KTokenType("then")
    @JvmField val ELSE = KTokenType("else")
    @JvmField val MATCH = KTokenType("match")
    @JvmField val WITH = KTokenType("with")
    @JvmField val CASE = KTokenType("case")
    @JvmField val WHILE = KTokenType("while")
    @JvmField val DO = KTokenType("do")
    @JvmField val FOR = KTokenType("for")
    @JvmField val IN = KTokenType("in")
    @JvmField val RETURN = KTokenType("return")
    @JvmField val BREAK = KTokenType("break")
    @JvmField val CONTINUE = KTokenType("continue")

    // Keywords - functions
    @JvmField val FUN = KTokenType("fun")
    @JvmField val PRE = KTokenType("pre")
    @JvmField val POST = KTokenType("post")

    // Keywords - modifiers
    @JvmField val VAL = KTokenType("val")
    @JvmField val VAR = KTokenType("var")
    @JvmField val PART = KTokenType("part")
    @JvmField val ORDERED = KTokenType("ordered")
    @JvmField val UNIQUE = KTokenType("unique")
    @JvmField val SOURCE = KTokenType("source")
    @JvmField val TARGET = KTokenType("target")

    // Constraints
    @JvmField val REQ = KTokenType("req")
    @JvmField val SOFT = KTokenType("soft")
    @JvmField val ASSERT = KTokenType("assert")

    // Optimization
    @JvmField val MINIMIZE = KTokenType("minimize")
    @JvmField val MAXIMIZE = KTokenType("maximize")
    @JvmField val WEIGHT = KTokenType("weight")

    // Quantifiers
    @JvmField val FORALL = KTokenType("forall")
    @JvmField val EXISTS = KTokenType("exists")

    // Primitive types
    @JvmField val BOOL_TYPE = KTokenType("Bool")
    @JvmField val CHAR_TYPE = KTokenType("Char")
    @JvmField val INT_TYPE = KTokenType("Int")
    @JvmField val REAL_TYPE = KTokenType("Real")
    @JvmField val STRING_TYPE = KTokenType("String")
    @JvmField val UNIT_TYPE = KTokenType("Unit")
    @JvmField val TIME_TYPE = KTokenType("Time")
    @JvmField val DURATION_TYPE = KTokenType("Duration")

    // Collection types
    @JvmField val SET = KTokenType("Set")
    @JvmField val OSET = KTokenType("OSet")
    @JvmField val BAG = KTokenType("Bag")
    @JvmField val SEQ = KTokenType("Seq")

    // Literals
    @JvmField val TRUE = KTokenType("true")
    @JvmField val FALSE = KTokenType("false")
    @JvmField val NULL = KTokenType("null")
    @JvmField val THIS = KTokenType("this")
    @JvmField val INTEGER_LITERAL = KTokenType("INTEGER_LITERAL")
    @JvmField val REAL_LITERAL = KTokenType("REAL_LITERAL")
    @JvmField val STRING_LITERAL = KTokenType("STRING_LITERAL")
    @JvmField val CHAR_LITERAL = KTokenType("CHAR_LITERAL")

    // Operators - arithmetic
    @JvmField val PLUS = KTokenType("+")
    @JvmField val MINUS = KTokenType("-")
    @JvmField val STAR = KTokenType("*")
    @JvmField val SLASH = KTokenType("/")
    @JvmField val PERCENT = KTokenType("%")

    // Operators - comparison
    @JvmField val EQ = KTokenType("=")
    @JvmField val NEQ = KTokenType("!=")
    @JvmField val LT = KTokenType("<")
    @JvmField val GT = KTokenType(">")
    @JvmField val LE = KTokenType("<=")
    @JvmField val GE = KTokenType(">=")

    // Operators - logical
    @JvmField val AND = KTokenType("&&")
    @JvmField val OR = KTokenType("||")
    @JvmField val NOT = KTokenType("!")
    @JvmField val IMPLIES = KTokenType("=>")
    @JvmField val IFF = KTokenType("<=>")

    // Operators - set
    @JvmField val ISIN = KTokenType("isin")
    @JvmField val NOT_ISIN = KTokenType("!isin")
    @JvmField val SUBSET = KTokenType("subset")
    @JvmField val PSUBSET = KTokenType("psubset")
    @JvmField val UNION = KTokenType("union")
    @JvmField val INTER = KTokenType("inter")

    // Operators - other
    @JvmField val ASSIGN = KTokenType(":=")
    @JvmField val ARROW = KTokenType("->")
    @JvmField val COLON = KTokenType(":")
    @JvmField val COLONCOLON = KTokenType("::")
    @JvmField val SUCHTHAT = KTokenType(":-")
    @JvmField val DOT = KTokenType(".")
    @JvmField val COMMA = KTokenType(",")
    @JvmField val SEMICOLON = KTokenType(";")

    // Brackets
    @JvmField val LPAREN = KTokenType("(")
    @JvmField val RPAREN = KTokenType(")")
    @JvmField val LBRACE = KTokenType("{")
    @JvmField val RBRACE = KTokenType("}")
    @JvmField val LBRACKET = KTokenType("[")
    @JvmField val RBRACKET = KTokenType("]")

    // Annotations
    @JvmField val AT = KTokenType("@")

    // Comments
    @JvmField val LINE_COMMENT = KTokenType("LINE_COMMENT")
    @JvmField val BLOCK_COMMENT = KTokenType("BLOCK_COMMENT")

    // Other
    @JvmField val IDENTIFIER = KTokenType("IDENTIFIER")
    @JvmField val WHITE_SPACE = KTokenType("WHITE_SPACE")
    @JvmField val BAD_CHARACTER = KTokenType("BAD_CHARACTER")
}

