package nasa.jpl.klang.ide.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import nasa.jpl.klang.ide.psi.KTokenTypes

/**
 * Adds a run gutter icon next to package declarations in K files
 */
class KRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        // Show run icon on the 'package' keyword at the start of the file
        if (element.node?.elementType == KTokenTypes.PACKAGE) {
            val actions = ExecutorAction.getActions(0)
            return Info(
                AllIcons.RunConfigurations.TestState.Run,
                { "Run K file" },
                *actions
            )
        }
        return null
    }
}
