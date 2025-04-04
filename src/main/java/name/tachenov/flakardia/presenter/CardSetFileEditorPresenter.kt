package name.tachenov.flakardia.presenter

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.Library
import name.tachenov.flakardia.data.Flashcard
import java.util.concurrent.atomic.AtomicLong

interface CardSetFileEditorView : View

data class CardSetFileEditorState(
    val epoch: Long,
    val fullState: CardSetFileEditorFullState,
    val changeFromPrevious: CardSetFileEditorIncrementalStateUpdate?,
) : PresenterState

data class CardSetFileEditorFullState(val cards: List<CardPresenterState>)

sealed class CardSetFileEditorIncrementalStateUpdate

class CardAdded(
    val index: Int,
    val card: CardPresenterState,
) : CardSetFileEditorIncrementalStateUpdate()

data class CardChanged(
    val index: Int,
    val updatedQuestion: String? = null,
    val updatedAnswer: String? = null,
) : CardSetFileEditorIncrementalStateUpdate()

data class CardRemoved(
    val index: Int,
) : CardSetFileEditorIncrementalStateUpdate()

interface CardPresenterState {
    val presenter: CardPresenter
    val question: String
    val answer: String
}

sealed interface CardPresenter {
    fun updateQuestion(newQuestionText: String)
    fun updateAnswer(newAnswerText: String)
}

private data class CardPresenterStateImpl(
    override val presenter: CardPresenter,
    override val question: String,
    override val answer: String,
) : CardPresenterState

class CardSetFileEditorPresenter(
    private val library: Library,
    private val fileEntry: FlashcardSetFileEntry,
    initialContent: List<Flashcard>,
) : Presenter<CardSetFileEditorState, CardSetFileEditorView>() {
    private val epoch = AtomicLong()
    private val stateUpdatesFlow = MutableSharedFlow<CardSetFileEditorState>(replay = 1)
    private val stateUpdatesChannel = Channel<CardSetFileEditorState>(Channel.UNLIMITED)
    private val cardPresenters = initialContent.map { CardPresenterImpl(it) }.toMutableList()
    private var mutableState = CardSetFileEditorFullState(cardPresenters.map { it.state })

    override val state: Flow<CardSetFileEditorState>
        get() = stateUpdatesFlow.asSharedFlow()

    override suspend fun runStateUpdates() {
        check(stateUpdatesChannel.trySend(CardSetFileEditorState(
            epoch = nextEpoch(),
            fullState = mutableState,
            changeFromPrevious = null,
        )).isSuccess)
        for (stateUpdate in stateUpdatesChannel) {
            stateUpdatesFlow.emit(stateUpdate)
        }
    }

    private fun nextEpoch(): Long = epoch.incrementAndGet()

    private inner class CardPresenterImpl(card: Flashcard) : CardPresenter {
        private var question: String = card.front.value
        private var answer: String = card.back.value

        val state: CardPresenterState get() = CardPresenterStateImpl(this, question, answer)

        override fun updateQuestion(newQuestionText: String) {
            updatePresenter(newQuestionText, null)
        }

        override fun updateAnswer(newAnswerText: String) {
            updatePresenter(null, newAnswerText)
        }

        private fun updatePresenter(newQuestionText: String?, newAnswerText: String?) {
            val index = cardPresenters.indexOf(this)
            if (index == -1) return
            if (newQuestionText != null) {
                this.question = newQuestionText
            }
            if (newAnswerText != null) {
                this.answer = newAnswerText
            }
            val updatedPresenterStates = mutableState.cards.withIndex().map {
                if (it.index == index) {
                    state
                }
                else {
                    it.value
                }
            }
            check(stateUpdatesChannel.trySend(
                CardSetFileEditorState(
                    epoch = nextEpoch(),
                    fullState = mutableState.copy(cards = updatedPresenterStates),
                    changeFromPrevious = CardChanged(index, updatedQuestion = newQuestionText, updatedAnswer = newAnswerText),
                )
            ).isSuccess)
        }

        override fun toString(): String = "CardPresenterImpl(question='$question', answer='$answer')"
    }
}
