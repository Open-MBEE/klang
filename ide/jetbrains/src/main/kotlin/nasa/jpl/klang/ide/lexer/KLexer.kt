package nasa.jpl.klang.ide.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import nasa.jpl.klang.ide.psi.KTokenTypes

/**
 * Simple lexer for K language syntax highlighting.
 * This is a basic implementation - for full parsing, integrate with ANTLR.
 */
class KLexer : LexerBase() {

    private var buffer: CharSequence = ""
    private var bufferEnd: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var currentToken: IElementType? = null

    // Keywords map
    private val keywords = mapOf(
        "class" to KTokenTypes.CLASS,
        "assoc" to KTokenTypes.ASSOC,
        "package" to KTokenTypes.PACKAGE,
        "import" to KTokenTypes.IMPORT,
        "extends" to KTokenTypes.EXTENDS,
        "type" to KTokenTypes.TYPE,
        "annotation" to KTokenTypes.ANNOTATION_KEYWORD,
        "if" to KTokenTypes.IF,
        "then" to KTokenTypes.THEN,
        "else" to KTokenTypes.ELSE,
        "match" to KTokenTypes.MATCH,
        "with" to KTokenTypes.WITH,
        "case" to KTokenTypes.CASE,
        "while" to KTokenTypes.WHILE,
        "do" to KTokenTypes.DO,
        "for" to KTokenTypes.FOR,
        "in" to KTokenTypes.IN,
        "return" to KTokenTypes.RETURN,
        "break" to KTokenTypes.BREAK,
        "continue" to KTokenTypes.CONTINUE,
        "fun" to KTokenTypes.FUN,
        "pre" to KTokenTypes.PRE,
        "post" to KTokenTypes.POST,
        "val" to KTokenTypes.VAL,
        "var" to KTokenTypes.VAR,
        "part" to KTokenTypes.PART,
        "ordered" to KTokenTypes.ORDERED,
        "unique" to KTokenTypes.UNIQUE,
        "source" to KTokenTypes.SOURCE,
        "target" to KTokenTypes.TARGET,
        "req" to KTokenTypes.REQ,
        "soft" to KTokenTypes.SOFT,
        "assert" to KTokenTypes.ASSERT,
        "minimize" to KTokenTypes.MINIMIZE,
        "maximize" to KTokenTypes.MAXIMIZE,
        "weight" to KTokenTypes.WEIGHT,
        "forall" to KTokenTypes.FORALL,
        "exists" to KTokenTypes.EXISTS,
        "true" to KTokenTypes.TRUE,
        "false" to KTokenTypes.FALSE,
        "null" to KTokenTypes.NULL,
        "this" to KTokenTypes.THIS,
        "isin" to KTokenTypes.ISIN,
        "subset" to KTokenTypes.SUBSET,
        "psubset" to KTokenTypes.PSUBSET,
        "union" to KTokenTypes.UNION,
        "inter" to KTokenTypes.INTER,
        // Types
        "Bool" to KTokenTypes.BOOL_TYPE,
        "Char" to KTokenTypes.CHAR_TYPE,
        "Int" to KTokenTypes.INT_TYPE,
        "Real" to KTokenTypes.REAL_TYPE,
        "String" to KTokenTypes.STRING_TYPE,
        "Unit" to KTokenTypes.UNIT_TYPE,
        "Time" to KTokenTypes.TIME_TYPE,
        "Duration" to KTokenTypes.DURATION_TYPE,
        "Set" to KTokenTypes.SET,
        "OSet" to KTokenTypes.OSET,
        "Bag" to KTokenTypes.BAG,
        "Seq" to KTokenTypes.SEQ
    )

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = currentToken

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun advance() {
        tokenStart = tokenEnd

        if (tokenStart >= bufferEnd) {
            currentToken = null
            return
        }

        val c = buffer[tokenStart]

        // Whitespace
        if (c.isWhitespace()) {
            tokenEnd = tokenStart + 1
            while (tokenEnd < bufferEnd && buffer[tokenEnd].isWhitespace()) {
                tokenEnd++
            }
            currentToken = KTokenTypes.WHITE_SPACE
            return
        }

        // Line comment: --
        if (c == '-' && tokenStart + 1 < bufferEnd && buffer[tokenStart + 1] == '-') {
            tokenEnd = tokenStart + 2
            while (tokenEnd < bufferEnd && buffer[tokenEnd] != '\n') {
                tokenEnd++
            }
            currentToken = KTokenTypes.LINE_COMMENT
            return
        }

        // Line comment: //
        if (c == '/' && tokenStart + 1 < bufferEnd && buffer[tokenStart + 1] == '/') {
            tokenEnd = tokenStart + 2
            while (tokenEnd < bufferEnd && buffer[tokenEnd] != '\n') {
                tokenEnd++
            }
            currentToken = KTokenTypes.LINE_COMMENT
            return
        }

        // Block comment: /* ... */
        if (c == '/' && tokenStart + 1 < bufferEnd && buffer[tokenStart + 1] == '*') {
            tokenEnd = tokenStart + 2
            while (tokenEnd < bufferEnd - 1) {
                if (buffer[tokenEnd] == '*' && buffer[tokenEnd + 1] == '/') {
                    tokenEnd += 2
                    break
                }
                tokenEnd++
            }
            if (tokenEnd >= bufferEnd - 1) tokenEnd = bufferEnd
            currentToken = KTokenTypes.BLOCK_COMMENT
            return
        }

        // Documentation block: ===== (2+ equals at start of line through matching line)
        // This handles lines like: ==============================
        if (c == '=' && tokenStart + 1 < bufferEnd && buffer[tokenStart + 1] == '=') {
            // Check if we're at the start of a line (or start of file)
            val atLineStart = tokenStart == 0 || buffer[tokenStart - 1] == '\n'
            if (atLineStart) {
                // Consume the opening === line
                tokenEnd = tokenStart
                while (tokenEnd < bufferEnd && buffer[tokenEnd] == '=') {
                    tokenEnd++
                }
                // Skip to end of line
                while (tokenEnd < bufferEnd && buffer[tokenEnd] != '\n') {
                    tokenEnd++
                }
                if (tokenEnd < bufferEnd) tokenEnd++ // consume newline

                // Now consume content until we find another line starting with ==
                while (tokenEnd < bufferEnd) {
                    // Check if this line starts with ==
                    if (buffer[tokenEnd] == '=') {
                        var eqCount = 0
                        var checkPos = tokenEnd
                        while (checkPos < bufferEnd && buffer[checkPos] == '=') {
                            eqCount++
                            checkPos++
                        }
                        if (eqCount >= 2) {
                            // Found closing line, consume it
                            tokenEnd = checkPos
                            while (tokenEnd < bufferEnd && buffer[tokenEnd] != '\n') {
                                tokenEnd++
                            }
                            if (tokenEnd < bufferEnd) tokenEnd++ // consume newline
                            break
                        }
                    }
                    // Skip to next line
                    while (tokenEnd < bufferEnd && buffer[tokenEnd] != '\n') {
                        tokenEnd++
                    }
                    if (tokenEnd < bufferEnd) tokenEnd++ // consume newline
                }
                currentToken = KTokenTypes.BLOCK_COMMENT
                return
            }
        }

        // String literal
        if (c == '"') {
            tokenEnd = tokenStart + 1
            while (tokenEnd < bufferEnd) {
                val sc = buffer[tokenEnd]
                if (sc == '\\' && tokenEnd + 1 < bufferEnd) {
                    tokenEnd += 2
                    continue
                }
                if (sc == '"') {
                    tokenEnd++
                    break
                }
                tokenEnd++
            }
            currentToken = KTokenTypes.STRING_LITERAL
            return
        }

        // Character literal
        if (c == '\'') {
            tokenEnd = tokenStart + 1
            while (tokenEnd < bufferEnd) {
                val sc = buffer[tokenEnd]
                if (sc == '\\' && tokenEnd + 1 < bufferEnd) {
                    tokenEnd += 2
                    continue
                }
                if (sc == '\'') {
                    tokenEnd++
                    break
                }
                tokenEnd++
            }
            currentToken = KTokenTypes.CHAR_LITERAL
            return
        }

        // Number
        if (c.isDigit()) {
            tokenEnd = tokenStart + 1
            while (tokenEnd < bufferEnd && buffer[tokenEnd].isDigit()) {
                tokenEnd++
            }
            // Check for decimal
            if (tokenEnd < bufferEnd && buffer[tokenEnd] == '.') {
                val afterDot = tokenEnd + 1
                if (afterDot < bufferEnd && buffer[afterDot].isDigit()) {
                    tokenEnd = afterDot + 1
                    while (tokenEnd < bufferEnd && buffer[tokenEnd].isDigit()) {
                        tokenEnd++
                    }
                    currentToken = KTokenTypes.REAL_LITERAL
                    return
                }
            }
            currentToken = KTokenTypes.INTEGER_LITERAL
            return
        }

        // Identifier or keyword
        if (c.isLetter() || c == '_') {
            tokenEnd = tokenStart + 1
            while (tokenEnd < bufferEnd && (buffer[tokenEnd].isLetterOrDigit() || buffer[tokenEnd] == '_')) {
                tokenEnd++
            }
            val word = buffer.substring(tokenStart, tokenEnd)

            // Check for !isin
            if (word == "!isin") {
                currentToken = KTokenTypes.NOT_ISIN
                return
            }

            currentToken = keywords[word] ?: KTokenTypes.IDENTIFIER
            return
        }

        // Annotation
        if (c == '@') {
            tokenEnd = tokenStart + 1
            currentToken = KTokenTypes.AT
            return
        }

        // Multi-character operators
        if (tokenStart + 1 < bufferEnd) {
            val next = buffer[tokenStart + 1]
            val twoChar = "$c$next"

            when (twoChar) {
                ":-" -> { tokenEnd = tokenStart + 2; currentToken = KTokenTypes.SUCHTHAT; return }
                ":=" -> { tokenEnd = tokenStart + 2; currentToken = KTokenTypes.ASSIGN; return }
                "::" -> { tokenEnd = tokenStart + 2; currentToken = KTokenTypes.COLONCOLON; return }
                "->" -> { tokenEnd = tokenStart + 2; currentToken = KTokenTypes.ARROW; return }
                "=>" -> { tokenEnd = tokenStart + 2; currentToken = KTokenTypes.IMPLIES; return }
                "&&" -> { tokenEnd = tokenStart + 2; currentToken = KTokenTypes.AND; return }
                "||" -> { tokenEnd = tokenStart + 2; currentToken = KTokenTypes.OR; return }
                "<=" -> { tokenEnd = tokenStart + 2; currentToken = KTokenTypes.LE; return }
                ">=" -> { tokenEnd = tokenStart + 2; currentToken = KTokenTypes.GE; return }
                "!=" -> { tokenEnd = tokenStart + 2; currentToken = KTokenTypes.NEQ; return }
            }

            // Three-char: <=>
            if (tokenStart + 2 < bufferEnd && twoChar == "<=" && buffer[tokenStart + 2] == '>') {
                tokenEnd = tokenStart + 3
                currentToken = KTokenTypes.IFF
                return
            }
        }

        // Single character tokens
        tokenEnd = tokenStart + 1
        currentToken = when (c) {
            '+' -> KTokenTypes.PLUS
            '-' -> KTokenTypes.MINUS
            '*' -> KTokenTypes.STAR
            '/' -> KTokenTypes.SLASH
            '%' -> KTokenTypes.PERCENT
            '=' -> KTokenTypes.EQ
            '<' -> KTokenTypes.LT
            '>' -> KTokenTypes.GT
            '!' -> KTokenTypes.NOT
            '(' -> KTokenTypes.LPAREN
            ')' -> KTokenTypes.RPAREN
            '{' -> KTokenTypes.LBRACE
            '}' -> KTokenTypes.RBRACE
            '[' -> KTokenTypes.LBRACKET
            ']' -> KTokenTypes.RBRACKET
            ':' -> KTokenTypes.COLON
            '.' -> KTokenTypes.DOT
            ',' -> KTokenTypes.COMMA
            ';' -> KTokenTypes.SEMICOLON
            else -> KTokenTypes.BAD_CHARACTER
        }
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = bufferEnd
}

