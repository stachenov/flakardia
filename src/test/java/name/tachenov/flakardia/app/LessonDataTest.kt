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

    @Test
    fun `4 words, 3 recent, 1 very recent without mistakes`() {
        doTest(
            "2024-04-16T10:00",
            testWords = listOf(
                TestWord("word a", "2024-04-16T09:00", mistakes = 0, prevInterval = 0.01),
                TestWord("word b", "2024-04-15T09:00", mistakes = 1, prevInterval = 1.0),
                TestWord("word c", "2024-04-15T09:00", mistakes = 1, prevInterval = 1.0),
                TestWord("word d", "2024-04-15T11:00", mistakes = 1, prevInterval = 1.0),
            ),
            expectedWords = listOf(
                "word b",
                "word c",
                "word d",
            )
        )
    }

    @Test
    fun randomness() {
        doTest(
            "2024-04-16T10:00",
            testWords = listOf(
                TestWord("word a", "2024-04-16T09:00", mistakes = 0, prevInterval = 0.01),
                TestWord("word b", "2024-04-15T09:00", mistakes = 1, prevInterval = 1.0),
                TestWord("word c", "2024-04-15T09:00", mistakes = 1, prevInterval = 1.0),
                TestWord("word d", "2024-04-15T09:00", mistakes = 1, prevInterval = 1.0),
                TestWord("word e", "2024-04-14T09:15", mistakes = 1, prevInterval = 2.0),
                TestWord("word f", "2024-04-14T09:15", mistakes = 1, prevInterval = 2.0),
                TestWord("word g", "2024-04-14T09:15", mistakes = 1, prevInterval = 2.0),
            ),
            expectedWords = listOf("word b", "word c", "word d", "word e", "word f", "word g"),
            expectedCount = 3,
            randomness = 0.1,
        )
    }

    private fun doTest(
        now: String,
        testWords: List<TestWord>,
        expectedWords: List<String>,
        expectedCount: Int = expectedWords.size,
        maxWordsPerLesson: Int = 3,
        intervalMultiplierWithoutMistakes: Double = 1.5,
        minIntervalWithoutMistakes: Double = 1.0,
        intervalMultiplierWithMistake: Double = 1.0,
        minIntervalWithMistake: Double = 0.5,
        intervalMultiplierWithManyMistakes: Double = 0.5,
        minIntervalWithManyMistakes: Double = 0.1,
        randomness: Double = 0.0,
    ) {
        val allActualWords = hashSetOf<Word>()
        val expectedWordSet = expectedWords.map { Word(it) }.toSet()
        repeat(if (randomness > 0.0) 100 else 1) {
            val actualWords = prepareLessonData(
                time(now),
                "test",
                buildFlashcards(testWords),
                buildStats(testWords),
                LessonSettings(
                    maxWordsPerLesson,
                    intervalMultiplierWithoutMistakes,
                    interval(minIntervalWithoutMistakes),
                    intervalMultiplierWithMistake,
                    interval(minIntervalWithMistake),
                    intervalMultiplierWithManyMistakes,
                    interval(minIntervalWithManyMistakes),
                    randomness,
                )
            ).flashcards.map { it.flashcard.back }
            val actualWordSet = actualWords.toSet()
            assertThat(actualWordSet).isSubsetOf(expectedWordSet)
            assertThat(actualWordSet).hasSize(expectedCount)
            allActualWords += actualWordSet
        }
        assertThat(allActualWords).isEqualTo(expectedWordSet)
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

    private fun expectedWords(vararg words: String): List<ExpectedWord> = words.map { ExpectedWord(it) }

    private data class ExpectedWord(
        val word: String,
    )

}
