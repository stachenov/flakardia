package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.CramLesson
import name.tachenov.flakardia.app.Lesson
import name.tachenov.flakardia.app.SimpleLesson
import name.tachenov.flakardia.data.FlashcardSet
import name.tachenov.flakardia.data.readFlashcards
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.*
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.LayoutStyle.ComponentPlacement.RELATED

class CardSetManagerFrame : JFrame("Flakardia") {

    private val list = JList<FlashcardSetFile>()
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
                    addComponent(simpleButton)
                    addPreferredGap(RELATED)
                    addComponent(cramButton)
                })
            })
            addContainerGap()
        }
        layout.linkSize(SwingConstants.HORIZONTAL, simpleButton, cramButton)
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        contentPane.layout = layout
        this.contentPane = contentPane

        list.model = DefaultListModel<FlashcardSetFile>().also { model ->
            Files.newDirectoryStream(Path.of("cards")).use { dir ->
                dir.forEach { path ->
                    model.addElement(FlashcardSetFile(path))
                }
            }
        }
        if (list.model.size > 0) {
            list.selectedIndex = 0
        }

        simpleButton.addActionListener {
            startLesson { flashcardSet -> SimpleLesson(flashcardSet) }
        }
        cramButton.addActionListener {
            startLesson { flashcardSet -> CramLesson(flashcardSet) }
        }
    }

    private fun startLesson(lesson: (FlashcardSet) -> Lesson) {
        list.requestFocusInWindow()
        val path = list.selectedValue?.path ?: return
        LessonFrame(lesson(readFlashcards(path))).apply {
            defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            pack()
            nextQuestion()
            setLocationRelativeTo(null)
            isVisible = true
        }
    }

}

class FlashcardSetFile(
    val path: Path
) {
    override fun toString(): String = path.fileName.toString()
}
