package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.data.FlashcardSet
import name.tachenov.flakardia.data.FlashcardSetError
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.*
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.LayoutStyle.ComponentPlacement.RELATED
import kotlin.math.max

class CardSetManagerFrame(private val manager: CardManager) : JFrame("Flakardia") {

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

    init {
        val contentPane = JPanel()
        val layout = GroupLayout(contentPane)
        val hg = layout.createSequentialGroup()
        val vg = layout.createSequentialGroup()
        val listScrollPane = JScrollPane(list)
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
                })
            })
            addContainerGap()
        }
        layout.linkSize(SwingConstants.HORIZONTAL, viewButton, simpleButton, cramButton)
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        contentPane.layout = layout
        this.contentPane = contentPane

        list.model = model

        viewButton.addActionListener {
            viewFlashcards()
        }
        simpleButton.addActionListener {
            startLesson { flashcardSet -> SimpleLesson(flashcardSet) }
        }
        cramButton.addActionListener {
            startLesson { flashcardSet -> CramLesson(flashcardSet) }
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

    private fun enableDisableButtons() {
        val selectedEntry = list.selectedValue?.entry ?: return
        val enabled = selectedEntry is FlashcardSetFileEntry
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
        enterDir(firstEntry.dir)
    }

    private fun openElement() {
        val selectedEntry = list.selectedValue?.entry ?: return
        val dirPath = when (selectedEntry) {
            is FlashcardSetFileEntry -> null
            is FlashcardSetDirEntry -> selectedEntry.dir
            is FlashcardSetUpEntry -> selectedEntry.dir
        }
        if (dirPath == null) {
            viewFlashcards()
        }
        else {
            enterDir(dirPath)
        }
    }

    private fun enterDir(dirPath: Path) {
        when (val result = manager.enter(dirPath)) {
            is DirEnterSuccess -> {
                updateEntries()
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

    private fun updateEntries() {
        dir.text = manager.path?.toString()
        model.clear()
        manager.entries.forEach { entry ->
            model.addElement(CardListEntryView(entry))
        }
        if (model.size() > 0) {
            list.selectedIndex = 0
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

    private fun startLesson(lesson: (FlashcardSet) -> Lesson) {
        openFrame { cards -> LessonFrame(lesson(cards)) }
    }

    private fun openFrame(frame: (FlashcardSet) -> JFrame) {
        list.requestFocusInWindow()
        val entry =list.selectedValue?.entry ?: return
        if (entry !is FlashcardSetFileEntry) {
            return
        }
        when (val result = entry.readCards()) {
            is FlashcardSet -> {
                frame(result).apply {
                    defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
                    pack()
                    setLocationRelativeTo(null)
                    isVisible = true
                }
            }
            is FlashcardSetError -> {
                JOptionPane.showMessageDialog(
                    this,
                    result.message,
                    "Error",
                    JOptionPane.ERROR_MESSAGE,
                )
            }
        }
    }

}

class CardListEntryView(
    val entry: FlashcardSetListEntry,
) {
    override fun toString(): String = entry.name
}
