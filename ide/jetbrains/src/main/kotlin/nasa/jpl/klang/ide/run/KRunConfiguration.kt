package nasa.jpl.klang.ide.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element
import java.io.File

class KRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<RunConfigurationOptions>(project, factory, name) {

    private var _kFilePath: String = ""
    private var _kInstallPath: String = ""
    private var _javaHome: String = ""

    var kFilePath: String
        get() = _kFilePath
        set(value) { _kFilePath = value }

    var kInstallPath: String
        get() = _kInstallPath
        set(value) { _kInstallPath = value }

    var javaHome: String
        get() = _javaHome
        set(value) { _javaHome = value }

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

    override fun readExternal(element: Element) {
        super.readExternal(element)
        _kFilePath = element.getAttributeValue("kFilePath") ?: ""
        _kInstallPath = element.getAttributeValue("kInstallPath") ?: ""
        _javaHome = element.getAttributeValue("javaHome") ?: ""
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("kFilePath", _kFilePath)
        element.setAttribute("kInstallPath", _kInstallPath)
        element.setAttribute("javaHome", _javaHome)
    }
}
