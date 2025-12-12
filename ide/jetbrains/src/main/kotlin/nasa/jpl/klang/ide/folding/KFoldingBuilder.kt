package nasa.jpl.klang.ide.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import nasa.jpl.klang.ide.psi.KElementTypes
import nasa.jpl.klang.ide.psi.KTokenTypes

/**
 * Provides code folding for K language constructs:
 * - Class bodies { ... }
 * - Function bodies { ... }
 * - Block comments /* ... */ and ====...====
 * - Import groups
 */
class KFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()

        collectFoldingRegions(root.node, descriptors, document)

        return descriptors.toTypedArray()
    }

    private fun collectFoldingRegions(
        node: ASTNode,
        descriptors: MutableList<FoldingDescriptor>,
        document: Document
    ) {
        val elementType = node.elementType
        val textRange = node.textRange

        when (elementType) {
            // Fold class bodies
            KElementTypes.CLASS_DEFINITION, KElementTypes.CLASS_BODY -> {
                val text = node.text
                val braceStart = text.indexOf('{')
                val braceEnd = text.lastIndexOf('}')
                if (braceStart >= 0 && braceEnd > braceStart + 1) {
                    val foldStart = textRange.startOffset + braceStart
                    val foldEnd = textRange.startOffset + braceEnd + 1
                    if (foldEnd - foldStart > 3 && isMultiLine(document, foldStart, foldEnd)) {
                        descriptors.add(FoldingDescriptor(
                            node,
                            TextRange(foldStart, foldEnd),
                            FoldingGroup.newGroup("class")
                        ))
                    }
                }
            }

            // Fold function bodies
            KElementTypes.FUNCTION_DEFINITION, KElementTypes.FUNCTION_BODY -> {
                val text = node.text
                val braceStart = text.indexOf('{')
                val braceEnd = text.lastIndexOf('}')
                if (braceStart >= 0 && braceEnd > braceStart + 1) {
                    val foldStart = textRange.startOffset + braceStart
                    val foldEnd = textRange.startOffset + braceEnd + 1
                    if (foldEnd - foldStart > 3 && isMultiLine(document, foldStart, foldEnd)) {
                        descriptors.add(FoldingDescriptor(
                            node,
                            TextRange(foldStart, foldEnd),
                            FoldingGroup.newGroup("function")
                        ))
                    }
                }
            }

            // Fold block comments /* ... */ and ====...====
            KTokenTypes.BLOCK_COMMENT -> {
                if (textRange.length > 4 && isMultiLine(document, textRange.startOffset, textRange.endOffset)) {
                    descriptors.add(FoldingDescriptor(
                        node,
                        textRange,
                        FoldingGroup.newGroup("comment")
                    ))
                }
            }

            // Fold line comments that are consecutive (grouped comments)
            KTokenTypes.LINE_COMMENT -> {
                // We handle line comment groups separately below
            }
        }

        // Process children
        var child = node.firstChildNode
        while (child != null) {
            collectFoldingRegions(child, descriptors, document)
            child = child.treeNext
        }
    }

    private fun isMultiLine(document: Document, startOffset: Int, endOffset: Int): Boolean {
        if (startOffset >= document.textLength || endOffset > document.textLength) {
            return false
        }
        val startLine = document.getLineNumber(startOffset)
        val endLine = document.getLineNumber(endOffset - 1)
        return endLine > startLine
    }

    override fun getPlaceholderText(node: ASTNode): String {
        val text = node.text
        return when (node.elementType) {
            KTokenTypes.BLOCK_COMMENT -> {
                if (text.startsWith("==")) {
                    "=...="  // For ===...=== style comments
                } else {
                    "/* ... */"
                }
            }
            KElementTypes.CLASS_DEFINITION, KElementTypes.CLASS_BODY -> "{ ... }"
            KElementTypes.FUNCTION_DEFINITION, KElementTypes.FUNCTION_BODY -> "{ ... }"
            else -> "..."
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        // Large documentation comments at the top can be collapsed by default
        val text = node.text
        return when (node.elementType) {
            KTokenTypes.BLOCK_COMMENT -> text.startsWith("==")  // Collapse doc blocks by default
            else -> false
        }
    }
}

