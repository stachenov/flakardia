package name.tachenov.flakardia.data

import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

data class FlashcardSet(
    val name: String,
    val cards: List<Flashcard>,
)

data class Flashcard(
    val front: Word,
    val back: Word,
)

data class Word(
    val value: String,
)

fun readFlashcards(file: Path): FlashcardSet = FlashcardSet(
    file.fileName.toString(),
    parse(Files.readAllLines(file)),
)

private fun parse(lines: List<String>): List<Flashcard> {
    if (isEmptyLineDelimited(lines)) {
        return parseUsingEmptyLines(lines)
    }
    else {
        val delimiter = guessDelimiter(lines)
        return parse(lines, delimiter)
    }
}

private fun isEmptyLineDelimited(lines: List<String>): Boolean {
    var linesSoFar = 0
    var delimiters = 0
    for (line in (lines + "")) {
        if (line.isBlank()) {
            if (linesSoFar == 2) {
                ++delimiters
            }
            linesSoFar = 0
        }
        else {
            ++linesSoFar
        }
    }
    return (lines.size + 1) / 3 == delimiters
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

private fun guessDelimiter(lines: List<String>): Char {
    val frequency = IntArray(65536)
    var count = 0
    for (line in lines) {
        if (line.isBlank()) {
            continue
        }
        ++count
        for (c in line) {
            val code = c.code
            if (code in frequency.indices) {
                ++frequency[code]
            }
        }
    }
    return frequency.withIndex().minBy { abs(it.value - count) }.index.toChar()
}

private fun parse(lines: List<String>, delimiter: Char): List<Flashcard> = lines.filter { it.isNotBlank() }.map { parse(it, delimiter) }

private fun parse(line: String, delimiter: Char): Flashcard = line.split(delimiter)
    .map { parseWord(it) }
    .let { Flashcard(it[0], it[1]) }

private fun parseWord(s: String) = Word(s.trim { c -> c.isWhitespace() || c == '"' })
