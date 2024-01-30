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
        test(dir("cards"), expect(root))
    }

    @Test
    fun `a couple of files`() {
        test(
            dir("cards", file("test.cards"), file("test2.cards")),
            expect(root, filePath("cards/test.cards"), filePath("cards/test2.cards")),
        )
    }

    @Test
    fun `no cards dir`() {
        test(
            structure = null,
            expect("cards", path = null),
        )
    }

    @Test
    fun `entering and leaving subdirectory`() {
        create(dir("cards", dir("sub", file("sub-file.cards")), file("file1.cards"), file("file2.cards")))
        val sut = CardManager()
        var enterResult = sut.enterLibrary(root)
        expect(root, dirPath("cards/sub"), filePath("cards/file1.cards"), filePath("cards/file2.cards")).match(enterResult, sut.path, sut.entries)
        val subPath = root.resolve("sub")
        enterResult = sut.enter(subPath)
        expect(subPath, upPath("cards"), filePath("cards/sub/sub-file.cards")).match(enterResult, sut.path, sut.entries)
        enterResult = sut.enter(root)
        expect(root, dirPath("cards/sub"), filePath("cards/file1.cards"), filePath("cards/file2.cards")).match(enterResult, sut.path, sut.entries)
    }

    @Test
    fun `entering non-existent dir`() {
        create(dir("cards", file("file1.cards"), file("file2.cards")))
        val sut = CardManager()
        var enterResult = sut.enterLibrary(root)
        expect(root, filePath("cards/file1.cards"), filePath("cards/file2.cards")).match(enterResult, sut.path, sut.entries)
        enterResult = sut.enter(root.resolve("sub"))
        expect("sub", root, filePath("cards/file1.cards"), filePath("cards/file2.cards")).match(enterResult, sut.path, sut.entries)
    }

    private fun test(structure: Dir?, expectation: Expectation) {
        if (structure != null) {
            create(structure)
        }
        val sut = CardManager()
        val enterResult = sut.enterLibrary(root)
        expectation.match(enterResult, sut.path, sut.entries)
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

    private fun filePath(path: String): FlashcardSetFileEntry = FlashcardSetFileEntry(fs.getPath(path))

    private fun dirPath(path: String): FlashcardSetDirEntry = FlashcardSetDirEntry(fs.getPath(path))

    private fun upPath(path: String): FlashcardSetUpEntry = FlashcardSetUpEntry(fs.getPath(path))

    private fun expect(path: Path?, vararg entry: FlashcardSetListEntry): Expectation = ListExpectation(path, entry.toList())

    private fun expect(errorMessage: String, path: Path?, vararg entry: FlashcardSetListEntry): Expectation = ErrorExpectation(errorMessage, path, entry.toList())

    private sealed class Entry

    private class Dir(val name: String, val entries: List<Entry>) : Entry()

    private class File(val name: String) : Entry()

    private sealed class Expectation {
        abstract fun match(result: DirEnterResult, path: Path?, entries: List<FlashcardSetListEntry>)
    }

    private class ListExpectation(
        private val expectedPath: Path?,
        private val expected: List<FlashcardSetListEntry>,
    ) : Expectation() {
        override fun match(result: DirEnterResult, path: Path?, entries: List<FlashcardSetListEntry>) {
            assertThat(result).isInstanceOf(DirEnterSuccess::class.java)
            assertThat(path).isEqualTo(expectedPath)
            assertThat(entries).isEqualTo(expected)
        }
    }

    private class ErrorExpectation(
        private val expectedErrorMessage: String,
        private val expectedPath: Path?,
        private val expectedEntries: List<FlashcardSetListEntry>,
    ) : Expectation() {
        override fun match(result: DirEnterResult, path: Path?, entries: List<FlashcardSetListEntry>) {
            assertThat(result).isInstanceOf(DirEnterError::class.java)
            result as DirEnterError
            assertThat(result.message).containsIgnoringCase(expectedErrorMessage)
            assertThat(path).isEqualTo(expectedPath)
            assertThat(entries).isEqualTo(expectedEntries)
        }
    }

}
