package name.tachenov.flakardia.storage

import com.google.common.jimfs.Jimfs
import name.tachenov.flakardia.app.EntryList
import name.tachenov.flakardia.app.FlashcardSetDirEntry
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.backgroundModelTest
import name.tachenov.flakardia.data.LibraryStats
import name.tachenov.flakardia.data.RelativePath
import name.tachenov.flakardia.data.Word
import name.tachenov.flakardia.data.WordStats
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

private const val DOT_FLAKARDIA = ".flakardia"

class FlashcardStorageTest {
    private lateinit var fs: FileSystem
    private lateinit var root: Path
    private lateinit var flakardiaDir: Path
    private lateinit var statsFile: Path
    private lateinit var renamedStatsFile1: Path
    private lateinit var renamedStatsFile2: Path
    private lateinit var notStatsFile: Path
    private lateinit var nonEmptyStats: LibraryStats
    private lateinit var recovery: StatsFileRecoverOptionsMock
    private lateinit var sut: FlashcardStorageImpl

    @BeforeEach
    fun setUp() {
        fs = Jimfs.newFileSystem()
        root = fs.getPath("cards")
        flakardiaDir = root.resolve(".flakardia")
        statsFile = flakardiaDir.resolve("stats.json")
        renamedStatsFile1 = flakardiaDir.resolve("stats (1).json")
        renamedStatsFile2 = flakardiaDir.resolve("stats (2).json")
        notStatsFile = flakardiaDir.resolve("just a random file")
        recovery = StatsFileRecoverOptionsMock()
        nonEmptyStats = LibraryStats(mapOf(
            Word("word") to WordStats(Instant.now(), Duration.ofHours(1), 0)
        ))
        sut = FlashcardStorageImpl(root, recovery)
    }

    @AfterEach
    fun tearDown() {
        fs.close()
    }

    @Test
    fun `flakardia dir is ignored`() {
        backgroundModelTest {
            Files.createDirectories(root.resolve(DOT_FLAKARDIA))
            assertThat(readRootEntries()).doesNotContain(FlashcardSetDirEntry(RelativePath(listOf(DOT_FLAKARDIA))))
        }
    }

    @Test
    fun `hidden dirs are ignored`() {
        backgroundModelTest {
            val dirName = ".some_dir"
            Files.createDirectories(root.resolve(dirName))
            assertThat(readRootEntries()).doesNotContain(FlashcardSetDirEntry(RelativePath(listOf(dirName))))
        }
    }

    @Test
    fun `hidden files are ignored`() {
        backgroundModelTest {
            Files.createDirectory(root)
            val fileName = ".some_file.tmp"
            Files.createFile(root.resolve(fileName))
            assertThat(readRootEntries()).doesNotContain(FlashcardSetFileEntry(RelativePath(listOf(fileName))))
        }
    }

    @Test
    fun `stats file recovery - one file, accepted`() {
        backgroundModelTest {
            Files.createDirectories(flakardiaDir)
            sut.saveLibraryStats(nonEmptyStats)
            Files.move(statsFile, renamedStatsFile1)
            val actualStats = sut.readLibraryStats()
            assertThat(recovery.requestMessage).contains(renamedStatsFile1.fileName.toString())
            assertThat(recovery.warningMessage).isNull()
            assertThat(statsFile).exists()
            assertThat(actualStats).isEqualTo(nonEmptyStats)
            assertThat(renamedStatsFile1).doesNotExist()
        }
    }

    @Test
    fun `stats file recovery - one file, rejected`() {
        backgroundModelTest {
            Files.createDirectories(flakardiaDir)
            sut.saveLibraryStats(nonEmptyStats)
            Files.move(statsFile, renamedStatsFile1)
            recovery.acceptRecovery = false
            val actualStats = sut.readLibraryStats()
            assertThat(recovery.requestMessage).contains(renamedStatsFile1.fileName.toString())
            assertThat(recovery.warningMessage).isNull()
            assertThat(statsFile).doesNotExist()
            assertThat((actualStats as LibraryStats).wordStats).isEmpty()
            assertThat(renamedStatsFile1).exists()
        }
    }

    @Test
    fun `stats file recovery - one file, wrong name`() {
        backgroundModelTest {
            Files.createDirectories(flakardiaDir)
            sut.saveLibraryStats(nonEmptyStats)
            Files.move(statsFile, notStatsFile)
            val actualStats = sut.readLibraryStats()
            assertThat(recovery.requestMessage).isNull()
            assertThat(recovery.warningMessage).isNull()
            assertThat(statsFile).doesNotExist()
            assertThat((actualStats as LibraryStats).wordStats).isEmpty()
            assertThat(notStatsFile).exists()
        }
    }

    @Test
    fun `stats file recovery - multiple files`() {
        backgroundModelTest {
            Files.createDirectories(flakardiaDir)
            sut.saveLibraryStats(nonEmptyStats)
            Files.copy(statsFile, renamedStatsFile1)
            Files.move(statsFile, renamedStatsFile2)
            val actualStats = sut.readLibraryStats()
            assertThat(recovery.requestMessage).isNull()
            assertThat(recovery.warningMessage)
                .contains(renamedStatsFile1.fileName.toString())
                .contains(renamedStatsFile2.fileName.toString())
            assertThat(statsFile).doesNotExist()
            assertThat((actualStats as LibraryStats).wordStats).isEmpty()
            assertThat(renamedStatsFile1).exists()
            assertThat(renamedStatsFile2).exists()
        }
    }

    private fun readRootEntries() =
        (sut.readEntries(RelativePath()) as EntryList).entries

    private class StatsFileRecoverOptionsMock : StatsFileRecoveryOptions {
        var acceptRecovery: Boolean = true
        var requestMessage: String? = null
        var warningMessage: String? = null

        override fun requestRecovery(message: String): Boolean {
            requestMessage = message
            return acceptRecovery
        }

        override fun notifyRecoveryImpossible(message: String) {
            warningMessage = message
        }
    }
}
