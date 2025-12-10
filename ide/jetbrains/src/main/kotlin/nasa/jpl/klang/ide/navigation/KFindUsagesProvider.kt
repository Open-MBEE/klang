package nasa.jpl.klang.ide.navigation

import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import nasa.jpl.klang.ide.parser.KClassDefinitionImpl
import nasa.jpl.klang.ide.parser.KFunctionDefinitionImpl
import nasa.jpl.klang.ide.parser.KPropertyDefinitionImpl
import nasa.jpl.klang.ide.parser.KParameterImpl
import nasa.jpl.klang.ide.psi.KTokenTypes

/**
 * Provides Find Usages functionality for K language elements.
 * Enables "Find Usages" (Alt+F7) and "Highlight Usages in File" (Ctrl+Shift+F7).
 */
class KFindUsagesProvider : FindUsagesProvider {

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean {
        return psiElement is PsiNamedElement && (
            psiElement is KClassDefinitionImpl ||
            psiElement is KFunctionDefinitionImpl ||
            psiElement is KPropertyDefinitionImpl ||
            psiElement is KParameterImpl
        )
    }

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String {
        return when (element) {
            is KClassDefinitionImpl -> "class"
            is KFunctionDefinitionImpl -> "function"
            is KPropertyDefinitionImpl -> "property"
            is KParameterImpl -> "parameter"
            else -> "element"
        }
    }

    override fun getDescriptiveName(element: PsiElement): String {
        return when (element) {
            is KClassDefinitionImpl -> element.getClassName() ?: "<unnamed class>"
            is KFunctionDefinitionImpl -> element.getMemberName() ?: "<unnamed function>"
            is KPropertyDefinitionImpl -> element.getMemberName() ?: "<unnamed property>"
            is KParameterImpl -> element.getParameterName() ?: "<unnamed parameter>"
            is PsiNamedElement -> element.name ?: "<unnamed>"
            else -> element.text ?: "<unknown>"
        }
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String {
        return getDescriptiveName(element)
    }

    override fun getWordsScanner() = null // Use default word scanner
}
