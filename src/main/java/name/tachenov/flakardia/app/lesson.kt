package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.Flashcard
import name.tachenov.flakardia.data.FlashcardSet
import name.tachenov.flakardia.data.Word

sealed class Lesson(
    flashcardSet: FlashcardSet,
) {

    val name: String = flashcardSet.name
    abstract val result: LessonResult

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
