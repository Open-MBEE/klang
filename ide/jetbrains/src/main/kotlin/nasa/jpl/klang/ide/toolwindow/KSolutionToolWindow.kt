package nasa.jpl.klang.ide.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * Factory for creating the K Solution tool window
 */
class KSolutionToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = KSolutionPanel()
        val content = ContentFactory.getInstance().createContent(panel, "Solution", false)
        toolWindow.contentManager.addContent(content)

        // Store reference for updates
        project.putUserData(SOLUTION_PANEL_KEY, panel)
    }

    companion object {
        val SOLUTION_PANEL_KEY = com.intellij.openapi.util.Key.create<KSolutionPanel>("K_SOLUTION_PANEL")
    }
}

/**
 * Panel displaying K solver solution
 */
class KSolutionPanel : JPanel(BorderLayout()) {

    private val statusLabel = JLabel("No solution").apply {
        font = font.deriveFont(Font.BOLD, 14f)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }

    private val statsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
    }

    private val objectsTableModel = DefaultTableModel(arrayOf("Ref", "Class", "Properties"), 0)
    private val objectsTable = JBTable(objectsTableModel).apply {
        setShowGrid(true)
        autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
    }

    private val errorArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font("Monospaced", Font.PLAIN, 12)
    }

    init {
        // Status header
        add(statusLabel, BorderLayout.NORTH)

        // Main content with tabs
        val tabbedPane = JTabbedPane()

        // Objects tab
        val objectsPanel = JPanel(BorderLayout())
        objectsPanel.add(statsPanel, BorderLayout.NORTH)
        objectsPanel.add(JBScrollPane(objectsTable), BorderLayout.CENTER)
        tabbedPane.addTab("Objects", objectsPanel)

        // Errors tab
        tabbedPane.addTab("Errors", JBScrollPane(errorArea))

        add(tabbedPane, BorderLayout.CENTER)
    }

    /**
     * Update the panel with a new solution
     */
    fun updateSolution(solution: KSolution) {
        // Update status
        statusLabel.text = when (solution.status) {
            SolutionStatus.SAT -> "✓ Satisfiable - Solution Found"
            SolutionStatus.UNSAT -> "✗ Unsatisfiable - No Solution"
            SolutionStatus.UNKNOWN -> "? Unknown"
            SolutionStatus.ERROR -> "⚠ Error"
        }
        statusLabel.foreground = when (solution.status) {
            SolutionStatus.SAT -> Color(0, 150, 0)
            SolutionStatus.UNSAT, SolutionStatus.ERROR -> Color(200, 0, 0)
            SolutionStatus.UNKNOWN -> Color(200, 150, 0)
        }

        // Update statistics
        statsPanel.removeAll()
        statsPanel.add(createStatLabel("Packages", solution.statistics.packages))
        statsPanel.add(Box.createHorizontalStrut(20))
        statsPanel.add(createStatLabel("Classes", solution.statistics.classes))
        statsPanel.add(Box.createHorizontalStrut(20))
        statsPanel.add(createStatLabel("Properties", solution.statistics.properties))
        statsPanel.add(Box.createHorizontalStrut(20))
        statsPanel.add(createStatLabel("Functions", solution.statistics.functions))
        statsPanel.add(Box.createHorizontalStrut(20))
        statsPanel.add(createStatLabel("Constraints", solution.statistics.constraints))
        statsPanel.add(Box.createHorizontalGlue())
        statsPanel.revalidate()
        statsPanel.repaint()

        // Update objects table
        objectsTableModel.rowCount = 0
        for (obj in solution.objects) {
            val propsStr = obj.properties.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            objectsTableModel.addRow(arrayOf(obj.ref, obj.className, propsStr))
        }

        // Update errors
        errorArea.text = buildString {
            if (solution.errors.isNotEmpty()) {
                appendLine("Errors:")
                solution.errors.forEach { appendLine("  $it") }
                appendLine()
            }
            if (solution.unsatCore.isNotEmpty()) {
                appendLine("UNSAT Core (conflicting constraints):")
                solution.unsatCore.forEach { appendLine("  $it") }
            }
        }
    }

    private fun createStatLabel(name: String, value: Int): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(JLabel(value.toString()).apply {
                font = font.deriveFont(Font.BOLD, 18f)
                alignmentX = CENTER_ALIGNMENT
            })
            add(JLabel(name).apply {
                font = font.deriveFont(10f)
                alignmentX = CENTER_ALIGNMENT
            })
        }
    }
}

/**
 * Represents a solver solution
 */
data class KSolution(
    val status: SolutionStatus,
    val objects: List<SolverObject>,
    val statistics: SolverStatistics,
    val errors: List<String> = emptyList(),
    val unsatCore: List<String> = emptyList()
)

enum class SolutionStatus {
    SAT, UNSAT, UNKNOWN, ERROR
}

data class SolverObject(
    val variable: String,
    val ref: String,
    val className: String,
    val properties: Map<String, String>
)

data class SolverStatistics(
    val packages: Int = 0,
    val classes: Int = 0,
    val properties: Int = 0,
    val functions: Int = 0,
    val constraints: Int = 0,
    val solveTime: String? = null
)

/**
 * Parses K solver output into a solution object
 */
object KSolutionParser {

    fun parse(output: String): KSolution {
        var status = SolutionStatus.UNKNOWN
        val objects = mutableListOf<SolverObject>()
        var statistics = SolverStatistics()
        val errors = mutableListOf<String>()
        val unsatCore = mutableListOf<String>()

        val lines = output.split("\n")

        // Parse statistics
        val statsSection = Regex("""STATISTICS:([\s\S]*?)(?:No instance|Extra objects|$)""").find(output)
        if (statsSection != null) {
            val statsText = statsSection.groupValues[1]
            val packagesMatch = Regex("""packages\s*:\s*(\d+)""").find(statsText)
            val classesMatch = Regex("""class definitions\s*:\s*(\d+)""").find(statsText)
            val propsMatch = Regex("""properties\s*:\s*(\d+)""").find(statsText)
            val funcsMatch = Regex("""functions\s*:\s*(\d+)""").find(statsText)
            val constrsMatch = Regex("""constraints\s*:\s*(\d+)""").find(statsText)

            statistics = SolverStatistics(
                packages = packagesMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                classes = classesMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                properties = propsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                functions = funcsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                constraints = constrsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            )
        }

        // Parse objects table
        val tableMatch = Regex("""\+[-+]+\+([\s\S]*?)\+[-+]+\+[\s\S]*$""").find(output)
        if (tableMatch != null) {
            val tableContent = tableMatch.groupValues[1]
            val rows = tableContent.split("\n").filter { it.contains("|") && !it.matches(Regex("""^\+[-+]+\+$""")) }

            for (row in rows) {
                val cells = row.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                if (cells.size >= 3) {
                    val variable = cells[0]
                    val ref = cells[1]
                    val valueStr = cells[2]

                    // Parse: ClassName(prop1::val1, prop2::val2, ...)
                    val valueMatch = Regex("""^(\w+)\((.*)\)$""").find(valueStr)
                    if (valueMatch != null) {
                        val className = valueMatch.groupValues[1]
                        val propsStr = valueMatch.groupValues[2]
                        val properties = mutableMapOf<String, String>()

                        Regex("""(\w+)::\s*([^,)]+)""").findAll(propsStr).forEach { pm ->
                            properties[pm.groupValues[1]] = pm.groupValues[2].trim()
                        }

                        objects.add(SolverObject(variable, ref, className, properties))
                    }
                }
            }
            status = SolutionStatus.SAT
        }

        // Check for UNSAT
        if (output.contains("UNSAT") || output.contains("unsatisfiable")) {
            status = SolutionStatus.UNSAT
            val coreMatch = Regex("""UNSAT core:([\s\S]*?)(?:$|\n\n)""").find(output)
            if (coreMatch != null) {
                unsatCore.addAll(coreMatch.groupValues[1].trim().split("\n").map { it.trim() }.filter { it.isNotEmpty() })
            }
        }

        // Check for errors
        if (output.contains("Error") || output.contains("Exception")) {
            errors.addAll(lines.filter { it.contains("Error") || it.contains("Exception") }.take(5))
            if (objects.isEmpty() && status != SolutionStatus.UNSAT) {
                status = SolutionStatus.ERROR
            }
        }

        // Check for type checking success with no instances
        if (output.contains("Type checking completed") && output.contains("No errors")) {
            if (objects.isEmpty() && status == SolutionStatus.UNKNOWN) {
                status = SolutionStatus.SAT
            }
        }

        return KSolution(status, objects, statistics, errors, unsatCore)
    }
}

