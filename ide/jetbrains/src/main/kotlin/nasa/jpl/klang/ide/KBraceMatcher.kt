package nasa.jpl.klang.ide

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import nasa.jpl.klang.ide.psi.KTokenTypes

/**
 * Brace matcher for K language
 */
class KBraceMatcher : PairedBraceMatcher {
    
    private val pairs = arrayOf(
        BracePair(KTokenTypes.LBRACE, KTokenTypes.RBRACE, true),
        BracePair(KTokenTypes.LBRACKET, KTokenTypes.RBRACKET, false),
        BracePair(KTokenTypes.LPAREN, KTokenTypes.RPAREN, false)
    )
    
    override fun getPairs(): Array<BracePair> = pairs
    
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true
    
    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}
