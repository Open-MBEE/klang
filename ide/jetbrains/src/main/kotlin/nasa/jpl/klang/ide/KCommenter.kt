package nasa.jpl.klang.ide

import com.intellij.lang.Commenter

/**
 * Comment support for K language.
 *
 * K supports multiple comment styles:
 * - Line comments: -- or //
 * - Block comments: /* ... */ or ===...===
 *
 * This commenter uses the most standard/familiar syntax.
 */
class KCommenter : Commenter {

    // Use -- for line comments (K's preferred style)
    override fun getLineCommentPrefix(): String = "-- "

    // Use /* */ for block comments (more universally recognized)
    override fun getBlockCommentPrefix(): String = "/*"

    override fun getBlockCommentSuffix(): String = "*/"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}

