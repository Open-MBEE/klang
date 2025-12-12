package nasa.jpl.klang.ide.formatting

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import nasa.jpl.klang.ide.KLanguage
import nasa.jpl.klang.ide.psi.KElementTypes
import nasa.jpl.klang.ide.psi.KTokenTypes

/**
 * Creates the formatting model for K language files
 */
class KFormattingModelBuilder : FormattingModelBuilder {

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val codeStyleSettings = formattingContext.codeStyleSettings
        val file = formattingContext.psiElement.containingFile
        val rootBlock = KBlock(
            file.node,
            null,
            Indent.getNoneIndent(),
            null,
            createSpaceBuilder(codeStyleSettings)
        )
        return FormattingModelProvider.createFormattingModelForPsiFile(file, rootBlock, codeStyleSettings)
    }

    // Deprecated method for backward compatibility
    @Suppress("DEPRECATION", "DeprecatedCallableAddReplaceWith")
    @Deprecated("Use createModel(FormattingContext) instead")
    override fun createModel(element: PsiElement, settings: CodeStyleSettings): FormattingModel {
        val file = element.containingFile
        val rootBlock = KBlock(
            file.node,
            null,
            Indent.getNoneIndent(),
            null,
            createSpaceBuilder(settings)
        )
        return FormattingModelProvider.createFormattingModelForPsiFile(file, rootBlock, settings)
    }

    private fun createSpaceBuilder(settings: CodeStyleSettings): SpacingBuilder {
        return SpacingBuilder(settings, KLanguage)
            .after(KTokenTypes.PACKAGE).spaces(1)
            .after(KTokenTypes.IMPORT).spaces(1)
            .after(KTokenTypes.CLASS).spaces(1)
            .after(KTokenTypes.ASSOC).spaces(1)
            .after(KTokenTypes.EXTENDS).spaces(1)
            .after(KTokenTypes.FUN).spaces(1)
            .after(KTokenTypes.REQ).spaces(1)
            .after(KTokenTypes.VAL).spaces(1)
            .after(KTokenTypes.VAR).spaces(1)
            .around(KTokenTypes.COLON).spaces(1)
            .around(KTokenTypes.EQ).spaces(1)
            .around(KTokenTypes.LT).spaces(1)
            .around(KTokenTypes.GT).spaces(1)
            .around(KTokenTypes.LE).spaces(1)
            .around(KTokenTypes.GE).spaces(1)
            .around(KTokenTypes.AND).spaces(1)
            .around(KTokenTypes.OR).spaces(1)
            .around(KTokenTypes.PLUS).spaces(1)
            .around(KTokenTypes.MINUS).spaces(1)
            .around(KTokenTypes.STAR).spaces(1)
            .around(KTokenTypes.SLASH).spaces(1)
            .before(KTokenTypes.COMMA).spaces(0)
            .after(KTokenTypes.COMMA).spaces(1)
            .before(KTokenTypes.LPAREN).spaces(0)
            .after(KTokenTypes.LBRACE).spaces(1)
            .before(KTokenTypes.RBRACE).spaces(1)
    }
}

class KBlock(
    private val node: ASTNode,
    private val alignment: Alignment?,
    private val indent: Indent,
    private val wrap: Wrap?,
    private val spacingBuilder: SpacingBuilder
) : Block {

    override fun getTextRange(): TextRange = node.textRange

    override fun getSubBlocks(): MutableList<Block> {
        val blocks = mutableListOf<Block>()
        var child = node.firstChildNode

        while (child != null) {
            if (child.elementType != KTokenTypes.WHITE_SPACE) {
                val childIndent = computeIndent(child)
                blocks.add(KBlock(child, null, childIndent, null, spacingBuilder))
            }
            child = child.treeNext
        }

        return blocks
    }

    private fun computeIndent(child: ASTNode): Indent {
        val parentType = node.elementType
        val childType = child.elementType

        if (parentType == KElementTypes.CLASS_DEFINITION || parentType == KElementTypes.CLASS_BODY) {
            if (childType != KTokenTypes.LBRACE && childType != KTokenTypes.RBRACE) {
                return Indent.getNormalIndent()
            }
        }

        if (parentType == KElementTypes.FUNCTION_DEFINITION || parentType == KElementTypes.FUNCTION_BODY) {
            if (childType != KTokenTypes.LBRACE && childType != KTokenTypes.RBRACE) {
                return Indent.getNormalIndent()
            }
        }

        return Indent.getNoneIndent()
    }

    override fun getWrap(): Wrap? = wrap
    override fun getIndent(): Indent = indent
    override fun getAlignment(): Alignment? = alignment

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        return spacingBuilder.getSpacing(this, child1, child2)
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        if (node.elementType == KElementTypes.CLASS_DEFINITION ||
            node.elementType == KElementTypes.CLASS_BODY ||
            node.elementType == KElementTypes.FUNCTION_DEFINITION ||
            node.elementType == KElementTypes.FUNCTION_BODY) {
            return ChildAttributes(Indent.getNormalIndent(), null)
        }
        return ChildAttributes(Indent.getNoneIndent(), null)
    }

    override fun isIncomplete(): Boolean {
        val text = node.text
        return text.count { it == '{' } > text.count { it == '}' }
    }

    override fun isLeaf(): Boolean = node.firstChildNode == null
}

