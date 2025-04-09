package name.tachenov.flakardia.presenter

import name.tachenov.flakardia.data.LessonData
import name.tachenov.flakardia.data.RelativePath
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

interface FlashcardSetView : View

data class FlashcardSetViewPresenterState(
    val title: String,
    val cards: List<FlashcardViewModel>,
) : PresenterState

data class FlashcardViewModel(
    val path: RelativePath,
    val question: String,
    val answer: String,
    val lastLearned: LastLearnedViewModel,
    val mistakes: Int?,
    val intervalBeforeLastLearned: IntervalViewModel
)

data class LastLearnedViewModel(val lastLearned: Instant?) {
    override fun toString(): String = lastLearned?.let { String.format("%tF  %<tT", it.atZone(ZoneId.systemDefault())) } ?: ""
}

data class IntervalViewModel(val interval: Duration?) : Comparable<IntervalViewModel> {
    override fun toString(): String = interval?.let { String.format("%.02f", it.toSeconds().toDouble() / 86400.0) } ?: ""
    override fun compareTo(other: IntervalViewModel): Int = compareValuesBy(this, other) { it.interval }
}

class FlashcardSetViewPresenter(
    private val lessonData: LessonData,
) : Presenter<FlashcardSetViewPresenterState, FlashcardSetView>() {
    override suspend fun computeInitialState(): FlashcardSetViewPresenterState = FlashcardSetViewPresenterState(
        title = lessonData.name,
        cards = lessonData.let { lessonData ->
            lessonData.flashcards.map { card ->
                val stats = lessonData.stats.wordStats[card.flashcard.answer]
                FlashcardViewModel(
                    card.path,
                    card.flashcard.question.value,
                    card.flashcard.answer.value,
                    LastLearnedViewModel(stats?.lastLearned),
                    stats?.mistakes,
                    IntervalViewModel(stats?.intervalBeforeLastLearned),
                )
            }
        }
    )
}
