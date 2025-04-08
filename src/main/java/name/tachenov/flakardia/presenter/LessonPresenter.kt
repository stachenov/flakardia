package name.tachenov.flakardia.presenter

import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.background
import name.tachenov.flakardia.data.*
import name.tachenov.flakardia.underModelLock

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
    val question: String,
    val answer: String,
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

    override suspend fun computeInitialState(): LessonPresenterState  = underModelLock {
        background {
            val nextQuestion = lesson.nextQuestion()
            LessonPresenterState(
                title = lesson.name,
                lessonResult = lesson.result,
                lessonStatus = nextQuestion?.let { question -> QuestionState(question) } ?: DoneState,
            )
        }
    }

    fun nextQuestion() {
        updateState { state ->
            underModelLock {
                background {
                    val nextQuestion = lesson.nextQuestion()
                    state.copy(
                        lessonResult = lesson.result,
                        lessonStatus = nextQuestion?.let { question -> QuestionState(question) } ?: DoneState,
                    )
                }
            }
        }
    }

    fun answered(answer: Answer?) {
        updateState { state ->
            val (newState, saveResult) = underModelLock {
                background {
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
            }
            when (saveResult) {
                is SaveError -> view.showError("An error occurred when trying to save word statistics", saveResult.message)
                is SaveWarning -> view.showWarnings(listOf(saveResult.warning))
                is SaveSuccess -> {}
            }
            newState
        }
    }

    fun editCurrentWord() {
        updateState { state ->
            val lessonState = state.lessonStatus
            if (lessonState !is AnswerState) return@updateState null
            state.copy(lessonStatus = EditWordState(
                previousState = lessonState,
                EditWordPresenter(
                    path = lessonState.answerResult.path,
                    question = lessonState.answerResult.question,
                    answer = lessonState.answerResult.correctAnswer,
                )
            ))
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
            val saveResult = underModelLock {
                background {
                    library.saveUpdatedFlashcard(
                        fileEntry = FlashcardSetFileEntry(lessonState.word.path),
                        oldValue = Flashcard(Word(lessonState.word.question), Word(lessonState.word.answer)),
                        newValue = Flashcard(Word(newQuestion), Word(newAnswer)),
                    )
                }
            }
            when (saveResult) {
                is SaveError -> {
                    view.showError("Save error", saveResult.message)
                    return@updateState null
                }
                is SaveWarning -> {
                    view.showWarnings(listOf(saveResult.warning))
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
