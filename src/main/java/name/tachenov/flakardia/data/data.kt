package name.tachenov.flakardia.data

import java.nio.file.Files
import java.nio.file.Path

sealed class FlashcardSetResult

data class FlashcardSet(
    val name: String,
    val cards: List<Flashcard>,
) : FlashcardSetResult()

data class FlashcardSetError(val message: String) : FlashcardSetResult()

data class Flashcard(
    val front: Word,
    val back: Word,
)

data class Word(
    val value: String,
)

fun readFlashcards(file: Path): FlashcardSetResult {
    val lines = try {
        Files.readAllLines(file)
    } catch (e: Exception) {
        return FlashcardSetError(e.toString())
    }
    return parse(file.fileName.toString(), lines)
}

private fun parse(name: String, lines: List<String>): FlashcardSetResult {
    if (isEmptyLineDelimited(lines)) {
        return FlashcardSet(name, parseUsingEmptyLines(lines))
    }
    else {
        val delimiter = guessDelimiter(lines)
        if (delimiter == null) {
            return FlashcardSetError("Could not determine the delimiter character.\n" +
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

private fun parseWord(s: String) = Word(s.trim { c -> c.isWhitespace() || c == '"' })
