package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.Flashcard
import name.tachenov.flakardia.data.FlashcardSet

class SimpleLesson(flashcardSet: FlashcardSet) : Lesson(flashcardSet) {

    private val flashcards: List<Flashcard> = flashcardSet.cards.shuffled()
    private var index = -1

    override var result: SimpleLessonResult = SimpleLessonResult(flashcards.size)
        private set

    override val currentFlashcard: Flashcard?
        get() = flashcards.getOrNull(index)

    override fun goToNextFlashcard() {
        ++index
    }

    override fun recordAnswerResult(answerResult: AnswerResult) {
        result = SimpleLessonResult(
            total = result.total,
            correct = if (answerResult.isCorrect) result.correct + 1 else result.correct,
            incorrect = if (answerResult.isCorrect) result.incorrect else result.incorrect + 1,
        )
    }

}

data class SimpleLessonResult(
    val total: Int,
    val correct: Int = 0,
    val incorrect: Int = 0,
) : LessonResult() {
    val remaining: Int get() = total - (correct + incorrect)
}
