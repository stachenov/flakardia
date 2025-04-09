package name.tachenov.flakardia.presenter

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.Library
import name.tachenov.flakardia.background
import name.tachenov.flakardia.data.*
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
    private val initialQuestion: String,
    private val initialAnswer: String,
) {

    constructor(id: CardId) : this(id, "", "", "", "")

    constructor(id: CardId, question: String, answer: String) : this(id, question, answer, question, answer)

    fun isValid(): Boolean = question.isNotBlank() && answer.isNotBlank()

    fun toValidFlashcard(): Flashcard {
        check(isValid())
        return Flashcard(Word(question), Word(answer))
    }

    fun getInitialFlashcardOrNull(): Flashcard? =
        if (initialQuestion.isNotBlank() && initialAnswer.isNotBlank()) {
            Flashcard(Word(initialQuestion), Word(initialAnswer))
        }
        else {
            null
        }
}

sealed class CardSetFileEditorPersistenceState
data object CardSetFileEditorEditedState : CardSetFileEditorPersistenceState()
data class CardSetFileEditorSavedState(val warnings: List<String>) : CardSetFileEditorPersistenceState()
data class CardSetFileEditorSaveErrorState(val message: String) : CardSetFileEditorPersistenceState()

class CardSetFileEditorPresenter(
    private val library: Library,
    private val fileEntry: FlashcardSetFileEntry,
    private val initialContent: List<Flashcard>,
) : Presenter<CardSetFileEditorState, CardSetFileEditorView>() {
    private val cardId = AtomicInteger()

    val name: String get() = fileEntry.name

    override suspend fun computeInitialState(): CardSetFileEditorState = CardSetFileEditorState(
        editorFullState = CardSetFileEditorFullState(
            if (initialContent.isNotEmpty()) {
                initialContent.map { CardPresenterState(allocateId(), it.question.value, it.answer.value) }
            }
            else {
                listOf(CardPresenterState(allocateId()))
            }
        ),
        changeFromPrevious = CardSetFileEditorFirstState,
        persistenceState = CardSetFileEditorSavedState(warnings = emptyList()),
    ).also {
        launchSaveJob()
    }

    private fun launchSaveJob() {
        launchUiTask {
            state.collectLatest {
                delay(1L.seconds)
                updateState { state ->
                    saveFlashcards(state, updateStats = false)
                }
            }
        }
    }

    override suspend fun saveLastState(state: CardSetFileEditorState) {
        // Only update the stats file when the editing is finished.
        // Otherwise, it may lead to very undesirable data losses.
        // For example, one has the word "super" somewhere, with accumulated stats from the previous lessons.
        // Then they add a word "supermarket," with no stats so far, but they make a mistake when editing,
        // entering "superarket."
        // Then they erase the end and at some point they have "super" in the editor.
        // And then they change it to "supermarket".
        // This last change will then seem to the editor as renaming "super" to "supermarket," which it isn't.
        // As a result, the stats for the word "super" will be lost and the stats for the word "supermarket" will be wrong.
        // Therefore, updating stats halfway through the editing is dangerous.
        // If we only do it when the editor is closed, the likelihood of something like that is much smaller.
        saveFlashcards(state, updateStats = true)
    }

    private suspend fun saveFlashcards(state: CardSetFileEditorState, updateStats: Boolean): CardSetFileEditorState? {
        val alreadySavedWithoutWarnings = when (state.persistenceState) {
            is CardSetFileEditorEditedState -> false
            is CardSetFileEditorSaveErrorState -> false
            is CardSetFileEditorSavedState -> {
                state.persistenceState.warnings.isEmpty()
            }
        }
        if (alreadySavedWithoutWarnings && !updateStats) return null
        val result = underModelLock {
            background {
                val validCards = state.editorFullState.cards
                    .filter { it.isValid() }
                library.saveFlashcardSetFile(fileEntry, validCards.map {
                    UpdatedOrNewFlashcard(
                        oldCard = if (updateStats) it.getInitialFlashcardOrNull() else null,
                        newCard = it.toValidFlashcard(),
                    )
                })
            }
        }
        return state.copy(
            changeFromPrevious = CardSetFileEditorNoChange,
            persistenceState = when (result) {
                is SaveSuccess -> CardSetFileEditorSavedState(warnings = emptyList())
                is SaveWarnings -> CardSetFileEditorSavedState(warnings = result.warnings)
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
        val newCard = CardPresenterState(allocateId())
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
