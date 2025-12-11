package nasa.jpl.klang.ide.psi

import com.intellij.psi.tree.IElementType

/**
 * Element types for K language AST nodes (non-leaf nodes)
 */
object KElementTypes {
    // File structure
    @JvmField val FILE = KElementType("FILE")
    @JvmField val PACKAGE_DECLARATION = KElementType("PACKAGE_DECLARATION")
    @JvmField val IMPORT_DECLARATION = KElementType("IMPORT_DECLARATION")

    // Definitions
    @JvmField val CLASS_DEFINITION = KElementType("CLASS_DEFINITION")
    @JvmField val CLASS_BODY = KElementType("CLASS_BODY")
    @JvmField val ASSOC_DEFINITION = KElementType("ASSOC_DEFINITION")
    @JvmField val TYPE_ALIAS = KElementType("TYPE_ALIAS")

    // Class members
    @JvmField val PROPERTY_DEFINITION = KElementType("PROPERTY_DEFINITION")
    @JvmField val FUNCTION_DEFINITION = KElementType("FUNCTION_DEFINITION")
    @JvmField val CONSTRAINT = KElementType("CONSTRAINT")

    // Type system
    @JvmField val TYPE_REFERENCE = KElementType("TYPE_REFERENCE")
    @JvmField val TYPE_PARAMETER = KElementType("TYPE_PARAMETER")
    @JvmField val TYPE_ARGUMENT = KElementType("TYPE_ARGUMENT")

    // Function parts
    @JvmField val PARAMETER_LIST = KElementType("PARAMETER_LIST")
    @JvmField val PARAMETER = KElementType("PARAMETER")
    @JvmField val FUNCTION_BODY = KElementType("FUNCTION_BODY")

    // Expressions
    @JvmField val EXPRESSION = KElementType("EXPRESSION")
    @JvmField val BINARY_EXPRESSION = KElementType("BINARY_EXPRESSION")
    @JvmField val UNARY_EXPRESSION = KElementType("UNARY_EXPRESSION")
    @JvmField val CALL_EXPRESSION = KElementType("CALL_EXPRESSION")
    @JvmField val MEMBER_ACCESS_EXPRESSION = KElementType("MEMBER_ACCESS_EXPRESSION")
    @JvmField val REFERENCE_EXPRESSION = KElementType("REFERENCE_EXPRESSION")
    @JvmField val LITERAL_EXPRESSION = KElementType("LITERAL_EXPRESSION")
    @JvmField val PARENTHESIZED_EXPRESSION = KElementType("PARENTHESIZED_EXPRESSION")
    @JvmField val IF_EXPRESSION = KElementType("IF_EXPRESSION")
    @JvmField val MATCH_EXPRESSION = KElementType("MATCH_EXPRESSION")
    @JvmField val QUANTIFIED_EXPRESSION = KElementType("QUANTIFIED_EXPRESSION")

    // Statements
    @JvmField val BLOCK = KElementType("BLOCK")
    @JvmField val STATEMENT = KElementType("STATEMENT")
    
    // Extends list
    @JvmField val EXTENDS_LIST = KElementType("EXTENDS_LIST")
    
    // Annotation
    @JvmField val ANNOTATION = KElementType("ANNOTATION")
    @JvmField val ANNOTATION_ARGUMENTS = KElementType("ANNOTATION_ARGUMENTS")
}
