package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DuplicateDetectionTest {
    @Test
    fun `no duplicates`() {
        doTest(
            cards = listOf(
                "front a" to "back a",
                "front b" to "back b",
                "front c" to "back c",
            ),
            expectedWarnings = emptyList(),
        )
    }

    @Test
    fun `duplicate front`() {
        doTest(
            cards = listOf(
                "front a" to "back a",
                "front a" to "back b",
                "front c" to "back c",
            ),
            expectedWarnings = listOf("front a"),
        )
    }

    @Test
    fun `duplicate back`() {
        doTest(
            cards = listOf(
                "front a" to "back a",
                "front b" to "back a",
                "front c" to "back c",
            ),
            expectedWarnings = listOf("back a"),
        )
    }

    @Test
    fun `duplicate both`() {
        doTest(
            cards = listOf(
                "front a" to "back a",
                "front b" to "back a",
                "front b" to "back c",
            ),
            expectedWarnings = listOf("back a", "front b"),
        )
    }

    private fun doTest(cards: List<Pair<String, String>>, expectedWarnings: List<String>) {
        val storage = MockStorage(cards)
        val sut = Library(storage)
        val actual = sut.prepareLessonData(storage.readEntries(storage.rootPath).first())
        val actualWarnings = getWarnings(actual)
        if (expectedWarnings.isEmpty()) {
            assertThat(actualWarnings).isEmpty()
        }
        else {
            for (expectedWarning in expectedWarnings) {
                assertThat(actualWarnings).`as`("must contain $expectedWarning").anyMatch { it.contains(expectedWarning) }
            }
        }
    }

    private fun getWarnings(result: LessonDataResult): List<String> {
        if (result !is LessonDataWarnings) return emptyList()
        return result.warnings
    }

    private class MockStorage(private val cards: List<Pair<String, String>>) : FlashcardStorage {
        override val path: String
            get() = "test"

        val rootPath = RelativePath(listOf(path))

        private val filePath = RelativePath(listOf(path, "test.txt"))

        override fun readEntries(path: RelativePath): List<FlashcardSetListEntry> = listOf(FlashcardSetFileEntry(filePath))

        override fun readFlashcards(path: RelativePath): FlashcardSetResult =
            FlashcardSet(
                cards.map {
                    FlashcardData(filePath, Flashcard(Word(it.first), Word(it.second)))
                }
            )

        override fun readLibraryStats(): LibraryStatsResult = LibraryStats(emptyMap())

        override fun saveLibraryStats(stats: LibraryStats): SaveResult = SaveSuccess

        override fun saveFlashcardSetFile(path: RelativePath, flashcards: List<Flashcard>): SaveResult = SaveSuccess

        override fun createDir(path: RelativePath) { }

        override fun createFile(path: RelativePath) { }
    }
}
