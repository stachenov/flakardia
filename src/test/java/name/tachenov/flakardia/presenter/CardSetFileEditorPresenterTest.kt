package name.tachenov.flakardia.presenter

import com.google.common.jimfs.Jimfs
import kotlinx.coroutines.*
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.Library
import name.tachenov.flakardia.background
import name.tachenov.flakardia.data.FlashcardSet
import name.tachenov.flakardia.data.RelativePath
import name.tachenov.flakardia.edtDispatcherForTesting
import name.tachenov.flakardia.storage.FlashcardStorageImpl
import name.tachenov.flakardia.underModelLock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.FileSystem
import java.nio.file.Files

class CardSetFileEditorPresenterTest {
    private lateinit var fs: FileSystem
    private lateinit var storage: FlashcardStorageImpl
    private lateinit var library: Library

    @BeforeEach
    fun setUp() {
        fs = Jimfs.newFileSystem()
        storage = FlashcardStorageImpl(fs.getPath("root"))
        library = Library(storage)
    }

    @AfterEach
    fun tearDown() {
        fs.close()
    }

    @Test
    fun `show initial content`() {
        addContent(listOf(
            "root/dir/file.txt" to listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            )
        ))
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state = sut.awaitStateUpdates()
            assertCards(state, listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            ))
            assertFirstState(state)
        }
    }

    @Test
    fun `show empty content`() {
        addContent(listOf(
            "root/dir/file.txt" to emptyList()
        ))
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state = sut.awaitStateUpdates()
            assertCards(state, listOf("" to ""))
            assertFirstState(state)
        }
    }

    @Test
    fun `remove first card`() {
        addContent(listOf(
            "root/dir/file.txt" to listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            )
        ))
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state1 = sut.awaitStateUpdates()
            sut.removeCard(state1.editorFullState.cards.first().id)
            val state2 = sut.awaitStateUpdates()
            assertCards(state2, listOf(
                "question b" to "answer b",
            ))
            assertCardRemoved(state2, index = 0)
        }
    }

    @Test
    fun `remove a non-existing card`() {
        addContent(listOf(
            "root/dir/file.txt" to listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            )
        ))
        edt {
            val sut = editFile(path("dir", "file.txt"))
            sut.awaitStateUpdates()
            sut.removeCard(CardId(999999))
            val state2 = sut.awaitStateUpdates()
            assertCards(state2, listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            ))
            assertFirstState(state2)
        }
    }

    @Test
    fun `the last card cannot be removed`() {
        addContent(listOf(
            "root/dir/file.txt" to listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            )
        ))
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state1 = sut.awaitStateUpdates()
            for (card in state1.editorFullState.cards) {
                sut.removeCard(card.id)
            }
            val state2 = sut.awaitStateUpdates()
            assertCards(state2, listOf(
                "question b" to "answer b",
            ))
            assertCardRemoved(state2, index = 0)
        }
    }

    @Test
    fun `insert card before`() {
        addContent(listOf(
            "root/dir/file.txt" to listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            )
        ))
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state1 = sut.awaitStateUpdates()
            sut.insertCardBefore(state1.editorFullState.cards.first().id)
            val state2 = sut.awaitStateUpdates()
            assertCards(state2, listOf(
                "" to "",
                "question a" to "answer a",
                "question b" to "answer b",
            ))
            assertCardAdded(state2, index = 0, card = "" to "")
        }
    }

    @Test
    fun `insert card before a non-existing one`() {
        addContent(listOf(
            "root/dir/file.txt" to listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            )
        ))
        edt {
            val sut = editFile(path("dir", "file.txt"))
            sut.awaitStateUpdates()
            sut.insertCardBefore(CardId(999999))
            val state2 = sut.awaitStateUpdates()
            assertCards(state2, listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            ))
            assertFirstState(state2)
        }
    }

    @Test
    fun `insert card after`() {
        addContent(listOf(
            "root/dir/file.txt" to listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            )
        ))
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state1 = sut.awaitStateUpdates()
            sut.insertCardAfter(state1.editorFullState.cards.first().id)
            val state2 = sut.awaitStateUpdates()
            assertCards(state2, listOf(
                "question a" to "answer a",
                "" to "",
                "question b" to "answer b",
            ))
            assertCardAdded(state2, index = 1, card = "" to "")
        }
    }

    @Test
    fun `insert card after a non-existing one`() {
        addContent(listOf(
            "root/dir/file.txt" to listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            )
        ))
        edt {
            val sut = editFile(path("dir", "file.txt"))
            sut.awaitStateUpdates()
            sut.insertCardAfter(CardId(999999))
            val state2 = sut.awaitStateUpdates()
            assertCards(state2, listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            ))
            assertFirstState(state2)
        }
    }

    @Test
    fun `update card question`() {
        addContent(listOf(
            "root/dir/file.txt" to listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            )
        ))
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state1 = sut.awaitStateUpdates()
            sut.updateQuestion(state1.editorFullState.cards.first().id, "new question")
            val state2 = sut.awaitStateUpdates()
            assertCards(state2, listOf(
                "new question" to "answer a",
                "question b" to "answer b",
            ))
            assertCardChanged(state2, index = 0, card = "new question" to null)
        }
    }

    @Test
    fun `update non-existing card question`() {
        addContent(listOf(
            "root/dir/file.txt" to listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            )
        ))
        edt {
            val sut = editFile(path("dir", "file.txt"))
            sut.awaitStateUpdates()
            sut.updateQuestion(CardId(999999), "new question")
            val state2 = sut.awaitStateUpdates()
            assertCards(state2, listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            ))
            assertFirstState(state2)
        }
    }

    @Test
    fun `update card answer`() {
        addContent(listOf(
            "root/dir/file.txt" to listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            )
        ))
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state1 = sut.awaitStateUpdates()
            sut.updateAnswer(state1.editorFullState.cards.first().id, "new answer")
            val state2 = sut.awaitStateUpdates()
            assertCards(state2, listOf(
                "question a" to "new answer",
                "question b" to "answer b",
            ))
            assertCardChanged(state2, index = 0, card = null to "new answer")
        }
    }

    @Test
    fun `update non-existing card answer`() {
        addContent(listOf(
            "root/dir/file.txt" to listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            )
        ))
        edt {
            val sut = editFile(path("dir", "file.txt"))
            sut.awaitStateUpdates()
            sut.updateAnswer(CardId(999999), "new answer")
            val state2 = sut.awaitStateUpdates()
            assertCards(state2, listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            ))
            assertFirstState(state2)
        }
    }

    private fun assertCards(state: CardSetFileEditorState, cards: List<Pair<String, String>>) {
        assertThat(state.editorFullState.cards.map { it.question to it.answer }).isEqualTo(cards)
    }

    private fun assertFirstState(state: CardSetFileEditorState) {
        assertThat(state.changeFromPrevious).isEqualTo(CardSetFileEditorFirstState)
    }

    private fun assertCardRemoved(state: CardSetFileEditorState, index: Int) {
        assertThat(state.changeFromPrevious).isEqualTo(CardRemoved(index))
    }

    private fun assertCardAdded(state: CardSetFileEditorState, index: Int, card: Pair<String, String>) {
        state.changeFromPrevious as CardAdded
        assertThat(state.changeFromPrevious.index).isEqualTo(index)
        assertThat(state.changeFromPrevious.card.question).isEqualTo(card.first)
        assertThat(state.changeFromPrevious.card.answer).isEqualTo(card.second)
    }

    private fun assertCardChanged(state: CardSetFileEditorState, index: Int, card: Pair<String?, String?>) {
        state.changeFromPrevious as CardChanged
        assertThat(state.changeFromPrevious.index).isEqualTo(index)
        assertThat(state.changeFromPrevious.updatedQuestion).isEqualTo(card.first)
        assertThat(state.changeFromPrevious.updatedAnswer).isEqualTo(card.second)
    }

    private fun edt(block: suspend CoroutineScope.() -> Unit) {
        var done = false
        try {
            runBlocking {
                withContext(edtDispatcherForTesting) {
                    block()
                    done = true
                    cancel()
                }
            }
        } catch (e: CancellationException) {
            if (!done) throw e
        }
    }

    private fun path(vararg elements: String): RelativePath = RelativePath(elements.toList())

    private fun addContent(files: List<Pair<String, List<Pair<String, String>>>>) {
        for (file in files) {
            val filePath = fs.getPath(file.first)
            val fileContent = file.second
            Files.createDirectories(filePath.parent)
            Files.write(filePath, fileContent.map { "${it.first}\n${it.second}\n" })
        }
    }

    private suspend fun CoroutineScope.editFile(path: RelativePath): CardSetFileEditorPresenter {
        val fileEntry = FlashcardSetFileEntry(path)
        val flashcardSet = underModelLock {
            background {
                library.readFlashcards(fileEntry) as FlashcardSet
            }
        }
        val presenter = CardSetFileEditorPresenter(
            library,
            fileEntry,
            initialContent = flashcardSet.cards.map { it.flashcard },
        )
        launch {
            presenter.run(MockView())
        }
        return presenter
    }

    private class MockView : CardSetFileEditorView {
        override suspend fun run() {
            awaitCancellation()
        }

        override fun adjustSize() { }

        override fun showError(title: String, message: String) { }

        override fun showWarnings(warnings: List<String>) { }
    }

}
