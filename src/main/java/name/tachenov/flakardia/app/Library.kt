package name.tachenov.flakardia.app

import name.tachenov.flakardia.LimitedValue
import name.tachenov.flakardia.assertBGT
import name.tachenov.flakardia.data.*
import name.tachenov.flakardia.getLessonSettings
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong
import kotlin.random.Random

private val defaultLessonSettings = LessonSettings()

data class LessonSettings(
    val maxWordsPerLesson: LimitedValue<Int> = LimitedValue.of(30, 3, 100),
    val intervalMultiplierWithoutMistakes: LimitedValue<Double> = LimitedValue.of(5.0, 1.0, 100.0),
    val minIntervalWithoutMistakes: LimitedValue<Duration> = LimitedValue.of(Duration.ofDays(5L), Duration.ofHours(1L), Duration.ofDays(100L)),
    val intervalMultiplierWithMistake: LimitedValue<Double> = LimitedValue.of(0.8, 0.1, 10.0),
    val minIntervalWithMistake: LimitedValue<Duration> = LimitedValue.of(Duration.ofHours(3L), Duration.ofHours(1L), Duration.ofDays(100L)),
    val intervalMultiplierWithManyMistakes: LimitedValue<Double> = LimitedValue.of(0.5, 0.01, 1.0),
    val minIntervalWithManyMistakes: LimitedValue<Duration> = LimitedValue.of(Duration.ofHours(1L), Duration.ofHours(1L), Duration.ofDays(100L)),
    val randomness: LimitedValue<Double> = LimitedValue.of(0.1, 0.0, 0.99)
) {
    constructor(
        maxWordsPerLesson: Int,
        intervalMultiplierWithoutMistakes: Double,
        minIntervalWithoutMistakes: Duration,
        intervalMultiplierWithMistake: Double,
        minIntervalWithMistake: Duration,
        intervalMultiplierWithManyMistakes: Double,
        minIntervalWithManyMistakes: Duration,
        randomness: Double,
    ) : this(
        defaultLessonSettings.maxWordsPerLesson.withValue(maxWordsPerLesson),
        defaultLessonSettings.intervalMultiplierWithoutMistakes.withValue(intervalMultiplierWithoutMistakes),
        defaultLessonSettings.minIntervalWithoutMistakes.withValue(minIntervalWithoutMistakes),
        defaultLessonSettings.intervalMultiplierWithMistake.withValue(intervalMultiplierWithMistake),
        defaultLessonSettings.minIntervalWithMistake.withValue(minIntervalWithMistake),
        defaultLessonSettings.intervalMultiplierWithManyMistakes.withValue(intervalMultiplierWithManyMistakes),
        defaultLessonSettings.minIntervalWithManyMistakes.withValue(minIntervalWithManyMistakes),
        defaultLessonSettings.randomness.withValue(randomness),
    )
}

private val lastLearnedFallback: Duration = Duration.ofDays(365)
private val intervalFallback: Duration = Duration.ofDays(1)

interface FlashcardStorage {
    val path: String
    fun readEntries(path: RelativePath): List<FlashcardSetListEntry>
    fun readFlashcards(path: RelativePath): FlashcardSetResult
    fun readLibraryStats(): LibraryStatsResult
    fun saveLibraryStats(stats: LibraryStats): SaveResult
    fun saveFlashcardSetFile(path: RelativePath, flashcards: List<Flashcard>): SaveResult
    fun createDir(path: RelativePath)
    fun createFile(path: RelativePath)
}

data class Library(private val storage: FlashcardStorage) {

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
        return prepareLessonData(entry) { name, flashcards, stats ->
            prepareLessonData(Instant.now(), name, flashcards, stats, getLessonSettings())
        }
    }

    private inline fun prepareLessonData(
        entry: FlashcardSetListEntry,
        prepare: (String, List<FlashcardData>, LibraryStats) -> LessonData,
    ): LessonDataResult {
        assertBGT()
        return when (val flashcardSet = readFlashcards(entry)) {
            is FlashcardSetError -> LessonDataError(flashcardSet.message)
            is FlashcardSet -> when (val stats = storage.readLibraryStats()) {
                is LibraryStatsError -> LessonDataError(stats.message)
                is LibraryStats -> addWarnings(prepare(entry.name, flashcardSet.cards, stats), flashcardSet.cards)
            }
        }
    }

    private fun addWarnings(data: LessonData, cards: List<FlashcardData>): LessonDataResult {
        val cardsByFront = hashMapOf<Word, MutableList<FlashcardData>>()
        val cardsByBack = hashMapOf<Word, MutableList<FlashcardData>>()
        for (cardData in cards) {
            val card = cardData.flashcard
            cardsByFront.getOrPut(card.front) { mutableListOf() } += cardData
            cardsByBack.getOrPut(card.back) { mutableListOf() } += cardData
        }
        val duplicates = mutableSetOf<FlashcardData>()
        duplicates += cardsByFront.values.asSequence().filter { it.size > 1 }.flatten().toSet()
        duplicates += cardsByBack.values.asSequence().filter { it.size > 1 }.flatten().toSet()
        if (duplicates.isNotEmpty()) {
            return LessonDataWarnings(
                data,
                listOf("The following duplicate words were detected") +
                duplicates.map { "${it.path}:${it.flashcard.front.value}:${it.flashcard.back.value}" }
            )
        }
        else {
            return data
        }
    }

    fun saveUpdatedStats(stats: LibraryStats): SaveResult {
        assertBGT()
        return when (val allStats = storage.readLibraryStats()) {
            is LibraryStats -> {
                val updatedStats = allStats.update(stats)
                storage.saveLibraryStats(updatedStats)
            }
            is LibraryStatsError -> SaveError(allStats.message)
        }
    }

    fun saveFlashcardSetFile(fileEntry: FlashcardSetFileEntry, flashcards: List<Flashcard>): SaveResult {
        assertBGT()
        return storage.saveFlashcardSetFile(fileEntry.path, flashcards)
    }

    fun readFlashcards(entry: FlashcardSetListEntry): FlashcardSetResult {
        return when (entry) {
            is FlashcardSetFileEntry -> storage.readFlashcards(entry.path)
            is FlashcardSetDirEntry -> {
                val result = mutableListOf<FlashcardData>()
                val subEntries = listEntries(entry.path)
                for (subEntry in subEntries) {
                    when (val subResult = readFlashcards(subEntry)) {
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

    fun createDir(path: RelativePath) {
        assertBGT()
        storage.createDir(path)
    }

    fun createFile(path: RelativePath) {
        assertBGT()
        storage.createFile(path)
    }
}

fun prepareLessonData(
    now: Instant,
    name: String,
    flashcardList: List<FlashcardData>,
    stats: LibraryStats,
    settings: LessonSettings,
): LessonData {
    assertBGT()
    // Take all flashcards and calculate for every flashcard, when it should be ideally learned next.
    // This is calculated as the last learned time plus the interval, which is the interval between two
    // previous learn times, multiplied by a factor that depends on how many mistakes were there the last time.
    // Then sort all flashcard by the estimated next learn time and simply take N first ones.
    // In theory this should lead to the selection of the most "forgotten" words.
    val flashcards = flashcardList.toMutableList()
    flashcards.shuffle()
    val random = Random.Default
    val orderingKeys = flashcardList.associateWith { cardData ->
        val word = cardData.flashcard.back
        val lastLearned: Instant = stats.wordStats[word]?.lastLearned ?: now.minus(lastLearnedFallback)
        val mistakes = stats.wordStats[word]?.mistakes ?: 0
        val previousInterval: Duration = stats.wordStats[word]?.intervalBeforeLastLearned ?: intervalFallback
        val interval = previousInterval * (when (mistakes) {
            0 -> settings.intervalMultiplierWithoutMistakes.value
            1 -> settings.intervalMultiplierWithMistake.value
            else -> settings.intervalMultiplierWithManyMistakes.value
        }) * (1.0 + settings.randomness.value * random.nextDouble(-1.0, 1.0))
        val minInterval = when (mistakes) {
            0 -> settings.minIntervalWithoutMistakes.value
            1 -> settings.minIntervalWithMistake.value
            else -> settings.minIntervalWithManyMistakes.value
        }
        val isVeryRecent = Duration.between(lastLearned, now) < minInterval
        OrderingKey(lastLearned.plus(interval) as Instant, isVeryRecent)
    }
    flashcards.sortBy { orderingKeys.getValue(it) }
    val effectiveFlashcards = flashcards.subList(0, flashcards.size.coerceAtMost(settings.maxWordsPerLesson.value))
    return LessonData(
        name,
        effectiveFlashcards,
        stats.filter(effectiveFlashcards),
    )
}

private data class OrderingKey(
    val nextLearnTime: Instant,
    val isVeryRecent: Boolean,
) : Comparable<OrderingKey> {
    override fun compareTo(other: OrderingKey): Int = compareValuesBy(this, other,
        { it.isVeryRecent },
        { it.nextLearnTime },
    )
}

private operator fun Duration.times(d: Double): Duration = Duration.ofSeconds((toSeconds().toDouble() * d).roundToLong())
