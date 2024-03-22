package name.tachenov.flakardia.data

import kotlinx.serialization.Serializable
import name.tachenov.flakardia.app.FlashcardSetDirEntry
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.FlashcardSetListEntry
import name.tachenov.flakardia.app.FlashcardSetUpEntry
import name.tachenov.flakardia.assertBGT
import name.tachenov.flakardia.storage.DurationSerializer
import name.tachenov.flakardia.storage.FlashcardStorage
import name.tachenov.flakardia.storage.InstantSerializer
import name.tachenov.flakardia.storage.WordSerializer
import java.time.Duration
import java.time.Instant

sealed class FlashcardSetResult

data class FlashcardSet(
    val name: String,
    val cards: List<Flashcard>,
) : FlashcardSetResult()

data class FlashcardSetError(val message: String) : FlashcardSetResult()

sealed class LessonDataResult

data class LessonData(
    val flashcardSet: FlashcardSet,
    val stats: LibraryStats,
) : LessonDataResult()

data class LessonDataError(val message: String) : LessonDataResult()

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

sealed class LibraryStatsResult

@Serializable
data class LibraryStats(
    val wordStats: Map<Word, WordStats>
) : LibraryStatsResult() {
    fun filter(flashcardSet: FlashcardSet): LibraryStats {
        val words = flashcardSet.cards.asSequence()
            .map { it.back }
            .toSet()
        return LibraryStats(
            wordStats.filter { it.key in words }
        )
    }

    fun update(stats: LibraryStats): LibraryStats = LibraryStats(
        (wordStats.keys + stats.wordStats.keys).associateWith { (stats.wordStats[it] ?: wordStats.getValue(it)) }
    )
}

data class LibraryStatsError(val message: String) : LibraryStatsResult()

@Serializable
data class WordStats(
    @Serializable(with = InstantSerializer::class)
    val lastLearned: Instant,
    @Serializable(with = DurationSerializer::class)
    val intervalBeforeLastLearned: Duration,
    val mistakes: Int,
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

sealed class StatsSaveResult

data object StatsSaveSuccess : StatsSaveResult()

data class StatsSaveError(val message: String) : StatsSaveResult()

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
        result += storage.readEntries(path)
        return result.sortedWith(compareBy({ it is FlashcardSetFileEntry }, { it.name }))
    }

    fun readLessonData(entry: FlashcardSetListEntry): LessonDataResult {
        assertBGT()
        return when (val flashcardSet = readFlashcards(entry)) {
            is FlashcardSetError -> LessonDataError(flashcardSet.message)
            is FlashcardSet -> when (val stats = storage.readLibraryStats()) {
                is LibraryStatsError -> LessonDataError(stats.message)
                is LibraryStats -> LessonData(
                    flashcardSet,
                    stats.filter(flashcardSet),
                )
            }
        }
    }

    fun saveUpdatedStats(stats: LibraryStats): StatsSaveResult {
        assertBGT()
        return when (val allStats = storage.readLibraryStats()) {
            is LibraryStats -> {
                val updatedStats = allStats.update(stats)
                storage.saveLibraryStats(updatedStats)
            }
            is LibraryStatsError -> StatsSaveError(allStats.message)
        }
    }

    private fun readFlashcards(entry: FlashcardSetListEntry): FlashcardSetResult {
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
