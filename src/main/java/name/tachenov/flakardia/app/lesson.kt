package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.Flashcard
import name.tachenov.flakardia.data.FlashcardSet

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
        return currentFlashcard?.let { Question(it.front.value) }
    }

    fun answer(answer: Answer?): AnswerResult {
        val currentFlashcard = currentFlashcard
        checkNotNull(currentFlashcard) { "Cannot answer when there is no question (active flashcard)" }
        val answerResult = AnswerResult(answer, Answer(currentFlashcard.back.value))
        recordAnswerResult(answerResult)
        return answerResult
    }

}

sealed class LessonResult

data class Question(val value: String)

data class Answer(val value: String)

data class AnswerResult(val yourAnswer: Answer?, val correctAnswer: Answer) {
    val isCorrect: Boolean get() = answersMatch(yourAnswer, correctAnswer)
}

private fun answersMatch(yourAnswer: Answer?, correctAnswer: Answer): Boolean = normalize(yourAnswer) == normalize(correctAnswer)

fun normalize(answer: Answer?): String? {
    if (answer == null) {
        return null
    }
    val sb = StringBuilder()
    for (c in answer.value) {
        if (c.isLetter()) {
            sb.append(c)
        }
    }
    return sb.toString()
}
