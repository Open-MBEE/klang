package nasa.jpl.klang.ide.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import nasa.jpl.klang.ide.KIcons

/**
 * Factory for creating K run configurations
 */
class KRunConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = "K Run Configuration"

    override fun getName(): String = "K"

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return KRunConfiguration(project, this, "K")
    }
}

