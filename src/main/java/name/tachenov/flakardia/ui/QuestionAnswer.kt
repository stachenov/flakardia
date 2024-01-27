package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.Question
import javax.swing.*
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.GroupLayout.PREFERRED_SIZE

class QuestionAnswer : JPanel() {

    private val question = FixedWidthLabel()
    private val answerInput = FixedWidthTextField()
    private val correctAnswer = FixedWidthLabel()

    private var isAnswerVisible: Boolean
        get() = correctAnswer.isVisible
        set(value) {
            correctAnswer.isVisible = value
        }

    private var isAnswerInputVisible: Boolean
        get() = answerInput.isVisible
        set(value) {
            answerInput.isVisible = value
        }

    fun displayQuestion(nextQuestion: Question) {
        isAnswerInputVisible = true
        isAnswerVisible = false
        question.text = nextQuestion.value
    }

    init {
        val layout = GroupLayout(this)
        val hg = layout.createParallelGroup(LEADING)
        hg.apply {
            addComponent(question, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addComponent(answerInput, DEFAULT_SIZE, PREFERRED_SIZE, INFINITY)
            addComponent(correctAnswer, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
        }
        val vg = layout.createSequentialGroup()
        vg.apply {
            addComponent(question, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            addComponent(answerInput, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            addComponent(correctAnswer, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
        }
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        this.layout = layout
    }

}
