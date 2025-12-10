package nasa.jpl.klang.ide.psi

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import nasa.jpl.klang.ide.KLanguage
import nasa.jpl.klang.ide.parser.KClassDefinitionImpl
import nasa.jpl.klang.ide.parser.KFunctionDefinitionImpl
import nasa.jpl.klang.ide.parser.KPropertyDefinitionImpl
import nasa.jpl.klang.ide.parser.KParameterImpl

/**
 * Contributes references from identifier usages to their definitions.
 * This enables Find Usages (Alt+F7) functionality.
 */
class KReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement()
                .withLanguage(KLanguage),
            KReferenceProvider()
        )
    }
}

/**
 * Provider that creates references for identifiers that are usages (not definitions).
 */
class KReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        // Only create references for identifiers
        if (element.node?.elementType != KTokenTypes.IDENTIFIER) {
            return PsiReference.EMPTY_ARRAY
        }

        // Don't create references for definition identifiers (they ARE the definitions)
        if (isDefinitionIdentifier(element)) {
            return PsiReference.EMPTY_ARRAY
        }

        // Create a reference for this usage
        return arrayOf(KReference(element, TextRange(0, element.textLength)))
    }

    /**
     * Check if this identifier is part of a definition (not a usage).
     * Definitions shouldn't have references - they ARE the targets.
     */
    private fun isDefinitionIdentifier(element: PsiElement): Boolean {
        val parent = element.parent ?: return false

        // Check if parent is a named element and this is its name identifier
        when (parent) {
            is KClassDefinitionImpl -> {
                return parent.nameIdentifier == element
            }
            is KFunctionDefinitionImpl -> {
                return parent.nameIdentifier == element
            }
            is KPropertyDefinitionImpl -> {
                return parent.nameIdentifier == element
            }
            is KParameterImpl -> {
                return parent.nameIdentifier == element
            }
        }

        return false
    }
}

