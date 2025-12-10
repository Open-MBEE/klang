package nasa.jpl.klang.ide.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import nasa.jpl.klang.ide.KFileType
import nasa.jpl.klang.ide.parser.KClassDefinitionImpl
import nasa.jpl.klang.ide.parser.KFunctionDefinitionImpl
import nasa.jpl.klang.ide.parser.KPropertyDefinitionImpl
import nasa.jpl.klang.ide.psi.KFile
import nasa.jpl.klang.ide.psi.KTokenTypes

/**
 * Handles "Go to Declaration" (Ctrl+Click or Ctrl+B) for K language.
 * Resolves references to their definitions.
 */
class KGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null
        
        // Only handle identifiers
        if (sourceElement.node?.elementType != KTokenTypes.IDENTIFIER) {
            return null
        }
        
        val name = sourceElement.text ?: return null
        val project = sourceElement.project
        val containingFile = sourceElement.containingFile ?: return null
        
        // Find all definitions with this name
        val results = mutableListOf<PsiElement>()
        
        // First, search in the current file
        findDefinitionsInFile(containingFile, name, results)
        
        // Then search in other K files in the project
        if (results.isEmpty()) {
            searchProjectForDefinition(project, name, containingFile, results)
        }
        
        return if (results.isNotEmpty()) results.toTypedArray() else null
    }

    private fun findDefinitionsInFile(file: PsiFile, name: String, results: MutableList<PsiElement>) {
        // Find classes
        PsiTreeUtil.findChildrenOfType(file, KClassDefinitionImpl::class.java)
            .filter { it.getClassName() == name }
            .forEach { results.add(it) }
        
        // Find functions
        PsiTreeUtil.findChildrenOfType(file, KFunctionDefinitionImpl::class.java)
            .filter { it.getMemberName() == name }
            .forEach { results.add(it) }
        
        // Find properties
        PsiTreeUtil.findChildrenOfType(file, KPropertyDefinitionImpl::class.java)
            .filter { it.getMemberName() == name }
            .forEach { results.add(it) }
    }

    private fun searchProjectForDefinition(
        project: Project,
        name: String,
        excludeFile: PsiFile,
        results: MutableList<PsiElement>
    ) {
        val scope = GlobalSearchScope.projectScope(project)
        val virtualFiles = FileTypeIndex.getFiles(KFileType, scope)
        
        for (vFile in virtualFiles) {
            val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(vFile)
            if (psiFile != null && psiFile != excludeFile && psiFile is KFile) {
                findDefinitionsInFile(psiFile, name, results)
            }
        }
    }
}
