package name.tachenov.flakardia.ui

import name.tachenov.flakardia.assertUiAccessAllowed
import name.tachenov.flakardia.getLessonFrameLocation
import name.tachenov.flakardia.presenter.*
import name.tachenov.flakardia.setLessonFrameLocation
import java.awt.Window
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.GroupLayout.PREFERRED_SIZE
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.LayoutStyle

enum class LessonFramePosition {
    RELATIVE,
    SAVED;
    companion object {
        fun default() = RELATIVE
    }
}

class LessonFrame(
    private val owner: Window,
    presenter: LessonPresenter,
) : FrameView<LessonPresenterState, LessonPresenterView, LessonPresenter>(presenter, PackFrame.BEFORE_STATE_INIT), LessonPresenterView {

    private val lessonResultPanel = LessonResultPanel()

    private val questionAnswerPanel = QuestionAnswerPanel(presenter)
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
    }

    override fun restoreSavedViewState() {
        assertUiAccessAllowed()
        val saved = getLessonFrameLocation()
        if (saved == null) {
            setLocationAboveOrBelowOf(owner)
        }
        else {
            location = saved
        }
    }

    override fun saveViewState() {
        assertUiAccessAllowed()
        setLessonFrameLocation(location)
    }

    override fun applyPresenterState(state: LessonPresenterState) {
        assertUiAccessAllowed()
        title = state.title
        when (val lessonStatus = state.lessonStatus) {
            is QuestionState -> {
                questionAnswerPanel.isVisible = true
                questionAnswerPanel.displayQuestion(lessonStatus.nextQuestion)
                status.text = "Press Enter to answer the question, or Esc to give up"
            }
            is AnswerState -> {
                questionAnswerPanel.isVisible = true
                questionAnswerPanel.displayAnswerResult(lessonStatus.answerResult)
                status.text = "Press Space to continue to the next question, or F2 to edit the card"
            }
            is EditWordState -> {
                questionAnswerPanel.isVisible = true
                questionAnswerPanel.editWord(lessonStatus.word)
                status.text = "Press Enter to save the word or Esc to cancel editing"
            }
            is DoneState -> {
                questionAnswerPanel.isVisible = false
                done.isVisible = true
                status.text = "Nothing more to do, close the window to finish with the lesson"
            }
        }
        lessonResultPanel.displayResult(state.lessonResult)
    }
}
