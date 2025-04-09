package name.tachenov.flakardia.app

import name.tachenov.flakardia.assertBGT
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

data class AnswerResult(val flashcardSetPath: RelativePath, val question: Word, val yourAnswer: Answer?, val correctAnswer: Answer) {
    val isCorrect: Boolean get() {
        assertBGT()
        return yourAnswer?.word == correctAnswer.word
    }
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
    private var step = 0
    private val lastSeen = hashMapOf<FlashcardData, Int>()

    private val correct: Int
        get() = total - remaining.size - incorrect.size

    val name: String = lessonData.name

    val result: LessonResult
        get() {
            assertBGT()
            return LessonResult(round, correctingMistakes, total, correct, incorrect.size, remaining.size)
        }

    val stats: LibraryStats
        get() {
            assertBGT()
            return LibraryStats(
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
        }

    private var currentFlashcard: FlashcardData? = null

    fun nextQuestion(): Question? {
        assertBGT()
        goToNextFlashcard()
        ++step
        return currentFlashcard?.let {
            lastSeen[it] = step
            Question(it.path, it.flashcard.question)
        }
    }

    fun answer(answer: Answer?): AnswerResult {
        assertBGT()
        val currentFlashcard = currentFlashcard
        checkNotNull(currentFlashcard) { "Cannot answer when there is no question (active flashcard)" }
        val answerResult = AnswerResult(
            flashcardSetPath = currentFlashcard.path,
            question = currentFlashcard.flashcard.question,
            yourAnswer = answer,
            correctAnswer = Answer(currentFlashcard.flashcard.answer)
        )
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
                remaining.addAll(shuffle(incorrect))
                incorrect.clear()
            }
            correctingMistakes || round == 0 -> {
                ++round
                correctingMistakes = false
                remaining.addAll(shuffle(lessonData.flashcards))
            }
        }
        if (remaining.isNotEmpty()) {
            currentFlashcard = remaining.first()
        }
        else {
            currentFlashcard = null
        }
    }

    private fun shuffle(cards: Collection<FlashcardData>): List<FlashcardData> {
        val shuffled = cards.shuffled().toMutableList()
        // Now move too recent cards to the end.
        val threshold = shuffled.size / 3
        val tooRecentByIndex = hashMapOf<Int, Int>()
        for ((index, flashcard) in shuffled.withIndex()) {
            val howRecent = stepsSinceLastSeen(flashcard) + index
            if (howRecent < threshold) {
                tooRecentByIndex[index] = howRecent
            }
        }
        // Now go over the indices in reverse order to preserve them as elements are removed.
        val indices = tooRecentByIndex.keys.toList().sortedDescending()
        val tooRecent = hashSetOf<FlashcardData>()
        for (index in indices) {
            tooRecent += shuffled.removeAt(index)
        }
        return shuffled + tooRecent
    }

    private fun stepsSinceLastSeen(flashcard: FlashcardData): Int {
        val lastSeen = lastSeen[flashcard]
        return lastSeen?.let { step - lastSeen } ?: total
    }

    private fun recordAnswerResult(answerResult: AnswerResult) {
        val current = remaining.removeFirst()
        currentFlashcard = null
        if (!answerResult.isCorrect) {
            incorrect += current
        }
    }

}
