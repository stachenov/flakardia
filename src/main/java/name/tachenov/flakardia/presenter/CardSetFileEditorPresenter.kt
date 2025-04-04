package name.tachenov.flakardia.presenter

import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.Library
import name.tachenov.flakardia.data.Flashcard
import java.util.concurrent.atomic.AtomicInteger

interface CardSetFileEditorView : View

data class CardSetFileEditorState(
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

@JvmInline value class CardId(val value: Int)

data class CardPresenterState(
    val id: CardId,
    val question: String,
    val answer: String,
)

class CardSetFileEditorPresenter(
    private val library: Library,
    private val fileEntry: FlashcardSetFileEntry,
    private val initialContent: List<Flashcard>,
) : Presenter<CardSetFileEditorState, CardSetFileEditorView>() {
    private val cardId = AtomicInteger()

    override suspend fun computeInitialState(): CardSetFileEditorState = CardSetFileEditorState(
        fullState = CardSetFileEditorFullState(initialContent.map { CardPresenterState(allocateId(), it.front.value, it.back.value) }),
        changeFromPrevious = null,
    )

    private fun allocateId(): CardId = CardId(cardId.incrementAndGet())

    fun updateQuestion(id: CardId, newQuestionText: String) {
        updateCard(id) { card -> card.copy(question = newQuestionText)}
    }

    fun updateAnswer(id: CardId, newAnswerText: String) {
        updateCard(id) { card -> card.copy(answer = newAnswerText)}
    }

    private fun updateCard(id: CardId, update: (CardPresenterState) -> CardPresenterState) {
        updateState { state ->
            val oldCardList = state.fullState.cards
            val index = oldCardList.indexOfFirst { it.id == id }
            if (index == -1) return@updateState null
            val oldCard = oldCardList[index]
            val updatedCard = update(oldCard)
            val updatedCardList = oldCardList.withIndex().map {
                if (it.index == index) {
                    updatedCard
                }
                else {
                    it.value
                }
            }
            state.copy(
                fullState = CardSetFileEditorFullState(updatedCardList),
                changeFromPrevious = CardChanged(
                    index = index,
                    updatedQuestion = if (oldCard.question == updatedCard.question) null else updatedCard.question,
                    updatedAnswer = if (oldCard.answer == updatedCard.answer) null else updatedCard.answer,
                )
            )
        }
    }

    fun insertCardBefore(beforeId: CardId) {
        updateState { state ->
            val index = state.fullState.cards.indexOfFirst { it.id == beforeId }
            if (index == -1) return@updateState null
            insertCardAt(state, index)
        }
    }

    fun insertCardAfter(afterId: CardId) {
        updateState { state ->
            val index = state.fullState.cards.indexOfFirst { it.id == afterId }
            if (index == -1) return@updateState null
            insertCardAt(state, index + 1)
        }
    }

    private fun insertCardAt(state: CardSetFileEditorState, index: Int): CardSetFileEditorState {
        val newCard = CardPresenterState(allocateId(), "", "")
        val oldCardList = state.fullState.cards
        val updatedCardList = oldCardList.subList(0, index) + newCard + oldCardList.subList(index, oldCardList.size)
        return state.copy(
            fullState = CardSetFileEditorFullState(updatedCardList),
            changeFromPrevious = CardAdded(index, newCard),
        )
    }
}
