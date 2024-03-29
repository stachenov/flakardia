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

    fun getAllFlashcards(entry: FlashcardSetListEntry): LessonDataResult {
        assertBGT()
        return prepareLessonData(entry) { name, flashcards, stats -> LessonData(name, flashcards, stats.filter(flashcards)) }
    }

    fun prepareLessonData(entry: FlashcardSetListEntry): LessonDataResult {
        assertBGT()
        return prepareLessonData(entry) { name, flashcards, stats -> prepareLessonData(name, flashcards, stats) }
    }

    private inline fun prepareLessonData(
        entry: FlashcardSetListEntry,
        prepare: (String, List<FlashcardData>, LibraryStats) -> LessonData,
    ): LessonDataResult {
        assertBGT()
        return when (val flashcardSet = readFlashcards(entry.path, entry)) {
            is FlashcardSetError -> LessonDataError(flashcardSet.message)
            is FlashcardSet -> when (val stats = storage.readLibraryStats()) {
                is LibraryStatsError -> LessonDataError(stats.message)
                is LibraryStats -> prepare(entry.name, flashcardSet.cards, stats)
            }
        }
    }

    private fun prepareLessonData(name: String, flashcardList: List<FlashcardData>, stats: LibraryStats): LessonData {
        // Take all flashcards and calculate for every flashcard, when it should be ideally learned next.
        // This is calculated as the last learned time plus the interval, which is the interval between two
        // previous learn times, multiplied by a factor that depends on how many mistakes were there the last time.
        // Then sort all flashcard by the estimated next learn time and simply take N first ones.
        // In theory this should lead to the selection of the most "forgotten" words.
        val now: Instant = Instant.now()
        val flashcards = flashcardList.toMutableList()
        flashcards.shuffle()
        val nextLearnTimes = flashcardList.associateWith { cardData ->
            val word = cardData.flashcard.back
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
        val effectiveFlashcards = flashcards.subList(0, flashcards.size.coerceAtMost(maxWordsPerLesson))
        return LessonData(
            name,
            effectiveFlashcards,
            stats.filter(effectiveFlashcards),
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

    private fun readFlashcards(path: RelativePath, entry: FlashcardSetListEntry): FlashcardSetResult {
        return when (entry) {
            is FlashcardSetFileEntry -> storage.readFlashcards(entry.path)
            is FlashcardSetDirEntry -> {
                val result = mutableListOf<FlashcardData>()
                val subEntries = listEntries(entry.path)
                for (subEntry in subEntries) {
                    when (val subResult = readFlashcards(path, subEntry)) {
                        is FlashcardSet -> result += subResult.cards
                        is FlashcardSetError -> return subResult
                    }
                }
                FlashcardSet(result)
            }
            else -> FlashcardSet(emptyList())
        }
    }

    fun fullPath(path: RelativePath): FullPath = FullPath(this, path)
}

private operator fun Duration.times(d: Double): Duration = Duration.ofSeconds((toSeconds().toDouble() * d).roundToLong())
