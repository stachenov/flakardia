package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.FlashcardSet

class Lesson(
    flashcardSet: FlashcardSet,
) {

    val name: String = flashcardSet.name

    private val flashcards = flashcardSet.cards.shuffled()
    private var index = -1

    fun nextQuestion(): Question? = flashcards.getOrNull(++index)?.let { Question(it.front.value) }

    fun answer(answer: Answer?): AnswerResult = AnswerResult(answer, Answer(flashcards[index].back.value))

}

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
