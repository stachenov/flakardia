package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.FlashcardSetResult
import name.tachenov.flakardia.data.readFlashcards
import java.nio.file.Files
import java.nio.file.Path

class CardManager(private var path: Path = Path.of("cards")) {

    var entries: FlashcardSetListResult = readEntries()
        private set

    private fun readEntries(): FlashcardSetListResult {
        try {
            val result = mutableListOf<FlashcardSetListEntry>()
            Files.newDirectoryStream(path).use { dir ->
                dir.forEach { entry ->
                    if (Files.isRegularFile(entry) && Files.isReadable(entry)) {
                        result += FlashcardSetFileEntry(entry)
                    }
                }
            }
            return FlashcardSetList(result)
        } catch (e: Exception) {
            return FlashcardSetListError(e.toString())
        }
    }

}

sealed class FlashcardSetListResult

data class FlashcardSetList(val entries: List<FlashcardSetListEntry>) : FlashcardSetListResult()

data class FlashcardSetListError(val message: String) : FlashcardSetListResult()

sealed class FlashcardSetListEntry

data class FlashcardSetFileEntry(val file: Path) : FlashcardSetListEntry() {
    fun readCards(): FlashcardSetResult = readFlashcards(file)
}
