package name.tachenov.flakardia.presenter

import com.google.common.jimfs.Jimfs
import kotlinx.coroutines.*
import name.tachenov.flakardia.NOT_PARSEABLE_FLASHCARDS
import name.tachenov.flakardia.accessModel
import name.tachenov.flakardia.app.FlashcardSetDirEntry
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.Library
import name.tachenov.flakardia.data.FlashcardDraftId
import name.tachenov.flakardia.data.FlashcardSet
import name.tachenov.flakardia.data.RelativePath
import name.tachenov.flakardia.edtDispatcherForTesting
import name.tachenov.flakardia.storage.FlashcardStorageImpl
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
    private lateinit var config: CardSetFileEditorConfig
    private var sut: CardSetFileEditorPresenter? = null

    @BeforeEach
    fun setUp() {
        fs = Jimfs.newFileSystem()
        storage = FlashcardStorageImpl(fs.getPath("root"))
        library = Library(storage)
        config = MockConfig()
        sut = null
    }

    @AfterEach
    fun tearDown() {
        sut = null
        fs.close()
    }

    @Test
    fun `show initial content`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
        )
        edt {
            editFile(path("dir", "file.txt"))
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
            assertFirstState()
        }
    }

    @Test
    fun `show empty content`() {
        addContent(
            listOf(
                "root/dir/file.txt" to emptyList()
            )
        )
        edt {
            editFile(path("dir", "file.txt"))
            assertCards(listOf("" to ""))
            assertFirstState()
        }
    }

    @Test
    fun `remove first card`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
        )
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state1 = sut.awaitStateUpdates()
            sut.removeCard(state1.editorFullState.cards.first().id)
            assertCards(
                listOf(
                    "question b" to "answer b",
                )
            )
            assertCardRemoved(index = 0)
        }
    }

    @Test
    fun `remove a non-existing card`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
        )
        edt {
            val sut = editFile(path("dir", "file.txt"))
            sut.awaitStateUpdates()
            sut.removeCard(FlashcardDraftId(999999))
            sut.awaitStateUpdates()
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
            assertFirstState()
        }
    }

    @Test
    fun `the last card cannot be removed`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
        )
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state1 = sut.awaitStateUpdates()
            for (card in state1.editorFullState.cards) {
                sut.removeCard(card.id)
            }
            assertCards(
                listOf(
                    "question b" to "answer b",
                )
            )
            assertCardRemoved(index = 0)
        }
    }

    @Test
    fun `insert card before`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
        )
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state1 = sut.awaitStateUpdates()
            sut.insertCardBefore(state1.editorFullState.cards.first().id)
            assertCards(
                listOf(
                    "" to "",
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
            assertCardAdded(index = 0, card = "" to "")
        }
    }

    @Test
    fun `insert card before a non-existing one`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
        )
        edt {
            val sut = editFile(path("dir", "file.txt"))
            sut.awaitStateUpdates()
            sut.insertCardBefore(FlashcardDraftId(999999))
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
            assertFirstState()
        }
    }

    @Test
    fun `insert card after`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
        )
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state1 = sut.awaitStateUpdates()
            sut.insertCardAfter(state1.editorFullState.cards.first().id)
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "" to "",
                    "question b" to "answer b",
                )
            )
            assertCardAdded(index = 1, card = "" to "")
        }
    }

    @Test
    fun `insert card after a non-existing one`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
        )
        edt {
            val sut = editFile(path("dir", "file.txt"))
            sut.awaitStateUpdates()
            sut.insertCardAfter(FlashcardDraftId(999999))
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
            assertFirstState()
        }
    }

    @Test
    fun `update card question`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
        )
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state1 = sut.awaitStateUpdates()
            sut.updateQuestion(state1.editorFullState.cards.first().id, "new question")
            assertCards(
                listOf(
                    "new question" to "answer a",
                    "question b" to "answer b",
                )
            )
            assertQuestionDuplicates(listOf(emptyList(), emptyList()))
            assertAnswerDuplicates(listOf(emptyList(), emptyList()))
            assertCardsChanged(listOf(0))
            assertCardChanged(index = 0, card = "new question" to null)
        }
    }

    @Test
    fun `update non-existing card question`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
        )
        edt {
            val sut = editFile(path("dir", "file.txt"))
            sut.awaitStateUpdates()
            sut.updateQuestion(FlashcardDraftId(999999), "new question")
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
            assertFirstState()
        }
    }

    @Test
    fun `update card answer`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
        )
        edt {
            val sut = editFile(path("dir", "file.txt"))
            val state1 = sut.awaitStateUpdates()
            sut.updateAnswer(state1.editorFullState.cards.first().id, "new answer")
            assertCards(
                listOf(
                    "question a" to "new answer",
                    "question b" to "answer b",
                )
            )
            assertQuestionDuplicates(listOf(emptyList(), emptyList()))
            assertAnswerDuplicates(listOf(emptyList(), emptyList()))
            assertCardChanged(index = 0, card = null to "new answer")
        }
    }

    @Test
    fun `update non-existing card answer`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
        )
        edt {
            val sut = editFile(path("dir", "file.txt"))
            sut.awaitStateUpdates()
            sut.updateAnswer(FlashcardDraftId(999999), "new answer")
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                )
            )
            assertFirstState()
        }
    }

    @Test
    fun `answer duplicates in the same file`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to "answer a",
                ),
            )
        )
        edt {
            val path = path("dir", "file.txt")
            editFile(path)
            assertAnswerDuplicates(
                listOf(
                    listOf(
                        path to ("question c" to "answer a"),
                    ),
                    emptyList(),
                    listOf(
                        path to ("question a" to "answer a"),
                    ),
                )
            )
        }
    }

    @Test
    fun `add duplicate question`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                ),
            )
        )
        edt {
            val path = path("dir", "file.txt")
            val sut = editFile(path)
            val state1 = sut.awaitStateUpdates()
            sut.insertCardAfter(state1.editorFullState.cards.last().id)
            val state2 = sut.awaitStateUpdates()
            sut.updateQuestion(state2.editorFullState.cards.last().id, "question a")
            assertQuestionDuplicates(
                listOf(
                    listOf(
                        path to ("question a" to ""),
                    ),
                    emptyList(),
                    listOf(
                        path to ("question a" to "answer a"),
                    ),
                )
            )
            assertCardQuestionDuplicatesChanged(index = 0, listOf(path to ("question a" to "")))
            assertCardQuestionDuplicatesChanged(index = 2, listOf(path to ("question a" to "answer a")))
        }
    }

    @Test
    fun `add duplicate answer`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                ),
            )
        )
        edt {
            val path = path("dir", "file.txt")
            val sut = editFile(path)
            val state1 = sut.awaitStateUpdates()
            sut.insertCardAfter(state1.editorFullState.cards.last().id)
            val state2 = sut.awaitStateUpdates()
            sut.updateAnswer(state2.editorFullState.cards.last().id, "answer a")
            assertAnswerDuplicates(
                listOf(
                    listOf(
                        path to ("" to "answer a"),
                    ),
                    emptyList(),
                    listOf(
                        path to ("question a" to "answer a"),
                    ),
                )
            )
            assertCardAnswerDuplicatesChanged(index = 0, listOf(path to ("" to "answer a")))
            assertCardAnswerDuplicatesChanged(index = 2, listOf(path to ("question a" to "answer a")))
        }
    }

    @Test
    fun `add duplicate card`() {
        addContent(listOf(
            "root/dir/file.txt" to listOf(
                "question a" to "answer a",
                "question b" to "answer b",
            ),
        ))
        edt {
            val path = path("dir", "file.txt")
            val sut = editFile(path)
            val state1 = sut.awaitStateUpdates()
            sut.insertCardAfter(state1.editorFullState.cards.last().id)
            val state2 = sut.awaitStateUpdates()
            sut.updateQuestion(state2.editorFullState.cards.last().id, "question a")
            sut.updateAnswer(state2.editorFullState.cards.last().id, "answer a")
            assertAnswerDuplicates(
                listOf(
                    listOf(
                        path to ("question a" to "answer a"),
                    ),
                    emptyList(),
                    listOf(
                        path to ("question a" to "answer a"),
                    ),
                )
            )
            assertCardAnswerDuplicatesChanged(index = 0, listOf(path to ("question a" to "answer a")))
            assertCardAnswerDuplicatesChanged(index = 2, listOf(path to ("question a" to "answer a")))
        }
    }

    @Test
    fun `blank strings are not duplicates`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                ),
            )
        )
        edt {
            val path = path("dir", "file.txt")
            val sut = editFile(path)
            val state1 = sut.awaitStateUpdates()
            sut.insertCardAfter(state1.editorFullState.cards.last().id)
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "" to "",
                )
            )
            assertNoDuplicates()
            val state2 = sut.awaitStateUpdates()
            sut.updateQuestion(state2.editorFullState.cards.last().id, "question c")
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to "",
                )
            )
            assertNoDuplicates()
            sut.updateAnswer(state2.editorFullState.cards.last().id, " ")
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to " ",
                )
            )
            assertNoDuplicates()
            sut.insertCardAfter(state2.editorFullState.cards.last().id)
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to " ",
                    "" to "",
                )
            )
            val state3 = sut.awaitStateUpdates()
            sut.updateQuestion(state3.editorFullState.cards.last().id, " ")
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to " ",
                    " " to "",
                )
            )
            assertNoDuplicates()
            sut.updateAnswer(state3.editorFullState.cards.last().id, "answer d")
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to " ",
                    " " to "answer d",
                )
            )
            assertNoDuplicates()
            sut.insertCardAfter(state3.editorFullState.cards.last().id)
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to " ",
                    " " to "answer d",
                    "" to "",
                )
            )
            assertNoDuplicates()
            val state4 = sut.awaitStateUpdates()
            sut.updateQuestion(state4.editorFullState.cards.last().id, " ")
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to " ",
                    " " to "answer d",
                    " " to "",
                )
            )
            assertNoDuplicates()
            sut.updateAnswer(state4.editorFullState.cards.last().id, " ")
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to " ",
                    " " to "answer d",
                    " " to " ",
                )
            )
            assertNoDuplicates()
            assertCards(
                listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to " ",
                    " " to "answer d",
                    " " to " ",
                )
            )
            assertNoDuplicates()
        }
    }

    @Test
    fun `answer duplicates in another file`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to "answer c",
                ),
                "root/dir/file2.txt" to listOf(
                    "question d" to "answer a",
                ),
            )
        )
        edt {
            val path = path("dir", "file.txt")
            editFile(path, detectDuplicatesIn = path("dir"))
            val path2 = path("dir", "file2.txt")
            assertAnswerDuplicates(
                listOf(
                    listOf(
                        path2 to ("question d" to "answer a"),
                    ),
                    emptyList(),
                    emptyList(),
                )
            )
        }
    }

    @Test
    fun `question duplicates in another file`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to "answer c",
                ),
                "root/dir/file2.txt" to listOf(
                    "question a" to "answer d",
                ),
            )
        )
        edt {
            val path = path("dir", "file.txt")
            editFile(path, detectDuplicatesIn = path("dir"))
            val path2 = path("dir", "file2.txt")
            assertQuestionDuplicates(
                listOf(
                    listOf(
                        path2 to ("question a" to "answer d"),
                    ),
                    emptyList(),
                    emptyList(),
                )
            )
        }
    }

    @Test
    fun `duplicates in another file when some file has errors`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to "answer c",
                ),
                "root/dir/file2.txt" to listOf(
                    "question a" to "answer d",
                ),
            )
        )
        Files.writeString(fs.getPath("root/err.txt"), NOT_PARSEABLE_FLASHCARDS)
        edt {
            val path = path("dir", "file.txt")
            editFile(path, detectDuplicatesIn = path())
            val path2 = path("dir", "file2.txt")
            assertQuestionDuplicates(
                listOf(
                    listOf(
                        path2 to ("question a" to "answer d"),
                    ),
                    emptyList(),
                    emptyList(),
                )
            )
        }
    }

    @Test
    fun `no duplicates in another file`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to "answer c",
                ),
                "root/dir/file2.txt" to listOf(
                    "question d" to "answer d",
                ),
            )
        )
        edt {
            val path = path("dir", "file.txt")
            val sut = editFile(path)
            assertDuplicateDetectionState(availablePaths = listOf("root", "root/dir", "root/dir/file.txt"), selectedPath = "root/dir/file.txt")
            sut.detectDuplicatesIn(FlashcardSetDirEntry(path("dir")))
            assertDuplicateDetectionState(availablePaths = listOf("root", "root/dir", "root/dir/file.txt"), selectedPath = "root/dir")
            assertStateNotChanged()
            assertSavedState()
            assertQuestionDuplicates(
                listOf(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                )
            )
        }
    }

    @Test
    fun `change duplicate detection area`() {
        addContent(
            listOf(
                "root/dir/file.txt" to listOf(
                    "question a" to "answer a",
                    "question b" to "answer b",
                    "question c" to "answer c",
                ),
                "root/dir/file2.txt" to listOf(
                    "question a" to "answer d",
                ),
                "root/file3.txt" to listOf(
                    "question a" to "answer e",
                ),
            )
        )
        edt {
            val path = path("dir", "file.txt")
            val dirPath = path("dir")
            val path2 = path("dir", "file2.txt")
            val path3 = path("file3.txt")
            val rootPath = path()
            val sut = editFile(path)
            assertConfig(null)
            assertSavedState()
            assertDuplicateDetectionState(availablePaths = listOf("root", "root/dir", "root/dir/file.txt"), selectedPath = "root/dir/file.txt")
            sut.detectDuplicatesIn(FlashcardSetDirEntry(rootPath))
            assertConfig(rootPath)
            assertSavedState()
            assertDuplicateDetectionState(availablePaths = listOf("root", "root/dir", "root/dir/file.txt"), selectedPath = "root")
            assertQuestionDuplicates(
                listOf(
                    listOf(
                        path2 to ("question a" to "answer d"),
                        path3 to ("question a" to "answer e"),
                    ),
                    emptyList(),
                    emptyList(),
                )
            )
            sut.detectDuplicatesIn(FlashcardSetDirEntry(dirPath))
            assertConfig(dirPath)
            assertSavedState()
            assertDuplicateDetectionState(availablePaths = listOf("root", "root/dir", "root/dir/file.txt"), selectedPath = "root/dir")
            assertQuestionDuplicates(
                listOf(
                    listOf(
                        path2 to ("question a" to "answer d"),
                    ),
                    emptyList(),
                    emptyList(),
                )
            )
        }
    }

    private suspend fun assertCards(cards: List<Pair<String, String>>) {
        val state = checkNotNull(sut?.awaitStateUpdates())
        assertThat(state.editorFullState.cards.map { it.question.word to it.answer.word }).isEqualTo(cards)
    }

    private suspend fun assertFirstState() {
        val state = checkNotNull(sut?.awaitStateUpdates())
        assertThat(state.changeFromPrevious).isEqualTo(CardSetFileEditorFirstState)
    }

    private suspend fun assertStateNotChanged() {
        val state = checkNotNull(sut?.awaitStateUpdates())
        assertThat(state.changeFromPrevious).isIn(CardSetFileEditorFirstState, CardSetFileEditorNoChange)
    }

    private suspend fun assertSavedState() {
        val state = checkNotNull(sut?.awaitStateUpdates())
        assertThat(state.persistenceState).isEqualTo(CardSetFileEditorSavedState(emptyList()))
    }

    private suspend fun assertCardRemoved(index: Int) {
        val state = checkNotNull(sut?.awaitStateUpdates())
        assertThat(state.changeFromPrevious).isEqualTo(CardRemoved(index))
    }

    private suspend fun assertCardAdded(index: Int, card: Pair<String, String>) {
        val state = checkNotNull(sut?.awaitStateUpdates())
        state.changeFromPrevious as CardAdded
        assertThat(state.changeFromPrevious.index).isEqualTo(index)
        assertThat(state.changeFromPrevious.card.question.word).isEqualTo(card.first)
        assertThat(state.changeFromPrevious.card.answer.word).isEqualTo(card.second)
    }

    private suspend fun assertCardsChanged(indices: List<Int>) {
        val state = checkNotNull(sut?.awaitStateUpdates())
        state.changeFromPrevious as CardsChanged
        assertThat(state.changeFromPrevious.changes.map { it.index }).isEqualTo(indices)
    }

    private suspend fun assertCardChanged(index: Int, card: Pair<String?, String?>) {
        val state = checkNotNull(sut?.awaitStateUpdates())
        state.changeFromPrevious as CardsChanged
        val change = state.changeFromPrevious.changes.find { it.index == index }
        assertThat(change?.updatedQuestion?.word).isEqualTo(card.first)
        assertThat(change?.updatedAnswer?.word).isEqualTo(card.second)
    }

    private suspend fun assertDuplicateDetectionState(availablePaths: List<String>, selectedPath: String) {
        val state = checkNotNull(sut?.awaitStateUpdates())
        assertThat(state.duplicateDetectionState.availablePaths.map { it.toString() }).isEqualTo(availablePaths)
        assertThat(state.duplicateDetectionState.selectedPath.toString()).isEqualTo(selectedPath)
    }

    private suspend fun assertConfig(savedPath: RelativePath?) {
        assertThat(config.duplicateDetectionPath?.path).isEqualTo(savedPath)
    }

    private suspend fun assertQuestionDuplicates(duplicates: List<List<Pair<RelativePath, Pair<String, String>>>>) {
        val state = checkNotNull(sut?.awaitStateUpdates())
        assertThat(state.editorFullState.cards.map { card ->
            card.question.duplicates.map { it.path to (it.question to it.answer) }
        }).isEqualTo(duplicates)
    }

    private suspend fun assertAnswerDuplicates(duplicates: List<List<Pair<RelativePath, Pair<String, String>>>>) {
        val state = checkNotNull(sut?.awaitStateUpdates())
        assertThat(state.editorFullState.cards.map { card ->
            card.answer.duplicates.map { it.path to (it.question to it.answer) }
        }).isEqualTo(duplicates)
    }

    private suspend fun assertNoDuplicates() {
        val state = checkNotNull(sut?.awaitStateUpdates())
        assertThat(state.editorFullState.cards.map { card ->
            card.question.duplicates
        }).allMatch { it.isEmpty() }
        assertThat(state.editorFullState.cards.map { card ->
            card.answer.duplicates
        }).allMatch { it.isEmpty() }
    }

    private suspend fun assertCardQuestionDuplicatesChanged(index: Int, duplicates: List<Pair<RelativePath, Pair<String, String>>>) {
        val state = checkNotNull(sut?.awaitStateUpdates())
        state.changeFromPrevious as CardsChanged
        val change = state.changeFromPrevious.changes.find { it.index == index }
        assertThat(change?.updatedQuestion?.duplicates?.map { it.path to (it.question to it.answer) }).isEqualTo(duplicates)
    }

    private suspend fun assertCardAnswerDuplicatesChanged(index: Int, duplicates: List<Pair<RelativePath, Pair<String, String>>>) {
        val state = checkNotNull(sut?.awaitStateUpdates())
        state.changeFromPrevious as CardsChanged
        val change = state.changeFromPrevious.changes.find { it.index == index }
        assertThat(change?.updatedAnswer?.duplicates?.map { it.path to (it.question to it.answer) }).isEqualTo(duplicates)
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

    private suspend fun CoroutineScope.editFile(path: RelativePath, detectDuplicatesIn: RelativePath? = null): CardSetFileEditorPresenter {
        val fileEntry = FlashcardSetFileEntry(path)
        val flashcardSet = accessModel {
            library.readFlashcards(fileEntry) as FlashcardSet
        }
        config.duplicateDetectionPath = detectDuplicatesIn?.let { FlashcardSetDirEntry(it) }
        val presenter = CardSetFileEditorPresenter(
            config,
            library,
            fileEntry,
            initialContent = flashcardSet.cards.map { it.flashcard },
        )
        launch {
            presenter.run(MockView())
        }
        presenter.awaitStateUpdates() // await the initial state
        sut = presenter
        return presenter
    }

    private class MockConfig : CardSetFileEditorConfig {
        override var duplicateDetectionPath: FlashcardSetDirEntry? = null
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
