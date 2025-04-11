package name.tachenov.flakardia.app

import name.tachenov.flakardia.assertModelAccessAllowed
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
        assertModelAccessAllowed()
        return yourAnswer?.word == correctAnswer.word
    }

    val flashcardData: FlashcardData get() {
        assertModelAccessAllowed()
        return FlashcardData(flashcardSetPath, Flashcard(question, correctAnswer.word))
    }
}

class Lesson(
    lessonData: LessonData,
) {

    private val allFlashcards: MutableSet<FlashcardData> = lessonData.flashcards.toHashSet()

    private val lessonTime: Instant = Instant.now()
    private val previousLessonStats: MutableMap<Word, WordStats> = lessonData.stats.wordStats.toMutableMap()
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
            assertModelAccessAllowed()
            return LessonResult(round, correctingMistakes, total, correct, incorrect.size, remaining.size)
        }

    val stats: LibraryStats
        get() {
            assertModelAccessAllowed()
            return LibraryStats(
                mistakes.keys.asSequence()
                    .associateWith { word ->
                        val lastLearnedInPreviousLesson = previousLessonStats[word]?.lastLearned
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
    private var currentAnswerResult: AnswerResult? = null

    fun updateCurrentCard(newCard: Flashcard): AnswerResult {
        val currentAnswerResult = checkNotNull(currentAnswerResult) {
            "Cannot update the current flashcard because there is no current flashcard"
        }
        val newAnswerResult = currentAnswerResult.copy(
            question = newCard.question,
            correctAnswer = Answer(newCard.answer),
        )
        changeAnswerResult(newAnswerResult)
        return newAnswerResult
    }

    fun nextQuestion(): Question? {
        assertModelAccessAllowed()
        goToNextFlashcard()
        ++step
        return currentFlashcard?.let {
            lastSeen[it] = step
            Question(it.path, it.flashcard.question)
        }
    }

    fun answer(answer: Answer?): AnswerResult {
        assertModelAccessAllowed()
        val currentFlashcard = currentFlashcard
        checkNotNull(currentFlashcard) { "Cannot answer when there is no question (active flashcard)" }
        val answerResult = AnswerResult(
            flashcardSetPath = currentFlashcard.path,
            question = currentFlashcard.flashcard.question,
            yourAnswer = answer,
            correctAnswer = Answer(currentFlashcard.flashcard.answer)
        )
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
                remaining.addAll(shuffle(allFlashcards))
            }
        }
        if (remaining.isNotEmpty()) {
            currentFlashcard = remaining.first()
        }
        else {
            currentFlashcard = null
        }
        currentAnswerResult = null
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
        check(currentAnswerResult == null)
        val current = remaining.removeFirst()
        val word = answerResult.correctAnswer.word

        var mistakes = this.mistakes[word] ?: 0
        if (!answerResult.isCorrect) {
            ++mistakes
        }
        this.mistakes[word] = mistakes

        currentFlashcard = null
        this.currentAnswerResult = answerResult
        if (!answerResult.isCorrect) {
            incorrect += current
        }
    }

    private fun changeAnswerResult(newAnswerResult: AnswerResult) {
        val previousAnswerResult = checkNotNull(currentAnswerResult)
        val previousFlashcardData = previousAnswerResult.flashcardData
        val newFlashcardData = newAnswerResult.flashcardData

        allFlashcards.remove(previousFlashcardData)
        allFlashcards.add(newFlashcardData)

        val mistakes = this.mistakes.remove(previousAnswerResult.correctAnswer.word)
        checkNotNull(mistakes) { "The old value ${previousAnswerResult.correctAnswer.word} not found in the stats" }
        this.mistakes[newAnswerResult.correctAnswer.word] = mistakes

        val lastSeen = this.lastSeen.remove(previousFlashcardData)
        checkNotNull(lastSeen) { "The current card never seen - makes no sense" }
        this.lastSeen[newFlashcardData] = lastSeen

        val wordStats = this.previousLessonStats.remove(previousAnswerResult.correctAnswer.word)
        if (wordStats != null) { // the word may not have existed in the previous lesson
            this.previousLessonStats[newAnswerResult.correctAnswer.word] = wordStats
        }

        if (!previousAnswerResult.isCorrect) {
            incorrect -= previousFlashcardData
        }
        if (!newAnswerResult.isCorrect) {
            incorrect += newFlashcardData
        }

        this.currentAnswerResult = newAnswerResult
    }

}
