package name.tachenov.flakardia

import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.OneDarkTheme
import name.tachenov.flakardia.app.SimpleLesson
import name.tachenov.flakardia.data.readFlashcards
import name.tachenov.flakardia.ui.LessonFrame
import java.awt.Font
import java.nio.file.Path
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants

fun main() {
    SwingUtilities.invokeLater {
        LafManager.install(OneDarkTheme())
        configureUiDefaults()
        LessonFrame(SimpleLesson(readFlashcards(Path.of("cards/test.cards")))).apply {
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
