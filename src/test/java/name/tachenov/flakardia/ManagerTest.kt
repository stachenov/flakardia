package name.tachenov.flakardia

import com.google.common.jimfs.Jimfs
import name.tachenov.flakardia.app.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path

class ManagerTest {

    private lateinit var fs: FileSystem
    private lateinit var root: Path

    @BeforeEach
    fun setUp() {
        fs = Jimfs.newFileSystem()
        root = fs.getPath("cards")
    }

    @AfterEach
    fun tearDown() {
        fs.close()
    }

    @Test
    fun `empty cards dir`() {
        test(dir("cards"), expect())
    }

    @Test
    fun `a couple of files`() {
        test(
            dir("cards", file("test.cards"), file("test2.cards")),
            expect(path("cards/test.cards"), path("cards/test2.cards")),
        )
    }

    @Test
    fun `no cards dir`() {
        test(
            structure = null,
            expect("cards"),
        )
    }

    private fun test(structure: Dir?, expectation: Expectation) {
        if (structure != null) {
            create(structure)
        }
        val sut = CardManager()
        val enterResult = sut.enter(root)
        expectation.match(enterResult, sut.entries)
    }

    private fun create(dir: Dir, parentPath: Path = fs.getPath(".")) {
        val dirPath = parentPath.resolve(dir.name)
        Files.createDirectory(dirPath)
        for (entry in dir.entries) {
            when (entry) {
                is File -> Files.createFile(dirPath.resolve(entry.name))
                is Dir -> create(entry, dirPath)
            }
        }
    }

    private fun dir(name: String, vararg entries: Entry): Dir = Dir(name, entries.toList())

    private fun file(name: String): File = File(name)

    private fun path(path: String): FlashcardSetFileEntry = FlashcardSetFileEntry(fs.getPath(path))

    private fun expect(vararg entry: FlashcardSetListEntry): Expectation = ListExpectation(entry.toList())

    private fun expect(errorMessage: String): Expectation = ErrorExpectation(errorMessage)

    private sealed class Entry

    private class Dir(val name: String, val entries: List<Entry>) : Entry()

    private class File(val name: String) : Entry()

    private sealed class Expectation {
        abstract fun match(result: DirEnterResult, entries: List<FlashcardSetListEntry>)
    }

    private class ListExpectation(private val expected: List<FlashcardSetListEntry>) : Expectation() {
        override fun match(result: DirEnterResult, entries: List<FlashcardSetListEntry>) {
            assertThat(result).isInstanceOf(DirEnterSuccess::class.java)
            assertThat(entries).isEqualTo(expected)
        }
    }

    private class ErrorExpectation(private val expectedErrorMessage: String) : Expectation() {
        override fun match(result: DirEnterResult, entries: List<FlashcardSetListEntry>) {
            assertThat(result).isInstanceOf(DirEnterError::class.java)
            result as DirEnterError
            assertThat(result.message).containsIgnoringCase(expectedErrorMessage)
            assertThat(entries).isEmpty()
        }
    }

}
