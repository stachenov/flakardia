package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.LessonResult
import name.tachenov.flakardia.app.SimpleLessonResult
import javax.swing.JPanel

sealed class LessonResultPanel : JPanel() {
    companion object {
        fun create(result: LessonResult): LessonResultPanel {
            val resultPanel: LessonResultPanel = when (result) {
                is SimpleLessonResult -> SimpleLessonResultPanel()
            }
            return resultPanel.apply { displayResult(result) }
        }
    }

    abstract fun displayResult(result: LessonResult)
}
