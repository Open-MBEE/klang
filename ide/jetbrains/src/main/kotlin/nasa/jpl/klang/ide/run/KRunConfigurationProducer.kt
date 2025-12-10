package nasa.jpl.klang.ide.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import nasa.jpl.klang.ide.KFileType

/**
 * Automatically creates run configurations when right-clicking on K files
 */
class KRunConfigurationProducer : LazyRunConfigurationProducer<KRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return KRunConfigurationType.getInstance().configurationFactories[0]
    }

    override fun isConfigurationFromContext(
        configuration: KRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        return file.extension == "k" && configuration.kFilePath == file.path
    }

    override fun setupConfigurationFromContext(
        configuration: KRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val file = context.location?.virtualFile ?: return false

        if (file.extension != "k") {
            return false
        }

        configuration.kFilePath = file.path
        configuration.name = file.nameWithoutExtension

        return true
    }
}

