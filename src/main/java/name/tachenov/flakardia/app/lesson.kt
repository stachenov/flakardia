package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.*
import java.time.Duration
import java.time.Instant

data class LessonResult(
    val round: Int,
    val correctingMistakes: Boolean,
    val total: Int,
    val correct: Int,
    val incorrect: Int,
    val remaining: Int,
)

data class Question(
    val flashcardSetPath: RelativePath,
    val word: Word,
)

data class Answer(val word: Word)

data class AnswerResult(val yourAnswer: Answer?, val correctAnswer: Answer) {
    val isCorrect: Boolean get() = yourAnswer?.word == correctAnswer.word
}

class Lesson(
    private val lessonData: LessonData,
) {

    private val lessonTime: Instant = Instant.now()
    private val mistakes = hashMapOf<Word, Int>()

    // For a word learned for the first time, we need some sensible fake stats to initialize them.
    private val previousIntervalFallback: Duration = Duration.ofDays(1L)

    private val total = lessonData.flashcards.size
    private var correctingMistakes = false
    private val remaining = ArrayDeque<FlashcardData>()
    private val incorrect: MutableSet<FlashcardData> = hashSetOf()
    private var round = 0

    private val correct: Int
        get() = total - remaining.size - incorrect.size

    val name: String = lessonData.name

    val result: LessonResult
        get() = LessonResult(round, correctingMistakes, total, correct, incorrect.size, remaining.size)

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

    private var currentFlashcard: FlashcardData? = null

    fun nextQuestion(): Question? {
        goToNextFlashcard()
        return currentFlashcard?.let { Question(it.path, it.flashcard.front) }
    }

    fun answer(answer: Answer?): AnswerResult {
        val currentFlashcard = currentFlashcard
        checkNotNull(currentFlashcard) { "Cannot answer when there is no question (active flashcard)" }
        val answerResult = AnswerResult(answer, Answer(currentFlashcard.flashcard.back))
        val word = answerResult.correctAnswer.word
        var mistakes = this.mistakes[word] ?: 0
        if (!answerResult.isCorrect) {
            ++mistakes
        }
        this.mistakes[word] = mistakes
        recordAnswerResult(answerResult)
        return answerResult
    }

    private fun goToNextFlashcard() {
        when {
            remaining.isNotEmpty() -> { }
            incorrect.isNotEmpty() -> {
                correctingMistakes = true
                remaining.addAll(incorrect.shuffled())
                incorrect.clear()
            }
            correctingMistakes || round == 0 -> {
                ++round
                correctingMistakes = false
                remaining.addAll(lessonData.flashcards.shuffled())
            }
        }
        if (remaining.isNotEmpty()) {
            currentFlashcard = remaining.first()
        }
        else {
            currentFlashcard = null
        }
    }

    private fun recordAnswerResult(answerResult: AnswerResult) {
        val current = remaining.removeFirst()
        currentFlashcard = null
        if (!answerResult.isCorrect) {
            incorrect += current
        }
    }

}
