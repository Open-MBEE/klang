package nasa.jpl.klang.ide.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import nasa.jpl.klang.ide.psi.*

/**
 * Provides refactoring support for K language (rename, etc.)
 */
class KRefactoringSupportProvider : RefactoringSupportProvider() {

    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        // Allow inline rename for class definitions, properties, functions, and constraints
        return element is KClassDefinition ||
               element is KPropertyDefinition ||
               element is KFunctionDefinition ||
               element is KConstraintDefinition
    }

    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        return isMemberInplaceRenameAvailable(element, context)
    }
}

