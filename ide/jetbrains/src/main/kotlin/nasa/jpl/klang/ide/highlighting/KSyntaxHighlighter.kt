package nasa.jpl.klang.ide.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import nasa.jpl.klang.ide.lexer.KLexer
import nasa.jpl.klang.ide.psi.KTokenTypes

/**
 * Syntax highlighting colors for K language
 */
object KHighlightingColors {
    // Keywords
    val KEYWORD = createTextAttributesKey("K_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)

    // Constraints (distinctive purple color)
    val CONSTRAINT = createTextAttributesKey("K_CONSTRAINT", DefaultLanguageHighlighterColors.METADATA)

    // Types
    val TYPE = createTextAttributesKey("K_TYPE", DefaultLanguageHighlighterColors.CLASS_NAME)

    // Operators
    val OPERATOR = createTextAttributesKey("K_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)

    // Literals
    val NUMBER = createTextAttributesKey("K_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val STRING = createTextAttributesKey("K_STRING", DefaultLanguageHighlighterColors.STRING)

    // Comments
    val COMMENT = createTextAttributesKey("K_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val BLOCK_COMMENT = createTextAttributesKey("K_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)

    // Annotations
    val ANNOTATION = createTextAttributesKey("K_ANNOTATION", DefaultLanguageHighlighterColors.METADATA)

    // Braces
    val BRACES = createTextAttributesKey("K_BRACES", DefaultLanguageHighlighterColors.BRACES)
    val BRACKETS = createTextAttributesKey("K_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
    val PARENTHESES = createTextAttributesKey("K_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)

    // Identifier
    val IDENTIFIER = createTextAttributesKey("K_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)

    // Bad character
    val BAD_CHARACTER = createTextAttributesKey("K_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)
}

/**
 * Syntax highlighter for K language
 */
class KSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = KLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            // Declaration keywords
            KTokenTypes.CLASS, KTokenTypes.ASSOC, KTokenTypes.PACKAGE,
            KTokenTypes.IMPORT, KTokenTypes.EXTENDS, KTokenTypes.TYPE,
            KTokenTypes.ANNOTATION_KEYWORD,
            // Control flow
            KTokenTypes.IF, KTokenTypes.THEN, KTokenTypes.ELSE,
            KTokenTypes.MATCH, KTokenTypes.WITH, KTokenTypes.CASE,
            KTokenTypes.WHILE, KTokenTypes.DO, KTokenTypes.FOR, KTokenTypes.IN,
            KTokenTypes.RETURN, KTokenTypes.BREAK, KTokenTypes.CONTINUE,
            // Functions & modifiers
            KTokenTypes.FUN, KTokenTypes.PRE, KTokenTypes.POST,
            KTokenTypes.VAL, KTokenTypes.VAR, KTokenTypes.PART,
            KTokenTypes.ORDERED, KTokenTypes.UNIQUE, KTokenTypes.SOURCE, KTokenTypes.TARGET,
            // Literals
            KTokenTypes.TRUE, KTokenTypes.FALSE, KTokenTypes.NULL, KTokenTypes.THIS,
            // Quantifiers
            KTokenTypes.FORALL, KTokenTypes.EXISTS ->
                pack(KHighlightingColors.KEYWORD)

            // Constraints
            KTokenTypes.REQ, KTokenTypes.SOFT, KTokenTypes.ASSERT,
            KTokenTypes.MINIMIZE, KTokenTypes.MAXIMIZE, KTokenTypes.WEIGHT ->
                pack(KHighlightingColors.CONSTRAINT)

            // Types
            KTokenTypes.BOOL_TYPE, KTokenTypes.CHAR_TYPE, KTokenTypes.INT_TYPE,
            KTokenTypes.REAL_TYPE, KTokenTypes.STRING_TYPE, KTokenTypes.UNIT_TYPE,
            KTokenTypes.TIME_TYPE, KTokenTypes.DURATION_TYPE,
            KTokenTypes.SET, KTokenTypes.OSET, KTokenTypes.BAG, KTokenTypes.SEQ ->
                pack(KHighlightingColors.TYPE)

            // Numbers
            KTokenTypes.INTEGER_LITERAL, KTokenTypes.REAL_LITERAL ->
                pack(KHighlightingColors.NUMBER)

            // Strings
            KTokenTypes.STRING_LITERAL, KTokenTypes.CHAR_LITERAL ->
                pack(KHighlightingColors.STRING)

            // Operators
            KTokenTypes.PLUS, KTokenTypes.MINUS, KTokenTypes.STAR, KTokenTypes.SLASH,
            KTokenTypes.PERCENT, KTokenTypes.EQ, KTokenTypes.NEQ, KTokenTypes.LT,
            KTokenTypes.GT, KTokenTypes.LE, KTokenTypes.GE, KTokenTypes.AND,
            KTokenTypes.OR, KTokenTypes.NOT, KTokenTypes.IMPLIES, KTokenTypes.IFF,
            KTokenTypes.ASSIGN, KTokenTypes.ARROW, KTokenTypes.SUCHTHAT,
            KTokenTypes.ISIN, KTokenTypes.NOT_ISIN, KTokenTypes.SUBSET,
            KTokenTypes.PSUBSET, KTokenTypes.UNION, KTokenTypes.INTER ->
                pack(KHighlightingColors.OPERATOR)

            // Braces
            KTokenTypes.LBRACE, KTokenTypes.RBRACE ->
                pack(KHighlightingColors.BRACES)

            KTokenTypes.LBRACKET, KTokenTypes.RBRACKET ->
                pack(KHighlightingColors.BRACKETS)

            KTokenTypes.LPAREN, KTokenTypes.RPAREN ->
                pack(KHighlightingColors.PARENTHESES)

            // Comments
            KTokenTypes.LINE_COMMENT ->
                pack(KHighlightingColors.COMMENT)

            KTokenTypes.BLOCK_COMMENT ->
                pack(KHighlightingColors.BLOCK_COMMENT)

            // Annotations
            KTokenTypes.AT ->
                pack(KHighlightingColors.ANNOTATION)

            // Identifiers
            KTokenTypes.IDENTIFIER ->
                pack(KHighlightingColors.IDENTIFIER)

            // Bad character
            KTokenTypes.BAD_CHARACTER ->
                pack(KHighlightingColors.BAD_CHARACTER)

            else -> emptyArray()
        }
    }
}

/**
 * Factory for creating K syntax highlighter
 */
class KSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        return KSyntaxHighlighter()
    }
}

