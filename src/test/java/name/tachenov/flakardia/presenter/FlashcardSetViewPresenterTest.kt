package name.tachenov.flakardia.presenter

import com.google.common.jimfs.Jimfs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import name.tachenov.flakardia.accessModel
import name.tachenov.flakardia.app.FlashcardSetDirEntry
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.Library
import name.tachenov.flakardia.data.*
import name.tachenov.flakardia.storage.FlashcardStorageImpl
import name.tachenov.flakardia.uiTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.time.*
import kotlin.io.path.name

class FlashcardSetViewPresenterTest {
    private lateinit var fs: FileSystem
    private lateinit var root: Path
    private lateinit var dir: Path
    private lateinit var storage: FlashcardStorageImpl
    private lateinit var library: Library
    private lateinit var sut: FlashcardSetViewPresenter
    private lateinit var testCards: Set<TestCard>

    @BeforeEach
    fun setUp() {
        fs = Jimfs.newFileSystem()
        root = fs.getPath("root")
        dir = root.resolve("dir")
        Files.createDirectories(root)
        storage = FlashcardStorageImpl(root)
        library = Library(storage)
    }

    @AfterEach
    fun tearDown() {
        fs.close()
    }

    @Test
    fun `initial state - no stats`() {
        uiTest {
            generateWords()
            viewFlashcards()
            assertCardsShown()
        }
    }

    @Test
    fun `initial state - some stats`() {
        uiTest {
            generateWords()
            val stats = generateSomeStats()
            viewFlashcards()
            assertCardsShown()
            assertStats(stats)
        }
    }

    private suspend fun generateSomeStats(): Map<TestCard, TestStats> =
        accessModel {
            val cardList = testCards.toList()
            val card1 = cardList[0]
            val card2 = cardList[1]
            val date1 = LocalDate.of(2025, Month.APRIL, 20)
            val date2 = LocalDate.of(2025, Month.MARCH, 21)
            val time1 = LocalTime.of(22, 5, 15)
            val time2 = LocalTime.of(8, 55, 45)
            val duration1 = Duration.ofDays(3)
            val duration2 = Duration.ofHours(5)
            val wordStats1 = WordStats(
                lastLearned = date1.atTime(time1).atZone(TIME_ZONE).toInstant(),
                intervalBeforeLastLearned = duration1,
                mistakes = 1,
            )
            val wordStats2 = WordStats(
                lastLearned = date2.atTime(time2).atZone(TIME_ZONE).toInstant(),
                intervalBeforeLastLearned = duration2,
                mistakes = 0,
            )
            library.saveUpdatedStats(LibraryStats(mapOf(card1.card.answer to wordStats1, card2.card.answer to wordStats2)))
            mapOf(
                card1 to TestStats(
                    lastLearnedString = "2025-04-20 22:05:15",
                    mistakes = 1,
                    intervalString = "3.00",
                ),
                card2 to TestStats(
                    lastLearnedString = "2025-03-21 08:55:45",
                    mistakes = 0,
                    intervalString = "0.21",
                ),
            )
        }

    private suspend fun assertCardsShown() {
        val state = sut.awaitStateUpdates()
        val cards = state.cards.map { it.toTestCard() }.toSet()
        assertThat(cards).isEqualTo(testCards)
    }

    private suspend fun assertStats(expectedStats: Map<TestCard, TestStats>) {
        val state = sut.awaitStateUpdates()
        val actualStats = state.cards.associate {
            it.toTestCard() to it.toTestStats()
        }
        val emptyStats = (actualStats.keys - expectedStats.keys).associateWith { emptyStats() }
        assertThat(actualStats).isEqualTo(expectedStats + emptyStats)
    }

    private suspend fun CoroutineScope.viewFlashcards() {
        accessModel {
            sut = FlashcardSetViewPresenter(library.getAllFlashcards(FlashcardSetDirEntry(RelativePath(listOf("dir")))) as LessonData)
        }
        launch {
            sut.run(MockView())
        }
    }

    private suspend fun generateWords() {
        accessModel {
            val allCards = mutableSetOf<TestCard>()
            for (pathIndex in 1..5) {
                val cards = (1..10).map {  wordIndex ->
                    Flashcard(Word("question $pathIndex.$wordIndex"), Word("answer $pathIndex.$wordIndex"))
                }
                val path = path(pathIndex)
                library.saveFlashcardSetFile(FlashcardSetFileEntry(path), cards.map { UpdatedOrNewFlashcard(null, it) })
                allCards += cards.map { TestCard(path, it) }
            }
            testCards = allCards
        }
    }

    private fun path(i: Int): RelativePath = RelativePath(listOf(dir.name, "subdir$i"))
}

private fun FlashcardViewModel.toTestCard() = TestCard(
    path = path,
    card = Flashcard(Word(question), Word(answer))
)

private fun FlashcardViewModel.toTestStats() = TestStats(
    lastLearnedString = lastLearned.toString(),
    mistakes = mistakes,
    intervalString = intervalBeforeLastLearned.toString(),
)

private data class TestCard(
    val path: RelativePath,
    val card: Flashcard,
)

private fun emptyStats() = TestStats("", null, "")

private data class TestStats(
    val lastLearnedString: String,
    val mistakes: Int?,
    val intervalString: String,
)

private class MockView : FlashcardSetView {
    override suspend fun run() {
        awaitCancellation()
    }

    override fun adjustSize() { }

    override fun showError(title: String, message: String) { }

    override fun showWarnings(warnings: List<String>) { }

    override val isApplyingStateNow: Boolean = false
}

private val TIME_ZONE: ZoneId = ZoneId.systemDefault()
