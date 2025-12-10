package nasa.jpl.klang.ide.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import nasa.jpl.klang.ide.KFileType
import nasa.jpl.klang.ide.KLanguage

/**
 * PSI file for K language.
 * Represents a .k file in the PSI tree.
 */
class KFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, KLanguage) {
    override fun getFileType(): FileType = KFileType

    override fun toString(): String = "K File"

    /**
     * Get all class definitions in this file
     */
    fun getClasses(): List<KClassDefinition> {
        return findChildrenByClass(KClassDefinition::class.java).toList()
    }

    /**
     * Get the package declaration if present
     */
    fun getPackageDeclaration(): KPackageDeclaration? {
        return findChildByClass(KPackageDeclaration::class.java)
    }

    /**
     * Get all import declarations
     */
    fun getImports(): List<KImportDeclaration> {
        return findChildrenByClass(KImportDeclaration::class.java).toList()
    }
}
