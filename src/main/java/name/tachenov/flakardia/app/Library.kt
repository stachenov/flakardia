package name.tachenov.flakardia.app

import name.tachenov.flakardia.assertBGT
import name.tachenov.flakardia.data.*
import name.tachenov.flakardia.storage.FlashcardStorage
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

private const val maxWordsPerLesson = 30
private const val intervalMultiplierWithoutMistakes = 1.5
private const val intervalMultiplierWithMistake = 1.0
private const val intervalMultiplierWithManyMistakes = 0.5
private val lastLearnedFallback: Duration = Duration.ofDays(365)
private val intervalFallback: Duration = Duration.ofDays(1)

data class Library(val storage: FlashcardStorage) {

    val path: String
        get() = storage.path

    fun listEntries(path: RelativePath): List<FlashcardSetListEntry> {
        assertBGT()
        val result = mutableListOf<FlashcardSetListEntry>()
        val parent = path.parent
        if (parent != null) {
            result += FlashcardSetUpEntry(parent)
        }
        result += storage.readEntries(path)
        return result.sortedWith(compareBy({ it is FlashcardSetFileEntry }, { it.name }))
    }

    fun prepareLessonData(entry: FlashcardSetListEntry): LessonDataResult {
        assertBGT()
        return when (val flashcardSet = readFlashcards(entry)) {
            is FlashcardSetError -> LessonDataError(flashcardSet.message)
            is FlashcardSet -> when (val stats = storage.readLibraryStats()) {
                is LibraryStatsError -> LessonDataError(stats.message)
                is LibraryStats -> prepareLessonData(flashcardSet, stats)
            }
        }
    }

    private fun prepareLessonData(flashcardSet: FlashcardSet, stats: LibraryStats): LessonData {
        // Take all flashcards and calculate for every flashcard, when it should be ideally learned next.
        // This is calculated as the last learned time plus the interval, which is the interval between two
        // previous learn times, multiplied by a factor that depends on how many mistakes were there the last time.
        // Then sort all flashcard by the estimated next learn time and simply take N first ones.
        // In theory this should lead to the selection of the most "forgotten" words.
        val now: Instant = Instant.now()
        val flashcards = flashcardSet.cards.toMutableList()
        flashcards.shuffle()
        val nextLearnTimes = flashcardSet.cards.associateWith { card ->
            val word = card.back
            val lastLearned: Instant = stats.wordStats[word]?.lastLearned ?: now.minus(lastLearnedFallback)
            val mistakes = stats.wordStats[word]?.mistakes ?: 0
            val previousInterval: Duration = stats.wordStats[word]?.intervalBeforeLastLearned ?: intervalFallback
            val interval = previousInterval * (when (mistakes) {
                0 -> intervalMultiplierWithoutMistakes
                1 -> intervalMultiplierWithMistake
                else -> intervalMultiplierWithManyMistakes
            })
            lastLearned.plus(interval) as Instant
        }
        flashcards.sortBy { nextLearnTimes.getValue(it) }
        val effectiveFlashcardSet = FlashcardSet(
            flashcardSet.name,
            flashcards.subList(0, flashcards.size.coerceAtMost(maxWordsPerLesson))
        )
        return LessonData(
            effectiveFlashcardSet,
            stats.filter(effectiveFlashcardSet),
        )
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
                val subEntries = listEntries(entry.dir)
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

private operator fun Duration.times(d: Double): Duration = Duration.ofSeconds((toSeconds().toDouble() * d).roundToLong())
