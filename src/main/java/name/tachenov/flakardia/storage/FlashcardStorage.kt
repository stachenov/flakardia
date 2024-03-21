package name.tachenov.flakardia.storage

import name.tachenov.flakardia.app.FlashcardSetDirEntry
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.FlashcardSetListEntry
import name.tachenov.flakardia.assertBGT
import name.tachenov.flakardia.data.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.relativeTo

data class FlashcardStorage(private val fsPath: Path) {

    val path: String
        get() = fsPath.fileName.toString()

    fun readEntries(library: Library, path: RelativePath): List<FlashcardSetListEntry> {
        assertBGT()
        val result = mutableListOf<FlashcardSetListEntry>()
        Files.newDirectoryStream(path.toFilePath()).use { dir ->
            dir.forEach { entry ->
                if (Files.isRegularFile(entry) && Files.isReadable(entry)) {
                    result += FlashcardSetFileEntry(library, entry.toRelativePath())
                }
                else if (Files.isDirectory(entry)) {
                    result += FlashcardSetDirEntry(entry.toRelativePath())
                }
            }
        }
        return result
    }

    fun readFlashcards(file: RelativePath): FlashcardSetResult  {
        assertBGT()
        val lines = try {
            Files.readAllLines(file.toFilePath())
        } catch (e: Exception) {
            return FlashcardSetError(file.fileName + ": " + e.toString())
        }
        return parse(file.fileName, lines)
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
}

private fun parse(name: String, lines: List<String>): FlashcardSetResult {
    if (isEmptyLineDelimited(lines)) {
        return FlashcardSet(name, parseUsingEmptyLines(lines))
    }
    else {
        val delimiter = guessDelimiter(lines)
        if (delimiter == null) {
            return FlashcardSetError("$name: Could not determine the delimiter character.\n" +
                "It must appear once and only once in every non-blank line, but there was no such character")
        }
        else {
            return parse(name, lines, delimiter)
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
    for (line in (lines + "")) {
        if (line.isBlank()) {
            if (words.isNotEmpty()) {
                result += Flashcard(words[0], words[1])
            }
            words.clear()
        }
        else {
            words += parseWord(line)
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

private fun parse(name: String, lines: List<String>, delimiter: Char): FlashcardSet =
    FlashcardSet(
        name,
        lines.filter { it.isNotBlank() }.map { parse(it, delimiter) },
    )

private fun parse(line: String, delimiter: Char): Flashcard = line.split(delimiter)
    .map { parseWord(it) }
    .let { Flashcard(it[0], it[1]) }

private fun parseWord(s: String) = Word(s.trim { c -> c.isWhitespace() || c == '"' || c == '\uFEFF' /* BOM */ })
