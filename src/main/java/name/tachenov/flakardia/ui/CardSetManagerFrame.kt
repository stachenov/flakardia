package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.data.FlashcardSet
import name.tachenov.flakardia.data.FlashcardSetError
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.LayoutStyle.ComponentPlacement.RELATED

class CardSetManagerFrame : JFrame("Flakardia") {

    private val manager = CardManager()
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
            addComponent(listScrollPane)
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
                addComponent(listScrollPane)
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

        updateEntries()
    }

    private fun updateEntries() {
        model.clear()
        manager.entries.forEach { entry ->
            model.addElement(CardListEntryView(entry))
        }
        if (model.size() > 0) {
            list.selectedIndex = 0
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
        if (entry !is CardSetFileEntry) {
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
    val entry: CardListEntry,
) {
    override fun toString(): String = when (entry) {
        is CardSetFileEntry -> entry.file.fileName.toString()
    }
}
