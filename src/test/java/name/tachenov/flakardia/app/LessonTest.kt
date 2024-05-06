package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.random.nextInt

class LessonTest {

    private val card1 = card("q1", "a1")
    private val card2 = card("q2", "a2")
    private val card3 = card("q3", "a3")
    private lateinit var flashcardSet: FlashcardSet
    private val emptyStats = LibraryStats(emptyMap())

    private fun card(front: String, back: String): Flashcard = Flashcard(Word(front), Word(back))

    private var lesson: Lesson? = null
    private var question: Question? = null
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

    @Test
    fun `mistakes in the first round only`() {
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

    @RepeatedTest(100)
    fun `words do not repeat too soon`() {
        startBigLesson()
        nextQuestion()
        runOneRound(mistakes = 1)
        correctMistakes()
        runOneRound(mistakes = 0)
        assertThatQuestionsDoNotRepeatSoonerThan(5)
    }

    @RepeatedTest(100)
    fun `words do not repeat too soon when correcting a lot of mistakes`() {
        startBigLesson()
        nextQuestion()
        runOneRound(mistakes = 15)
        correctMistakes()
        runOneRound(mistakes = 0)
        assertThatQuestionsDoNotRepeatSoonerThan(5)
    }

    private fun startBigLesson() {
        val bigFlashcardList = mutableListOf<Flashcard>()
        for (c in 'a'..'z') {
            bigFlashcardList += card("front$c", "back$c")
        }
        val bigFlashcardSet = FlashcardSet(bigFlashcardList.map { FlashcardData(RelativePath(listOf("test")), it) })
        startLesson(bigFlashcardSet, emptyStats)
    }

    private fun runOneRound(mistakes: Int) {
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
        if (question != null) {
            val index = questions.size
            questions += question
            stepWasDuringCorrectingMistakes[index] = lesson.result.correctingMistakes
        }
    }

    private fun answerCorrectly() {
        val lesson = getCreatedLesson()
        val question = getAskedQuestion()
        for (card in flashcardSet.cards) {
            if (card.flashcard.front == question.word) {
                lesson.answer(Answer(card.flashcard.back))
                return
            }
        }
        throw AssertionError("No matching flashcard for $question")
    }

    private fun answerIncorrectly() {
        getCreatedLesson().answer(Answer(Word("Incorrect")))
    }

    private fun getCreatedLesson(): Lesson = this.lesson ?: throw AssertionError("Lesson not started")

    private fun getAskedQuestion(): Question = this.question ?: throw AssertionError("No current question")

    private fun startLesson(flashcardSet: FlashcardSet, stats: LibraryStats) {
        lesson = Lesson(LessonData("test", flashcardSet.cards, stats))
        this.flashcardSet = flashcardSet
    }

}
