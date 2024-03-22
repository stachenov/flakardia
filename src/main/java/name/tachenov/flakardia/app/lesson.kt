package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.*
import java.time.Duration
import java.time.Instant

sealed class Lesson(
    private val lessonData: LessonData,
) {

    private val lessonTime: Instant = Instant.now()
    private val mistakes = hashMapOf<Word, Int>()

    // For a word learned for the first time, we need some sensible fake stats to initialize them.
    private val previousIntervalFallback: Duration = Duration.ofDays(1L)

    val name: String = lessonData.flashcardSet.name

    abstract val result: LessonResult

    val stats: LibraryStats
        get() = LibraryStats(
            mistakes.keys.asSequence()
                .associateWith { word ->
                    val lastLearnedInPreviousLesson = lessonData.stats.wordStats[word]?.lastLearned
                    WordStats(
                        lessonTime,
                        if (lastLearnedInPreviousLesson == null) {
                            previousIntervalFallback
                        }
                        else {
                            Duration.between(lastLearnedInPreviousLesson, lessonTime)
                        },
                        mistakes[word] ?: 0,
                    )
                }
        )

    protected abstract val currentFlashcard: Flashcard?

    protected abstract fun goToNextFlashcard()

    protected abstract fun recordAnswerResult(answerResult: AnswerResult)

    fun nextQuestion(): Question? {
        goToNextFlashcard()
        return currentFlashcard?.let { Question(it.front) }
    }

    fun answer(answer: Answer?): AnswerResult {
        val currentFlashcard = currentFlashcard
        checkNotNull(currentFlashcard) { "Cannot answer when there is no question (active flashcard)" }
        val answerResult = AnswerResult(answer, Answer(currentFlashcard.back))
        val word = answerResult.correctAnswer.word
        var mistakes = this.mistakes[word] ?: 0
        if (!answerResult.isCorrect) {
            ++mistakes
        }
        this.mistakes[word] = mistakes
        recordAnswerResult(answerResult)
        return answerResult
    }

}

sealed class LessonResult

data class Question(val word: Word)

data class Answer(val word: Word)

data class AnswerResult(val yourAnswer: Answer?, val correctAnswer: Answer) {
    val isCorrect: Boolean get() = yourAnswer?.word == correctAnswer.word
}
