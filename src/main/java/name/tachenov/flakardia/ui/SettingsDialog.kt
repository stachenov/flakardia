package name.tachenov.flakardia.ui

import name.tachenov.flakardia.*
import name.tachenov.flakardia.app.LessonSettings
import java.awt.Component
import java.awt.Container
import java.awt.Font
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.swing.*
import javax.swing.GroupLayout.*
import javax.swing.GroupLayout.Alignment.BASELINE
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.LayoutStyle.ComponentPlacement.RELATED
import javax.swing.LayoutStyle.ComponentPlacement.UNRELATED
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SettingsDialog : JDialog(null as Frame?, "Flakardia settings", true) {

    var isAccepted = false
        private set

    private val tabs = mutableListOf<SettingsTab>()

    private val ok = JButton("OK").apply { mnemonic = KeyEvent.VK_O }
    private val status = JLabel("Initializing...")

    init {
        val contentPane = JPanel()
        val layout = GroupLayout(contentPane)
        val hg = layout.createSequentialGroup()
        val vg = layout.createSequentialGroup()
        val tabs = JTabbedPane()
        addTab(GeneralTab(tabs))
        addTab(LessonTab(tabs))
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

        rootPane.setupShortcuts(this)
    }

    private fun <T : SettingsTab> addTab(tab: T): T {
        tabs += tab.apply { build() }
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
        for (tab in tabs) {
            tab.apply()
        }
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

private class LessonTab(tabs: JTabbedPane) : SettingsTab(tabs, "Lesson") {
    override val errorMessage: String? = null

    private val lessonSettings = getLessonSettings()
    private val maxWordsInLesson = JSpinner(SpinnerNumberModel(lessonSettings.maxWordsPerLesson))
    private val randomness = JSpinner(SpinnerNumberModel(lessonSettings.randomness))
    private val intervalMultiplierWithoutMistakes = JSpinner(SpinnerNumberModel(lessonSettings.intervalMultiplierWithoutMistakes))
    private val minIntervalWithoutMistakes = JSpinner(SpinnerNumberModel(lessonSettings.minIntervalWithoutMistakes))
    private val intervalMultiplierWithMistake = JSpinner(SpinnerNumberModel(lessonSettings.intervalMultiplierWithMistake))
    private val minIntervalWithMistake = JSpinner(SpinnerNumberModel(lessonSettings.minIntervalWithMistake))
    private val intervalMultiplierWithManyMistakes = JSpinner(SpinnerNumberModel(lessonSettings.intervalMultiplierWithManyMistakes))
    private val minIntervalWithManyMistakes = JSpinner(SpinnerNumberModel(lessonSettings.minIntervalWithManyMistakes))

    private fun  <T : Comparable<T>> SpinnerNumberModel(value: LimitedValue<T>): SpinnerNumberModel =
        when (value.value) {
            is Int -> SpinnerNumberModel(value.value, value.min as Int, value.max as Int, 1)
            is Double -> SpinnerNumberModel(value.value, value.min as Double, value.max as Double, 0.1)
            is Duration -> SpinnerNumberModel(value.value.toHours(), (value.min as Duration).toHours(), (value.max as Duration).toHours(), 1L)
            else -> throw IllegalArgumentException("Not supported type: ${value.value.javaClass}")
        }

    init {
        addComponent(TitledPanel("Common").apply {
            addComponent(JLabel("Max words per lesson").apply {
                labelFor = maxWordsInLesson
                toolTipText = "The maximum number of words that can appear in a single lesson"
            })
            addRelatedGap()
            addComponent(maxWordsInLesson)
            addUnrelatedGap()
            addComponent(JLabel("Randomness").apply {
                labelFor = randomness
                toolTipText = "To avoid repeating the same group of words, they'll be scattered in time, the more the higher this value is"
            })
            addRelatedGap()
            addComponent(randomness)
        }.build())
        addComponent(TitledPanel("If no mistakes were made").apply {
            addComponent(JLabel("Interval multiplier").apply {
                labelFor = intervalMultiplierWithoutMistakes
                toolTipText = "If a word was learned in a lesson without making any mistakes, its interval between lessons will be multiplied by this value"
            })
            addRelatedGap()
            addComponent(intervalMultiplierWithoutMistakes)
            addUnrelatedGap()
            addComponent(JLabel("Min interval").apply {
                labelFor = minIntervalWithoutMistakes
                toolTipText = "If a word was learned in a lesson without making any mistakes, it won't appear in another lesson until this many hours have passed"
            })
            addRelatedGap()
            addComponent(minIntervalWithoutMistakes)
        }.build())
        addComponent(TitledPanel("If a single mistake was made").apply {
            addComponent(JLabel("Interval multiplier").apply {
                labelFor = intervalMultiplierWithMistake
                toolTipText = "If a word was learned in a lesson with exactly one mistake, its interval between lessons will be multiplied by this value"
            })
            addRelatedGap()
            addComponent(intervalMultiplierWithMistake)
            addUnrelatedGap()
            addComponent(JLabel("Min interval").apply {
                labelFor = minIntervalWithMistake
                toolTipText = "If a word was learned in a lesson with exactly one mistake, it won't appear in another lesson until this many hours have passed"
            })
            addRelatedGap()
            addComponent(minIntervalWithMistake)
        }.build())
        addComponent(TitledPanel("If many mistakes were made").apply {
            addComponent(JLabel("Interval multiplier").apply {
                labelFor = intervalMultiplierWithManyMistakes
                toolTipText = "If a word was learned in a lesson with more than one mistake, its interval between lessons will be multiplied by this value"
            })
            addRelatedGap()
            addComponent(intervalMultiplierWithManyMistakes)
            addUnrelatedGap()
            addComponent(JLabel("Min interval").apply {
                labelFor = minIntervalWithManyMistakes
                toolTipText = "If a word was learned in a lesson with more than one mistake, it won't appear in another lesson until this many hours have passed"
            })
            addRelatedGap()
            addComponent(minIntervalWithManyMistakes)
        }.build())
    }

    override fun apply() {
        setLessonSettings(LessonSettings(
            maxWordsInLesson.value as Int,
            intervalMultiplierWithoutMistakes.value as Double,
            Duration.ofHours((minIntervalWithoutMistakes.value as Long)),
            intervalMultiplierWithMistake.value as Double,
            Duration.ofHours((minIntervalWithMistake.value as Long)),
            intervalMultiplierWithManyMistakes.value as Double,
            Duration.ofHours((minIntervalWithManyMistakes.value as Long)),
            randomness.value as Double,
        ))
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

    fun addUnrelatedGap() {
        descriptors.add(GapDescriptor(UNRELATED))
    }

    fun addRelatedGap() {
        descriptors.add(GapDescriptor(RELATED))
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

    private data class GapDescriptor(val gap: LayoutStyle.ComponentPlacement) : Descriptor() {
        override fun addToHG(group: Group) {
            addRelatedGap(group)
        }

        override fun addToVG(group: Group) {
            addRelatedGap(group)
        }

        private fun addRelatedGap(group: Group) {
            if (group is SequentialGroup) {
                group.addPreferredGap(gap)
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
        applyLabelToolTips(result)
        tabs.addTab(title, result)
    }

    private fun applyLabelToolTips(result: Container) {
        for (component in result.components) {
            if (component is JLabel) {
                val target = component.labelFor
                if (target is JComponent) {
                    target.toolTipText = component.toolTipText
                }
            }
            if (component is Container) {
                applyLabelToolTips(component)
            }
        }
    }

    abstract fun apply()
}
