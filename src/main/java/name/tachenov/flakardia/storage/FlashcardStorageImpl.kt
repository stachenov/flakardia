package name.tachenov.flakardia.storage

import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.assertModelAccessAllowed
import name.tachenov.flakardia.data.*
import name.tachenov.flakardia.reportCurrentOperation
import name.tachenov.flakardia.reportProgress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.name
import kotlin.io.path.relativeTo

interface StatsFileRecoveryOptions {
    fun requestRecovery(message: String): Boolean
    fun notifyRecoveryImpossible(message: String)
}

data class FlashcardStorageImpl(
    private val fsPath: Path,
    private val statsFileRecoveryOptions: StatsFileRecoveryOptions,
) : FlashcardStorage {

    private val flakardiaDir: Path = fsPath.resolve(".flakardia")
    private val statsFile: Path = flakardiaDir.resolve("stats.json")

    override val path: String
        get() = fsPath.fileName.toString()

    override fun readEntries(path: RelativePath): EntryListResult {
        assertModelAccessAllowed()
        val result = mutableListOf<FlashcardSetListEntry>()
        return try {
            Files.newDirectoryStream(
                path.toFilePath()
            ) {
                !it.fileName.toString().startsWith(".")
            }.use { dir ->
                dir.forEach { entry ->
                    if (Files.isRegularFile(entry) && Files.isReadable(entry)) {
                        result += FlashcardSetFileEntry(entry.toRelativePath())
                    }
                    else if (Files.isDirectory(entry)) {
                        result += FlashcardSetDirEntry(entry.toRelativePath())
                    }
                }
            }
            EntryList(result)
        } catch (e: Exception) {
            EntryListError(e.toString())
        }
    }

    override fun readFlashcards(path: RelativePath): FlashcardSetResult  {
        assertModelAccessAllowed()
        val lines = try {
            Files.readAllLines(path.toFilePath())
        } catch (e: Exception) {
            return FlashcardSetError(path.fileName + ": " + e.toString())
        }
        return parse(path, lines)
    }

    private fun RelativePath.toFilePath(): Path {
        var result = fsPath
        for (element in elements) {
            result = result.resolve(element)
        }
        return result
    }

    private fun Path.toRelativePath(): RelativePath = relativeTo(fsPath).let { path ->
        RelativePath((0 until path.nameCount).map { path.getName(it).name })
    }

    override fun readLibraryStats(): LibraryStatsResult {
        assertModelAccessAllowed()
        try {
            ensureFlakardiaDirExists()
            val json = if (Files.exists(statsFile) || recoverStatsFile()) {
                Files.readString(statsFile)
            }
            else {
                return LibraryStats(emptyMap())
            }
            return SERIALIZER.decodeFromString<LibraryStats>(json)
        } catch (e: Exception) {
            return LibraryStatsError(e.toString())
        }
    }

    private fun recoverStatsFile(): Boolean {
        val possiblyRenamedStatsFiles = Files.find(
            flakardiaDir,
            1,
            { file, _ -> Files.isRegularFile(file) && file.fileName.toString().let { it.startsWith("stats") && it.endsWith(".json") } },
        ).toList()
        return when (possiblyRenamedStatsFiles.size) {
            0 -> false
            1 -> {
                val fileToRecover = possiblyRenamedStatsFiles.single()
                if (statsFileRecoveryOptions.requestRecovery(
                        "Found a file named " + fileToRecover.fileName.toString() +
                            ", which is likely the stats file backed up by some external service, such as Google Drive.\n" +
                            "Would you like to recover the lesson stats from this file?\n" +
                            "If you choose No, all learning statistics will be reset to the initial state."
                    )
                ) {
                    Files.move(fileToRecover, statsFile)
                    true
                }
                else {
                    false
                }
            }
            else -> {
                statsFileRecoveryOptions.notifyRecoveryImpossible(
                    "Found files named " + (possiblyRenamedStatsFiles.joinToString(", ") { it.fileName.toString() }) +
                        ", which are likely the stats file backed up by some external service, such as Google Drive.\n" +
                        "However, since there are several files, automatic recovery is impossible.\n" +
                        "Please find the correct file and rename it manually."
                )
                false
            }
        }
    }

    override fun saveLibraryStats(stats: LibraryStats): SaveResult =
        saveTextFile(statsFile, "stats", ".json") {
            SERIALIZER.encodeToString(stats)
        }

    override fun saveFlashcardSetFile(path: RelativePath, flashcards: List<Flashcard>): SaveResult =
        saveTextFile(path.toFilePath(), "cards", ".txt") {
            flashcards.joinToString("\n") { "${it.question.value}\n${it.answer.value}\n" }
        }

    private fun saveTextFile(path: Path, tempPrefix: String, tempSuffix: String, content: () -> String): SaveResult {
        assertModelAccessAllowed()
        var tempFile: Path? = null
        try {
            ensureFlakardiaDirExists()
            Files.createDirectories(path.parent)
            tempFile = Files.createTempFile(path.parent, tempPrefix, tempSuffix)
            checkNotNull(tempFile)
            Files.writeString(tempFile, content())
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
            tempFile = null
            return SaveSuccess
        } catch (e: Exception) {
            return SaveError(e.toString())
        } finally {
            if (tempFile != null && Files.exists(tempFile)) {
                try {
                    Files.delete(tempFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun ensureFlakardiaDirExists() {
        if (!Files.exists(flakardiaDir)) {
            Files.createDirectories(flakardiaDir)
        }
    }

    override fun createDir(path: RelativePath) {
        assertModelAccessAllowed()
        Files.createDirectories(path.toFilePath())
    }

    override fun createFile(path: RelativePath) {
        assertModelAccessAllowed()
        Files.createFile(path.toFilePath())
    }
}

private fun parse(path: RelativePath, lines: List<String>): FlashcardSetResult {
    reportCurrentOperation("Reading $path")
    if (isEmptyLineDelimited(lines)) {
        return FlashcardSet(parseUsingEmptyLines(lines).map { FlashcardData(path, it) })
    }
    else {
        val delimiter = guessDelimiter(lines)
        if (delimiter == null) {
            return FlashcardSetError("$path: Could not determine the delimiter character.\n" +
                "It must appear once and only once in every non-blank line, but there was no such character")
        }
        else {
            return parse(path, lines, delimiter)
        }
    }
}

private fun isEmptyLineDelimited(lines: List<String>): Boolean {
    var linesSoFar = 0
    for (line in (lines + "")) {
        if (line.isBlank()) {
            if (linesSoFar != 0 && linesSoFar != 2) {
                return false
            }
            linesSoFar = 0
        }
        else {
            ++linesSoFar
        }
    }
    return true
}

private fun parseUsingEmptyLines(lines: List<String>): List<Flashcard> {
    val result = mutableListOf<Flashcard>()
    val words = mutableListOf<Word>()
    for (line in (lines + "").withIndex()) {
        reportProgress(line.index * 100 / (lines.size + 1))
        if (line.value.isBlank()) {
            if (words.isNotEmpty()) {
                result += Flashcard(words[0], words[1])
            }
            words.clear()
        }
        else {
            words += parseWord(line.value)
        }
    }
    return result
}

private fun guessDelimiter(lines: List<String>): Char? {
    val encounteredOnce = BooleanArray(65536)
    val encounteredMultipleTimes = BooleanArray(65536)
    val totalCount = IntArray(65536)
    val count = IntArray(65536)
    var nonBlankLines = 0
    for (line in lines) {
        if (line.isBlank()) {
            continue
        }
        ++nonBlankLines
        for (c in line) {
            ++count[c.code]
            ++totalCount[c.code]
        }
        for (c in line) {
            if (count[c.code] > 1) {
                encounteredMultipleTimes[c.code] = true
            }
            else {
                encounteredOnce[c.code] = true
            }
        }
        // reset back, to avoid iterating over all 65536 elements
        for (c in line) {
            count[c.code] = 0
        }
    }
    return (0..65535).firstOrNull { code ->
        encounteredOnce[code] && !encounteredMultipleTimes[code] && totalCount[code] == nonBlankLines
    }?.toChar()
}

private fun parse(path: RelativePath, lines: List<String>, delimiter: Char): FlashcardSet =
    FlashcardSet(
        lines.filter { it.isNotBlank() }.withIndex()
            .map {
                reportProgress(it.index * 100 / lines.size)
                FlashcardData(path, parse(it.value, delimiter))
            },
    )

private fun parse(line: String, delimiter: Char): Flashcard = line.split(delimiter)
    .map { parseWord(it) }
    .let { Flashcard(it[0], it[1]) }

private fun parseWord(s: String) = Word(s.trim { c -> c.isWhitespace() || c == '"' || c == '\uFEFF' /* BOM */ })
