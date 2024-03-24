package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LessonTest {

    private val card1 = card("q1", "a1")
    private val card2 = card("q2", "a2")
    private val card3 = card("q3", "a3")
    private val flashcardSet = FlashcardSet(listOf(card1, card2, card3).map { FlashcardData(RelativePath(listOf("test")), it) })
    private val emptyStats = LibraryStats(emptyMap())

    private fun card(front: String, back: String): Flashcard = Flashcard(Word(front), Word(back))

    private var lesson: Lesson? = null
    private var question: Question? = null

    @BeforeEach
    fun setUp() {
        lesson = null
        question = null
    }

    @AfterEach
    fun tearDown() {
        lesson = null
        question = null
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
        question = lesson.nextQuestion()
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
    }

}
