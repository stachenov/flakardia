package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.Answer
import name.tachenov.flakardia.app.Lesson
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.JFrame
import javax.swing.JPanel

class LessonFrame(private val lesson: Lesson) : JFrame(lesson.name) {

    private val questionAnswerPanel = QuestionAnswerPanel(
        answered = this::answered,
        nextQuestion = this::nextQuestion,
    )

    init {
        val contentPane = JPanel()
        val layout = GroupLayout(contentPane)
        val hg = layout.createSequentialGroup()
        val vg = layout.createParallelGroup(LEADING)
        hg.addContainerGap()
        hg.addComponent(questionAnswerPanel)
        hg.addContainerGap()
        vg.addGroup(layout.createSequentialGroup().apply {
            addContainerGap()
            addComponent(questionAnswerPanel)
            addContainerGap()
        })
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        contentPane.layout = layout
        this.contentPane = contentPane
    }

    fun nextQuestion() {
        val nextQuestion = lesson.nextQuestion()
        if (nextQuestion == null) {
            questionAnswerPanel.isVisible = false
        }
        else {
            questionAnswerPanel.isVisible = true
            questionAnswerPanel.displayQuestion(nextQuestion)
        }
    }

    private fun answered(answer: Answer?) {
        questionAnswerPanel.displayAnswerResult(lesson.answer(answer))
    }

}
