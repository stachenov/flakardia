package name.tachenov.flakardia.ui

import name.tachenov.flakardia.FlashcardSetViewFrameState
import name.tachenov.flakardia.data.RelativePath
import name.tachenov.flakardia.getFlashcardSetViewFrameState
import name.tachenov.flakardia.presenter.*
import name.tachenov.flakardia.setFlashcardSetViewFrameState
import java.awt.Component
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.text.Collator
import javax.swing.*
import javax.swing.GroupLayout.Alignment.BASELINE
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.LayoutStyle.ComponentPlacement.RELATED
import javax.swing.LayoutStyle.ComponentPlacement.UNRELATED
import javax.swing.table.*
import kotlin.math.max

enum class FlashcardSetViewColumn(val title: String) {
    PATH("Path"),
    QUESTION("Question"),
    ANSWER("Answer"),
    LAST_LEARNED("Last learned"),
    MISTAKES_MADE("Mistakes made"),
    LEARN_INTERVAL("Learn interval (days)");
    companion object {
        fun defaultSort(): FlashcardSetViewColumn = PATH
    }
}

class FlashcardSetViewFrame(
    private val parent: JFrame,
    presenter: FlashcardSetViewPresenter,
) : FrameView<FlashcardSetViewPresenterState, FlashcardSetView, FlashcardSetViewPresenter>(presenter), FlashcardSetView {

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
        table.autoCreateColumnsFromModel = false
        table.model = model
        table.addColumn(FlashcardSetViewColumn.PATH, RelativePath::class.java,
            valueForMaxWidth = RelativePath(listOf("some reasonable file path.txt")),
            valueCutStrategy = PathCutStrategy
        )
        table.addColumn(FlashcardSetViewColumn.QUESTION, String::class.java, valueForMaxWidth = "supercalifragilisticexpialidocious")
        table.addColumn(FlashcardSetViewColumn.ANSWER, String::class.java, valueForMaxWidth = "supercalifragilisticexpialidocious")
        table.addColumn(FlashcardSetViewColumn.LAST_LEARNED, LastLearnedViewModel::class.java)
        table.addColumn(FlashcardSetViewColumn.MISTAKES_MADE, Int::class.javaObjectType)
        table.addColumn(FlashcardSetViewColumn.LEARN_INTERVAL, IntervalViewModel::class.java)
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
        for (i in 0 until table.columnCount) {
            val column = table.getColumn(table.getColumnName(i))
            column.addPropertyChangeListener { e ->
                if (e.propertyName == "width") {
                    requestSaveViewState()
                }
            }
        }
        table.rowSorter.addRowSorterListener {
            requestSaveViewState()
        }
        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent?) {
                val screenBounds = graphicsConfiguration?.bounds ?: return
                if (width > screenBounds.width) {
                    setSize((screenBounds.width * 0.95).toInt(), height)
                }
            }
        })
    }

    override fun restoreSavedViewState() {
        val viewState = getFlashcardSetViewFrameState()
        packColumns(table, viewState)
        pack()
        if (viewState.bounds == null) {
            setLocationRelativeTo(parent)
        }
        else {
            this.bounds = viewState.bounds
        }
        if (viewState.sortKey != null) {
            table.rowSorter.sortKeys = listOf(viewState.sortKey)
        }
    }

    override fun saveViewState() {
        val state = FlashcardSetViewFrameState(
            bounds = this.bounds,
            (0 until model.columnCount).associate { modelIndex ->
                val column = FlashcardSetViewColumn.entries[modelIndex]
                column to table.getColumn(column.title).width
            },
            table.rowSorter.sortKeys.firstOrNull(),
        )
        setFlashcardSetViewFrameState(state)
    }

    override fun applyPresenterState(state: FlashcardSetViewPresenterState) {
        title = state.title
        for (card in state.cards) {
            model.addRow(arrayOf(
                card.path,
                card.front,
                card.back,
                card.lastLearned,
                card.mistakes,
                card.intervalBeforeLastLearned,
            ))
        }
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

private interface ValueCutStrategy {
    fun cut(value: Any?, maxWidth: Int, fontMetrics: FontMetrics): String
}

private fun <T> JTable.addColumn(
    column: FlashcardSetViewColumn,
    type: Class<T>,
    valueForMaxWidth: T? = null,
    valueCutStrategy: ValueCutStrategy? = null,
) {
    val tableColumn = TableColumn(model.columnCount)
    val model = this.model as MyTableModel
    model.addColumn(column.title, type)
    addColumn(tableColumn)
    val cellRenderer = MyTableCellRenderer(this, valueForMaxWidth, valueCutStrategy)
    tableColumn.cellRenderer = cellRenderer
}

private class MyTableCellRenderer<T>(
    private val table: JTable,
    valueForMaxWidth: T?,
    private val valueCutStrategy: ValueCutStrategy?,
) : DefaultTableCellRenderer() {
    private val maxWidth: Int? = valueForMaxWidth?.let {
        value ->  super.getTableCellRendererComponent(table, value, false, false, 0, 0).preferredSize.width
    }

    fun preferredWidth(value: Any?): Int = super.getTableCellRendererComponent(table, value, false, false, 0, 0)
        .preferredSize.width
        .coerceAtMost(maxWidth ?: Int.MAX_VALUE)

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component? {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as MyTableCellRenderer<T>
        val columnWidth = table.getColumn(table.getColumnName(column)).width - 2 * MARGIN
        val cutoff = component.preferredSize.width > columnWidth
        component.toolTipText = if (cutoff) {
            value.toString()
        }
        else {
            null
        }
        if (cutoff && valueCutStrategy != null) {
            val maxStringWidth = columnWidth - component.insets.let { it.left + it.right }
            setValue(valueCutStrategy.cut(value, maxStringWidth, component.getFontMetrics(component.font)))
        }
        return component
    }
}

object PathCutStrategy : ValueCutStrategy {
    override fun cut(value: Any?, maxWidth: Int, fontMetrics: FontMetrics): String {
        val fullString = value.toString()
        val cutoffLength = -1 - (0..fullString.length).toList().binarySearch { length ->
            val cutString = "..." + fullString.substring(fullString.length - length)
            val cutWidth = fontMetrics.stringWidth(cutString)
            if (cutWidth <= maxWidth) -1 else +1
        } - 1
        return if (cutoffLength > 0) {
            "..." + fullString.substring(fullString.length - cutoffLength)
        } else {
            ""
        }
    }
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
fun packColumns(table: JTable, viewState: FlashcardSetViewFrameState) {
    for (columnIndex in 0 until table.columnCount) {
        packColumn(table, columnIndex, viewState.columnWidths[FlashcardSetViewColumn.entries[columnIndex]])
    }
}

/**
 * Sets the preferred width of the visible column specified by vColIndex. The column
 * will be just wide enough to show the column head and the widest cell in the column.
 * Margin pixels are added to the left and right.
 * @param table Table for which the column width must be adjusted
 * @param vColIndex Column index
 */
private fun packColumn(table: JTable, vColIndex: Int, savedWidth: Int?) {
    val colModel = table.columnModel as DefaultTableColumnModel
    val col = colModel.getColumn(vColIndex)
    if (savedWidth != null) {
        col.preferredWidth = savedWidth
        return
    }

    // Get width of column header
    var renderer = col.headerRenderer
    if (renderer == null) {
        renderer = table.tableHeader.defaultRenderer
    }
    val comp = renderer!!.getTableCellRendererComponent(table, col.headerValue, false, false, 0, 0)
    var width = comp.preferredSize.width

    // Get maximum width of column data
    for (r in 0 until table.rowCount) {
        val cellRenderer = table.getCellRenderer(r, vColIndex) as MyTableCellRenderer<*>
        width = max(width.toDouble(), cellRenderer.preferredWidth(table.getValueAt(r, vColIndex)).toDouble()).toInt()
    }

    // Add margin
    width += 2 * MARGIN

    // Set the width
    col.preferredWidth = width
}

private const val MARGIN = 2
private const val EXTRA_PREFERRED_WIDTH = 20
