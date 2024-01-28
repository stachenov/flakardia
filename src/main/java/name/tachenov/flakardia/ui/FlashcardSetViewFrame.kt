package name.tachenov.flakardia.ui

import name.tachenov.flakardia.data.FlashcardSet
import javax.swing.*
import javax.swing.table.DefaultTableModel

class FlashcardSetViewFrame(flashcardSet: FlashcardSet) : JFrame(flashcardSet.name) {

    init {
        val contentPane = JPanel()
        val layout = GroupLayout(contentPane)
        val hg = layout.createSequentialGroup()
        val vg = layout.createSequentialGroup()
        val table = JTable()
        val scrollPane = JScrollPane(table)
        hg.apply {
            addContainerGap()
            addComponent(scrollPane)
            addContainerGap()
        }
        vg.apply {
            addContainerGap()
            addComponent(scrollPane)
            addContainerGap()
        }
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        contentPane.layout = layout
        this.contentPane = contentPane

        val model = DefaultTableModel()
        model.addColumn("Question")
        model.addColumn("Answer")
        for (card in flashcardSet.cards) {
            model.addRow(arrayOf(card.front.value, card.back.value))
        }
        table.model = model

        contentPane.preferredSize = contentPane.preferredSize.apply {
            width = getFontMetrics(contentPane.font).stringWidth(WIDTH_STRING) * 2
        }
    }

}
