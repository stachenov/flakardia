package name.tachenov.flakardia.presenter

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import name.tachenov.flakardia.accessModel
import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.data.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

interface CardSetFileEditorView : View

data class CardSetFileEditorState(
    val editorFullState: CardSetFileEditorFullState,
    val changeFromPrevious: CardSetFileEditorIncrementalStateUpdate,
    val persistenceState: CardSetFileEditorPersistenceState,
    val duplicateDetectionState: DuplicateDetectionState,
) : PresenterState

data class DuplicateDetectionState(
    val availablePaths: List<DuplicateDetectionPath>,
    val selectedPath: DuplicateDetectionPath,
)

interface DuplicateDetectionPath {
    val path: FullPath
    val dirEntry: FlashcardSetDirEntry?
}

data class CardSetFileEditorFullState(val cards: List<CardPresenterState>)

sealed class CardSetFileEditorIncrementalStateUpdate

data object CardSetFileEditorFirstState : CardSetFileEditorIncrementalStateUpdate()
data object CardSetFileEditorNoChange : CardSetFileEditorIncrementalStateUpdate()

class CardAdded(
    val index: Int,
    val card: CardPresenterState,
) : CardSetFileEditorIncrementalStateUpdate()

data class CardsChanged(
    val changes: List<CardChanged>
) : CardSetFileEditorIncrementalStateUpdate() {
    init {
        require(changes.isNotEmpty())
    }
}

data class CardChanged(
    val index: Int,
    val updatedQuestion: WordPresenterState? = null,
    val updatedAnswer: WordPresenterState? = null,
)

data class CardRemoved(
    val index: Int,
) : CardSetFileEditorIncrementalStateUpdate()

data class CardPresenterState(
    val id: FlashcardDraftId,
    val question: WordPresenterState,
    val answer: WordPresenterState,
    private val initialQuestion: String,
    private val initialAnswer: String,
) {

    constructor(id: FlashcardDraftId) : this(
        id = id,
        question = WordPresenterState("", emptyList()),
        answer = WordPresenterState("", emptyList()),
        initialQuestion = "",
        initialAnswer = ""
    )

    constructor(id: FlashcardDraftId, question: WordPresenterState, answer: WordPresenterState) : this(
        id = id,
        question = question,
        answer = answer,
        initialQuestion = question.word,
        initialAnswer = answer.word,
    )

    fun isValid(): Boolean = question.word.isNotBlank() && answer.word.isNotBlank()

    fun toValidFlashcard(): Flashcard {
        check(isValid())
        return Flashcard(Word(question.word), Word(answer.word))
    }

    fun getInitialFlashcardOrNull(): Flashcard? =
        if (initialQuestion.isNotBlank() && initialAnswer.isNotBlank()) {
            Flashcard(Word(initialQuestion), Word(initialAnswer))
        }
        else {
            null
        }
}

data class WordPresenterState(
    val word: String,
    val duplicates: List<Duplicate>,
)

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
    private val duplicateDetector = DuplicateDetector(library, fileEntry)

    val name: String get() = fileEntry.name

    override suspend fun computeInitialState(): CardSetFileEditorState = CardSetFileEditorState(
        editorFullState = CardSetFileEditorFullState(
            if (initialContent.isNotEmpty()) {
                accessModel {
                    val drafts = initialContent.map {
                        FlashcardDraft(allocateId(), fileEntry.path, it.question.value, it.answer.value)
                    }
                    for (draft in drafts) {
                        duplicateDetector.addCard(draft)
                    }
                    drafts.map { draft ->
                        CardPresenterState(
                            id = draft.id,
                            question = WordPresenterState(
                                draft.question,
                                duplicateDetector.getQuestionDuplicates(draft),
                            ),
                            answer = WordPresenterState(
                                draft.answer,
                                duplicateDetector.getAnswerDuplicates(draft),
                            ),
                        )
                    }
                }
            }
            else {
                listOf(CardPresenterState(allocateId()))
            }
        ),
        changeFromPrevious = CardSetFileEditorFirstState,
        persistenceState = CardSetFileEditorSavedState(warnings = emptyList()),
        duplicateDetectionState = computeInitialDuplicateDetectionState(),
    ).also {
        launchSaveJob()
    }

    private fun computeInitialDuplicateDetectionState(): DuplicateDetectionState {
        val allPaths = mutableListOf<DuplicateDetectionPath>()
        val editedPath = library.fullPath(fileEntry.path)
        var path: FullPath? = editedPath
        while (path != null) {
            allPaths += DuplicateDetectionPathImpl(path, editedPath.relativePath)
            path = path.parent
        }
        return DuplicateDetectionState(
            availablePaths = allPaths.reversed(),
            selectedPath = allPaths.first(),
        )
    }

    private data class DuplicateDetectionPathImpl(val fullPath: FullPath, val editedPath: RelativePath) : DuplicateDetectionPath {
        override val path: FullPath
            get() = fullPath
        override val dirEntry: FlashcardSetDirEntry?
            get() = if (fullPath.relativePath == editedPath) null else FlashcardSetDirEntry(fullPath.relativePath)
        override fun toString(): String = fullPath.toString()
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
        val result = accessModel {
            val validCards = state.editorFullState.cards
                .filter { it.isValid() }
            library.saveFlashcardSetFile(fileEntry, validCards.map {
                UpdatedOrNewFlashcard(
                    oldCard = if (updateStats) it.getInitialFlashcardOrNull() else null,
                    newCard = it.toValidFlashcard(),
                )
            })
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

    private fun allocateId(): FlashcardDraftId = FlashcardDraftId(cardId.incrementAndGet())

    fun detectDuplicatesIn(path: FlashcardSetDirEntry?) {
        updateState { state ->
            val oldCardList = state.editorFullState.cards
            val updateBuilder = UpdateBuilder()
            accessModel {
                duplicateDetector.area = path
                for ((index, oldCardPresenter) in oldCardList.withIndex()) {
                    updateBuilder.addCardWithPossiblyUpdatedDuplicates(index, oldCardPresenter)
                }
            }
            val cardsChanged = updateBuilder.cardsChanged.isNotEmpty()
            val newDuplicateDetectionState = DuplicateDetectionState(
                availablePaths = state.duplicateDetectionState.availablePaths,
                selectedPath = DuplicateDetectionPathImpl(
                    fullPath = library.fullPath(path?.path ?: fileEntry.path),
                    editedPath = fileEntry.path,
                )
            )
            when {
                cardsChanged -> {
                    state.copy(
                        editorFullState = CardSetFileEditorFullState(updateBuilder.updatedCardList),
                        changeFromPrevious = CardsChanged(updateBuilder.cardsChanged),
                        duplicateDetectionState = newDuplicateDetectionState,
                    )
                }
                newDuplicateDetectionState != state.duplicateDetectionState -> { // another dir was chosen, but didn't affect the presentation
                    state.copy(
                        duplicateDetectionState = newDuplicateDetectionState,
                    )
                }
                else -> null
            }
        }
    }

    fun updateQuestion(id: FlashcardDraftId, newQuestionText: String) {
        updateCard(id) { card -> card.copy(question = newQuestionText)}
    }

    fun updateAnswer(id: FlashcardDraftId, newAnswerText: String) {
        updateCard(id) { card -> card.copy(answer = newAnswerText)}
    }

    private fun updateCard(id: FlashcardDraftId, update: (FlashcardDraft) -> FlashcardDraft) {
        updateState { state ->
            val oldCardList = state.editorFullState.cards
            val index = oldCardList.indexOfFirst { it.id == id }
            if (index == -1) return@updateState null

            val oldPresenter = oldCardList[index]
            val updateBuilder = UpdateBuilder()
            accessModel {
                val oldCard = FlashcardDraft(oldPresenter.id, fileEntry.path, oldPresenter.question.word, oldPresenter.answer.word)
                duplicateDetector.removeCard(oldCard)
                val updatedCard = update(oldCard)
                duplicateDetector.addCard(updatedCard)

                for ((i, value) in oldCardList.withIndex()) {
                    if (i == index) {
                        updateBuilder.addUpdatedCard(i, oldPresenter, updatedCard)
                    }
                    else {
                        updateBuilder.addCardWithPossiblyUpdatedDuplicates(i, value)
                    }
                }
            }
            state.copy(
                editorFullState = CardSetFileEditorFullState(updateBuilder.updatedCardList),
                changeFromPrevious = CardsChanged(updateBuilder.cardsChanged),
                persistenceState = CardSetFileEditorEditedState,
            )
        }
    }

    private inner class UpdateBuilder {
        val updatedCardList = mutableListOf<CardPresenterState>()
        val cardsChanged = mutableListOf<CardChanged>()

        fun addUpdatedCard(index: Int, oldPresenter: CardPresenterState, updatedCard: FlashcardDraft) {
            val updatedPresenter = oldPresenter.copy(
                question = WordPresenterState(updatedCard.question, duplicateDetector.getQuestionDuplicates(updatedCard)),
                answer = WordPresenterState(updatedCard.answer, duplicateDetector.getAnswerDuplicates(updatedCard)),
            )
            updatedCardList.add(updatedPresenter)
            cardsChanged.add(CardChanged(
                index = index,
                updatedQuestion = if (oldPresenter.question == updatedPresenter.question) null else updatedPresenter.question,
                updatedAnswer = if (oldPresenter.answer == updatedPresenter.answer) null else updatedPresenter.answer,
            ))
        }

        fun addCardWithPossiblyUpdatedDuplicates(index: Int, oldPresenter: CardPresenterState) {
            val card = FlashcardDraft(oldPresenter.id, fileEntry.path, oldPresenter.question.word, oldPresenter.answer.word)
            val questionDuplicates = duplicateDetector.getQuestionDuplicates(card)
            val answerDuplicates = duplicateDetector.getAnswerDuplicates(card)
            val newPresenter = CardPresenterState(
                id = oldPresenter.id,
                question = WordPresenterState(card.question, questionDuplicates),
                answer = WordPresenterState(card.answer, answerDuplicates),
            )
            updatedCardList.add(newPresenter)
            if (newPresenter != oldPresenter) {
                cardsChanged.add(CardChanged(
                    index = index,
                    updatedQuestion = if (oldPresenter.question == newPresenter.question) null else newPresenter.question,
                    updatedAnswer = if (oldPresenter.answer == newPresenter.answer) null else newPresenter.answer,
                ))
            }
        }

    }

    fun insertCardBefore(beforeId: FlashcardDraftId) {
        updateState { state ->
            val index = state.editorFullState.cards.indexOfFirst { it.id == beforeId }
            if (index == -1) return@updateState null
            insertCardAt(state, index)
        }
    }

    fun insertCardAfter(afterId: FlashcardDraftId) {
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

    fun removeCard(id: FlashcardDraftId) {
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
