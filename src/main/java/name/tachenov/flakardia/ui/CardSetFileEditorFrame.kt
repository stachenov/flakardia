package name.tachenov.flakardia.ui

import name.tachenov.flakardia.getEditorBounds
import name.tachenov.flakardia.presenter.*
import name.tachenov.flakardia.setEditorBounds
import java.awt.*
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Document
import javax.swing.text.JTextComponent

class CardSetFileEditorFrame(
    private val parent: JFrame,
    presenter: CardSetFileEditorPresenter,
) : FrameView<CardSetFileEditorState, CardSetFileEditorView, CardSetFileEditorPresenter>(presenter), CardSetFileEditorView
{
    private val editor = CardSetEditor(presenter)
    private val scrollPane = JScrollPane(editor)
    private val statusPanel = JPanel().apply {
        border = BorderFactory.createEmptyBorder(INSET_TOP, INSET_LEFT, INSET_BOTTOM, INSET_RIGHT)
    }
    private val statusLabel = JLabel()

    init {
        title = presenter.name
        layout = BorderLayout()
        add(scrollPane, BorderLayout.CENTER)
        statusPanel.layout = BorderLayout()
        statusPanel.add(statusLabel, BorderLayout.WEST)
        add(statusPanel, BorderLayout.SOUTH)
    }

    override fun restoreSavedViewState() {
        val bounds = getEditorBounds()
        if (bounds == null) {
            setLocationRelativeTo(parent)
        }
        else {
            this.bounds = bounds
        }
    }

    override fun saveViewState() {
        setEditorBounds(this.bounds)
    }

    override fun applyPresenterState(state: CardSetFileEditorState) {
        editor.updateState(state)
        statusLabel.text = when (val persistenceState = state.persistenceState) {
            is CardSetFileEditorEditedState -> "Saving..."
            is CardSetFileEditorSavedState -> "Saved"
            is CardSetFileEditorSaveErrorState -> "Save error: ${persistenceState.message}"
        }
    }
}

class CardSetEditor(private val presenter: CardSetFileEditorPresenter) : JPanel(), Scrollable {
    private val editors = mutableListOf<CardEditor>()

    override fun getPreferredScrollableViewportSize(): Dimension =
        preferredSize.apply {
            height = computeLayout(MIN_WIDTH, numberOfEditors = 8) { preferredSize }.fullHeight
        }

    override fun getScrollableUnitIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int =
        editors.first().questionEditor.height

    override fun getScrollableBlockIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int {
        val parent = this.parent
        return if (parent is JViewport) {
            parent.extentSize.height
        }
        else {
            height
        }
    }

    override fun getScrollableTracksViewportWidth(): Boolean = true

    override fun getScrollableTracksViewportHeight(): Boolean = false

    fun updateState(update: CardSetFileEditorState) {
        var updated = true
        when (val change = update.changeFromPrevious) {
            CardSetFileEditorFirstState -> {
                for (editorComponent in editors) {
                    remove(editorComponent.questionEditor)
                    remove(editorComponent.answerEditor)
                }
                editors.clear()
                for (card in update.editorFullState.cards) {
                    insertCardEditor(editors.size, card)
                }
                val lastEditor = editors.lastOrNull()
                if (lastEditor != null) {
                    if (lastEditor.questionEditor.text.isEmpty()) {
                        lastEditor.questionEditor.focusAndScroll()
                    }
                    else {
                        lastEditor.answerEditor.focusAndScroll()
                    }
                }
            }
            is CardSetFileEditorNoChange -> { }
            is CardAdded -> {
                insertCardEditor(change.index, change.card)
                editors[change.index].questionEditor.focusAndScroll()
            }
            is CardChanged -> {
                updated = false
            }
            is CardRemoved -> {
                removeCardEditor(change.index)
            }
        }
        if (updated) {
            revalidate()
            repaint()
        }
    }

    private fun insertCardEditor(index: Int, card: CardPresenterState) {
        val editor = CardEditor(presenter, card)
        editors.add(index, editor)
        add(editor.questionEditor, index * 2)
        add(editor.answerEditor, index * 2 + 1)
        val questionEditor = editor.questionEditor
        val answerEditor = editor.answerEditor
        questionEditor.addKeyListener(KeyEvent.VK_UP, condition = { true }) {
            focusPreviousEditor(editor)
        }
        questionEditor.addKeyListener(KeyEvent.VK_DOWN, condition = { true }) {
            keepCaretPositionIfHomeOrEnd(questionEditor, answerEditor)
            answerEditor.focusAndScroll()
        }
        questionEditor.addKeyListener(KeyEvent.VK_DELETE,
            condition = { questionEditor.text.isNullOrEmpty() && !answerEditor.text.isNullOrEmpty() }
        ) {
            answerEditor.caretPosition = 0
            answerEditor.focusAndScroll()
        }
        answerEditor.addKeyListener(KeyEvent.VK_UP, condition = { true }) {
            keepCaretPositionIfHomeOrEnd(answerEditor, questionEditor)
            questionEditor.focusAndScroll()
        }
        answerEditor.addKeyListener(KeyEvent.VK_DOWN, condition = { true }) {
            focusNextEditor(editor)
        }
        answerEditor.addKeyListener(KeyEvent.VK_BACK_SPACE,
            condition = { !questionEditor.text.isNullOrEmpty() && answerEditor.text.isNullOrEmpty() }
        ) {
            questionEditor.caretPosition = questionEditor.text.length
            questionEditor.focusAndScroll()
        }
    }

    private fun focusPreviousEditor(thisEditor: CardEditor) {
        val index = editors.indexOf(thisEditor)
        if (index == -1) return
        val previousAnswerEditor = if (index > 0) {
            editors[index - 1].answerEditor
        }
        else {
            editors.last().answerEditor
        }
        keepCaretPositionIfHomeOrEnd(thisEditor.questionEditor, previousAnswerEditor)
        previousAnswerEditor.focusAndScroll()
    }

    private fun focusNextEditor(thisEditor: CardEditor) {
        val index = editors.indexOf(thisEditor)
        if (index == -1) return
        val nextQuestionEditor = if (index + 1 < editors.size) {
            editors[index + 1].questionEditor
        }
        else {
            editors.first().questionEditor
        }
        keepCaretPositionIfHomeOrEnd(thisEditor.answerEditor, nextQuestionEditor)
        nextQuestionEditor.focusAndScroll()
    }

    private fun keepCaretPositionIfHomeOrEnd(fromEditor: JTextComponent, toEditor: JTextComponent) {
        // If we meet an empty editor while moving between them,
        // then both of these conditions are true.
        // The first one wins, so the caret will keep moving to the starting position of every editor after the empty one.
        // This doesn't really matter much because normally there should be no empty editors to begin with,
        // so this is an uncommon case.
        if (fromEditor.caretPosition == 0) {
            toEditor.caretPosition = 0
        }
        else if (fromEditor.caretPosition == fromEditor.text.length) {
            toEditor.caretPosition = toEditor.text.length
        }
    }

    private fun removeCardEditor(index: Int) {
        val removed = editors.removeAt(index)
        remove(removed.questionEditor)
        remove(removed.answerEditor)
        val removedFirstEditor = index == 0
        val removedLastEditor = index == editors.size
        val focusPreviousEditor = removedLastEditor || (removed.isRemovedUsingBackSpace && !removedFirstEditor)
        if (focusPreviousEditor) {
            editors[index - 1].answerEditor.caretPosition = editors[index - 1].answerEditor.text.length
            editors[index - 1].answerEditor.focusAndScroll()
        }
        else {
            editors[index].questionEditor.caretPosition = 0
            editors[index].questionEditor.focusAndScroll()
        }
    }

    override fun getMinimumSize(): Dimension = computeSize { minimumSize }

    override fun getPreferredSize(): Dimension = computeSize { preferredSize }

    override fun getMaximumSize(): Dimension = computeSize { maximumSize }

    private fun computeSize(sizeToUse: JComponent.() -> Dimension): Dimension {
        val layout = computeLayout(MIN_WIDTH) { sizeToUse() }
        return Dimension(layout.fullWidth, layout.fullHeight)
    }

    override fun doLayout() {
        val forcedWidth = this.width - INSET_LEFT - INSET_RIGHT
        val layout = computeLayout(minWidth = forcedWidth) { preferredSize.also { it.width = forcedWidth } }
        for ((editorComponent, y) in editors.zip(layout.yCoordinates)) {
            editorComponent.questionEditor.bounds = Rectangle(layout.x, y.questionY1, layout.componentWidth, y.questionHeight)
            editorComponent.answerEditor.bounds = Rectangle(layout.x, y.answerY1, layout.componentWidth, y.answerHeight)
        }
    }

    private fun computeLayout(
        minWidth: Int,
        numberOfEditors: Int = editors.size,
        sizeToUse: JComponent.() -> Dimension,
    ): Layout {
        var width = minWidth
        var y = INSET_TOP
        val yCoordinates = mutableListOf<CardEditorLayout>()
        for (i in 0 until numberOfEditors) {
            // When computing the theoretical size for N editors when N is more than the actual number of editors,
            // we fall back to using the first editor's size as a sort of default template size,
            // which works fine in practice because they're all the same height usually.
            val editorComponent = editors.getOrNull(i) ?: editors.first()
            val size1 = editorComponent.questionEditor.sizeToUse()
            width = width.coerceAtLeast(size1.width)

            val questionY1 = y
            y += size1.height
            val questionY2 = y

            y += GAP_BETWEEN_QUESTION_AND_ANSWER

            val size2 = editorComponent.answerEditor.sizeToUse()
            width = width.coerceAtLeast(size2.width)

            val answerY1 = y
            y += size2.height
            val answerY2 = y

            y += GAP_BETWEEN_CARDS

            yCoordinates += CardEditorLayout(questionY1, questionY2, answerY1, answerY2)
        }
        return Layout(
            width,
            yCoordinates,
            Insets(INSET_TOP, INSET_LEFT, INSET_BOTTOM, INSET_RIGHT)
        )
    }

    data class Layout(
        val componentWidth: Int,
        val yCoordinates: List<CardEditorLayout>,
        val insets: Insets,
    ) {
        val x: Int get() = insets.left
        val fullWidth: Int get() = INSET_LEFT + componentWidth + INSET_RIGHT
        val fullHeight: Int get() = yCoordinates.last().answerY2 + insets.bottom
    }

    data class CardEditorLayout(
        val questionY1: Int,
        val questionY2: Int,
        val answerY1: Int,
        val answerY2: Int,
    ) {
        val questionHeight: Int get() = questionY2 - questionY1
        val answerHeight: Int get() = answerY2 - answerY1
    }
}

private class CardEditor(
    private val presenter: CardSetFileEditorPresenter,
    initialState: CardPresenterState,
) {
    val id = initialState.id
    val questionEditor = FixedWidthTextField(initialState.question).also { enableSpellchecker(it) }
    val answerEditor = FixedWidthTextField(initialState.answer).also { enableSpellchecker(it) }
    var isRemovedUsingBackSpace = false
        private set

    init {
        questionEditor.document.addDocumentChangeListener {
            presenter.updateQuestion(id, questionEditor.text)
        }
        questionEditor.addKeyListener(KeyEvent.VK_ENTER,
            condition = { caretPosition == 0 && !questionEditor.text.isNullOrBlank() && !answerEditor.text.isNullOrBlank()}) {
            presenter.insertCardBefore(id)
        }
        questionEditor.addKeyListener(KeyEvent.VK_ENTER, condition = { caretPosition > 0 }) {
            answerEditor.focusAndScroll()
        }
        questionEditor.addKeyListener(KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE,
            condition = { questionEditor.text.isNullOrEmpty() && answerEditor.text.isNullOrEmpty() }
        ) { e ->
            isRemovedUsingBackSpace = e.keyCode == KeyEvent.VK_BACK_SPACE
            presenter.removeCard(id)
        }
        answerEditor.document.addDocumentChangeListener {
            presenter.updateAnswer(id, answerEditor.text)
        }
        answerEditor.addKeyListener(KeyEvent.VK_ENTER, condition = { true }) {
            presenter.insertCardAfter(id)
        }
        answerEditor.addKeyListener(KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE,
            condition = { questionEditor.text.isNullOrEmpty() && answerEditor.text.isNullOrEmpty() }
        ) { e ->
            isRemovedUsingBackSpace = e.keyCode == KeyEvent.VK_BACK_SPACE
            presenter.removeCard(id)
        }
    }

    override fun toString(): String = "($questionEditor, $answerEditor)"
}

private fun JComponent.focusAndScroll() {
    when {
        isShowing && isValid -> {
            doFocusAndScroll()
        }
        isShowing && !isValid -> {
            revalidate()
            SwingUtilities.invokeLater {
                focusAndScroll()
            }
        }
        else -> {
            val listener = object : HierarchyListener {
                override fun hierarchyChanged(e: HierarchyEvent?) {
                    if (isShowing) {
                        focusAndScroll()
                        removeHierarchyListener(this)
                    }
                }
            }
            addHierarchyListener(listener)
        }
    }
}

private fun JComponent.doFocusAndScroll() {
    // Can't just use scrollRectToVisible() because JTextField would only scroll itself then,
    // as it has its own scrolling to scroll the text horizontally.
    // But that's not what we want here, we want to scroll the whole editor vertically.
    val parent = parent ?: return
    var viewport: Component? = parent
    while (viewport != null && viewport !is JViewport) {
        viewport = viewport.parent
    }
    if (viewport is JViewport) {
        val rect = SwingUtilities.convertRectangle(parent, bounds, viewport)
        viewport.scrollRectToVisible(rect)
    }
    requestFocusInWindow()
}

private fun Document.addDocumentChangeListener(block: () -> Unit) {
    addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) {
            block()
        }

        override fun removeUpdate(e: DocumentEvent?) {
            block()
        }

        override fun changedUpdate(e: DocumentEvent?) {
            block()
        }
    })
}

private fun JTextComponent.addKeyListener(vararg keyCodes: Int, condition: JTextComponent.() -> Boolean, listener: (KeyEvent) -> Unit) {
    addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
            if (e.keyCode in keyCodes && condition()) {
                listener(e)
                e.consume()
            }
        }
    })
}

private const val INSET_TOP = 2
private const val INSET_LEFT = 2
private const val INSET_BOTTOM = 2
private const val INSET_RIGHT = 2
private const val GAP_BETWEEN_QUESTION_AND_ANSWER = 2
private const val GAP_BETWEEN_CARDS = 10
private const val MIN_WIDTH = 100
