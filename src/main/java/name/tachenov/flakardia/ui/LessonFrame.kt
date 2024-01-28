package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.Answer
import name.tachenov.flakardia.app.Lesson
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.GroupLayout.PREFERRED_SIZE

class LessonFrame(private val lesson: Lesson) : JFrame(lesson.name) {

    private val lessonResultPanel = LessonResultPanel.create(lesson.result)

    private val questionAnswerPanel = QuestionAnswerPanel(
        answered = this::answered,
        nextQuestion = this::nextQuestion,
    )
    private val done = JLabel("All done!").apply { isVisible = false }
    private val status = JLabel("Initializing...")

    init {
        val contentPane = JPanel()
        val layout = GroupLayout(contentPane)
        val hg = layout.createSequentialGroup()
        val vg = layout.createSequentialGroup()
        hg.addContainerGap()
        hg.addGroup(layout.createParallelGroup(LEADING).apply {
            addComponent(lessonResultPanel)
            addComponent(questionAnswerPanel)
            addComponent(done)
            addComponent(status)
        })
        hg.addContainerGap()
        vg.apply {
            addContainerGap()
            addComponent(lessonResultPanel, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            addComponent(questionAnswerPanel, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            addComponent(done, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, DEFAULT_SIZE, INFINITY)
            addComponent(status, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addContainerGap()
        }
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        contentPane.layout = layout
        this.contentPane = contentPane

        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent?) {
                nextQuestion()
            }
        })
    }

    fun nextQuestion() {
        val nextQuestion = lesson.nextQuestion()
        if (nextQuestion == null) {
            questionAnswerPanel.isVisible = false
            done.isVisible = true
            status.text = "Nothing more to do, close the window to finish with the lesson"
        }
        else {
            questionAnswerPanel.isVisible = true
            questionAnswerPanel.displayQuestion(nextQuestion)
            status.text = "Press Enter to answer the question, or Esc to give up"
        }
        lessonResultPanel.displayResult(lesson.result)
    }

    private fun answered(answer: Answer?) {
        val answerResult = lesson.answer(answer)
        questionAnswerPanel.displayAnswerResult(answerResult)
        status.text = "Press Space to continue to the next question"
        lessonResultPanel.displayResult(lesson.result)
    }

}
