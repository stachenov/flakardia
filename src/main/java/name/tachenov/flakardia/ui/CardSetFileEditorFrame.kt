package name.tachenov.flakardia.ui

import name.tachenov.flakardia.presenter.*
import java.awt.BorderLayout
import javax.swing.JTextArea

class CardSetFileEditorFrame(
    presenter: CardSetFileEditorPresenter
) : FrameView<CardSetFileEditorStateUpdate, CardSetFileEditorView, CardSetFileEditorPresenter>(presenter), CardSetFileEditorView
{

    private val textArea = JTextArea()

    init {
        layout = BorderLayout()
        add(textArea, BorderLayout.CENTER)
    }

    override fun restoreSavedViewState() { }

    override fun saveViewState() { }

    override fun applyPresenterState(state: CardSetFileEditorStateUpdate) {
        textArea.text = when (state) {
            is CardSetFileEditorFullState -> state.cards.joinToString("\n") {
                "${it.question} ${it.answer}"
            }
            is CardSetFileEditorErrorState -> state.message
            else -> "Not implemented yet"
        }
    }

}
