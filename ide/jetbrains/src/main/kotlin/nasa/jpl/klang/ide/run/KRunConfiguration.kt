package nasa.jpl.klang.ide.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element
import java.io.File

/**
 * Run configuration for K language files
 */
class KRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<KRunConfigurationOptions>(project, factory, name) {

    override fun getOptions(): KRunConfigurationOptions {
        return super.getOptions() as KRunConfigurationOptions
    }

    var kFilePath: String
        get() = options.kFilePath ?: ""
        set(value) { options.kFilePath = value }

    var kInstallPath: String
        get() = options.kInstallPath ?: ""
        set(value) { options.kInstallPath = value }

    var javaHome: String
        get() = options.javaHome ?: ""
        set(value) { options.javaHome = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return KRunConfigurationEditor(project)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return KRunState(this, environment)
    }

    override fun checkConfiguration() {
        if (kFilePath.isBlank()) {
            throw RuntimeConfigurationError("K file path is not specified")
        }
        if (!File(kFilePath).exists()) {
            throw RuntimeConfigurationError("K file does not exist: $kFilePath")
        }
    }
}

/**
 * Options for K run configuration (persisted)
 */
class KRunConfigurationOptions : RunConfigurationOptions() {
    var kFilePath: String? = null
    var kInstallPath: String? = null
    var javaHome: String? = null
}

