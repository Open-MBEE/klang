package nasa.jpl.klang.ide.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings editor UI for K run configuration
 */
class KRunConfigurationEditor(private val project: Project) : SettingsEditor<KRunConfiguration>() {

    private val kFileField = TextFieldWithBrowseButton()
    private val kInstallField = TextFieldWithBrowseButton()
    private val javaHomeField = TextFieldWithBrowseButton()

    init {
        kFileField.addBrowseFolderListener(
            "Select K File",
            "Choose the K file to run",
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor("k")
        )

        kInstallField.addBrowseFolderListener(
            "Select K Installation",
            "Choose the K language installation directory",
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )

        javaHomeField.addBrowseFolderListener(
            "Select Java Home",
            "Choose the Java installation directory (optional)",
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }

    override fun resetEditorFrom(config: KRunConfiguration) {
        kFileField.text = config.kFilePath
        kInstallField.text = config.kInstallPath
        javaHomeField.text = config.javaHome
    }

    override fun applyEditorTo(config: KRunConfiguration) {
        config.kFilePath = kFileField.text
        config.kInstallPath = kInstallField.text
        config.javaHome = javaHomeField.text
    }

    override fun createEditor(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("K file:"), kFileField, 1, false)
            .addLabeledComponent(JBLabel("K installation (optional):"), kInstallField, 1, false)
            .addLabeledComponent(JBLabel("Java home (optional):"), javaHomeField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
}

