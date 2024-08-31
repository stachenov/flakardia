package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.configureAndEnterLibrary
import name.tachenov.flakardia.data.LessonData
import name.tachenov.flakardia.data.LessonDataError
import name.tachenov.flakardia.data.LessonDataResult
import name.tachenov.flakardia.data.LessonDataWarnings
import name.tachenov.flakardia.data.RelativePath
import name.tachenov.flakardia.service.FlashcardService
import name.tachenov.flakardia.showHelp
import name.tachenov.flakardia.showSettingsDialog
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.LayoutStyle.ComponentPlacement.RELATED
import kotlin.math.max

class CardSetManagerFrame(
    private val manager: CardManager,
    private val service: FlashcardService,
) : JFrame("Flakardia 0.6") {

    private val dir = JLabel()
    private val list = JList<CardListEntryView>()
    private val model = DefaultListModel<CardListEntryView>()
    private val viewButton = ListEntryActionButton("View flashcards").apply {
        horizontalAlignment = SwingConstants.LEADING
        mnemonic = KeyEvent.VK_V
    }
    private val lessonButton = ListEntryActionButton("Start lesson").apply {
        horizontalAlignment = SwingConstants.LEADING
        mnemonic = KeyEvent.VK_S
    }
    private val settingsButton = JButton("Settings").apply {
        horizontalAlignment = SwingConstants.LEADING
        mnemonic = KeyEvent.VK_T
    }
    private val helpButton = JButton("Help").apply {
        horizontalAlignment = SwingConstants.LEADING
        mnemonic = KeyEvent.VK_H
    }

    init {
        val contentPane = JPanel()
        val layout = GroupLayout(contentPane)
        val hg = layout.createSequentialGroup()
        val vg = layout.createSequentialGroup()
        val listScrollPane = JScrollPane(list).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
            )
        }
        hg.apply {
            addContainerGap()
            addGroup(layout.createParallelGroup(LEADING).apply {
                addComponent(dir)
                addComponent(listScrollPane)
            })
            addPreferredGap(RELATED)
            addGroup(layout.createParallelGroup(LEADING).apply {
                addComponent(viewButton)
                addComponent(lessonButton)
                addComponent(settingsButton)
                addComponent(helpButton)
            })
            addContainerGap()
        }
        vg.apply {
            addContainerGap()
            addGroup(layout.createParallelGroup(LEADING).apply {
                addGroup(layout.createSequentialGroup().apply {
                    addComponent(dir)
                    addPreferredGap(RELATED)
                    addComponent(listScrollPane)
                })
                addGroup(layout.createSequentialGroup().apply {
                    addComponent(viewButton)
                    addPreferredGap(RELATED)
                    addComponent(lessonButton)
                    addPreferredGap(RELATED, DEFAULT_SIZE, INFINITY)
                    addComponent(settingsButton)
                    addPreferredGap(RELATED)
                    addComponent(helpButton)
                })
            })
            addContainerGap()
        }
        layout.linkSize(SwingConstants.HORIZONTAL, viewButton, lessonButton, settingsButton, helpButton)
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        contentPane.layout = layout
        this.contentPane = contentPane

        list.model = model

        viewButton.addEntryActionListener {
            viewFlashcards(it)
        }
        lessonButton.addEntryActionListener {
            startLesson(it)
        }
        settingsButton.addActionListener {
            configure()
        }
        helpButton.addActionListener {
            showHelp()
        }

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    e.consume()
                    openElement()
                }
            }
        })
        list.addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                if (e.keyChar == '\n') {
                    e.consume()
                    openElement()
                }
            }

            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_BACK_SPACE) {
                    e.consume()
                    goUp()
                }
            }
        })
        list.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.addListSelectionListener {
            enableDisableButtons()
        }

        val focusableComponents = listOf(list, viewButton, lessonButton)
        focusTraversalPolicy = object : SortingFocusTraversalPolicy(compareBy { c ->
            focusableComponents.indexOf(c)
        }) {
            override fun accept(aComponent: Component?): Boolean = aComponent in focusableComponents
        }

        updateEntries()
    }

    private fun configure() {
        if (showSettingsDialog()) {
            configureAndEnterLibrary(manager) {
                updateEntries()
                pack()
            }
        }
    }

    private fun enableDisableButtons() {
        val selectedEntry = list.selectedValue?.entry
        val enabled = selectedEntry is FlashcardSetFileEntry || selectedEntry is FlashcardSetDirEntry
        viewButton.isEnabled = enabled
        lessonButton.isEnabled = enabled
    }

    private fun goUp() {
        if (list.model.size == 0) {
            return
        }
        val firstEntry = list.model.getElementAt(0)?.entry ?: return
        if (firstEntry !is FlashcardSetUpEntry) {
            return
        }
        enterDir(firstEntry.path, selectDir = manager.path?.relativePath)
    }

    private fun openElement() {
        val selectedEntry = list.selectedValue?.entry ?: return
        when (selectedEntry) {
            is FlashcardSetFileEntry -> viewFlashcards(selectedEntry)
            is FlashcardSetDirEntry -> enterDir(selectedEntry.path)
            is FlashcardSetUpEntry -> goUp()
        }
    }

    private fun enterDir(dirPath: RelativePath, selectDir: RelativePath? = null) {
        service.processEntries(
            dialogIndicator(),
            source = {
                manager.enter(dirPath)
            },
            processor = { result ->
                when (result) {
                    is DirEnterSuccess -> {
                        updateEntries(selectDir)
                    }
                    is DirEnterError -> {
                        showError(result.message)
                    }
                }
            }
        )
    }

    private fun updateEntries(selectDir: RelativePath? = null) {
        dir.text = manager.path?.toString()
        model.clear()
        manager.entries.forEach { entry ->
            model.addElement(CardListEntryView(entry))
        }
        if (selectDir == null) {
            if (model.size() > 0) {
                list.selectedIndex = 0
            }
        }
        else {
            list.setSelectedValue(CardListEntryView(FlashcardSetDirEntry(selectDir)), true)
        }
        val listAndListAreaWidth = max(list.width, dir.width)
        val listPreferredWidth = list.preferredScrollableViewportSize.width
        val dirPreferredWidth = dir.preferredSize.width
        val extraForList = listPreferredWidth - listAndListAreaWidth
        val extraForDir = dirPreferredWidth - listAndListAreaWidth
        val extra = max(extraForList, extraForDir)
        if (extra > 0) {
            setSize(width + extra + FRAME_EXTRA_WIDTH, height)
        }
    }

    private fun viewFlashcards(entry: FlashcardSetListEntry) {
        val library = manager.library ?: return
        service.processLessonData(
            dialogIndicator(),
            source = {
                library.getAllFlashcards(entry)
            },
            processor = { result ->
                openFrame(result) { data -> FlashcardSetViewFrame(data) }
            },
        )
    }

    private fun startLesson(entry: FlashcardSetListEntry) {
        val library = manager.library ?: return
        service.processLessonData(
            dialogIndicator(),
            source = {
                library.prepareLessonData(entry)
            },
            processor = { result ->
                openFrame(result) { data -> LessonFrame(service, library, Lesson(data)) }
            },
        )
    }

    private fun openFrame(result: LessonDataResult, frame: (LessonData) -> JFrame) {
        when (result) {
            is LessonData -> {
                showFrame(frame, result)
            }
            is LessonDataWarnings -> {
                showWarnings(result.warnings)
                showFrame(frame, result.data)
            }
            is LessonDataError -> {
                val error = result.message
                showError(error)
            }
        }
    }

    private fun showFrame(
        frame: (LessonData) -> JFrame,
        result: LessonData
    ) {
        frame(result).apply {
            defaultCloseOperation = DISPOSE_ON_CLOSE
            pack()
            setLocationRelativeTo(null)
            isVisible = true
        }
    }

    private fun showWarnings(warnings: List<String>) {
        JOptionPane.showMessageDialog(
            this,
            "<html>" + warnings.joinToString("<br>"),
            "Warning",
            JOptionPane.WARNING_MESSAGE,
        )
    }

    private fun showError(error: String) {
        JOptionPane.showMessageDialog(
            this,
            error,
            "Error",
            JOptionPane.ERROR_MESSAGE,
        )
    }

    private inner class ListEntryActionButton(text: String) : JButton(text) {
        fun addEntryActionListener(listener: (FlashcardSetListEntry) -> Unit) {
            addActionListener {
                list.requestFocusInWindow()
                val entry = list.selectedValue?.entry ?: return@addActionListener
                listener(entry)
            }
        }

    }

}

data class CardListEntryView(
    val entry: FlashcardSetListEntry,
) {
    override fun toString(): String = entry.name
}
