package name.tachenov.flakardia.ui

import name.tachenov.flakardia.presenter.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Document
import javax.swing.text.JTextComponent
import kotlin.math.max

class CardSetFileEditorFrame(
    presenter: CardSetFileEditorPresenter
) : FrameView<CardSetFileEditorState, CardSetFileEditorView, CardSetFileEditorPresenter>(presenter), CardSetFileEditorView
{
    private val editor = CardSetEditor(presenter)
    private val scrollPane = JScrollPane(editor)

    init {
        layout = BorderLayout()
        add(scrollPane, BorderLayout.CENTER)
    }

    override fun restoreSavedViewState() { }

    override fun saveViewState() { }

    override fun applyPresenterState(state: CardSetFileEditorState) {
        editor.updateState(state)
    }
}

class CardSetEditor(private val presenter: CardSetFileEditorPresenter) : JPanel() {
    private val editors = mutableListOf<CardEditor>()

    fun updateState(update: CardSetFileEditorState) {
        var updated = true
        when (val change = update.changeFromPrevious) {
            null -> {
                for (editorComponent in editors) {
                    remove(editorComponent.questionEditor)
                    remove(editorComponent.answerEditor)
                }
                editors.clear()
                for (card in update.fullState.cards) {
                    insertCardEditor(editors.size, card)
                }
            }
            is CardAdded -> {
                insertCardEditor(change.index, change.card)
                editors[change.index].questionEditor.requestFocusInWindow()
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
    }

    private fun removeCardEditor(index: Int) {
        val removed = editors.removeAt(index)
        remove(removed.questionEditor)
        remove(removed.answerEditor)
    }

    override fun getMinimumSize(): Dimension = computeSize { it.minimumSize }

    override fun getPreferredSize(): Dimension = computeSize { it.preferredSize }

    override fun getMaximumSize(): Dimension = computeSize { it.maximumSize }

    private fun computeSize(sizeFunction: (JComponent) -> Dimension): Dimension {
        var width = 100
        var height = 0
        for (editorComponent in editors) {
            val size1 = sizeFunction(editorComponent.questionEditor)
            val size2 = sizeFunction(editorComponent.answerEditor)
            width = max(width, max(size1.width, size2.width))
            height += size1.height
            height += size2.height
            height += GAP_BETWEEN_CARDS
        }
        return Dimension(width, height)
    }

    override fun doLayout() {
        val width = this.width
        val x = 0
        var y = 0
        for (editorComponent in editors) {
            val h1 = editorComponent.questionEditor.preferredSize.height
            editorComponent.questionEditor.bounds = Rectangle(x, y, width, h1)
            y += h1
            val h2 = editorComponent.answerEditor.preferredSize.height
            editorComponent.answerEditor.bounds = Rectangle(x, y, width, h2)
            y += h2
            y += GAP_BETWEEN_CARDS
        }
    }
}

private class CardEditor(
    private val presenter: CardSetFileEditorPresenter,
    initialState: CardPresenterState,
) {
    val id = initialState.id
    val questionEditor = JTextField(initialState.question)
    val answerEditor = JTextField(initialState.answer)

    init {
        questionEditor.document.addDocumentChangeListener {
            presenter.updateQuestion(id, questionEditor.text)
        }
        questionEditor.addKeyListener(KeyEvent.VK_ENTER) {
            if (questionEditor.caretPosition == 0) {
                presenter.insertCardBefore(id)
            }
        }
        answerEditor.document.addDocumentChangeListener {
            presenter.updateAnswer(id, answerEditor.text)
        }
        answerEditor.addKeyListener(KeyEvent.VK_ENTER) {
            if (answerEditor.caretPosition == answerEditor.text.length) {
                presenter.insertCardAfter(id)
            }
        }
    }

    override fun toString(): String = "($questionEditor, $answerEditor)"
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

private fun JTextComponent.addKeyListener(keyCode: Int, listener: () -> Unit) {
    addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == keyCode) {
                e.consume()
                listener()
            }
        }
    })
}

private const val GAP_BETWEEN_CARDS = 5
