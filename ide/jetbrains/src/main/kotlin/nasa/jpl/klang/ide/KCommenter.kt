package nasa.jpl.klang.ide

import com.intellij.lang.Commenter

/**
 * Comment support for K language
 */
class KCommenter : Commenter {

    override fun getLineCommentPrefix(): String = "--"

    override fun getBlockCommentPrefix(): String = "=="

    override fun getBlockCommentSuffix(): String = "=="

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}

