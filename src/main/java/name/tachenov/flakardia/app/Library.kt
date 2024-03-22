package name.tachenov.flakardia.app

import name.tachenov.flakardia.assertBGT
import name.tachenov.flakardia.data.*
import name.tachenov.flakardia.storage.FlashcardStorage

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
