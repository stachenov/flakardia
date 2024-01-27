package name.tachenov.flakardia

import name.tachenov.flakardia.app.Lesson
import name.tachenov.flakardia.data.readFlashcards
import name.tachenov.flakardia.ui.LessonFrame
import java.awt.Font
import java.nio.file.Path
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants

fun main() {
    SwingUtilities.invokeLater {
        configureUiDefaults()
        LessonFrame(Lesson(readFlashcards(Path.of("cards/test.cards")))).apply {
            defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
            pack()
            nextQuestion()
            setLocationRelativeTo(null)
            isVisible = true
        }
    }
}

private fun configureUiDefaults() {
    val uiDefaults = UIManager.getDefaults()
    uiDefaults["Label.font"] = Font("Verdana", Font.PLAIN, 16)
    uiDefaults["TextField.font"] = Font("Verdana", Font.PLAIN, 16)
}
