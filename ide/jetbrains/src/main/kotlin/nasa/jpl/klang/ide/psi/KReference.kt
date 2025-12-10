package nasa.jpl.klang.ide.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import nasa.jpl.klang.ide.KFileType
import nasa.jpl.klang.ide.parser.KClassDefinitionImpl
import nasa.jpl.klang.ide.parser.KFunctionDefinitionImpl
import nasa.jpl.klang.ide.parser.KPropertyDefinitionImpl
import nasa.jpl.klang.ide.parser.KParameterImpl

/**
 * Reference from an identifier usage to its definition.
 * This is essential for Find Usages functionality.
 */
class KReference(element: PsiElement, range: TextRange) : PsiReferenceBase<PsiElement>(element, range) {

    private val name: String = element.text

    override fun resolve(): PsiElement? {
        val project = myElement.project
        val containingFile = myElement.containingFile ?: return null

        // First look in current file for local definitions
        val localResult = findDefinitionInFile(containingFile, name)
        if (localResult != null) {
            return localResult
        }

        // Then search other K files
        val scope = GlobalSearchScope.projectScope(project)
        val virtualFiles = FileTypeIndex.getFiles(KFileType, scope)

        for (vFile in virtualFiles) {
            val psiFile = PsiManager.getInstance(project).findFile(vFile)
            if (psiFile != null && psiFile != containingFile) {
                val result = findDefinitionInFile(psiFile, name)
                if (result != null) {
                    return result
                }
            }
        }

        return null
    }

    private fun findDefinitionInFile(file: PsiFile, targetName: String): PsiElement? {
        // Find classes
        PsiTreeUtil.findChildrenOfType(file, KClassDefinitionImpl::class.java)
            .firstOrNull { it.getClassName() == targetName }
            ?.let { return it }

        // Find functions
        PsiTreeUtil.findChildrenOfType(file, KFunctionDefinitionImpl::class.java)
            .firstOrNull { it.getMemberName() == targetName }
            ?.let { return it }

        // Find properties
        PsiTreeUtil.findChildrenOfType(file, KPropertyDefinitionImpl::class.java)
            .firstOrNull { it.getMemberName() == targetName }
            ?.let { return it }

        // Find parameters (local scope)
        if (myElement.containingFile == file) {
            // Look for parameters in enclosing function
            val enclosingFunction = PsiTreeUtil.getParentOfType(myElement, KFunctionDefinitionImpl::class.java)
            if (enclosingFunction != null) {
                enclosingFunction.getParameters()
                    .firstOrNull { it.getParameterName() == targetName }
                    ?.let { return it as? PsiElement }
            }
        }

        return null
    }

    override fun getVariants(): Array<Any> {
        // Could provide completion variants here
        return emptyArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        // TODO: Support rename refactoring
        return myElement
    }
}

