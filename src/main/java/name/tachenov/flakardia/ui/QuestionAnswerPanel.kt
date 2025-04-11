package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.Answer
import name.tachenov.flakardia.app.Question
import name.tachenov.flakardia.data.Word
import name.tachenov.flakardia.presenter.AnswerResultPresenter
import name.tachenov.flakardia.presenter.EditWordPresenter
import name.tachenov.flakardia.presenter.LessonPresenter
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.GroupLayout.PREFERRED_SIZE
import javax.swing.JPanel
import javax.swing.LayoutStyle
import javax.swing.SwingUtilities

class QuestionAnswerPanel(private val presenter: LessonPresenter) : JPanel() {

    private val question = FixedWidthLabel()
    private val questionEditor = WordTextField().also {
        it.isVisible = false
    }
    private val answerInput = FixedWidthTextField()
    private val answerEditor = WordTextField().also {
        it.isVisible = false
    }
    private val blinkRate = answerInput.caret.blinkRate
    private val correctAnswer = FixedWidthLabel().apply { foreground = CORRECT_COLOR }

    fun displayQuestion(nextQuestion: Question) {
        questionEditor.isVisible = false
        answerEditor.isVisible = false

        answerInput.isVisible = true
        answerInput.isEditable = true
        answerInput.caret.blinkRate = blinkRate
        answerInput.caret.isVisible = true
        answerInput.repaint()
        answerInput.text = ""
        answerInput.foreground = null
        correctAnswer.isVisible = false
        question.isVisible = true
        question.text = nextQuestion.word.value
        question.toolTipText = nextQuestion.flashcardSetPath.toString()

        SwingUtilities.invokeLater {
            answerInput.requestFocusInWindow()
        }
    }

    fun displayAnswerResult(answerResult: AnswerResultPresenter) {
        questionEditor.isVisible = false
        answerEditor.isVisible = false

        answerInput.isVisible = true
        answerInput.text = answerResult.yourAnswer
        answerInput.isEditable = false
        answerInput.caret.blinkRate = 0
        answerInput.caret.isVisible = true
        answerInput.repaint()
        question.isVisible = true
        if (answerResult.isCorrect) {
            answerInput.foreground = CORRECT_COLOR
            correctAnswer.isVisible = false
        }
        else {
            answerInput.foreground = INCORRECT_COLOR
            correctAnswer.isVisible = true
            correctAnswer.text = answerResult.correctAnswer
        }

        SwingUtilities.invokeLater {
            answerInput.requestFocusInWindow()
        }
    }

    fun editWord(word: EditWordPresenter) {
        questionEditor.isVisible = true
        answerEditor.isVisible = true

        answerInput.isVisible = false
        question.isVisible = false
        correctAnswer.isVisible = false

        questionEditor.text = word.question
        answerEditor.text = word.answer

        SwingUtilities.invokeLater {
            questionEditor.requestFocusInWindow()
        }
    }

    init {
        val layout = GroupLayout(this)
        val hg = layout.createParallelGroup(LEADING)
        hg.apply {
            addComponent(question, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addComponent(questionEditor, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addComponent(answerInput, DEFAULT_SIZE, PREFERRED_SIZE, INFINITY)
            addComponent(answerEditor, DEFAULT_SIZE, PREFERRED_SIZE, INFINITY)
            addComponent(correctAnswer, DEFAULT_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
        }
        val vg = layout.createSequentialGroup()
        vg.apply {
            addComponent(question, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            addComponent(questionEditor, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            addComponent(answerInput, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
            addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            addComponent(answerEditor, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
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
                        presenter.answered(Answer(Word(answerInput.text)))
                    }
                    !answerInput.isEditable && e.keyChar == ' ' -> {
                        e.consume()
                        presenter.nextQuestion()
                    }
                }
            }

            override fun keyPressed(e: KeyEvent) {
                if (answerInput.isEditable && e.keyCode == KeyEvent.VK_ESCAPE) {
                    e.consume()
                    presenter.answered(null)
                }
                if (!answerInput.isEditable && e.keyCode == KeyEvent.VK_F2) {
                    e.consume()
                    presenter.editCurrentWord()
                }
            }
        })
        val editorKeyListener = object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                when {
                    e.keyChar == '\n' -> {
                        e.consume()
                        presenter.saveWord(questionEditor.text, answerEditor.text)
                    }
                }
            }

            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    presenter.cancelEditing()
                }
            }
        }
        questionEditor.addKeyListener(editorKeyListener)
        answerEditor.addKeyListener(editorKeyListener)
    }

}
