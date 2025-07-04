package name.tachenov.flakardia.ui

import name.tachenov.flakardia.*
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.FlashcardSetListEntry
import name.tachenov.flakardia.app.Lesson
import name.tachenov.flakardia.app.Library
import name.tachenov.flakardia.data.Flashcard
import name.tachenov.flakardia.data.LessonData
import name.tachenov.flakardia.presenter.*
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.LayoutStyle.ComponentPlacement.RELATED
import javax.swing.LayoutStyle.ComponentPlacement.UNRELATED
import kotlin.math.max

class CardSetManagerFrame(
    presenter: CardSetManagerPresenter,
) : FrameView<CardSetManagerPresenterState, CardSetManagerView, CardSetManagerPresenter>(presenter), CardSetManagerView {

    private val dir = JLabel()
    private val list = JList<CardListEntryPresenter>()
    private val listScrollPane: JScrollPane
    private val model = DefaultListModel<CardListEntryPresenter>()
    private val viewButton = ListEntryActionButton("View flashcards").apply {
        horizontalAlignment = SwingConstants.LEADING
        mnemonic = KeyEvent.VK_V
    }
    private val lessonButton = ListEntryActionButton("Start lesson").apply {
        horizontalAlignment = SwingConstants.LEADING
        mnemonic = KeyEvent.VK_S
    }
    private val newDirButton = ListEntryActionButton("New dir").apply {
        horizontalAlignment = SwingConstants.LEADING
        mnemonic = KeyEvent.VK_D
    }
    private val newFileButton = ListEntryActionButton("New file").apply {
        horizontalAlignment = SwingConstants.LEADING
        mnemonic = KeyEvent.VK_F
    }
    private val editFileButton = ListEntryActionButton("Edit file").apply {
        horizontalAlignment = SwingConstants.LEADING
        mnemonic = KeyEvent.VK_E
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
        listScrollPane = JScrollPane(list).apply {
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
                addComponent(newDirButton)
                addComponent(newFileButton)
                addComponent(editFileButton)
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
                    addPreferredGap(UNRELATED)
                    addComponent(newDirButton)
                    addPreferredGap(RELATED)
                    addComponent(newFileButton)
                    addPreferredGap(RELATED)
                    addComponent(editFileButton)
                    addPreferredGap(UNRELATED, DEFAULT_SIZE, INFINITY)
                    addComponent(settingsButton)
                    addPreferredGap(RELATED)
                    addComponent(helpButton)
                })
            })
            addContainerGap()
        }
        val buttons = listOf(
            viewButton,
            lessonButton,
            newDirButton,
            newFileButton,
            editFileButton,
            settingsButton,
            helpButton,
        )
        layout.linkSize(
            SwingConstants.HORIZONTAL,
            *buttons.toTypedArray(),
        )
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        contentPane.layout = layout
        this.contentPane = contentPane

        list.model = model

        viewButton.addEntryActionListener {
            presenter.viewFlashcards(it)
        }
        lessonButton.addEntryActionListener {
            presenter.startLesson(it)
        }
        newDirButton.addEntryActionListener {
            val name = askForDirName() ?: return@addEntryActionListener
            presenter.newDir(name)
        }
        newFileButton.addEntryActionListener {
            val name = askForFileName() ?: return@addEntryActionListener
            presenter.newFile(name)
        }
        editFileButton.addEntryActionListener {
            presenter.editFile(it)
        }
        settingsButton.addActionListener {
            presenter.configure()
        }
        helpButton.addActionListener {
            presenter.showHelpFrame()
        }

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    e.consume()
                    presenter.openElement()
                }
            }
        })
        list.addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                if (e.keyChar == '\n') {
                    e.consume()
                    presenter.openElement()
                }
            }

            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_BACK_SPACE) {
                    e.consume()
                    presenter.goUp()
                }
            }
        })
        list.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.addListSelectionListener {
            presenter.selectItem(list.selectedValue)
        }

        val focusableComponents = listOf(list) + buttons
        focusTraversalPolicy = object : SortingFocusTraversalPolicy(compareBy { c ->
            focusableComponents.indexOf(c)
        }) {
            override fun accept(aComponent: Component?): Boolean = aComponent in focusableComponents
        }
        defaultCloseOperation = EXIT_ON_CLOSE
    }

    override fun restoreSavedViewState() {
        assertUiAccessAllowed()
        val location = getManagerFrameLocation()
        if (location == null) {
            setLocationRelativeTo(null)
        }
        else {
            this.location = location
        }
        updateWidth()
    }

    override fun saveViewState() {
        assertUiAccessAllowed()
        setManagerFrameLocation(location)
    }

    override fun adjustSize() {
        super.adjustSize()
        updateWidth()
    }

    override fun applyPresenterState(state: CardSetManagerPresenterState) {
        assertUiAccessAllowed()
        title = "Flakardia ${version()}"
        var updateWidth = false
        if (dir.text != state.currentPresentablePath) {
            updateWidth = true
            dir.text = state.currentPresentablePath
        }
        if (model.elements().toList() != state.entries) {
            model.clear()
            model.addAll(state.entries)
            updateWidth = true
        }
        val shouldScroll = state.isScrollToSelectionRequested
        // Can't just use shouldScroll = true, because the model might have just changed,
        // and the list may not have been revalidated yet.
        // Therefore, use invokeLater to ensure the selection happens after validation.
        list.setSelectedValue(state.selectedEntry, false)
        if (shouldScroll) {
            SwingUtilities.invokeLater {
                val selectedIndex = list.selectedIndex
                if (selectedIndex >= 0) {
                    list.ensureIndexIsVisible(selectedIndex)
                }
            }
            presenter.scrollRequestCompleted()
        }
        viewButton.isEnabled = state.isViewButtonEnabled
        lessonButton.isEnabled = state.isLessonButtonEnabled
        if (updateWidth) {
            updateWidth()
        }
    }

    private fun updateWidth() {
        assertUiAccessAllowed()
        val listAndListAreaWidth = max(listScrollPane.width, dir.width)
        val listPreferredWidth = list.preferredScrollableViewportSize.width + FRAME_EXTRA_WIDTH
        val dirPreferredWidth = dir.preferredSize.width + FRAME_EXTRA_WIDTH
        val extraForList = listPreferredWidth - listAndListAreaWidth
        val extraForDir = dirPreferredWidth - listAndListAreaWidth
        val extra = max(extraForList, extraForDir)
        if (extra > 0) {
            setSize(width + extra, height)
        }
    }

    override suspend fun viewFlashcards(result: LessonData) {
        assertUiAccessAllowed()
        showPresenterFrame(
            presenterFactory = { FlashcardSetViewPresenter(result) },
            viewFactory = { FlashcardSetViewFrame(this@CardSetManagerFrame, it) },
        )
    }

    override suspend fun startLesson(library: Library, result: LessonData) {
        assertUiAccessAllowed()
        showPresenterFrame(
            presenterFactory = { LessonPresenter(library, Lesson(result)) },
            viewFactory = { LessonFrame(this@CardSetManagerFrame, it) },
        )
    }

    override suspend fun editFile(library: Library, fileEntry: FlashcardSetFileEntry, cards: List<Flashcard>) {
        assertUiAccessAllowed()
        showPresenterFrame(
            presenterFactory = { CardSetFileEditorPresenter(cardSetFileEditorConfig(), library, fileEntry, cards) },
            viewFactory = { CardSetFileEditorFrame(this, it) },
        )
    }

    override fun showError(error: String) {
        assertUiAccessAllowed()
        showError("Error", error)
    }

    private fun askForDirName(): String? = showInputDialog(title = "New Dir", message = "Enter the new dir name")

    private fun askForFileName(): String? = showInputDialog(title = "New File", message = "Enter the new file name")

    private fun showInputDialog(title: String, message: String): String? {
        val result = JOptionPane.showInputDialog(
            this,
            message,
            title,
            JOptionPane.QUESTION_MESSAGE,
        )
        return result?.trim()
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
