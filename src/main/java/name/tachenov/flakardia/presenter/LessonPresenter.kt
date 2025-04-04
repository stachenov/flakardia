package name.tachenov.flakardia.presenter

import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.background
import name.tachenov.flakardia.data.StatsSaveError
import name.tachenov.flakardia.data.StatsSaveSuccess
import name.tachenov.flakardia.underModelLock

interface LessonPresenterView : View

sealed class LessonStatusState
data class QuestionState(val nextQuestion: Question) : LessonStatusState()
data class AnswerState(val answerResult: AnswerResultPresenter) : LessonStatusState()
data object DoneState : LessonStatusState()

data class AnswerResultPresenter(
    val yourAnswer: String?,
    val correctAnswer: String,
    val isCorrect: Boolean,
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
            underModelLock {
                val newState = background {
                    val answerResult = lesson.answer(answer).let {
                        AnswerResultPresenter(
                            yourAnswer = it.yourAnswer?.word?.value,
                            correctAnswer = it.correctAnswer.word.value,
                            isCorrect = it.isCorrect,
                        )
                    }
                    state.copy(
                        lessonResult = lesson.result,
                        lessonStatus = AnswerState(answerResult),
                    )
                }
                val saveResult = background {
                    library.saveUpdatedStats(lesson.stats)
                }
                when (saveResult) {
                    is StatsSaveError -> view.showError("An error occurred when trying to save word statistics", saveResult.message)
                    is StatsSaveSuccess -> {}
                }
                newState
            }
        }
    }

}
