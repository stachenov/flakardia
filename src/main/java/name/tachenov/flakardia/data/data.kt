package name.tachenov.flakardia.data

import kotlinx.serialization.Serializable
import name.tachenov.flakardia.app.Library
import name.tachenov.flakardia.storage.DurationSerializer
import name.tachenov.flakardia.storage.InstantSerializer
import name.tachenov.flakardia.storage.WordSerializer
import java.time.Duration
import java.time.Instant

sealed class FlashcardSetResult

data class FlashcardSet(
    val cards: List<FlashcardData>,
) : FlashcardSetResult()

data class FlashcardSetError(val message: String) : FlashcardSetResult()

sealed class LessonDataResult

data class LessonData(
    val name: String,
    val flashcards: List<FlashcardData>,
    val stats: LibraryStats,
) : LessonDataResult()

data class LessonDataWarnings(
    val data: LessonData,
    val warnings: List<String>,
): LessonDataResult()

data class LessonDataError(val message: String) : LessonDataResult()

data class FlashcardData(
    val path: RelativePath,
    val flashcard: Flashcard,
)

data class Flashcard(
    val question: Word,
    val answer: Word,
)

data class UpdatedOrNewFlashcard(
    val oldCard: Flashcard?,
    val newCard: Flashcard,
) {
    fun toUpdatedFlashcardOrNull(): UpdatedFlashcard? = if (oldCard == null) null else UpdatedFlashcard(oldCard, newCard)
}

data class UpdatedFlashcard(
    val oldCard: Flashcard,
    val newCard: Flashcard,
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
            if (c.isLetter() || c.isDigit()) {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    override fun toString(): String {
        return "Word(value='$value')"
    }
}

data class FlashcardDraft(
    val path: RelativePath,
    val question: String,
    val answer: String,
)

sealed class LibraryStatsResult

@Serializable
data class LibraryStats(
    val wordStats: Map<Word, WordStats>
) : LibraryStatsResult() {
    fun filter(flashcards: List<FlashcardData>): LibraryStats {
        val words = flashcards.asSequence()
            .map { it.flashcard.answer }
            .toSet()
        return LibraryStats(
            wordStats.filter { it.key in words }
        )
    }

    fun update(stats: LibraryStats): LibraryStats = LibraryStats(
        (wordStats.keys + stats.wordStats.keys).associateWith { (stats.wordStats[it] ?: wordStats.getValue(it)) }
    )

    fun renameWords(updatedFlashcards: List<UpdatedFlashcard>): LibraryStats {
        val changedAnswers = updatedFlashcards.associate { it.oldCard.answer to it.newCard.answer }
        return LibraryStats(
            wordStats.mapKeys { changedAnswers[it.key] ?: it.key }
        )
    }
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

operator fun RelativePath.plus(name: String): RelativePath = RelativePath(elements + name)

fun String.parseRelativePath(): RelativePath {
    if (isEmpty()) return RelativePath(emptyList())
    return RelativePath(split('/'))
}

data class FullPath(
    val library: Library,
    val relativePath: RelativePath,
) {
    override fun toString(): String = if (relativePath.isEmpty()) library.path else library.path + "/" + relativePath.toString()
}

operator fun FullPath.plus(name: String): FullPath = FullPath(library, relativePath + name)

sealed class SaveResult {
    abstract fun addWarnings(warnings: List<String>): SaveResult
    fun addWarning(warning: String): SaveResult = addWarnings(listOf(warning))
}

data object SaveSuccess : SaveResult() {
    override fun addWarnings(warnings: List<String>): SaveResult = SaveWarnings(warnings)
}

data class SaveWarnings(val warnings: List<String>) : SaveResult() {
    constructor(warning: String) : this(listOf(warning))
    override fun addWarnings(warnings: List<String>): SaveResult = SaveWarnings(this.warnings + warnings)
}

data class SaveError(val message: String) : SaveResult() {
    override fun addWarnings(warnings: List<String>): SaveResult {
        throw UnsupportedOperationException("No warnings allowed after an error, should have stopped earlier")
    }
}
