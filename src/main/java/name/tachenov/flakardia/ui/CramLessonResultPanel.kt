package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.CramLessonResult
import name.tachenov.flakardia.app.CramLevel
import name.tachenov.flakardia.app.LessonResult
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment.*
import javax.swing.JLabel
import javax.swing.LayoutStyle.ComponentPlacement.RELATED
import javax.swing.LayoutStyle.ComponentPlacement.UNRELATED

class CramLessonResultPanel : LessonResultPanel() {

    private val level = JLabel()
    private val count = mutableListOf<JLabel>()

    init {
        val layout = GroupLayout(this)
        val hg = layout.createSequentialGroup()
        val vg = layout.createParallelGroup(CENTER)
        val levelLabel = JLabel("Level")
        val countLabel = mutableListOf<JLabel>()
        for (cramLevel in CramLevel.entries) {
            count += JLabel()
            countLabel += JLabel("L" + cramLevel.number)
        }
        hg.apply {
            addComponent(levelLabel)
            addPreferredGap(RELATED)
            addComponent(level)
            addPreferredGap(UNRELATED)
            for (cramLevel in CramLevel.entries) {
                addGroup(layout.createParallelGroup(LEADING).apply {
                    addComponent(countLabel[cramLevel.ordinal])
                    addComponent(count[cramLevel.ordinal])
                })
                addPreferredGap(RELATED)
            }
        }
        vg.apply {
            addGroup(layout.createParallelGroup(BASELINE).apply {
                addComponent(levelLabel)
                addComponent(level)
            })
            for (cramLevel in CramLevel.entries) {
                addGroup(layout.createSequentialGroup().apply {
                    addComponent(countLabel[cramLevel.ordinal])
                    addPreferredGap(RELATED)
                    addComponent(count[cramLevel.ordinal])
                })
            }
        }
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        this.layout = layout
    }

    override fun displayResult(result: LessonResult) {
        result as CramLessonResult
        level.text = result.level.number.toString()
        for (cramLevel in CramLevel.entries) {
            count[cramLevel.ordinal].text = result.cardCount.getValue(cramLevel).toString()
        }
    }
}
