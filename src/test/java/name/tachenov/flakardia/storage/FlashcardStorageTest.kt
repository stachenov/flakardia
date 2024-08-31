package name.tachenov.flakardia.storage

import com.google.common.jimfs.Jimfs
import name.tachenov.flakardia.app.FlashcardSetDirEntry
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.data.RelativePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path

private const val DOT_FLAKARDIA = ".flakardia"

class FlashcardStorageTest {
    private lateinit var fs: FileSystem
    private lateinit var root: Path
    private lateinit var sut: FlashcardStorageImpl

    @BeforeEach
    fun setUp() {
        fs = Jimfs.newFileSystem()
        root = fs.getPath("cards")
        sut = FlashcardStorageImpl(root)
    }

    @AfterEach
    fun tearDown() {
        fs.close()
    }

    @Test
    fun `flakardia dir is ignored`() {
        Files.createDirectories(root.resolve(DOT_FLAKARDIA))
        assertThat(sut.readEntries(RelativePath())).doesNotContain(FlashcardSetDirEntry(RelativePath(listOf(DOT_FLAKARDIA))))
    }

    @Test
    fun `hidden dirs are ignored`() {
        val dirName = ".some_dir"
        Files.createDirectories(root.resolve(dirName))
        assertThat(sut.readEntries(RelativePath())).doesNotContain(FlashcardSetDirEntry(RelativePath(listOf(dirName))))
    }

    @Test
    fun `hidden files are ignored`() {
        Files.createDirectory(root)
        val fileName = ".some_file.tmp"
        Files.createFile(root.resolve(fileName))
        assertThat(sut.readEntries(RelativePath())).doesNotContain(FlashcardSetFileEntry(RelativePath(listOf(fileName))))
    }
}
