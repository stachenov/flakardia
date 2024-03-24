package name.tachenov.flakardia.ui

import name.tachenov.flakardia.data.LessonData
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.swing.*
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.DefaultTableModel
import kotlin.math.max


class FlashcardSetViewFrame(lessonData: LessonData) : JFrame(lessonData.name) {

    init {
        val contentPane = JPanel()
        val layout = GroupLayout(contentPane)
        val hg = layout.createSequentialGroup()
        val vg = layout.createSequentialGroup()
        val table = JTable()
        val scrollPane = object : JScrollPane(table) {
            override fun getPreferredSize(): Dimension {
                return super.getPreferredSize().apply { width = table.preferredSize.width + EXTRA_PREFERRED_WIDTH }
            }
        }
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
        model.addColumn("Path")
        model.addColumn("Question")
        model.addColumn("Answer")
        model.addColumn("Last learned")
        model.addColumn("Mistakes made")
        model.addColumn("Learn interval (days)")
        for (card in lessonData.flashcards) {
            val stats = lessonData.stats.wordStats[card.flashcard.back]
            model.addRow(arrayOf(
                card.path,
                card.flashcard.front.value,
                card.flashcard.back.value,
                LastLearnedViewModel(stats?.lastLearned),
                stats?.mistakes ?: "",
                IntervalViewModel(stats?.intervalBeforeLastLearned),
            ))
        }
        table.model = model
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        table.autoCreateRowSorter = true
        packColumns(table)
        pack()
        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent?) {
                val screenBounds = graphicsConfiguration?.bounds ?: return
                if (width > screenBounds.width) {
                    setSize((screenBounds.width * 0.95).toInt(), height)
                }
            }
        })
    }

}

data class LastLearnedViewModel(val lastLearned: Instant?) {
    override fun toString(): String = lastLearned?.let { String.format("%tF  %<tT", it.atZone(ZoneId.systemDefault())) } ?: ""
}

data class IntervalViewModel(val interval: Duration?) {
    override fun toString(): String = interval?.let { String.format("%.02f", it.toSeconds().toDouble() / 86400.0) } ?: ""
}

// Open source utility method from
// http://www.java2s.com/example/java-utility-method/jtable-column-pack/packcolumns-jtable-table-e8cce.html

/**
 * Adjusts the widths of columns to be just wide enough to show all of
 * the column headers and the widest cells in the columns
 * @param table Table for which the columns widths must be adjusted
 */
fun packColumns(table: JTable) {
    for (columnIndex in 0 until table.columnCount) {
        packColumn(table, columnIndex)
    }
}

/**
 * Sets the preferred width of the visible column specified by vColIndex. The column
 * will be just wide enough to show the column head and the widest cell in the column.
 * Margin pixels are added to the left and right.
 * @param table Table for which the column width must be adjusted
 * @param vColIndex Column index
 */
private fun packColumn(table: JTable, vColIndex: Int) {
    val colModel = table.columnModel as DefaultTableColumnModel
    val col = colModel.getColumn(vColIndex)

    // Get width of column header
    var renderer = col.headerRenderer
    if (renderer == null) {
        renderer = table.tableHeader.defaultRenderer
    }
    var comp = renderer!!.getTableCellRendererComponent(table, col.headerValue, false, false, 0, 0)
    var width = comp.preferredSize.width

    // Get maximum width of column data
    for (r in 0 until table.rowCount) {
        renderer = table.getCellRenderer(r, vColIndex)
        comp = renderer.getTableCellRendererComponent(
            table, table.getValueAt(r, vColIndex), false, false, r,
            vColIndex
        )
        width = max(width.toDouble(), comp.preferredSize.width.toDouble()).toInt()
    }

    // Add margin
    width += 2 * MARGIN

    // Set the width
    col.preferredWidth = width
}

private const val MARGIN = 2
private const val EXTRA_PREFERRED_WIDTH = 20
