package name.tachenov.flakardia.presenter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.background
import name.tachenov.flakardia.data.StatsSaveError
import name.tachenov.flakardia.data.StatsSaveSuccess
import name.tachenov.flakardia.launchUiTask

interface LessonPresenterView : View

sealed class LessonStatusState
data class QuestionState(val nextQuestion: Question) : LessonStatusState()
data class AnswerState(val answerResult: AnswerResult) : LessonStatusState()
data object DoneState : LessonStatusState()

data class LessonPresenterState(
    val title: String,
    val lessonResult: LessonResult,
    val lessonStatus: LessonStatusState,
) : PresenterState

class LessonPresenter(
    private val library: Library,
    private val lesson: Lesson,
) : Presenter<LessonPresenterState, LessonPresenterView>() {
    private val mutableState = MutableStateFlow<LessonPresenterState?>(null)

    override val state: Flow<LessonPresenterState>
        get() = mutableState.asStateFlow().filterNotNull()

    override fun initializeState() {
        val nextQuestion = lesson.nextQuestion()
        mutableState.value = LessonPresenterState(
            title = lesson.name,
            lessonResult = lesson.result,
            lessonStatus = nextQuestion?.let { question -> QuestionState(question) } ?: DoneState,
        )
    }

    fun nextQuestion() {
        val nextQuestion = lesson.nextQuestion()
        mutableState.value = mutableState.value?.copy(
            lessonResult = lesson.result,
            lessonStatus = nextQuestion?.let { question -> QuestionState(question) } ?: DoneState,
        )
    }

    fun answered(answer: Answer?) {
        launchUiTask {
            val answerResult = lesson.answer(answer)
            mutableState.value = mutableState.value?.copy(
                lessonResult = lesson.result,
                lessonStatus = AnswerState(answerResult),
            )
            val result = background {
                library.saveUpdatedStats(lesson.stats)
            }
            when (result) {
                is StatsSaveError -> view.showError("An error occurred when trying to save word statistics", result.message)
                is StatsSaveSuccess -> { }
            }
        }
    }

}
