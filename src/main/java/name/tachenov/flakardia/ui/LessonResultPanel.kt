package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.LessonResult
import javax.swing.GroupLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.LayoutStyle

class LessonResultPanel(result: LessonResult) : JPanel() {

    private val round = JLabel()
    private val total = JLabel()
    private val correct = JLabel()
    private val incorrect = JLabel()
    private val remaining = JLabel()

    init {
        val layout = GroupLayout(this)
        val hg = layout.createSequentialGroup()
        val vg = layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        val roundLabel = JLabel("Round:")
        val totalLabel = JLabel("Total:")
        val correctLabel = JLabel("Correct:")
        val incorrectLabel = JLabel("Incorrect:")
        val remainingLabel = JLabel("Remaining:")
        hg.apply {
            addComponent(roundLabel)
            addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            addComponent(round)
            addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            addComponent(totalLabel)
            addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            addComponent(total)
            addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            addComponent(correctLabel)
            addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            addComponent(correct)
            addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            addComponent(incorrectLabel)
            addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            addComponent(incorrect)
            addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            addComponent(remainingLabel)
            addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            addComponent(remaining)
        }
        vg.apply {
            addComponent(roundLabel)
            addComponent(round)
            addComponent(totalLabel)
            addComponent(total)
            addComponent(correctLabel)
            addComponent(correct)
            addComponent(incorrectLabel)
            addComponent(incorrect)
            addComponent(remainingLabel)
            addComponent(remaining)
        }
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        this.layout = layout
        displayResult(result)
    }

    fun displayResult(result: LessonResult) {
        round.text = result.round.toString()
        round.foreground = if (result.correctingMistakes) INCORRECT_COLOR else null
        total.text = result.total.toString()
        correct.text = result.correct.toString()
        correct.foreground = if (result.correct > 0) CORRECT_COLOR else null
        incorrect.text = result.incorrect.toString()
        incorrect.foreground = if (result.incorrect > 0) INCORRECT_COLOR else null
        remaining.text = result.remaining.toString()
    }

}
