package nasa.jpl.klang.ide.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.icons.AllIcons
import nasa.jpl.klang.ide.KIcons
import javax.swing.Icon

/**
 * Run configuration type for K language files
 */
class KRunConfigurationType : ConfigurationType {

    override fun getDisplayName(): String = "K"

    override fun getConfigurationTypeDescription(): String = "K Language Run Configuration"

    override fun getIcon(): Icon = KIcons.FILE

    override fun getId(): String = "KRunConfiguration"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> {
        return arrayOf(KRunConfigurationFactory(this))
    }

    companion object {
        fun getInstance(): KRunConfigurationType {
            return ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
                .filterIsInstance<KRunConfigurationType>()
                .first()
        }
    }
}

