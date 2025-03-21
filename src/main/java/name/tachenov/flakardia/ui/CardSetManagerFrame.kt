package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.FlashcardSetListEntry
import name.tachenov.flakardia.app.Lesson
import name.tachenov.flakardia.app.Library
import name.tachenov.flakardia.assertEDT
import name.tachenov.flakardia.data.LessonData
import name.tachenov.flakardia.presenter.CardListEntryPresenter
import name.tachenov.flakardia.presenter.CardSetManagerPresenter
import name.tachenov.flakardia.presenter.CardSetManagerView
import name.tachenov.flakardia.showHelp
import name.tachenov.flakardia.version
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
    private val presenter: CardSetManagerPresenter,
) : JFrame("Flakardia ${version()}"), CardSetManagerView {

    private val dir = JLabel()
    private val list = JList<CardListEntryPresenter>()
    private val model = DefaultListModel<CardListEntryPresenter>()
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
            presenter.viewFlashcards(it)
        }
        lessonButton.addEntryActionListener {
            presenter.startLesson(it)
        }
        settingsButton.addActionListener {
            presenter.configure()
        }
        helpButton.addActionListener {
            showHelp()
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

        val focusableComponents = listOf(list, viewButton, lessonButton)
        focusTraversalPolicy = object : SortingFocusTraversalPolicy(compareBy { c ->
            focusableComponents.indexOf(c)
        }) {
            override fun accept(aComponent: Component?): Boolean = aComponent in focusableComponents
        }
        defaultCloseOperation = EXIT_ON_CLOSE
    }

    override suspend fun run() {
        presenter.state.collect { state ->
            var updateWidth = false
            if (dir.text != state.currentPath) {
                updateWidth = true
                dir.text = state.currentPath
            }
            if (model.elements().toList() != state.entries) {
                model.clear()
                model.addAll(state.entries)
                updateWidth = true
            }
            val shouldScroll = state.isScrollToSelectionRequested
            list.setSelectedValue(state.selectedEntry, shouldScroll)
            if (shouldScroll) {
                presenter.scrollRequestCompleted()
            }
            viewButton.isEnabled = state.isViewButtonEnabled
            lessonButton.isEnabled = state.isLessonButtonEnabled
            if (!isVisible) {
                showOnFirstStateInit()
            } else if (updateWidth) {
                updateWidth()
            }
        }
    }

    override fun adjustSize() {
        assertEDT()
        pack()
    }

    private fun showOnFirstStateInit() {
        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }

    private fun updateWidth() {
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

    override fun viewFlashcards(result: LessonData) {
        assertEDT()
        showFrame(result) { data -> FlashcardSetViewFrame(data) }
    }

    override fun startLesson(library: Library, result: LessonData) {
        assertEDT()
        showFrame(result) { data -> LessonFrame(library, Lesson(data)) }
    }

    private fun showFrame(
        result: LessonData,
        frame: (LessonData) -> JFrame,
    ) {
        frame(result).apply {
            defaultCloseOperation = DISPOSE_ON_CLOSE
            pack()
            setLocationRelativeTo(null)
            isVisible = true
        }
    }

    override fun showWarnings(warnings: List<String>) {
        assertEDT()
        JOptionPane.showMessageDialog(
            this,
            "<html>" + warnings.joinToString("<br>"),
            "Warning",
            JOptionPane.WARNING_MESSAGE,
        )
    }

    override fun showError(error: String) {
        assertEDT()
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
