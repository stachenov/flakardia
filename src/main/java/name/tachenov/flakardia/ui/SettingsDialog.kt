package name.tachenov.flakardia.ui

import name.tachenov.flakardia.getAppFont
import name.tachenov.flakardia.getLibraryPath
import name.tachenov.flakardia.setAppFont
import name.tachenov.flakardia.setLibraryPath
import java.awt.Component
import java.awt.Font
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.*
import javax.swing.GroupLayout.*
import javax.swing.GroupLayout.Alignment.BASELINE
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.LayoutStyle.ComponentPlacement.RELATED
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SettingsDialog : JDialog(null as Frame?, "Flakardia settings", true) {

    var isAccepted = false
        private set

    private val dirBrowse: JButton
    private val dirInput = JTextField(getLibraryPath()?.toString() ?: "")
    private val fontName = JComboBox(GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames)
    private val fontSize = JSpinner(SpinnerNumberModel(16, 4, 100, 1))
    private val fontBold = JCheckBox("Bold").apply { mnemonic = KeyEvent.VK_B }
    private val fontItalic = JCheckBox("Italic").apply { mnemonic = KeyEvent.VK_I }
    private val ok = JButton("OK").apply { mnemonic = KeyEvent.VK_O }
    private val status = JLabel("Initializing...")

    private val libraryPath: Path
        get() = Path.of(dirInput.text)

    init {
        val contentPane = JPanel()
        val layout = GroupLayout(contentPane)
        val hg = layout.createSequentialGroup()
        val vg = layout.createSequentialGroup()
        val tabs = JTabbedPane()
        SettingsTab(tabs, "General").apply {
            dirBrowse = JButton("Browse").apply { mnemonic = KeyEvent.VK_R }
            addComponent(
                TitledPanel("Library").apply {
                    addComponent(dirInput, DEFAULT_SIZE, PREFERRED_SIZE, INFINITY)
                    addRelatedGap()
                    addComponent(dirBrowse)
                }.build()
            )
            val font = getAppFont()
            fontName.isEditable = true
            fontName.selectedItem = font.family
            fontSize.value = font.size
            fontBold.isSelected = font.isBold
            fontItalic.isSelected = font.isItalic
            addComponent(
                TitledPanel("Font").apply {
                    addComponent(fontName)
                    addRelatedGap()
                    addComponent(fontSize)
                    addRelatedGap()
                    addComponent(fontBold)
                    addRelatedGap()
                    addComponent(fontItalic)
                }.build()
            )
        }.build()
        val cancel = JButton("Cancel").apply { mnemonic = KeyEvent.VK_C }
        hg.apply {
            addContainerGap()
            addGroup(layout.createParallelGroup(LEADING).apply {
                addComponent(tabs)
                addGroup(layout.createSequentialGroup().apply {
                    addComponent(ok)
                    addPreferredGap(RELATED)
                    addComponent(cancel)
                })
                addComponent(status)
            })
            addContainerGap()
        }
        vg.apply {
            addComponent(tabs)
            addPreferredGap(RELATED, DEFAULT_SIZE, INFINITY)
            addGroup(layout.createParallelGroup(BASELINE).apply {
                addComponent(ok)
                addComponent(cancel)
            })
            addPreferredGap(RELATED)
            addComponent(status)
            addContainerGap()
        }
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        contentPane.layout = layout
        this.contentPane = contentPane

        dirInput.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                enableDisable()
            }

            override fun removeUpdate(e: DocumentEvent?) {
                enableDisable()
            }

            override fun changedUpdate(e: DocumentEvent?) {
                enableDisable()
            }
        })
        dirBrowse.addActionListener {
            browseForLibrary()
        }

        ok.addActionListener { ok() }
        cancel.addActionListener { cancel() }
        rootPane.defaultButton = ok
        rootPane.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "Cancel")
        rootPane.actionMap.put("Cancel", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                cancel()
            }
        })

        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent?) {
                enableDisable()
            }
        })
    }

    private fun enableDisable() {
        val isValid = dirInput.text.isNotBlank() && Files.isDirectory(libraryPath)
        ok.isEnabled = isValid
        status.foreground = if (isValid) null else INCORRECT_COLOR
        status.text = if (isValid) {
            " " // to keep height
        }
        else if (dirInput.text.isBlank()) {
            "Please specify the library directory where flashcards are stored"
        }
        else {
            "The specified library directory doesn't exist or isn't a directory"
        }
    }

    private fun browseForLibrary() {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Select the library directory"
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            dirInput.text = chooser.selectedFile.toString()
        }
    }

    private fun ok() {
        setLibraryPath(libraryPath)
        var fontStyle = 0
        if (fontBold.isSelected) {
            fontStyle = fontStyle or Font.BOLD
        }
        if (fontItalic.isSelected) {
            fontStyle = fontStyle or Font.ITALIC
        }
        setAppFont(Font(fontName.selectedItem?.toString(),fontStyle, fontSize.value as Int))
        isAccepted = true
        dispose()
    }

    private fun cancel() {
        dispose()
    }

}

private enum class Orientation {
    HORIZONTAL,
    VERTICAL,
}

private open class SettingsPanel(private val orientation: Orientation) {

    private val descriptors = mutableListOf<Descriptor>()

    private val isHorizontal: Boolean get() = orientation == Orientation.HORIZONTAL

    fun addComponent(
        component: Component,
        minSize: Int = DEFAULT_SIZE,
        prefSize: Int = DEFAULT_SIZE,
        maxSize: Int = DEFAULT_SIZE,
    ) {
        descriptors.add(ComponentDescriptor(component, minSize, prefSize, maxSize))
    }

    fun addRelatedGap() {
        descriptors.add(RelatedGapDescriptor)
    }

    fun build(): JPanel {
        val result = JPanel()
        val layout = GroupLayout(result)
        val hg: Group = if (isHorizontal) layout.createSequentialGroup() else layout.createParallelGroup(LEADING)
        val vg: Group = if (isHorizontal) layout.createParallelGroup(BASELINE) else layout.createSequentialGroup()
        for (descriptor in descriptors) {
            descriptor.addToHG(hg)
        }
        for (descriptor in descriptors) {
            descriptor.addToVG(vg)
        }
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        result.layout = layout
        afterBuild(result)
        return result
    }

    protected open fun afterBuild(result: JPanel) { }

    private sealed class Descriptor {
        abstract fun addToHG(group: Group)
        abstract fun addToVG(group: Group)
    }

    private class ComponentDescriptor(
        private val component: Component,
        private val minSize: Int = DEFAULT_SIZE,
        private val prefSize: Int = DEFAULT_SIZE,
        private val maxSize: Int = DEFAULT_SIZE,
    ) : Descriptor() {
        override fun addToHG(group: Group) {
            group.addComponent(component, minSize, prefSize, maxSize)
        }
        override fun addToVG(group: Group) {
            group.addComponent(component)
        }
    }

    private data object RelatedGapDescriptor : Descriptor() {
        override fun addToHG(group: Group) {
            addRelatedGap(group)
        }

        override fun addToVG(group: Group) {
            addRelatedGap(group)
        }

        private fun addRelatedGap(group: Group) {
            if (group is SequentialGroup) {
                group.addPreferredGap(RELATED)
            }
        }
    }

}

private class TitledPanel(private val name: String) : SettingsPanel(Orientation.HORIZONTAL) {
    override fun afterBuild(result: JPanel) {
        result.border = BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), name)
    }
}

private class SettingsTab(private val tabs: JTabbedPane, private val title: String) : SettingsPanel(Orientation.VERTICAL) {
    override fun afterBuild(result: JPanel) {
        tabs.addTab(title, result)
    }
}
