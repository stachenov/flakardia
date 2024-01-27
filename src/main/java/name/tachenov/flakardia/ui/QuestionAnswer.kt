package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.Question
import javax.swing.*
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.GroupLayout.PREFERRED_SIZE

class QuestionAnswer : JPanel() {

    private val question = JLabel()
    private val answerInput = JTextField()
    private val yourAnswerLabel = JLabel("Your answer:")
    private val correctAnswerLabel = JLabel("Correct answer:")
    private val yourAnswer = JLabel()
    private val correctAnswer = JLabel()

    private var isAnswerVisible: Boolean
        get() = yourAnswerLabel.isVisible
        set(value) {
            yourAnswer.isVisible = value
            yourAnswerLabel.isVisible = value
            correctAnswer.isVisible = value
            correctAnswerLabel.isVisible = value
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
            addComponent(answerInput, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addGroup(layout.createSequentialGroup().apply {
                addGroup(layout.createParallelGroup(LEADING).apply {
                    addComponent(yourAnswerLabel, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
                    addComponent(correctAnswerLabel, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
                })
                addGroup(layout.createParallelGroup(LEADING).apply {
                    addComponent(yourAnswer, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
                    addComponent(correctAnswer, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
                })
            })
        }
        val vg = layout.createSequentialGroup()
        vg.apply {
            addComponent(question, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            addComponent(answerInput, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addGroup(layout.createParallelGroup(LEADING).apply {
                addGroup(layout.createSequentialGroup().apply {
                    addComponent(yourAnswerLabel, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
                    addComponent(correctAnswerLabel, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
                })
                addGroup(layout.createSequentialGroup().apply {
                    addComponent(yourAnswer, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
                    addComponent(correctAnswer, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
                })
            })
        }
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        this.layout = layout
    }

    override fun addNotify() {
        super.addNotify()
        val widthString = "califragilisticexpialidocious / califragilisticexpialidocious / califragilisticexpialidocious"
        question.setPreferredWidthString(widthString)
        answerInput.setPreferredWidthString(widthString)
        yourAnswer.setPreferredWidthString(widthString)
        correctAnswer.setPreferredWidthString(widthString)
    }
}
