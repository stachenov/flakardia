package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.configureAndEnterLibrary
import name.tachenov.flakardia.data.LessonData
import name.tachenov.flakardia.data.LessonDataError
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
) : JFrame("Flakardia") {

    private val dir = JLabel()
    private val list = JList<CardListEntryView>()
    private val model = DefaultListModel<CardListEntryView>()
    private val viewButton = JButton("View flashcards").apply {
        horizontalAlignment = SwingConstants.LEADING
        mnemonic = KeyEvent.VK_V
    }
    private val simpleButton = JButton("Start simple lesson").apply {
        horizontalAlignment = SwingConstants.LEADING
        mnemonic = KeyEvent.VK_S
    }
    private val cramButton = JButton("Start cram lesson").apply {
        horizontalAlignment = SwingConstants.LEADING
        mnemonic = KeyEvent.VK_C
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
                addComponent(simpleButton)
                addComponent(cramButton)
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
                    addComponent(simpleButton)
                    addPreferredGap(RELATED)
                    addComponent(cramButton)
                    addPreferredGap(RELATED, DEFAULT_SIZE, INFINITY)
                    addComponent(settingsButton)
                    addPreferredGap(RELATED)
                    addComponent(helpButton)
                })
            })
            addContainerGap()
        }
        layout.linkSize(SwingConstants.HORIZONTAL, viewButton, simpleButton, cramButton, settingsButton, helpButton)
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        contentPane.layout = layout
        this.contentPane = contentPane

        list.model = model

        viewButton.addActionListener {
            viewFlashcards()
        }
        simpleButton.addActionListener {
            startLesson { lessonData -> SimpleLesson(lessonData) }
        }
        cramButton.addActionListener {
            startLesson { lessonData -> CramLesson(lessonData) }
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

        val focusableComponents = listOf(list, viewButton, simpleButton, cramButton)
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
        simpleButton.isEnabled = enabled
        cramButton.isEnabled = enabled
    }

    private fun goUp() {
        if (list.model.size == 0) {
            return
        }
        val firstEntry = list.model.getElementAt(0)?.entry ?: return
        if (firstEntry !is FlashcardSetUpEntry) {
            return
        }
        enterDir(firstEntry.dir, selectDir = manager.path?.relativePath)
    }

    private fun openElement() {
        val selectedEntry = list.selectedValue?.entry ?: return
        when (selectedEntry) {
            is FlashcardSetFileEntry -> viewFlashcards()
            is FlashcardSetDirEntry -> enterDir(selectedEntry.dir)
            is FlashcardSetUpEntry -> goUp()
        }
    }

    private fun enterDir(dirPath: RelativePath, selectDir: RelativePath? = null) {
        service.processEntries(
            source = {
                manager.enter(dirPath)
            },
            processor = { result ->
                when (result) {
                    is DirEnterSuccess -> {
                        updateEntries(selectDir)
                    }
                    is DirEnterError -> {
                        JOptionPane.showMessageDialog(
                            this,
                            result.message,
                            "Error",
                            JOptionPane.ERROR_MESSAGE,
                        )
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

    private fun viewFlashcards() {
        openFrame { cards -> FlashcardSetViewFrame(cards) }
    }

    private fun startLesson(lesson: (LessonData) -> Lesson) {
        val library = manager.library ?: return
        openFrame { cards -> LessonFrame(service, library, lesson(cards)) }
    }

    private fun openFrame(frame: (LessonData) -> JFrame) {
        list.requestFocusInWindow()
        val entry = list.selectedValue?.entry ?: return
        val library = manager.library ?: return
        service.processLessonData(
            source = {
                library.readLessonData(entry)
            },
            processor = { result ->
                when (result) {
                    is LessonData -> {
                        frame(result).apply {
                            defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
                            pack()
                            setLocationRelativeTo(null)
                            isVisible = true
                        }
                    }
                    is LessonDataError -> {
                        JOptionPane.showMessageDialog(
                            this,
                            result.message,
                            "Error",
                            JOptionPane.ERROR_MESSAGE,
                        )
                    }
                }
            },
        )
    }

}

data class CardListEntryView(
    val entry: FlashcardSetListEntry,
) {
    override fun toString(): String = entry.name
}
