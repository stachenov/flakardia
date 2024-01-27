package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.Lesson
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.JFrame
import javax.swing.JPanel

class LessonFrame(private val lesson: Lesson) : JFrame(lesson.name) {

    private val questionAnswer = QuestionAnswer()

    init {
        val contentPane = JPanel()
        val layout = GroupLayout(contentPane)
        val hg = layout.createSequentialGroup()
        val vg = layout.createParallelGroup(LEADING)
        hg.addContainerGap()
        hg.addComponent(questionAnswer)
        hg.addContainerGap()
        vg.addGroup(layout.createSequentialGroup().apply {
            addContainerGap()
            addComponent(questionAnswer)
            addContainerGap()
        })
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        contentPane.layout = layout
        this.contentPane = contentPane
        nextQuestion()
    }

    private fun nextQuestion() {
        val nextQuestion = lesson.nextQuestion()
        if (nextQuestion == null) {
            questionAnswer.isVisible = false
        }
        else {
            questionAnswer.isVisible = true
            questionAnswer.displayQuestion(nextQuestion)
        }
    }

}
