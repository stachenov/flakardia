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

    private val mapping = CardIdMapping(lessonData)

    private val lessonTime: Instant = Instant.now()

    private val lastLearned: Map<Id, Instant> = mapping.entries
        .mapNotNull { lessonData.stats.wordStats[it.value.flashcard.answer]?.let { stats -> it.key to stats.lastLearned } }
        .toMap()

    private val mistakes = hashMapOf<Id, Int>()

    // For a word learned for the first time, we need some sensible fake stats to initialize them.
    private val previousIntervalFallback: Duration = Duration.ofDays(1L)

    private val total = lessonData.flashcards.size
    private var correctingMistakes = false
    private val remaining = ArrayDeque<Id>()
    private val incorrect: MutableSet<Id> = hashSetOf()
    private var round = 0
    private var step = 0
    private val lastSeen = hashMapOf<Id, Int>()

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
                    .associateWith { id ->
                        val lastLearnedInPreviousLesson = lastLearned[id]
                        WordStats(
                            lessonTime,
                            if (lastLearnedInPreviousLesson == null) {
                                previousIntervalFallback
                            }
                            else {
                                Duration.between(lastLearnedInPreviousLesson, lessonTime)
                            },
                            mistakes[id] ?: 0,
                        )
                    }.mapKeys {
                        mapping.answer(it.key)
                    }
            )
        }

    private var currentFlashcardId: Id? = null
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
        return currentFlashcardId?.let { id ->
            lastSeen[id] = step
            val data = mapping.data(id)
            Question(data.path, data.flashcard.question)
        }
    }

    fun answer(answer: Answer?): AnswerResult {
        assertModelAccessAllowed()
        val currentFlashcardId = currentFlashcardId
        checkNotNull(currentFlashcardId) { "Cannot answer when there is no question (active flashcard)" }
        val currentFlashcard = mapping.data(currentFlashcardId)
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
                remaining.addAll(shuffle(mapping.ids))
            }
        }
        if (remaining.isNotEmpty()) {
            currentFlashcardId = remaining.first()
        }
        else {
            currentFlashcardId = null
        }
        currentAnswerResult = null
    }

    private fun shuffle(ids: Collection<Id>): List<Id> {
        val shuffled = ids.shuffled().toMutableList()
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
        val tooRecent = hashSetOf<Id>()
        for (index in indices) {
            tooRecent += shuffled.removeAt(index)
        }
        return shuffled + tooRecent
    }

    private fun stepsSinceLastSeen(id: Id): Int {
        val lastSeen = lastSeen[id]
        return lastSeen?.let { step - lastSeen } ?: total
    }

    private fun recordAnswerResult(answerResult: AnswerResult) {
        check(currentAnswerResult == null)
        val current = remaining.removeFirst()
        val word = answerResult.correctAnswer.word

        var mistakes = this.mistakes[mapping.idByAnswer(word)] ?: 0
        if (!answerResult.isCorrect) {
            ++mistakes
        }
        this.mistakes[mapping.idByAnswer(word)] = mistakes

        currentFlashcardId = null
        this.currentAnswerResult = answerResult
        if (!answerResult.isCorrect) {
            incorrect += current
        }
    }

    private fun changeAnswerResult(newAnswerResult: AnswerResult) {
        val previousAnswerResult = checkNotNull(currentAnswerResult)
        val previousFlashcardData = previousAnswerResult.flashcardData
        val newFlashcardData = newAnswerResult.flashcardData
        val id = mapping.idByAnswer(previousFlashcardData.flashcard.answer)

        mapping.updateCard(previousFlashcardData, newFlashcardData)

        if (!previousAnswerResult.isCorrect && newAnswerResult.isCorrect) {
            incorrect -= id
        }
        if (previousAnswerResult.isCorrect && !newAnswerResult.isCorrect) {
            incorrect += id
        }

        this.currentAnswerResult = newAnswerResult
    }

    @JvmInline private value class Id(val value: Int)

    private class CardIdMapping(lessonData: LessonData) {
        private val flashcardsById = lessonData.flashcards.withIndex().associateTo(hashMapOf()) { Id(it.index) to it.value }
        private val idByAnswer = flashcardsById.entries.associateTo(hashMapOf()) { it.value.flashcard.answer to it.key }

        val entries: Set<Map.Entry<Id, FlashcardData>> get() = flashcardsById.entries

        val ids: Set<Id> get() = flashcardsById.keys

        fun idByAnswer(answer: Word): Id = idByAnswer.getValue(answer)

        fun answer(id: Id): Word = flashcardsById.getValue(id).flashcard.answer

        fun data(id: Id): FlashcardData  = flashcardsById.getValue(id)

        fun updateCard(old: FlashcardData, new: FlashcardData) {
            val id = idByAnswer(old.flashcard.answer)
            flashcardsById[id] = new
            idByAnswer.remove(old.flashcard.answer)
            idByAnswer[new.flashcard.answer] = id
        }
    }

}
