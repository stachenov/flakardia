package name.tachenov.flakardia.ui

import name.tachenov.flakardia.data.RelativePath
import name.tachenov.flakardia.presenter.FlashcardSetView
import name.tachenov.flakardia.presenter.FlashcardSetViewPresenter
import name.tachenov.flakardia.presenter.IntervalViewModel
import name.tachenov.flakardia.presenter.LastLearnedViewModel
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.text.Collator
import javax.swing.*
import javax.swing.GroupLayout.Alignment.BASELINE
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.LayoutStyle.ComponentPlacement.RELATED
import javax.swing.LayoutStyle.ComponentPlacement.UNRELATED
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter
import kotlin.math.max


class FlashcardSetViewFrame(
    private val presenter: FlashcardSetViewPresenter,
) : JFrame(), FlashcardSetView {

    private val table: JTable
    private val model: MyTableModel

    init {
        val contentPane = JPanel()
        val layout = GroupLayout(contentPane)
        val hg = layout.createSequentialGroup()
        val vg = layout.createSequentialGroup()
        table = JTable()
        val scrollPane = object : JScrollPane(table) {
            override fun getPreferredSize(): Dimension {
                return super.getPreferredSize().apply { width = table.preferredSize.width + EXTRA_PREFERRED_WIDTH }
            }
        }
        val countLabel = JLabel("Flashcards selected:")
        val count = JLabel("0")
        hg.apply {
            addContainerGap()
            addGroup(layout.createParallelGroup(LEADING).apply {
                addComponent(scrollPane)
                addGroup(layout.createSequentialGroup().apply {
                    addComponent(countLabel)
                    addPreferredGap(RELATED)
                    addComponent(count)
                })
            })
            addContainerGap()
        }
        vg.apply {
            addContainerGap()
            addComponent(scrollPane)
            addPreferredGap(UNRELATED)
            addGroup(layout.createParallelGroup(BASELINE).apply {
                addComponent(countLabel)
                addComponent(count)
            })
            addContainerGap()
        }
        layout.setHorizontalGroup(hg)
        layout.setVerticalGroup(vg)
        contentPane.layout = layout
        this.contentPane = contentPane

        model = MyTableModel()
        model.addColumn("Path", RelativePath::class.java)
        model.addColumn("Question", String::class.java)
        model.addColumn("Answer", String::class.java)
        model.addColumn("Last learned", LastLearnedViewModel::class.java)
        model.addColumn("Mistakes made", Int::class.javaObjectType)
        model.addColumn("Learn interval (days)", IntervalViewModel::class.java)
        table.model = model
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        table.autoCreateRowSorter = true
        for (i in 0 until model.columnCount) {
            if (model.getColumnClass(i) == String::class.java) {
                (table.rowSorter as TableRowSorter).setComparator(i, COLLATOR)
            }
        }
        table.selectionModel.addListSelectionListener {
            count.text = table.selectionModel.selectedItemsCount.toString()
        }
        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent?) {
                val screenBounds = graphicsConfiguration?.bounds ?: return
                if (width > screenBounds.width) {
                    setSize((screenBounds.width * 0.95).toInt(), height)
                }
            }
        })
        defaultCloseOperation = DISPOSE_ON_CLOSE
    }

    override suspend fun run() {
        title = presenter.state.title
        for (card in presenter.state.cards) {
            model.addRow(arrayOf(
                card.path,
                card.front,
                card.back,
                card.lastLearned,
                card.mistakes,
                card.intervalBeforeLastLearned,
            ))
        }
        packColumns(table)
        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }
}

private class MyTableModel : DefaultTableModel() {

    private val classes = mutableListOf<Class<*>>()

    fun addColumn(columnName: String, columnClass: Class<*>) {
        super.addColumn(columnName)
        classes += columnClass
    }

    override fun getColumnClass(columnIndex: Int): Class<*> = classes[columnIndex]

    override fun isCellEditable(row: Int, column: Int): Boolean = false
}

private val COLLATOR = Collator.getInstance().apply {
    strength = Collator.PRIMARY
    decomposition = Collator.CANONICAL_DECOMPOSITION
}

// Open source utility method from
// http://www.java2s.com/example/java-utility-method/jtable-column-pack/packcolumns-jtable-table-e8cce.html

/**
 * Adjusts the widths of columns to be just wide enough to show all
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
