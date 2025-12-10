package nasa.jpl.klang.ide

import com.intellij.lang.Language

/**
 * K Language definition for IntelliJ Platform
 */
object KLanguage : Language("K") {

    override fun getDisplayName(): String = "K"

    override fun isCaseSensitive(): Boolean = true
}

