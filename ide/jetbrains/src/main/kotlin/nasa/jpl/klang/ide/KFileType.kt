package nasa.jpl.klang.ide

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/**
 * K file type (.k extension)
 */
object KFileType : LanguageFileType(KLanguage) {

    override fun getName(): String = "K File"

    override fun getDescription(): String = "K constraint programming language file"

    override fun getDefaultExtension(): String = "k"

    override fun getIcon(): Icon? = KIcons.FILE
}

