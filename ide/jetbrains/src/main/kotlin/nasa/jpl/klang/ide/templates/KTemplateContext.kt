package nasa.jpl.klang.ide.templates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import nasa.jpl.klang.ide.KFileType

/**
 * Template context for K language live templates.
 * This enables IntelliJ's Live Templates feature for K files.
 */
class KTemplateContext : TemplateContextType("K", "K") {

    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        val file = templateActionContext.file
        return file.fileType == KFileType
    }
}

