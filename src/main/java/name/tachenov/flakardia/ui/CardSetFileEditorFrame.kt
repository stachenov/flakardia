package name.tachenov.flakardia.ui

import name.tachenov.flakardia.getEditorBounds
import name.tachenov.flakardia.presenter.*
import name.tachenov.flakardia.setEditorBounds
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.text.JTextComponent
import kotlin.math.max
import kotlin.math.min

class CardSetFileEditorFrame(
    private val parent: JFrame,
    presenter: CardSetFileEditorPresenter,
) : FrameView<CardSetFileEditorState, CardSetFileEditorView, CardSetFileEditorPresenter>(presenter), CardSetFileEditorView
{
    private val editor = CardSetEditor(presenter)
    private val scrollPane = JScrollPane(editor)
    private val toolBar = JPanel()
    private val duplicateComboModel = DefaultComboBoxModel<DuplicateDetectionPath>()
    private val duplicateCombo = JComboBox(duplicateComboModel)
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
        add(toolBar, BorderLayout.NORTH)
        toolBar.layout = FlowLayout(FlowLayout.LEADING)
        toolBar.add(JLabel("Detect duplicates in:"))
        toolBar.add(duplicateCombo)
        add(statusPanel, BorderLayout.SOUTH)

        duplicateCombo.addActionListener {
            presenter.detectDuplicatesIn((duplicateCombo.selectedItem as? DuplicateDetectionPath?)?.dirEntry)
        }
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
        duplicateComboModel.removeAllElements()
        duplicateComboModel.addAll(state.duplicateDetectionState.availablePaths)
        duplicateComboModel.selectedItem = state.duplicateDetectionState.selectedPath
        statusLabel.text = when (val persistenceState = state.persistenceState) {
            is CardSetFileEditorEditedState -> "Saving..."
            is CardSetFileEditorSavedState -> persistenceState.warnings.firstOrNull() ?: "Saved"
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
            is CardsChanged -> {
                for (changed in change.changes) {
                    val updatedQuestion = changed.updatedQuestion
                    val updatedAnswer = changed.updatedAnswer
                    if (updatedQuestion != null) {
                        editors[changed.index].questionEditor.duplicates = updatedQuestion.duplicates
                    }
                    if (updatedAnswer != null) {
                        editors[changed.index].answerEditor.duplicates = updatedAnswer.duplicates
                    }
                }
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
            focusPreviousEditor(editor, CaretPositionBehavior.KEEP)
        }
        questionEditor.addKeyListener(KeyEvent.VK_LEFT, condition = { questionEditor.caretPosition == 0 }) {
            focusPreviousEditor(editor, CaretPositionBehavior.END)
        }
        questionEditor.addKeyListener(KeyEvent.VK_DOWN, condition = { true }) {
            keepCaretPositionIfHomeOrEnd(questionEditor, answerEditor)
            answerEditor.focusAndScroll()
        }
        questionEditor.addKeyListener(KeyEvent.VK_RIGHT, condition = { questionEditor.caretPosition == questionEditor.text.length }) {
            answerEditor.caretPosition = 0
            answerEditor.focusAndScroll()
        }
        questionEditor.addKeyListener(KeyEvent.VK_HOME, condition = { e -> e.modifiersEx.isCtrlOrCmdDown }) {
            controlHome()
        }
        questionEditor.addKeyListener(KeyEvent.VK_END, condition = { e -> e.modifiersEx.isCtrlOrCmdDown }) {
            controlEnd()
        }
        answerEditor.addKeyListener(KeyEvent.VK_UP, condition = { true }) {
            keepCaretPositionIfHomeOrEnd(answerEditor, questionEditor)
            questionEditor.focusAndScroll()
        }
        answerEditor.addKeyListener(KeyEvent.VK_LEFT, condition = { answerEditor.caretPosition == 0 }) {
            questionEditor.caretPosition = questionEditor.text.length
            questionEditor.focusAndScroll()
        }
        answerEditor.addKeyListener(KeyEvent.VK_DOWN, condition = { true }) {
            focusNextEditor(editor, CaretPositionBehavior.KEEP)
        }
        answerEditor.addKeyListener(KeyEvent.VK_RIGHT, condition = { answerEditor.caretPosition == answerEditor.text.length }) {
            focusNextEditor(editor, CaretPositionBehavior.HOME)
        }
        answerEditor.addKeyListener(KeyEvent.VK_HOME, condition = { e -> e.modifiersEx.isCtrlOrCmdDown }) {
            controlHome()
        }
        answerEditor.addKeyListener(KeyEvent.VK_END, condition = { e -> e.modifiersEx.isCtrlOrCmdDown }) {
            controlEnd()
        }
    }

    private fun controlHome() {
        editors.first().questionEditor.caretPosition = 0
        editors.first().questionEditor.focusAndScroll()
    }

    private fun controlEnd() {
        editors.last().answerEditor.caretPosition = editors.last().answerEditor.text.length
        editors.last().answerEditor.focusAndScroll()
    }

    private enum class CaretPositionBehavior {
        KEEP,
        HOME,
        END
    }

    private fun focusPreviousEditor(thisEditor: CardEditor, caretPositionBehavior: CaretPositionBehavior) {
        val index = editors.indexOf(thisEditor)
        if (index == -1) return
        val previousAnswerEditor = if (index > 0) {
            editors[index - 1].answerEditor
        }
        else {
            editors.last().answerEditor
        }
        adjustCaretPosition(caretPositionBehavior, thisEditor.questionEditor, previousAnswerEditor)
        previousAnswerEditor.focusAndScroll()
    }

    private fun focusNextEditor(thisEditor: CardEditor, caretPositionBehavior: CaretPositionBehavior) {
        val index = editors.indexOf(thisEditor)
        if (index == -1) return
        val nextQuestionEditor = if (index + 1 < editors.size) {
            editors[index + 1].questionEditor
        }
        else {
            editors.first().questionEditor
        }
        adjustCaretPosition(caretPositionBehavior, thisEditor.answerEditor, nextQuestionEditor)
        nextQuestionEditor.focusAndScroll()
    }

    private fun adjustCaretPosition(
        caretPositionBehavior: CaretPositionBehavior,
        fromEditor: WordTextField,
        toEditor: WordTextField,
    ) {
        when (caretPositionBehavior) {
            CaretPositionBehavior.KEEP -> keepCaretPositionIfHomeOrEnd(fromEditor, toEditor)
            CaretPositionBehavior.HOME -> toEditor.caretPosition = 0
            CaretPositionBehavior.END -> toEditor.caretPosition = toEditor.text.length
        }
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
        // This part is a bit counter-intuitive: when removing an empty card using Backspace,
        // we generally don't want the caret at the end of the previous field, unlike regular text editing,
        // because then holding Backspace to remove several cards would mean that after the last one is removed,
        // we'll start wiping the previous non-empty card, which is unlikely what we want.
        // So using Backspace moves the caret to the start and using Delete moves it to the end,
        // then the deletion will stop once we reach the first non-empty card.
        // With this approach, wiping a lot of cards at once by holding some key isn't supported,
        // but that's a good thing, because it's normally not needed.
        val caretAtStart = removed.isRemovedUsingBackSpace
        val fieldToFocus = if (focusPreviousEditor) editors[index - 1].answerEditor else editors[index].questionEditor
        fieldToFocus.caretPosition = if (caretAtStart) 0 else fieldToFocus.text.length
        fieldToFocus.focusAndScroll()
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
    val questionEditor = WordTextField(initialState.question.word).apply {
        duplicates = initialState.question.duplicates
        putClientProperty(CARD_EDITOR, this@CardEditor)
        scrollOnFocus()
    }
    val answerEditor = WordTextField(initialState.answer.word).apply {
        duplicates = initialState.answer.duplicates
        putClientProperty(CARD_EDITOR, this@CardEditor)
        scrollOnFocus()
    }
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

private const val CARD_EDITOR = "CARD_EDITOR"

private fun WordTextField.focusAndScroll() {
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

private fun WordTextField.scrollOnFocus() {
    addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent) {
            when (e.cause) {
                FocusEvent.Cause.MOUSE_EVENT,
                FocusEvent.Cause.TRAVERSAL,
                FocusEvent.Cause.TRAVERSAL_UP,
                FocusEvent.Cause.TRAVERSAL_DOWN,
                FocusEvent.Cause.TRAVERSAL_FORWARD,
                FocusEvent.Cause.TRAVERSAL_BACKWARD -> {
                    doScroll()
                }
                FocusEvent.Cause.UNKNOWN,
                FocusEvent.Cause.ROLLBACK,
                FocusEvent.Cause.UNEXPECTED,
                FocusEvent.Cause.ACTIVATION,
                FocusEvent.Cause.CLEAR_GLOBAL_FOCUS_OWNER,
                null -> { }
            }
        }
    })
}

private fun WordTextField.doFocusAndScroll() {
    doFocus()
    doScroll()
}

private fun WordTextField.doFocus() {
    requestFocusInWindow()
}

private fun WordTextField.doScroll() {
    val parent = parent ?: return
    val editor = getClientProperty(CARD_EDITOR) as? CardEditor ?: return
    val x1 = min(editor.questionEditor.x, editor.answerEditor.x)
    val x2 = max(editor.questionEditor.x + editor.questionEditor.width, editor.answerEditor.x + editor.answerEditor.width)
    val y1 = editor.questionEditor.y
    val y2 = editor.answerEditor.y + editor.answerEditor.height
    val parentRect = Rectangle(x1, y1, x2 - x1, y2 - y1)
    // Can't just use scrollRectToVisible() because JTextField would only scroll itself then,
    // as it has its own scrolling to scroll the text horizontally.
    // But that's not what we want here, we want to scroll the whole editor vertically.
    val viewport = findParentOfType<JViewport>()
    if (viewport != null) {
        val viewportRect = SwingUtilities.convertRectangle(parent, parentRect, viewport)
        viewport.scrollRectToVisible(viewportRect)
    }
}

private fun JTextComponent.addKeyListener(vararg keyCodes: Int, condition: JTextComponent.(KeyEvent) -> Boolean, listener: (KeyEvent) -> Unit) {
    addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
            if (e.keyCode in keyCodes && condition(e)) {
                listener(e)
                e.consume()
            }
        }
    })
}

private val Int.isCtrlOrCmdDown: Boolean
    get() = (this and (KeyEvent.META_DOWN_MASK or KeyEvent.CTRL_DOWN_MASK)) != 0

private const val INSET_TOP = 2
private const val INSET_LEFT = 2
private const val INSET_BOTTOM = 2
private const val INSET_RIGHT = 2
private const val GAP_BETWEEN_QUESTION_AND_ANSWER = 2
private const val GAP_BETWEEN_CARDS = 10
private const val MIN_WIDTH = 100
