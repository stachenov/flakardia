package name.tachenov.flakardia.presenter

import name.tachenov.flakardia.accessModel
import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.cardSetFileEditorConfig
import name.tachenov.flakardia.data.*

interface LessonPresenterView : View

sealed class LessonStatusState
data class QuestionState(val nextQuestion: Question) : LessonStatusState()
data class AnswerState(val answerResult: AnswerResultPresenter) : LessonStatusState()
data class EditWordState(val previousState: AnswerState, val word: EditWordPresenter) : LessonStatusState()
data object DoneState : LessonStatusState()

data class AnswerResultPresenter(
    val path: RelativePath,
    val question: String,
    val yourAnswer: String?,
    val correctAnswer: String,
    val isCorrect: Boolean,
)

data class EditWordPresenter(
    val path: RelativePath,
    val initialQuestion: String,
    val question: String,
    val questionDuplicates: List<Duplicate>,
    val initialAnswer: String,
    val answer: String,
    val answerDuplicates: List<Duplicate>,
)

data class LessonPresenterState(
    val title: String,
    val lessonResult: LessonResult,
    val lessonStatus: LessonStatusState,
) : PresenterState

class LessonPresenter(
    private val library: Library,
    private val lesson: Lesson,
) : Presenter<LessonPresenterState, LessonPresenterView>() {

    private var duplicateDetector: DuplicateDetector? = null

    override suspend fun computeInitialState(): LessonPresenterState  = accessModel {
        val nextQuestion = lesson.nextQuestion()
        LessonPresenterState(
            title = lesson.name,
            lessonResult = lesson.result,
            lessonStatus = nextQuestion?.let { question -> QuestionState(question) } ?: DoneState,
        )
    }

    fun nextQuestion() {
        updateState { state ->
            accessModel {
                val nextQuestion = lesson.nextQuestion()
                state.copy(
                    lessonResult = lesson.result,
                    lessonStatus = nextQuestion?.let { question -> QuestionState(question) } ?: DoneState,
                )
            }
        }
    }

    fun answered(answer: Answer?) {
        updateState { state ->
            val (newState, saveResult) = accessModel {
                val answerResult = lesson.answer(answer).let {
                    AnswerResultPresenter(
                        path = it.flashcardSetPath,
                        question = it.question.value,
                        yourAnswer = it.yourAnswer?.word?.value,
                        correctAnswer = it.correctAnswer.word.value,
                        isCorrect = it.isCorrect,
                    )
                }
                val newState = state.copy(
                    lessonResult = lesson.result,
                    lessonStatus = AnswerState(answerResult),
                )
                val saveResult = library.saveUpdatedStats(lesson.stats)
                newState to saveResult
            }
            when (saveResult) {
                is SaveError -> view.showError("An error occurred when trying to save word statistics", saveResult.message)
                is SaveWarnings -> view.showWarnings(saveResult.warnings)
                is SaveSuccess -> {}
            }
            newState
        }
    }

    fun editCurrentWord() {
        updateState { state ->
            val lessonState = state.lessonStatus
            if (lessonState !is AnswerState) return@updateState null
            accessModel {
                var id = DRAFT_ID // this ID goes to the card we edit
                val draft = FlashcardDraft(
                    id = id,
                    path = lessonState.answerResult.path,
                    question = lessonState.answerResult.question,
                    answer = lessonState.answerResult.correctAnswer,
                )
                val fileEntry = FlashcardSetFileEntry(draft.path)
                val duplicateDetector = DuplicateDetector(library, fileEntry)
                duplicateDetector.area = cardSetFileEditorConfig().duplicateDetectionPath
                duplicateDetector.addCard(draft)
                when (val otherWords = library.readFlashcards(fileEntry)) {
                    is FlashcardSet -> {
                        for (card in otherWords.cards) {
                            if (card.flashcard != Flashcard(Word(draft.question), Word(draft.answer))) {
                                id = FlashcardDraftId(id.value + 1) // other cards get IDs 1, 2, 3...
                                duplicateDetector.addCard(FlashcardDraft(
                                    id = id,
                                    path = fileEntry.path,
                                    question = card.flashcard.question.value,
                                    answer = card.flashcard.answer.value,
                                ))
                            }
                        }
                    }
                    is FlashcardSetError -> { }
                }
                this.duplicateDetector = duplicateDetector
                state.copy(
                    lessonStatus = EditWordState(
                        previousState = lessonState,
                        EditWordPresenter(
                            path = draft.path,
                            initialQuestion = draft.question,
                            question = draft.question,
                            questionDuplicates = duplicateDetector.getQuestionDuplicates(draft),
                            initialAnswer = draft.answer,
                            answer = draft.answer,
                            answerDuplicates = duplicateDetector.getAnswerDuplicates(draft),
                        )
                    )
                )
            }
        }
    }

    fun refreshQuestionDuplicates(newQuestion: String) {
        updateCard { card -> card.copy(question = newQuestion) }
    }

    fun refreshAnswerDuplicates(newAnswer: String) {
        updateCard { card -> card.copy(answer = newAnswer) }
    }

    private fun updateCard(update: (FlashcardDraft) -> FlashcardDraft) {
        updateState { state ->
            val lessonState = state.lessonStatus
            if (lessonState !is EditWordState) return@updateState null // Most likely already finished editing.
            accessModel {
                val oldCard = FlashcardDraft(DRAFT_ID, lessonState.word.path, lessonState.word.question, lessonState.word.answer)
                val duplicateDetector = checkNotNull(duplicateDetector)
                duplicateDetector.removeCard(oldCard)
                val newCard = update(oldCard)
                duplicateDetector.addCard(newCard)
                state.copy(
                    lessonStatus = lessonState.copy(
                        word = lessonState.word.copy(
                            question = newCard.question,
                            questionDuplicates = duplicateDetector.getQuestionDuplicates(newCard),
                            answer = newCard.answer,
                            answerDuplicates = duplicateDetector.getAnswerDuplicates(newCard),
                        )
                    )
                )
            }
        }
    }

    fun saveWord(newQuestion: String, newAnswer: String) {
        updateState { state ->
            val lessonState = state.lessonStatus
            if (lessonState !is EditWordState) return@updateState null
            if (newQuestion.isBlank() || newAnswer.isBlank()) {
                view.showError("Save error", "Both the question and the answer must be specified")
                return@updateState null
            }
            val saveResult = accessModel {
                library.saveUpdatedFlashcard(
                    fileEntry = FlashcardSetFileEntry(lessonState.word.path),
                    card = UpdatedFlashcard(
                        oldCard = Flashcard(Word(lessonState.word.initialQuestion), Word(lessonState.word.initialAnswer)),
                        newCard = Flashcard(Word(newQuestion), Word(newAnswer)),
                    )
                )
            }
            when (saveResult) {
                is SaveError -> {
                    view.showError("Save error", saveResult.message)
                    return@updateState null
                }
                is SaveWarnings -> {
                    view.showWarnings(saveResult.warnings)
                }
                SaveSuccess -> { }
            }
            state.copy(lessonStatus = lessonState.previousState.copy(
                answerResult = lessonState.previousState.answerResult.copy(
                    question = newQuestion,
                    correctAnswer = newAnswer,
                )
            ))
        }
    }

    fun cancelEditing() {
        updateState { state ->
            val lessonState = state.lessonStatus
            if (lessonState !is EditWordState) return@updateState null
            state.copy(lessonStatus = lessonState.previousState)
        }
    }
}

// We have only one draft in this editor, so the ID is always the same.
private val DRAFT_ID = FlashcardDraftId(0)
