package nasa.jpl.klang.ide.breadcrumbs

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import nasa.jpl.klang.ide.KLanguage
import nasa.jpl.klang.ide.psi.KElementTypes

/**
 * Provides breadcrumb navigation for K language files.
 * Shows package → class → function/constraint hierarchy.
 */
class KBreadcrumbsProvider : BreadcrumbsProvider {

    override fun getLanguages(): Array<Language> = arrayOf(KLanguage)

    override fun acceptElement(element: PsiElement): Boolean {
        val elementType = element.node?.elementType ?: return false
        return elementType in setOf(
            KElementTypes.PACKAGE_DECLARATION,
            KElementTypes.CLASS_DEFINITION,
            KElementTypes.FUNCTION_DEFINITION,
            KElementTypes.CONSTRAINT_DEFINITION,
            KElementTypes.PROPERTY_DECLARATION
        )
    }

    override fun getElementInfo(element: PsiElement): String {
        val node = element.node ?: return ""
        val text = element.text

        return when (node.elementType) {
            KElementTypes.PACKAGE_DECLARATION -> {
                // Extract package name
                val match = Regex("package\\s+([\\w.]+)").find(text)
                match?.groupValues?.get(1) ?: "package"
            }
            KElementTypes.CLASS_DEFINITION -> {
                // Extract class name
                val match = Regex("(class|assoc)\\s+(\\w+)").find(text)
                match?.groupValues?.get(2) ?: "class"
            }
            KElementTypes.FUNCTION_DEFINITION -> {
                // Extract function name
                val match = Regex("fun\\s+(\\w+)").find(text)
                match?.groupValues?.get(1) ?: "fun"
            }
            KElementTypes.CONSTRAINT_DEFINITION -> {
                // Extract constraint name if present
                val match = Regex("req\\s+(\\w+)\\s*:").find(text)
                match?.groupValues?.get(1) ?: "req"
            }
            KElementTypes.PROPERTY_DECLARATION -> {
                // Extract property name
                val match = Regex("(\\w+)\\s*:").find(text)
                match?.groupValues?.get(1) ?: "property"
            }
            else -> ""
        }
    }

    override fun getElementTooltip(element: PsiElement): String? {
        return getElementInfo(element)
    }
}

