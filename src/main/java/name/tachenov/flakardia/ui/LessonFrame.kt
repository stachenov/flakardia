package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.Answer
import name.tachenov.flakardia.app.Lesson
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.GroupLayout.PREFERRED_SIZE
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.LayoutStyle

class LessonFrame(private val lesson: Lesson) : JFrame(lesson.name) {

    private val lessonResultPanel = LessonResultPanel.create(lesson.result)

    private val questionAnswerPanel = QuestionAnswerPanel(
        answered = this::answered,
        nextQuestion = this::nextQuestion,
    )

    init {
        val contentPane = JPanel()
        val layout = GroupLayout(contentPane)
        val hg = layout.createSequentialGroup()
        val vg = layout.createSequentialGroup()
        hg.addContainerGap()
        hg.addGroup(layout.createParallelGroup(LEADING).apply {
            addComponent(lessonResultPanel)
            addComponent(questionAnswerPanel)
        })
        hg.addContainerGap()
        vg.apply {
            addContainerGap()
            addComponent(lessonResultPanel, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            addComponent(questionAnswerPanel, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addContainerGap()
        }
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
        lessonResultPanel.displayResult(lesson.result)
    }

    private fun answered(answer: Answer?) {
        val answerResult = lesson.answer(answer)
        questionAnswerPanel.displayAnswerResult(answerResult)
        lessonResultPanel.displayResult(lesson.result)
    }

}
