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

    private val tabs = mutableListOf<SettingsTab>()
    private val generalTab: GeneralTab

    private val ok = JButton("OK").apply { mnemonic = KeyEvent.VK_O }
    private val status = JLabel("Initializing...")

    init {
        val contentPane = JPanel()
        val layout = GroupLayout(contentPane)
        val hg = layout.createSequentialGroup()
        val vg = layout.createSequentialGroup()
        val tabs = JTabbedPane()
        generalTab = addTab(GeneralTab(tabs))
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

    private fun <T : SettingsTab> addTab(tab: T): T {
        tabs += tab
        tab.addChangeListener { enableDisable() }
        return tab
    }

    private fun enableDisable() {
        ok.isEnabled = true
        status.foreground = null
        status.text = " " // to keep height
        for (tab in tabs) {
            val error = tab.errorMessage
            if (error != null) {
                ok.isEnabled = false
                status.text = error
                status.foreground = INCORRECT_COLOR
                break
            }
        }
    }

    private fun ok() {
        generalTab.apply()
        isAccepted = true
        dispose()
    }

    private fun cancel() {
        dispose()
    }

}

private class GeneralTab(tabs: JTabbedPane) : SettingsTab(tabs, "General") {
    private val dirBrowse: JButton = JButton("Browse").apply { mnemonic = KeyEvent.VK_R }
    private val dirInput = JTextField(getLibraryPath()?.toString() ?: "")
    private val fontName = JComboBox(GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames)
    private val fontSize = JSpinner(SpinnerNumberModel(16, 4, 100, 1))
    private val fontBold = JCheckBox("Bold").apply { mnemonic = KeyEvent.VK_B }
    private val fontItalic = JCheckBox("Italic").apply { mnemonic = KeyEvent.VK_I }

    private val libraryPath: Path
        get() = Path.of(dirInput.text)

    init {
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
        build()

        dirInput.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                fireChanged()
            }

            override fun removeUpdate(e: DocumentEvent?) {
                fireChanged()
            }

            override fun changedUpdate(e: DocumentEvent?) {
                fireChanged()
            }
        })
        dirBrowse.addActionListener {
            browseForLibrary()
        }
    }

    override val errorMessage: String?
        get() = if (dirInput.text.isBlank()) {
            "Please specify the library directory where flashcards are stored"
        }
        else if (!Files.isDirectory(libraryPath)) {
            "The specified library directory doesn't exist or isn't a directory"
        }
        else {
            null
        }

    private fun browseForLibrary() {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Select the library directory"
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        if (chooser.showOpenDialog(component) == JFileChooser.APPROVE_OPTION) {
            dirInput.text = chooser.selectedFile.toString()
        }
    }

    override fun apply() {
        setLibraryPath(libraryPath)
        var fontStyle = 0
        if (fontBold.isSelected) {
            fontStyle = fontStyle or Font.BOLD
        }
        if (fontItalic.isSelected) {
            fontStyle = fontStyle or Font.ITALIC
        }
        setAppFont(Font(fontName.selectedItem?.toString(),fontStyle, fontSize.value as Int))
    }
}

private enum class Orientation {
    HORIZONTAL,
    VERTICAL,
}

private open class SettingsPanel(private val orientation: Orientation) {

    protected var component: JPanel? = null
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
        component = result
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

private abstract class SettingsTab(private val tabs: JTabbedPane, private val title: String) : SettingsPanel(Orientation.VERTICAL) {

    abstract val errorMessage: String?

    private val changeListeners = mutableListOf<() -> Unit>()

    fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    protected fun fireChanged() {
        changeListeners.forEach { it() }
    }

    override fun afterBuild(result: JPanel) {
        tabs.addTab(title, result)
    }

    abstract fun apply()
}
