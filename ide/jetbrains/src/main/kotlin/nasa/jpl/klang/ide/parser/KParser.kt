package nasa.jpl.klang.ide.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import nasa.jpl.klang.ide.psi.KElementTypes
import nasa.jpl.klang.ide.psi.KTokenTypes

/**
 * Parser for K language.
 * Builds the PSI tree from tokens.
 * 
 * This is a hand-written recursive descent parser.
 * For a production implementation, consider using ANTLR integration.
 */
class KParser : PsiParser {

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        
        while (!builder.eof()) {
            parseTopLevel(builder)
        }
        
        rootMarker.done(root)
        return builder.treeBuilt
    }

    private fun parseTopLevel(builder: PsiBuilder) {
        when (builder.tokenType) {
            KTokenTypes.PACKAGE -> parsePackageDeclaration(builder)
            KTokenTypes.IMPORT -> parseImportDeclaration(builder)
            KTokenTypes.CLASS -> parseClassDefinition(builder)
            KTokenTypes.ASSOC -> parseAssocDefinition(builder)
            KTokenTypes.TYPE -> parseTypeAlias(builder)
            KTokenTypes.LINE_COMMENT, KTokenTypes.BLOCK_COMMENT -> {
                builder.advanceLexer()
            }
            KTokenTypes.WHITE_SPACE -> {
                builder.advanceLexer()
            }
            KTokenTypes.AT -> {
                parseAnnotation(builder)
            }
            else -> {
                // Skip unknown tokens
                builder.advanceLexer()
            }
        }
    }

    private fun parsePackageDeclaration(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume 'package'
        
        // Parse qualified name
        parseQualifiedName(builder)
        
        marker.done(KElementTypes.PACKAGE_DECLARATION)
    }

    private fun parseImportDeclaration(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume 'import'
        
        // Parse qualified name
        parseQualifiedName(builder)
        
        marker.done(KElementTypes.IMPORT_DECLARATION)
    }

    private fun parseClassDefinition(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume 'class'
        skipWhitespace(builder)
        
        // Class name
        if (builder.tokenType == KTokenTypes.IDENTIFIER) {
            builder.advanceLexer()
        }
        skipWhitespace(builder)
        
        // extends clause
        if (builder.tokenType == KTokenTypes.EXTENDS) {
            parseExtendsList(builder)
        }
        skipWhitespace(builder)
        
        // Class body
        if (builder.tokenType == KTokenTypes.LBRACE) {
            parseClassBody(builder)
        }
        
        marker.done(KElementTypes.CLASS_DEFINITION)
    }

    private fun parseAssocDefinition(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume 'assoc'
        skipWhitespace(builder)
        
        // Association name
        if (builder.tokenType == KTokenTypes.IDENTIFIER) {
            builder.advanceLexer()
        }
        skipWhitespace(builder)
        
        // Association body
        if (builder.tokenType == KTokenTypes.LBRACE) {
            parseClassBody(builder)
        }
        
        marker.done(KElementTypes.ASSOC_DEFINITION)
    }

    private fun parseTypeAlias(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume 'type'
        skipWhitespace(builder)
        
        // Type name
        if (builder.tokenType == KTokenTypes.IDENTIFIER) {
            builder.advanceLexer()
        }
        skipWhitespace(builder)
        
        // = Type
        if (builder.tokenType == KTokenTypes.EQ) {
            builder.advanceLexer()
            skipWhitespace(builder)
            parseTypeReference(builder)
        }
        
        marker.done(KElementTypes.TYPE_ALIAS)
    }

    private fun parseExtendsList(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume 'extends'
        skipWhitespace(builder)
        
        // Parse comma-separated type references
        parseTypeReference(builder)
        skipWhitespace(builder)
        
        while (builder.tokenType == KTokenTypes.COMMA) {
            builder.advanceLexer()
            skipWhitespace(builder)
            parseTypeReference(builder)
            skipWhitespace(builder)
        }
        
        marker.done(KElementTypes.EXTENDS_LIST)
    }

    private fun parseTypeReference(builder: PsiBuilder) {
        val marker = builder.mark()
        
        // Type name (could be builtin or user-defined)
        when (builder.tokenType) {
            KTokenTypes.IDENTIFIER,
            KTokenTypes.BOOL_TYPE, KTokenTypes.INT_TYPE, KTokenTypes.REAL_TYPE,
            KTokenTypes.STRING_TYPE, KTokenTypes.CHAR_TYPE, KTokenTypes.UNIT_TYPE,
            KTokenTypes.TIME_TYPE, KTokenTypes.DURATION_TYPE,
            KTokenTypes.SET, KTokenTypes.OSET, KTokenTypes.BAG, KTokenTypes.SEQ -> {
                builder.advanceLexer()
            }
        }
        skipWhitespace(builder)
        
        // Generic type arguments: [T]
        if (builder.tokenType == KTokenTypes.LBRACKET) {
            builder.advanceLexer()
            skipWhitespace(builder)
            parseTypeReference(builder)
            skipWhitespace(builder)
            while (builder.tokenType == KTokenTypes.COMMA) {
                builder.advanceLexer()
                skipWhitespace(builder)
                parseTypeReference(builder)
                skipWhitespace(builder)
            }
            if (builder.tokenType == KTokenTypes.RBRACKET) {
                builder.advanceLexer()
            }
        }
        
        marker.done(KElementTypes.TYPE_REFERENCE)
    }

    private fun parseClassBody(builder: PsiBuilder) {
        builder.advanceLexer() // consume '{'
        skipWhitespace(builder)
        
        while (!builder.eof() && builder.tokenType != KTokenTypes.RBRACE) {
            when (builder.tokenType) {
                KTokenTypes.FUN -> parseFunctionDefinition(builder)
                KTokenTypes.REQ, KTokenTypes.SOFT -> parseConstraint(builder)
                KTokenTypes.AT -> parseAnnotation(builder)
                KTokenTypes.IDENTIFIER -> parsePropertyDefinition(builder)
                KTokenTypes.LINE_COMMENT, KTokenTypes.BLOCK_COMMENT -> {
                    builder.advanceLexer()
                }
                KTokenTypes.WHITE_SPACE -> {
                    builder.advanceLexer()
                }
                else -> {
                    // Skip unknown tokens in body
                    builder.advanceLexer()
                }
            }
            skipWhitespace(builder)
        }
        
        if (builder.tokenType == KTokenTypes.RBRACE) {
            builder.advanceLexer()
        }
    }

    private fun parsePropertyDefinition(builder: PsiBuilder) {
        val marker = builder.mark()
        
        // Property name
        builder.advanceLexer() // consume identifier
        skipWhitespace(builder)
        
        // : Type
        if (builder.tokenType == KTokenTypes.COLON) {
            builder.advanceLexer()
            skipWhitespace(builder)
            parseTypeReference(builder)
        }
        
        // Optional multiplicity [n..m] or [*]
        skipWhitespace(builder)
        if (builder.tokenType == KTokenTypes.LBRACKET) {
            parseMultiplicity(builder)
        }
        
        marker.done(KElementTypes.PROPERTY_DEFINITION)
    }

    private fun parseFunctionDefinition(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume 'fun'
        skipWhitespace(builder)
        
        // Function name
        if (builder.tokenType == KTokenTypes.IDENTIFIER) {
            builder.advanceLexer()
        }
        skipWhitespace(builder)
        
        // Parameters: (x: Int, y: Int)
        if (builder.tokenType == KTokenTypes.LPAREN) {
            parseParameterList(builder)
        }
        skipWhitespace(builder)
        
        // Return type: : Int
        if (builder.tokenType == KTokenTypes.COLON) {
            builder.advanceLexer()
            skipWhitespace(builder)
            parseTypeReference(builder)
        }
        skipWhitespace(builder)
        
        // Function body: { expr }
        if (builder.tokenType == KTokenTypes.LBRACE) {
            parseFunctionBody(builder)
        }
        
        marker.done(KElementTypes.FUNCTION_DEFINITION)
    }

    private fun parseParameterList(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume '('
        skipWhitespace(builder)
        
        while (!builder.eof() && builder.tokenType != KTokenTypes.RPAREN) {
            parseParameter(builder)
            skipWhitespace(builder)
            if (builder.tokenType == KTokenTypes.COMMA) {
                builder.advanceLexer()
                skipWhitespace(builder)
            }
        }
        
        if (builder.tokenType == KTokenTypes.RPAREN) {
            builder.advanceLexer()
        }
        
        marker.done(KElementTypes.PARAMETER_LIST)
    }

    private fun parseParameter(builder: PsiBuilder) {
        val marker = builder.mark()
        
        // Parameter name
        if (builder.tokenType == KTokenTypes.IDENTIFIER) {
            builder.advanceLexer()
        }
        skipWhitespace(builder)
        
        // : Type
        if (builder.tokenType == KTokenTypes.COLON) {
            builder.advanceLexer()
            skipWhitespace(builder)
            parseTypeReference(builder)
        }
        
        marker.done(KElementTypes.PARAMETER)
    }

    private fun parseFunctionBody(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume '{'
        
        var braceCount = 1
        while (!builder.eof() && braceCount > 0) {
            when (builder.tokenType) {
                KTokenTypes.LBRACE -> {
                    braceCount++
                    builder.advanceLexer()
                }
                KTokenTypes.RBRACE -> {
                    braceCount--
                    if (braceCount > 0) {
                        builder.advanceLexer()
                    }
                }
                else -> builder.advanceLexer()
            }
        }
        
        if (builder.tokenType == KTokenTypes.RBRACE) {
            builder.advanceLexer()
        }
        
        marker.done(KElementTypes.FUNCTION_BODY)
    }

    private fun parseConstraint(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume 'req' or 'soft'
        skipWhitespace(builder)
        
        // Optional constraint name: constraintName:
        if (builder.tokenType == KTokenTypes.IDENTIFIER) {
            val lookahead = builder.lookAhead(1)
            if (lookahead == KTokenTypes.COLON) {
                builder.advanceLexer() // name
                builder.advanceLexer() // colon
                skipWhitespace(builder)
            }
        }
        
        // Expression until end of line or next statement
        parseExpressionUntilEnd(builder)
        
        marker.done(KElementTypes.CONSTRAINT)
    }

    private fun parseAnnotation(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume '@'
        
        // Annotation name
        if (builder.tokenType == KTokenTypes.IDENTIFIER) {
            builder.advanceLexer()
        }
        
        // Optional arguments
        skipWhitespace(builder)
        if (builder.tokenType == KTokenTypes.LPAREN) {
            parseAnnotationArguments(builder)
        }
        
        marker.done(KElementTypes.ANNOTATION)
    }

    private fun parseAnnotationArguments(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume '('
        
        var parenCount = 1
        while (!builder.eof() && parenCount > 0) {
            when (builder.tokenType) {
                KTokenTypes.LPAREN -> {
                    parenCount++
                    builder.advanceLexer()
                }
                KTokenTypes.RPAREN -> {
                    parenCount--
                    if (parenCount > 0) {
                        builder.advanceLexer()
                    }
                }
                else -> builder.advanceLexer()
            }
        }
        
        if (builder.tokenType == KTokenTypes.RPAREN) {
            builder.advanceLexer()
        }
        
        marker.done(KElementTypes.ANNOTATION_ARGUMENTS)
    }

    private fun parseMultiplicity(builder: PsiBuilder) {
        builder.advanceLexer() // consume '['
        while (!builder.eof() && builder.tokenType != KTokenTypes.RBRACKET) {
            builder.advanceLexer()
        }
        if (builder.tokenType == KTokenTypes.RBRACKET) {
            builder.advanceLexer()
        }
    }

    private fun parseExpressionUntilEnd(builder: PsiBuilder) {
        // Simple approach: consume until we hit a class member keyword or closing brace
        while (!builder.eof()) {
            when (builder.tokenType) {
                KTokenTypes.FUN, KTokenTypes.REQ, KTokenTypes.SOFT,
                KTokenTypes.RBRACE, KTokenTypes.CLASS, KTokenTypes.ASSOC -> return
                KTokenTypes.IDENTIFIER -> {
                    // Check if this looks like a property definition (identifier followed by colon)
                    val next = builder.lookAhead(1)
                    if (next == KTokenTypes.COLON) {
                        // Might be next property, check further
                        val afterColon = builder.lookAhead(2)
                        if (afterColon == KTokenTypes.INT_TYPE || 
                            afterColon == KTokenTypes.REAL_TYPE ||
                            afterColon == KTokenTypes.BOOL_TYPE ||
                            afterColon == KTokenTypes.STRING_TYPE ||
                            afterColon == KTokenTypes.IDENTIFIER) {
                            return
                        }
                    }
                    builder.advanceLexer()
                }
                else -> builder.advanceLexer()
            }
        }
    }

    private fun parseQualifiedName(builder: PsiBuilder) {
        skipWhitespace(builder)
        while (!builder.eof()) {
            if (builder.tokenType == KTokenTypes.IDENTIFIER) {
                builder.advanceLexer()
                skipWhitespace(builder)
                if (builder.tokenType == KTokenTypes.DOT) {
                    builder.advanceLexer()
                    skipWhitespace(builder)
                } else {
                    break
                }
            } else {
                break
            }
        }
    }

    private fun skipWhitespace(builder: PsiBuilder) {
        while (builder.tokenType == KTokenTypes.WHITE_SPACE ||
               builder.tokenType == KTokenTypes.LINE_COMMENT ||
               builder.tokenType == KTokenTypes.BLOCK_COMMENT) {
            builder.advanceLexer()
        }
    }
}
