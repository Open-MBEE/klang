package nasa.jpl.klang.ide.run

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ExecutionResult
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.filters.TextConsoleBuilderFactory
import java.io.File

/**
 * Execution state for running K files
 */
class KRunState(
    private val configuration: KRunConfiguration,
    private val environment: ExecutionEnvironment
) : RunProfileState {

    override fun execute(executor: com.intellij.execution.Executor?, runner: ProgramRunner<*>): ExecutionResult? {
        val processHandler = startProcess()
        val console = createConsole()
        console.attachToProcess(processHandler)
        return DefaultExecutionResult(console, processHandler)
    }

    private fun startProcess(): ProcessHandler {
        val commandLine = createCommandLine()
        val processHandler = KillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(processHandler)
        return processHandler
    }

    private fun createConsole(): ConsoleView {
        return TextConsoleBuilderFactory.getInstance()
            .createBuilder(environment.project)
            .console
    }

    private fun createCommandLine(): GeneralCommandLine {
        val kScript = findKScript()
        val workingDir = File(kScript).parentFile.parentFile // Go up from export/k to klang root

        val commandLine = GeneralCommandLine()
            .withExePath(kScript)
            .withParameters(configuration.kFilePath)
            .withWorkDirectory(workingDir)
            .withCharset(Charsets.UTF_8)

        // Set up environment
        val env = commandLine.environment

        // Prefer SDKMAN Java on macOS (ARM64 compatible)
        val javaHome = findJavaHome()
        if (javaHome != null) {
            env["JAVA_HOME"] = javaHome
            env["PATH"] = "$javaHome/bin:${System.getenv("PATH")}"
        }

        return commandLine
    }

    private fun findKScript(): String {
        // Check configured path first
        if (configuration.kInstallPath.isNotBlank()) {
            val candidates = listOf(
                File(configuration.kInstallPath, "export/k"),
                File(configuration.kInstallPath, "k"),
                File(configuration.kInstallPath, "bin/k")
            )
            for (candidate in candidates) {
                if (candidate.exists()) {
                    return candidate.absolutePath
                }
            }
        }

        // Try common locations
        val home = System.getProperty("user.home")
        val projectPath = environment.project.basePath

        val commonPaths = listOfNotNull(
            projectPath?.let { File(it, "export/k") },
            projectPath?.let { File(File(it).parent, "export/k") },
            File(home, "klang/export/k"),
            File(home, ".klang/export/k"),
            File(home, "git/klang/export/k"),
            File("/usr/local/klang/export/k"),
            File("/opt/klang/export/k")
        )

        for (path in commonPaths) {
            if (path.exists()) {
                return path.absolutePath
            }
        }

        throw IllegalStateException("K installation not found. Please configure the K installation path.")
    }

    private fun findJavaHome(): String? {
        // Check configured Java home
        if (configuration.javaHome.isNotBlank()) {
            return configuration.javaHome
        }

        val home = System.getProperty("user.home")

        // Check SDKMAN first (likely ARM64 on Apple Silicon)
        val sdkmanCurrent = File(home, ".sdkman/candidates/java/current")
        if (sdkmanCurrent.exists()) {
            return sdkmanCurrent.absolutePath
        }

        // Check for specific SDKMAN versions
        val sdkmanBase = File(home, ".sdkman/candidates/java")
        if (sdkmanBase.exists() && sdkmanBase.isDirectory) {
            val versions = sdkmanBase.listFiles()?.map { it.name } ?: emptyList()
            for (ver in versions) {
                if (ver.contains("21") || ver.contains("tem") || ver.contains("zulu")) {
                    val javaPath = File(sdkmanBase, ver)
                    if (File(javaPath, "bin/java").exists()) {
                        return javaPath.absolutePath
                    }
                }
            }
        }

        // Fall back to system JAVA_HOME
        return System.getenv("JAVA_HOME")
    }
}

