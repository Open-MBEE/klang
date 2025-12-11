package nasa.jpl.klang.ide.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import nasa.jpl.klang.ide.KIcons

/**
 * Action to run a K file from the context menu or keyboard shortcut
 */
class KRunAction : AnAction("Run K File", "Run the current K file", KIcons.FILE) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (file.extension != "k") {
            return
        }

        val runManager = RunManager.getInstance(project)
        val configType = KRunConfigurationType.getInstance()
        val factory = configType.configurationFactories[0]

        // Check if a configuration for this file already exists
        var settings = runManager.allSettings.find { setting ->
            setting.type == configType &&
            (setting.configuration as? KRunConfiguration)?.kFilePath == file.path
        }

        if (settings == null) {
            // Create a new configuration
            settings = runManager.createConfiguration(file.nameWithoutExtension, factory)
            (settings.configuration as? KRunConfiguration)?.kFilePath = file.path
            runManager.addConfiguration(settings)
        }

        runManager.selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && file.extension == "k"
    }
}

