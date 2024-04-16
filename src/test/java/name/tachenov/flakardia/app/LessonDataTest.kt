package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class LessonDataTest {
    @Test
    fun `4 words, 3 recent, 1 mistake each`() {
        doTest(
            "2024-04-16T10:00",
            testWords = listOf(
                TestWord("word a", "2024-04-15T09:00", mistakes = 1, prevInterval = 1.0),
                TestWord("word b", "2024-04-15T09:00", mistakes = 1, prevInterval = 1.0),
                TestWord("word c", "2024-04-15T09:00", mistakes = 1, prevInterval = 1.0),
                TestWord("word d", "2024-04-15T11:00", mistakes = 1, prevInterval = 1.0),
            ),
            expectedWords = listOf(
                "word a",
                "word b",
                "word c",
            )
        )
    }

    private fun doTest(
        now: String,
        testWords: List<TestWord>,
        expectedWords: List<String>,
        maxWordsPerLesson: Int = 3,
        intervalMultiplierWithoutMistakes: Double = 1.5,
        intervalMultiplierWithMistake: Double = 1.0,
        intervalMultiplierWithManyMistakes: Double = 0.5,
    ) {
        val actualWords = prepareLessonData(
            time(now),
            "test",
            buildFlashcards(testWords),
            buildStats(testWords),
            LessonSettings(
                maxWordsPerLesson,
                intervalMultiplierWithoutMistakes,
                intervalMultiplierWithMistake,
                intervalMultiplierWithManyMistakes,
            )
        ).flashcards.map { it.flashcard.back }
        val actualWordSet = actualWords.toSet()
        val expectedWordSet = expectedWords.map { Word(it) }.toSet()
        assertThat(actualWordSet).isEqualTo(expectedWordSet)
    }

    private fun buildFlashcards(testWords: List<TestWord>): List<FlashcardData> =
        testWords.map {
            FlashcardData(RelativePath(listOf("test")), Flashcard(Word("front"), Word(it.word)))
        }

    private fun buildStats(testWords: List<TestWord>): LibraryStats =
        LibraryStats(
            testWords.associate {
                Word(it.word) to WordStats(time(it.lastLesson), interval(it.prevInterval), it.mistakes)
            }
        )

    private fun time(string: String): Instant = Instant.parse("$string:00Z")

    private fun interval(value: Double): Duration = Duration.ofMillis((value * 86_400_000.0).toLong())

    private data class TestWord(
        val word: String,
        val lastLesson: String,
        val mistakes: Int,
        val prevInterval: Double
    )

}
