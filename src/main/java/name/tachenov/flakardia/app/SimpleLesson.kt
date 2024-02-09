package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.Flashcard
import name.tachenov.flakardia.data.FlashcardSet

class SimpleLesson(flashcardSet: FlashcardSet) : Lesson(flashcardSet) {

    private val total = flashcardSet.cards.size
    private val remaining = ArrayDeque(flashcardSet.cards.shuffled())
    private val incorrect: MutableSet<Flashcard> = hashSetOf()
    private var round = 1

    private val correct: Int
        get() = total - remaining.size - incorrect.size

    override val result: SimpleLessonResult
        get() = SimpleLessonResult(round, total, correct, incorrect.size, remaining.size)

    override val currentFlashcard: Flashcard?
        get() = remaining.firstOrNull()

    override fun goToNextFlashcard() {
        if (remaining.isEmpty() && incorrect.isNotEmpty()) {
            ++round
            remaining.addAll(incorrect.shuffled())
            incorrect.clear()
        }
    }

    override fun recordAnswerResult(answerResult: AnswerResult) {
        val current = remaining.removeFirst()
        if (!answerResult.isCorrect) {
            incorrect += current
        }
    }

}

data class SimpleLessonResult(
    val round: Int,
    val total: Int,
    val correct: Int,
    val incorrect: Int,
    val remaining: Int,
) : LessonResult()
