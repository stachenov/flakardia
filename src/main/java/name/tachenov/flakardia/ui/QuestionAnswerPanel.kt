package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.Answer
import name.tachenov.flakardia.app.AnswerResult
import name.tachenov.flakardia.app.Question
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.GroupLayout.PREFERRED_SIZE
import javax.swing.JPanel
import javax.swing.LayoutStyle

class QuestionAnswerPanel(
    private val answered: (Answer?) -> Unit,
    private val nextQuestion: () -> Unit,
) : JPanel() {

    private val question = FixedWidthLabel()
    private val answerInput = FixedWidthTextField()
    private val blinkRate = answerInput.caret.blinkRate
    private val correctAnswer = FixedWidthLabel().apply { foreground = CORRECT_COLOR }

    fun displayQuestion(nextQuestion: Question) {
        answerInput.isEditable = true
        answerInput.caret.blinkRate = blinkRate
        answerInput.caret.isVisible = true
        answerInput.repaint()
        answerInput.text = ""
        answerInput.foreground = null
        correctAnswer.isVisible = false
        question.text = nextQuestion.value
    }

    fun displayAnswerResult(answerResult: AnswerResult) {
        answerInput.text = answerResult.yourAnswer?.value
        answerInput.isEditable = false
        answerInput.caret.blinkRate = 0
        answerInput.caret.isVisible = true
        answerInput.repaint()
        if (answerResult.isCorrect) {
            answerInput.foreground = CORRECT_COLOR
            correctAnswer.isVisible = false
        }
        else {
            answerInput.foreground = INCORRECT_COLOR
            correctAnswer.isVisible = true
            correctAnswer.text = answerResult.correctAnswer.value
        }
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
        answerInput.addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                when {
                    answerInput.isEditable && e.keyChar == '\n' -> {
                        e.consume()
                        answered(Answer(answerInput.text))
                    }
                    !answerInput.isEditable && e.keyChar == ' ' -> {
                        e.consume()
                        nextQuestion()
                    }
                }
            }

            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    answered(null)
                }
            }
        })
    }

}
