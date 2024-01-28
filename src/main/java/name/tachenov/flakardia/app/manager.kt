package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.FlashcardSetResult
import name.tachenov.flakardia.data.readFlashcards
import java.nio.file.Files
import java.nio.file.Path

class CardManager {

    var path: Path? = null
        private set

    var entries: List<FlashcardSetListEntry> = emptyList()
        private set

    fun enter(path: Path): DirEnterResult {
        try {
            entries = readEntries(path)
            this.path = path
            return DirEnterSuccess
        }
        catch (e: Exception) {
            return DirEnterError(e.toString())
        }
    }

    private fun readEntries(path: Path): List<FlashcardSetListEntry> {
        val result = mutableListOf<FlashcardSetListEntry>()
        Files.newDirectoryStream(path).use { dir ->
            dir.forEach { entry ->
                if (Files.isRegularFile(entry) && Files.isReadable(entry)) {
                    result += FlashcardSetFileEntry(entry)
                }
            }
        }
        return result
    }

}

sealed class DirEnterResult

data object DirEnterSuccess : DirEnterResult()

data class DirEnterError(val message: String) : DirEnterResult()

sealed class FlashcardSetListEntry

data class FlashcardSetFileEntry(val file: Path) : FlashcardSetListEntry() {
    fun readCards(): FlashcardSetResult = readFlashcards(file)
}
