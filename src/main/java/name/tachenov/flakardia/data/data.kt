package name.tachenov.flakardia.data

import java.nio.file.Files
import java.nio.file.Path

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

fun readFlashcards(file: Path): FlashcardSet =
    FlashcardSet(
        file.fileName.toString(),
        Files.readAllLines(file).filter { it.isNotBlank() }.map { parse(it) },
    )

private fun parse(line: String): Flashcard = line.split(':').map { Word(it) }.let { Flashcard(it[0], it[1]) }
