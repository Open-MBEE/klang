package nasa.jpl.klang.ide.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import nasa.jpl.klang.ide.KLanguage
import nasa.jpl.klang.ide.lexer.KLexer
import nasa.jpl.klang.ide.psi.KFile
import nasa.jpl.klang.ide.psi.KTokenTypes

/**
 * Parser definition for K language.
 * This defines how the IDE should parse K files.
 */
class KParserDefinition : ParserDefinition {

    companion object {
        val FILE = IFileElementType(KLanguage)

        val COMMENTS = TokenSet.create(
            KTokenTypes.LINE_COMMENT,
            KTokenTypes.BLOCK_COMMENT
        )

        val STRINGS = TokenSet.create(
            KTokenTypes.STRING_LITERAL,
            KTokenTypes.CHAR_LITERAL
        )

        val WHITE_SPACES = TokenSet.create(KTokenTypes.WHITE_SPACE)
    }

    override fun createLexer(project: Project?): Lexer = KLexer()

    override fun createParser(project: Project?): PsiParser = KParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = STRINGS

    override fun getWhitespaceTokens(): TokenSet = WHITE_SPACES

    override fun createElement(node: ASTNode): PsiElement {
        return KPsiFactory.createElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return KFile(viewProvider)
    }
}
