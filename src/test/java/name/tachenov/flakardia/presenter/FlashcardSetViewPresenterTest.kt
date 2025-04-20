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

    private suspend fun assertCardsShown() {
        val state = sut.awaitStateUpdates()
        val cards = state.cards.map { TestCard(it.path, Flashcard(Word(it.question), Word(it.answer))) }.toSet()
        assertThat(cards).isEqualTo(testCards)
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

private data class TestCard(
    val path: RelativePath,
    val card: Flashcard,
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
