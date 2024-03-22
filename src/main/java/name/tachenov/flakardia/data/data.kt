package name.tachenov.flakardia.data

import kotlinx.serialization.Serializable
import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.storage.DurationSerializer
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

