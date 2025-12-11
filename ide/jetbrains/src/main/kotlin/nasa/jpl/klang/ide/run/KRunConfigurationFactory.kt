package nasa.jpl.klang.ide.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.project.Project

/**
 * Factory for creating K run configurations
 */
class KRunConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = "KRunConfigurationFactory"

    override fun getName(): String = "K"

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return KRunConfiguration(project, this, "K")
    }

    override fun getOptionsClass(): Class<out RunConfigurationOptions> {
        return RunConfigurationOptions::class.java
    }
}

