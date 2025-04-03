package name.tachenov.flakardia.ui

import name.tachenov.flakardia.presenter.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import kotlin.math.max

class CardSetFileEditorFrame(
    presenter: CardSetFileEditorPresenter
) : FrameView<CardSetFileEditorStateUpdate, CardSetFileEditorView, CardSetFileEditorPresenter>(presenter), CardSetFileEditorView
{

    private val editor = CardSetEditor()
    private val scrollPane = JScrollPane(editor)

    init {
        layout = BorderLayout()
        add(scrollPane, BorderLayout.CENTER)
    }

    override fun restoreSavedViewState() { }

    override fun saveViewState() { }

    override fun applyPresenterState(state: CardSetFileEditorStateUpdate) {
        when (state) {
            is CardSetFileEditorFullState -> {
                editor.cards = state.cards
            }
            is CardSetFileEditorErrorState -> {

            }
            else -> {

            }
        }
    }

}

class CardSetEditor : JPanel() {
    private val editorComponents = mutableListOf<CardEditor>()

    private class CardEditor(val card: CardPresenter, val questionEditor: JTextField, val answerEditor: JTextField) {
        override fun toString(): String = "($questionEditor, $answerEditor)"
    }

    var cards: List<CardPresenter>
        get() = editorComponents.map { it.card }
        set(value) {
            for (editorComponent in editorComponents) {
                remove(editorComponent.questionEditor)
                remove(editorComponent.answerEditor)
            }
            editorComponents.clear()
            for (card in value) {
                insertCardWithoutValidation(editorComponents.size, card)
            }
            revalidate()
            repaint()
        }

    private fun insertCardWithoutValidation(index: Int, card: CardPresenter) {
        val questionEditor = JTextField(card.question)
        val answerEditor = JTextField(card.answer)
        editorComponents.add(index, CardEditor(card, questionEditor, answerEditor))
        add(questionEditor, index * 2)
        add(answerEditor, index * 2 + 1)
    }

    override fun getMinimumSize(): Dimension = computeSize { it.minimumSize }

    override fun getPreferredSize(): Dimension = computeSize { it.preferredSize }

    override fun getMaximumSize(): Dimension = computeSize { it.maximumSize }

    private fun computeSize(sizeFunction: (JComponent) -> Dimension): Dimension {
        var width = 100
        var height = 0
        for (editorComponent in editorComponents) {
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
        for (editorComponent in editorComponents) {
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

private const val GAP_BETWEEN_CARDS = 5
