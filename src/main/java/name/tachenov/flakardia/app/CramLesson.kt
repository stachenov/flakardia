package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.Flashcard
import name.tachenov.flakardia.data.LessonData

class CramLesson(lessonData: LessonData) : Lesson(lessonData) {

    private val cardSets: Map<CramLevel, MutableSet<Flashcard>> = CramLevel.entries.associateWith {
        if (it == CramLevel.LEVEL_1) lessonData.flashcardSet.cards.toMutableSet() else mutableSetOf()
    }

    private var cramLevel: CramLevel = CramLevel.LEVEL_1
    private var currentLevelCards: List<Flashcard> = cardSets.getValue(CramLevel.LEVEL_1).shuffled()
    private var index = -1

    override val result: LessonResult
        get() = CramLessonResult(cramLevel, cardSets.entries.associate { it.key to it.value.size })

    override val currentFlashcard: Flashcard?
        get() = currentLevelCards.getOrNull(index)

    override fun goToNextFlashcard() {
        ++index
        if (index == currentLevelCards.size) {
            goToNextLevel()
        }
    }

    private fun goToNextLevel() {
        val firstNonEmptyLevel = CramLevel.entries.first { cardSets.getValue(it).isNotEmpty() }
        cramLevel = firstNonEmptyLevel
        if (firstNonEmptyLevel.isLast) {
            currentLevelCards = emptyList()
        }
        else {
            currentLevelCards = cardSets.getValue(firstNonEmptyLevel).shuffled()
        }
        index = 0
    }

    override fun recordAnswerResult(answerResult: AnswerResult) {
        val currentFlashcard = currentFlashcard
        checkNotNull(currentFlashcard) { "Cannot record an answer when there is no question (active flashcard)" }
        cardSets.getValue(cramLevel).remove(currentFlashcard)
        if (answerResult.isCorrect) {
            cardSets.getValue(cramLevel.next()).add(currentFlashcard)
        }
        else {
            cardSets.getValue(CramLevel.LEVEL_1).add(currentFlashcard)
        }
    }

}

data class CramLessonResult(
    val level: CramLevel,
    val cardCount: Map<CramLevel, Int>,
) : LessonResult()

enum class CramLevel {
    LEVEL_1,
    LEVEL_2,
    LEVEL_3,
    LEVEL_4,
    LEVEL_5;

    val number: Int get() = ordinal + 1

    val isLast: Boolean get() = this == LEVEL_5

    fun next(): CramLevel = CramLevel.entries[ordinal + 1]
}
