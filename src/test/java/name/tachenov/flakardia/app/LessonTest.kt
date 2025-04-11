package name.tachenov.flakardia.app

import name.tachenov.flakardia.backgroundModelTest
import name.tachenov.flakardia.data.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class LessonTest {

    private val card1 = card("q1", "a1")
    private val card2 = card("q2", "a2")
    private val card3 = card("q3", "a3")
    private lateinit var flashcardSet: FlashcardSet
    private val emptyStats = LibraryStats(emptyMap())

    private fun card(front: String, back: String): Flashcard = Flashcard(Word(front), Word(back))

    private var lesson: Lesson? = null
    private var question: Question? = null
    private var answerResult: AnswerResult? = null
    private lateinit var questions: MutableList<Question>
    private lateinit var stepWasDuringCorrectingMistakes: MutableMap<Int, Boolean>

    @BeforeEach
    fun setUp() {
        flashcardSet = FlashcardSet(listOf(card1, card2, card3).map { FlashcardData(RelativePath(listOf("test")), it) })
        lesson = null
        question = null
        questions = mutableListOf()
        stepWasDuringCorrectingMistakes = hashMapOf()
    }

    @Test
    fun `no mistakes`() {
        backgroundModelTest {
            startLesson(flashcardSet, emptyStats)
            nextQuestion()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 0, incorrect = 0, remaining = 3)
            answerCorrectly()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 1, incorrect = 0, remaining = 2)
            nextQuestion()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 1, incorrect = 0, remaining = 2)
            answerCorrectly()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 2, incorrect = 0, remaining = 1)
            nextQuestion()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 2, incorrect = 0, remaining = 1)
            answerCorrectly()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 3, incorrect = 0, remaining = 0)
            nextQuestion()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 3, incorrect = 0, remaining = 0)
            assertThat(question).isNull()
        }
    }

    @Test
    fun `mistakes in the first round only`() {
        backgroundModelTest {
            startLesson(flashcardSet, emptyStats)
            nextQuestion()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 0, incorrect = 0, remaining = 3)
            answerCorrectly()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 1, incorrect = 0, remaining = 2)
            nextQuestion()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 1, incorrect = 0, remaining = 2)
            answerCorrectly()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 2, incorrect = 0, remaining = 1)
            nextQuestion()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 2, incorrect = 0, remaining = 1)
            answerIncorrectly()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 2, incorrect = 1, remaining = 0)
            nextQuestion()
            assertResult(round = 1, correctingMistakes = true, total = 3, correct = 2, incorrect = 0, remaining = 1)
            answerCorrectly()
            assertResult(round = 1, correctingMistakes = true, total = 3, correct = 3, incorrect = 0, remaining = 0)
            nextQuestion()
            assertResult(round = 2, correctingMistakes = false, total = 3, correct = 0, incorrect = 0, remaining = 3)
            answerCorrectly()
            assertResult(round = 2, correctingMistakes = false, total = 3, correct = 1, incorrect = 0, remaining = 2)
            nextQuestion()
            assertResult(round = 2, correctingMistakes = false, total = 3, correct = 1, incorrect = 0, remaining = 2)
            answerCorrectly()
            assertResult(round = 2, correctingMistakes = false, total = 3, correct = 2, incorrect = 0, remaining = 1)
            nextQuestion()
            assertResult(round = 2, correctingMistakes = false, total = 3, correct = 2, incorrect = 0, remaining = 1)
            answerCorrectly()
            assertResult(round = 2, correctingMistakes = false, total = 3, correct = 3, incorrect = 0, remaining = 0)
            nextQuestion()
            assertResult(round = 2, correctingMistakes = false, total = 3, correct = 3, incorrect = 0, remaining = 0)
            assertThat(question).isNull()
        }
    }

    @RepeatedTest(100)
    fun `words do not repeat too soon`() {
        backgroundModelTest {
            startBigLesson()
            nextQuestion()
            runOneRound(mistakes = 1)
            correctMistakes()
            runOneRound(mistakes = 0)
            assertThatQuestionsDoNotRepeatSoonerThan(5)
        }
    }

    @RepeatedTest(100)
    fun `words do not repeat too soon when correcting a lot of mistakes`() {
        backgroundModelTest {
            startBigLesson()
            nextQuestion()
            runOneRound(mistakes = 15)
            correctMistakes()
            runOneRound(mistakes = 0)
            assertThatQuestionsDoNotRepeatSoonerThan(5)
        }
    }

    @RepeatedTest(100)
    fun `words do not repeat too soon even when renamed`() {
        backgroundModelTest {
            startBigLesson()
            nextQuestion()
            runOneRound(mistakes = 1, renameWords = true)
            correctMistakes()
            runOneRound(mistakes = 0)
            assertThatQuestionsDoNotRepeatSoonerThan(5)
        }
    }

    @Test
    fun `update incorrect word to incorrect`() {
        backgroundModelTest {
            startLesson(flashcardSet, emptyStats)
            nextQuestion()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 0, incorrect = 0, remaining = 3)
            answerIncorrectly()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 0, incorrect = 1, remaining = 2)
            updateCurrentCardAnswerToIncorrect()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 0, incorrect = 1, remaining = 2)
        }
    }

    @Test
    fun `update incorrect word to correct`() {
        backgroundModelTest {
            startLesson(flashcardSet, emptyStats)
            nextQuestion()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 0, incorrect = 0, remaining = 3)
            answerIncorrectly()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 0, incorrect = 1, remaining = 2)
            updateCurrentCardAnswerToCorrect()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 1, incorrect = 0, remaining = 2)
        }
    }

    @Test
    fun `update correct word to incorrect`() {
        backgroundModelTest {
            startLesson(flashcardSet, emptyStats)
            nextQuestion()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 0, incorrect = 0, remaining = 3)
            answerCorrectly()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 1, incorrect = 0, remaining = 2)
            updateCurrentCardAnswerToIncorrect()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 0, incorrect = 1, remaining = 2)
        }
    }

    @Test
    fun `sequentially update answer and question`() {
        backgroundModelTest {
            startLesson(flashcardSet, emptyStats)
            nextQuestion()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 0, incorrect = 0, remaining = 3)
            answerIncorrectly()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 0, incorrect = 1, remaining = 2)
            updateCurrentCardAnswerToIncorrect()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 0, incorrect = 1, remaining = 2)
            updateCurrentQuestion()
            assertResult(round = 1, correctingMistakes = false, total = 3, correct = 0, incorrect = 1, remaining = 2)
        }
    }

    @Test
    fun `updating answer preserves stats`() {
        backgroundModelTest {
            val now = Instant.now()
            val lastLearned = now.minus(100L, ChronoUnit.DAYS)
            val stats = LibraryStats(
                flashcardSet.cards.associate { card ->
                    card.flashcard.answer to WordStats(
                        lastLearned,
                        Duration.ZERO,
                        0,
                    )
                }
            )
            startLesson(flashcardSet, stats)
            nextQuestion()
            answerIncorrectly()
            updateCurrentCardAnswerToIncorrect()
            val newAnswer = checkNotNull(answerResult).correctAnswer
            // Since we set "last learned" to now minus 100 days, "interval before last learned" should be approximately that,
            // assuming that the word was successfully renamed (otherwise it falls back to 1 day).
            assertThat(getCreatedLesson().stats.wordStats[newAnswer.word]?.intervalBeforeLastLearned)
                .isGreaterThanOrEqualTo(Duration.ofDays(100L))
        }
    }

    private fun startBigLesson() {
        val bigFlashcardList = mutableListOf<Flashcard>()
        for (c in 'a'..'z') {
            bigFlashcardList += card("front$c", "back$c")
        }
        val bigFlashcardSet = FlashcardSet(bigFlashcardList.map { FlashcardData(RelativePath(listOf("test")), it) })
        startLesson(bigFlashcardSet, emptyStats)
    }

    private fun runOneRound(mistakes: Int, renameWords: Boolean = false) {
        val lesson = getCreatedLesson()
        val wordCount = lesson.result.remaining
        val positions = (0 until wordCount).toMutableSet()
        val mistakePositions = hashSetOf<Int>()
        repeat(mistakes) {
            val mistakePosition = positions.random()
            mistakePositions += mistakePosition
            positions -= mistakePosition
        }
        for (i in 0 until wordCount) {
            if (i in mistakePositions) {
                answerIncorrectly()
            }
            else {
                answerCorrectly()
            }
            if (renameWords) {
                val currentAnswerResult = checkNotNull(answerResult)
                val oldCard = Flashcard(currentAnswerResult.question, currentAnswerResult.correctAnswer.word)
                val newCard = Flashcard(currentAnswerResult.question, Word("New answer $i"))
                lesson.updateCurrentCard(newCard)
                flashcardSet = flashcardSet.copy(
                    cards = flashcardSet.cards.map {
                        if (it.flashcard == oldCard) {
                            it.copy(flashcard = newCard)
                        }
                        else {
                            it
                        }
                    }
                )
            }
            nextQuestion()
        }
    }

    private fun correctMistakes() {
        val lesson = getCreatedLesson()
        val mistakes = lesson.result.remaining
        repeat(mistakes) {
            answerCorrectly()
            nextQuestion()
        }
    }

    private fun assertThatQuestionsDoNotRepeatSoonerThan(step: Int) {
        val previousRepetition = hashMapOf<Question, Int>()
        for ((i, q) in questions.withIndex()) {
            val prevIndex = previousRepetition[q]
            if (prevIndex != null && stepWasDuringCorrectingMistakes[i] != true) {
                assertThat(i - prevIndex).`as`("at step $i").isGreaterThanOrEqualTo(step)
            }
            previousRepetition[q] = i
        }
    }

    private fun assertResult(
        round: Int,
        correctingMistakes: Boolean,
        total: Int,
        correct: Int,
        incorrect: Int,
        remaining: Int,
    ) {
        val lesson = getCreatedLesson()
        assertThat(lesson.result).isEqualTo(LessonResult(round, correctingMistakes, total, correct, incorrect, remaining))
    }

    private fun nextQuestion() {
        val lesson = getCreatedLesson()
        val question = lesson.nextQuestion()
        this.question = question
        this.answerResult = null
        if (question != null) {
            val index = questions.size
            questions += question
            stepWasDuringCorrectingMistakes[index] = lesson.result.correctingMistakes
        }
    }

    private fun answerCorrectly() {
        val lesson = getCreatedLesson()
        answerResult = lesson.answer(getCorrectAnswer())
    }

    private fun answerIncorrectly() {
        answerResult = getCreatedLesson().answer(Answer(Word("Incorrect")))
    }

    private fun updateCurrentCardAnswerToIncorrect() {
        val previousResult = checkNotNull(answerResult)
        val oldAnswer = previousResult.correctAnswer.word
        val newAnswer = Answer(Word("Another incorrect"))
        val lesson = getCreatedLesson()
        val result = lesson.updateCurrentCard(Flashcard(getAskedQuestion().word, newAnswer.word))
        assertThat(result.isCorrect).isFalse()
        assertThat(result.question).isEqualTo(previousResult.question)
        assertThat(result.yourAnswer).isEqualTo(previousResult.yourAnswer)
        assertThat(result.correctAnswer).isEqualTo(newAnswer)
        assertThat(lesson.stats.wordStats.keys).contains(newAnswer.word)
        assertThat(lesson.stats.wordStats.keys).doesNotContain(oldAnswer)
        this.answerResult = result
    }

    private fun updateCurrentCardAnswerToCorrect() {
        val previousResult = checkNotNull(answerResult)
        val oldAnswer = previousResult.correctAnswer.word
        val correctAnswer = checkNotNull(previousResult.yourAnswer)
        val lesson = getCreatedLesson()
        val result = lesson.updateCurrentCard(Flashcard(getAskedQuestion().word, correctAnswer.word))
        assertThat(result.isCorrect).isTrue()
        assertThat(result.question).isEqualTo(previousResult.question)
        assertThat(result.yourAnswer).isEqualTo(previousResult.yourAnswer)
        assertThat(result.correctAnswer).isEqualTo(correctAnswer)
        assertThat(lesson.stats.wordStats.keys).contains(correctAnswer.word)
        assertThat(lesson.stats.wordStats.keys).doesNotContain(oldAnswer)
        this.answerResult = result
    }

    private fun updateCurrentQuestion() {
        val previousResult = checkNotNull(answerResult)
        val newQuestion = Word("New question")
        val result = getCreatedLesson().updateCurrentCard(Flashcard(newQuestion, previousResult.correctAnswer.word))
        assertThat(result.isCorrect).isFalse()
        assertThat(result.question).isEqualTo(newQuestion)
        assertThat(result.yourAnswer).isEqualTo(previousResult.yourAnswer)
        assertThat(result.correctAnswer).isEqualTo(previousResult.correctAnswer)
        this.answerResult = result
    }

    private fun getCreatedLesson(): Lesson = this.lesson ?: throw AssertionError("Lesson not started")

    private fun getAskedQuestion(): Question = this.question ?: throw AssertionError("No current question")

    private fun getCorrectAnswer(): Answer {
        val question = getAskedQuestion()
        for (card in flashcardSet.cards) {
            if (card.flashcard.question == question.word) {
                return Answer(card.flashcard.answer)
            }
        }
        throw AssertionError("No matching flashcard for $question")
    }

    private fun startLesson(flashcardSet: FlashcardSet, stats: LibraryStats) {
        lesson = Lesson(LessonData("test", flashcardSet.cards, stats))
        this.flashcardSet = flashcardSet
    }

}
