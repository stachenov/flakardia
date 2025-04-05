package name.tachenov.flakardia.storage

import name.tachenov.flakardia.app.FlashcardSetDirEntry
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.FlashcardSetListEntry
import name.tachenov.flakardia.app.FlashcardStorage
import name.tachenov.flakardia.assertBGT
import name.tachenov.flakardia.data.*
import name.tachenov.flakardia.reportCurrentOperation
import name.tachenov.flakardia.reportProgress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.name
import kotlin.io.path.relativeTo

data class FlashcardStorageImpl(private val fsPath: Path) : FlashcardStorage {

    private val flakardiaDir: Path = fsPath.resolve(".flakardia")
    private val statsFile: Path = flakardiaDir.resolve("stats.json")

    override val path: String
        get() = fsPath.fileName.toString()

    override fun readEntries(path: RelativePath): List<FlashcardSetListEntry> {
        assertBGT()
        val result = mutableListOf<FlashcardSetListEntry>()
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
        return result
    }

    override fun readFlashcards(path: RelativePath): FlashcardSetResult  {
        assertBGT()
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
        assertBGT()
        try {
            ensureFlakardiaDirExists()
            val json = if (Files.exists(statsFile)) {
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

    override fun saveLibraryStats(stats: LibraryStats): SaveResult =
        saveTextFile(statsFile, "stats", ".json") {
            SERIALIZER.encodeToString(stats)
        }

    override fun saveFlashcardSetFile(path: RelativePath, flashcards: List<Flashcard>): SaveResult =
        saveTextFile(path.toFilePath(), "cards", ".txt") {
            flashcards.joinToString("\n") { "${it.front.value}\n${it.back.value}\n" }
        }

    private fun saveTextFile(path: Path, tempPrefix: String, tempSuffix: String, content: () -> String): SaveResult {
        assertBGT()
        try {
            ensureFlakardiaDirExists()
            val tempFile = Files.createTempFile(path.parent, tempPrefix, tempSuffix)
            Files.writeString(tempFile, content())
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
            return SaveSuccess
        } catch (e: Exception) {
            return SaveError(e.toString())
        }
    }

    private fun ensureFlakardiaDirExists() {
        if (!Files.exists(flakardiaDir)) {
            Files.createDirectories(flakardiaDir)
        }
    }

    override fun createDir(path: RelativePath) {
        assertBGT()
        Files.createDirectories(path.toFilePath())
    }

    override fun createFile(path: RelativePath) {
        assertBGT()
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
