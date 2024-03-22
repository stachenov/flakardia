package name.tachenov.flakardia.data

import kotlinx.serialization.Serializable
import name.tachenov.flakardia.app.FlashcardSetDirEntry
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.FlashcardSetListEntry
import name.tachenov.flakardia.app.FlashcardSetUpEntry
import name.tachenov.flakardia.assertBGT
import name.tachenov.flakardia.storage.FlashcardStorage
import name.tachenov.flakardia.storage.InstantSerializer
import name.tachenov.flakardia.storage.WordSerializer
import java.time.Instant

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

@Serializable(with = WordSerializer::class)
class Word(val value: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Word

        return normalized() == other.normalized()
    }

    override fun hashCode(): Int {
        return normalized().hashCode()
    }

    private fun normalized(): String {
        val sb = StringBuilder()
        for (c in value) {
            if (c.isLetter()) {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    override fun toString(): String {
        return "Word(value='$value')"
    }
}

@Serializable
data class LibraryStats(
    val wordStats: Map<Word, WordStats>
)

@Serializable
data class WordStats(
    @Serializable(with = InstantSerializer::class)
    val lastLearned: Instant,
    val attempts: Int,
)

data class RelativePath(
    val elements: List<String>,
) {
    constructor() : this(emptyList())

    val parent: RelativePath?
        get() = if (elements.isEmpty()) null else RelativePath(elements.subList(0, elements.size - 1))

    val fileName: String
        get() = elements.last()

    fun isEmpty(): Boolean = elements.isEmpty()

    override fun toString(): String = elements.joinToString("/")
}

data class FullPath(
    val library: Library,
    val relativePath: RelativePath,
) {
    override fun toString(): String = if (relativePath.isEmpty()) library.path else library.path + "/" + relativePath.toString()
}

data class Library(val storage: FlashcardStorage) {

    val path: String
        get() = storage.path

    fun readEntries(path: RelativePath): List<FlashcardSetListEntry> {
        assertBGT()
        val result = mutableListOf<FlashcardSetListEntry>()
        val parent = path.parent
        if (parent != null) {
            result += FlashcardSetUpEntry(parent)
        }
        result += storage.readEntries(this, path)
        return result.sortedWith(compareBy({ it is FlashcardSetFileEntry }, { it.name }))
    }

    fun readFlashcards(entry: FlashcardSetListEntry): FlashcardSetResult {
        assertBGT()
        return when (entry) {
            is FlashcardSetFileEntry -> storage.readFlashcards(entry.file)
            is FlashcardSetDirEntry -> {
                val result = mutableListOf<Flashcard>()
                val subEntries = readEntries(entry.dir)
                for (subEntry in subEntries) {
                    when (val subResult = readFlashcards(subEntry)) {
                        is FlashcardSet -> result += subResult.cards
                        is FlashcardSetError -> return subResult
                    }
                }
                FlashcardSet(entry.name, result)
            }
            else -> FlashcardSet("", emptyList())
        }
    }

    fun fullPath(path: RelativePath): FullPath = FullPath(this, path)
}
