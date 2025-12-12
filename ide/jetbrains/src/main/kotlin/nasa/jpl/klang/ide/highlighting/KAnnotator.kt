package nasa.jpl.klang.ide.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import nasa.jpl.klang.ide.psi.*

/**
 * Provides semantic highlighting and real-time error checking for K files.
 *
 * Semantic highlighting includes:
 * - Class definition names (bold)
 * - Function definition names (italic)
 * - Type references in declarations
 * - Property names
 * - Constraint names
 */
class KAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val elementType = element.node?.elementType

        // Semantic highlighting based on context
        when (elementType) {
            KElementTypes.CLASS_DEFINITION -> {
                highlightClassDefinition(element, holder)
                checkClassErrors(element, holder)
            }
            KElementTypes.FUNCTION_DEFINITION -> {
                highlightFunctionDefinition(element, holder)
                checkFunctionErrors(element, holder)
            }
            KElementTypes.PROPERTY_DEFINITION, KElementTypes.PROPERTY_DECLARATION -> {
                highlightPropertyDefinition(element, holder)
                checkPropertyErrors(element, holder)
            }
            KElementTypes.CONSTRAINT, KElementTypes.CONSTRAINT_DEFINITION -> {
                highlightConstraint(element, holder)
            }
            KElementTypes.TYPE_REFERENCE -> {
                highlightTypeReference(element, holder)
            }
        }

        // Also check by token type for identifiers in specific contexts
        if (elementType == KTokenTypes.IDENTIFIER) {
            highlightIdentifierByContext(element, holder)
        }
    }

    private fun highlightClassDefinition(element: PsiElement, holder: AnnotationHolder) {
        // Find the class name identifier and highlight it
        val text = element.text
        val classMatch = Regex("(?:class|assoc)\\s+(\\w+)").find(text)
        if (classMatch != null) {
            val nameStart = element.textRange.startOffset + classMatch.range.first +
                (if (text.startsWith("assoc")) 6 else 6) // "class " or "assoc "
            val name = classMatch.groupValues[1]
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(com.intellij.openapi.util.TextRange(nameStart, nameStart + name.length))
                .textAttributes(KHighlightingColors.CLASS_NAME)
                .create()
        }
    }

    private fun highlightFunctionDefinition(element: PsiElement, holder: AnnotationHolder) {
        val text = element.text
        val funMatch = Regex("fun\\s+(\\w+)").find(text)
        if (funMatch != null) {
            val nameStart = element.textRange.startOffset + funMatch.range.first + 4 // "fun "
            val name = funMatch.groupValues[1]
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(com.intellij.openapi.util.TextRange(nameStart, nameStart + name.length))
                .textAttributes(KHighlightingColors.FUNCTION_NAME)
                .create()
        }
    }

    private fun highlightPropertyDefinition(element: PsiElement, holder: AnnotationHolder) {
        val text = element.text
        val propMatch = Regex("^\\s*(\\w+)\\s*:").find(text)
        if (propMatch != null) {
            val nameStart = element.textRange.startOffset + propMatch.range.first
            val name = propMatch.groupValues[1]
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(com.intellij.openapi.util.TextRange(nameStart, nameStart + name.length))
                .textAttributes(KHighlightingColors.PROPERTY_NAME)
                .create()
        }
    }

    private fun highlightConstraint(element: PsiElement, holder: AnnotationHolder) {
        val text = element.text
        // Named constraint: req Name: expression
        val namedMatch = Regex("req\\s+(\\w+)\\s*:").find(text)
        if (namedMatch != null) {
            val nameStart = element.textRange.startOffset + namedMatch.range.first + 4 // "req "
            val name = namedMatch.groupValues[1]
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(com.intellij.openapi.util.TextRange(nameStart, nameStart + name.length))
                .textAttributes(KHighlightingColors.CONSTRAINT_NAME)
                .create()
        }
    }

    private fun highlightTypeReference(element: PsiElement, holder: AnnotationHolder) {
        val text = element.text.trim()
        // User-defined types (start with uppercase, not a primitive)
        val primitives = setOf("Int", "Real", "Bool", "String", "Char", "Unit", "Time", "Duration",
            "Set", "OSet", "Bag", "Seq")
        if (text.isNotEmpty() && text[0].isUpperCase() && text !in primitives) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.textRange)
                .textAttributes(KHighlightingColors.CLASS_REFERENCE)
                .create()
        }
    }

    private fun highlightIdentifierByContext(element: PsiElement, holder: AnnotationHolder) {
        val parent = element.parent ?: return
        val parentType = parent.node?.elementType

        // Check if this identifier is a type reference after a colon
        val prevSibling = element.prevSibling
        if (prevSibling != null && prevSibling.text.trim() == ":") {
            val text = element.text
            val primitives = setOf("Int", "Real", "Bool", "String", "Char", "Unit", "Time", "Duration")
            if (text.isNotEmpty() && text[0].isUpperCase() && text !in primitives) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element.textRange)
                    .textAttributes(KHighlightingColors.CLASS_REFERENCE)
                    .create()
            }
        }
    }

    // Error checking methods

    private fun checkClassErrors(element: PsiElement, holder: AnnotationHolder) {
        val text = element.text
        val classMatch = Regex("(?:class|assoc)\\s+(\\w+)").find(text) ?: return
        val name = classMatch.groupValues[1]

        if (name.isNotEmpty() && !name[0].isUpperCase()) {
            val nameStart = element.textRange.startOffset + classMatch.range.first +
                (if (text.startsWith("assoc")) 6 else 6)
            holder.newAnnotation(HighlightSeverity.WARNING, "Class names should start with uppercase")
                .range(com.intellij.openapi.util.TextRange(nameStart, nameStart + name.length))
                .create()
        }
    }

    private fun checkPropertyErrors(element: PsiElement, holder: AnnotationHolder) {
        val text = element.text
        val propMatch = Regex("^\\s*(\\w+)\\s*:").find(text) ?: return
        val name = propMatch.groupValues[1]

        if (name.isNotEmpty() && name[0].isUpperCase() && !name.all { it.isUpperCase() || it == '_' }) {
            val nameStart = element.textRange.startOffset + propMatch.range.first
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING, "Property names typically start with lowercase")
                .range(com.intellij.openapi.util.TextRange(nameStart, nameStart + name.length))
                .create()
        }
    }

    private fun checkFunctionErrors(element: PsiElement, holder: AnnotationHolder) {
        val text = element.text
        val funMatch = Regex("fun\\s+(\\w+)").find(text) ?: return
        val name = funMatch.groupValues[1]

        if (name.isNotEmpty() && name[0].isUpperCase()) {
            val nameStart = element.textRange.startOffset + funMatch.range.first + 4
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING, "Function names typically start with lowercase")
                .range(com.intellij.openapi.util.TextRange(nameStart, nameStart + name.length))
                .create()
        }
    }
}

