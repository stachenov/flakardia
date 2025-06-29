package name.tachenov.flakardia

import com.google.common.jimfs.Jimfs
import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.data.FullPath
import name.tachenov.flakardia.data.RelativePath
import name.tachenov.flakardia.storage.FlashcardStorageImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.relativeTo

class ManagerTest {

    private lateinit var fs: FileSystem
    private lateinit var storagePath: Path
    private lateinit var root: RelativePath
    private lateinit var storage: FlashcardStorageImpl
    private lateinit var library: Library

    @BeforeEach
    fun setUp() {
        fs = Jimfs.newFileSystem()
        root = RelativePath()
        storagePath = fs.getPath("cards")
        storage = FlashcardStorageImpl(storagePath, StatsFileRecoveryOptionsStub)
        library = Library(storage)
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
        backgroundModelTest {
            create(dir("cards", dir("sub", file("sub-file.cards")), file("file1.cards"), file("file2.cards")))
            val sut = CardManager()
            var enterResult = sut.enterLibrary(library)
            expect(root, dirPath("cards/sub"), filePath("cards/file1.cards"), filePath("cards/file2.cards")).match(enterResult, sut.path, sut.entries)
            val subPath = root.resolve("sub")
            enterResult = sut.enter(subPath)
            expect(subPath, upPath("cards"), filePath("cards/sub/sub-file.cards")).match(enterResult, sut.path, sut.entries)
            enterResult = sut.enter(root)
            expect(root, dirPath("cards/sub"), filePath("cards/file1.cards"), filePath("cards/file2.cards")).match(enterResult, sut.path, sut.entries)
        }
    }

    @Test
    fun `entering non-existent dir`() {
        backgroundModelTest {
            create(dir("cards", file("file1.cards"), file("file2.cards")))
            val sut = CardManager()
            var enterResult = sut.enterLibrary(library)
            expect(root, filePath("cards/file1.cards"), filePath("cards/file2.cards")).match(enterResult, sut.path, sut.entries)
            enterResult = sut.enter(root.resolve("sub"))
            expect("sub", root, filePath("cards/file1.cards"), filePath("cards/file2.cards")).match(enterResult, sut.path, sut.entries)
        }
    }

    private fun test(structure: Dir?, expectation: Expectation) {
        backgroundModelTest {
            if (structure != null) {
                create(structure)
            }
            val sut = CardManager()
            val enterResult = sut.enterLibrary(library)
            expectation.match(enterResult, sut.path, sut.entries)
        }
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

    private fun filePath(path: String): FlashcardSetFileEntry = FlashcardSetFileEntry(path.parsePath())

    private fun dirPath(path: String): FlashcardSetDirEntry = FlashcardSetDirEntry(path.parsePath())

    private fun upPath(path: String): FlashcardSetUpEntry = FlashcardSetUpEntry(path.parsePath())

    private fun expect(path: RelativePath?, vararg entry: FlashcardSetListEntry): Expectation =
        ListExpectation(path?.let { FullPath(library, it) }, entry.toList())

    private fun expect(errorMessage: String, path: RelativePath?, vararg entry: FlashcardSetListEntry): Expectation =
        ErrorExpectation(errorMessage, path?.let { FullPath(library, it) }, entry.toList())

    private sealed class Entry

    private class Dir(val name: String, val entries: List<Entry>) : Entry()

    private class File(val name: String) : Entry()

    private sealed class Expectation {
        abstract fun match(result: DirEnterResult, path: FullPath?, entries: List<FlashcardSetListEntry>)
    }

    private class ListExpectation(
        private val expectedPath: FullPath?,
        private val expected: List<FlashcardSetListEntry>,
    ) : Expectation() {
        override fun match(result: DirEnterResult, path: FullPath?, entries: List<FlashcardSetListEntry>) {
            assertThat(result).isInstanceOf(DirEnterSuccess::class.java)
            assertThat(path).isEqualTo(expectedPath)
            assertThat(entries).isEqualTo(expected)
        }
    }

    private class ErrorExpectation(
        private val expectedErrorMessage: String,
        private val expectedPath: FullPath?,
        private val expectedEntries: List<FlashcardSetListEntry>,
    ) : Expectation() {
        override fun match(result: DirEnterResult, path: FullPath?, entries: List<FlashcardSetListEntry>) {
            assertThat(result).isInstanceOf(DirEnterError::class.java)
            result as DirEnterError
            assertThat(result.message).containsIgnoringCase(expectedErrorMessage)
            assertThat(path).isEqualTo(expectedPath)
            assertThat(entries).isEqualTo(expectedEntries)
        }
    }

    private fun String.parsePath() = RelativePath(fs.getPath(this).relativeTo(storagePath).let { path ->
        (0 until path.nameCount).map { path.getName(it).name }.filter { it.isNotEmpty() }
    })

}

private fun RelativePath.resolve(subDir: String) = RelativePath(elements + subDir)
