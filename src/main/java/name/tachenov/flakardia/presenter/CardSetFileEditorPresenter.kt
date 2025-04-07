package name.tachenov.flakardia.presenter

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.Library
import name.tachenov.flakardia.background
import name.tachenov.flakardia.data.Flashcard
import name.tachenov.flakardia.data.SaveError
import name.tachenov.flakardia.data.SaveSuccess
import name.tachenov.flakardia.data.Word
import name.tachenov.flakardia.underModelLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

interface CardSetFileEditorView : View

data class CardSetFileEditorState(
    val editorFullState: CardSetFileEditorFullState,
    val changeFromPrevious: CardSetFileEditorIncrementalStateUpdate,
    val persistenceState: CardSetFileEditorPersistenceState,
) : PresenterState

data class CardSetFileEditorFullState(val cards: List<CardPresenterState>)

sealed class CardSetFileEditorIncrementalStateUpdate

data object CardSetFileEditorFirstState : CardSetFileEditorIncrementalStateUpdate()
data object CardSetFileEditorNoChange : CardSetFileEditorIncrementalStateUpdate()

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

sealed class CardSetFileEditorPersistenceState
data object CardSetFileEditorEditedState : CardSetFileEditorPersistenceState()
data object CardSetFileEditorSavedState : CardSetFileEditorPersistenceState()
data class CardSetFileEditorSaveErrorState(val message: String) : CardSetFileEditorPersistenceState()

class CardSetFileEditorPresenter(
    private val library: Library,
    private val fileEntry: FlashcardSetFileEntry,
    private val initialContent: List<Flashcard>,
) : Presenter<CardSetFileEditorState, CardSetFileEditorView>() {
    private val cardId = AtomicInteger()

    override suspend fun computeInitialState(): CardSetFileEditorState = CardSetFileEditorState(
        editorFullState = CardSetFileEditorFullState(
            if (initialContent.isNotEmpty()) {
                initialContent.map { CardPresenterState(allocateId(), it.front.value, it.back.value) }
            }
            else {
                listOf(CardPresenterState(allocateId(), "", ""))
            }
        ),
        changeFromPrevious = CardSetFileEditorFirstState,
        persistenceState = CardSetFileEditorSavedState,
    ).also {
        launchSaveJob()
    }

    private fun launchSaveJob() {
        launchUiTask {
            state.collectLatest {
                delay(1L.seconds)
                updateState { state ->
                    saveFlashcards(state)
                }
            }
        }
    }

    override suspend fun saveLastState(state: CardSetFileEditorState) {
        saveFlashcards(state)
    }

    private suspend fun saveFlashcards(state: CardSetFileEditorState): CardSetFileEditorState? {
        if (state.persistenceState == CardSetFileEditorSavedState) return null
        val result = underModelLock {
            background {
                library.saveFlashcardSetFile(fileEntry, state.editorFullState.cards
                    .asSequence()
                    .filter { it.question.isNotBlank() && it.answer.isNotBlank() }
                    .map { Flashcard(Word(it.question), Word(it.answer)) }
                    .toList()
                )
            }
        }
        return state.copy(
            changeFromPrevious = CardSetFileEditorNoChange,
            persistenceState = when (result) {
                is SaveSuccess -> CardSetFileEditorSavedState
                is SaveError -> CardSetFileEditorSaveErrorState(result.message)
            },
        )
    }

    private fun allocateId(): CardId = CardId(cardId.incrementAndGet())

    fun updateQuestion(id: CardId, newQuestionText: String) {
        updateCard(id) { card -> card.copy(question = newQuestionText)}
    }

    fun updateAnswer(id: CardId, newAnswerText: String) {
        updateCard(id) { card -> card.copy(answer = newAnswerText)}
    }

    private fun updateCard(id: CardId, update: (CardPresenterState) -> CardPresenterState) {
        updateState { state ->
            val oldCardList = state.editorFullState.cards
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
                editorFullState = CardSetFileEditorFullState(updatedCardList),
                changeFromPrevious = CardChanged(
                    index = index,
                    updatedQuestion = if (oldCard.question == updatedCard.question) null else updatedCard.question,
                    updatedAnswer = if (oldCard.answer == updatedCard.answer) null else updatedCard.answer,
                ),
                persistenceState = CardSetFileEditorEditedState,
            )
        }
    }

    fun insertCardBefore(beforeId: CardId) {
        updateState { state ->
            val index = state.editorFullState.cards.indexOfFirst { it.id == beforeId }
            if (index == -1) return@updateState null
            insertCardAt(state, index)
        }
    }

    fun insertCardAfter(afterId: CardId) {
        updateState { state ->
            val index = state.editorFullState.cards.indexOfFirst { it.id == afterId }
            if (index == -1) return@updateState null
            insertCardAt(state, index + 1)
        }
    }

    private fun insertCardAt(state: CardSetFileEditorState, index: Int): CardSetFileEditorState {
        val newCard = CardPresenterState(allocateId(), "", "")
        val oldCardList = state.editorFullState.cards
        val updatedCardList = oldCardList.subList(0, index) + newCard + oldCardList.subList(index, oldCardList.size)
        return state.copy(
            editorFullState = CardSetFileEditorFullState(updatedCardList),
            changeFromPrevious = CardAdded(index, newCard),
            persistenceState = CardSetFileEditorEditedState,
        )
    }

    fun removeCard(id: CardId) {
        updateState { state ->
            val oldCardList = state.editorFullState.cards
            if (oldCardList.size == 1) return@updateState null // do not allow to remove the last card
            val index = oldCardList.indexOfFirst { it.id == id }
            if (index == -1) return@updateState null
            val newCardList = oldCardList - oldCardList[index]
            state.copy(
                editorFullState = CardSetFileEditorFullState(newCardList),
                changeFromPrevious = CardRemoved(index),
                persistenceState = CardSetFileEditorEditedState,
            )
        }
    }
}
