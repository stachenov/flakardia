package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.FlashcardSetResult
import name.tachenov.flakardia.data.readFlashcards
import java.nio.file.Files
import java.nio.file.Path

class CardManager {

    private var path: Path = Path.of("cards")

    var entries: List<CardListEntry> = readEntries()
        private set

    private fun readEntries(): List<CardListEntry> {
        val result = mutableListOf<CardListEntry>()
        Files.newDirectoryStream(path).use { dir ->
            dir.forEach { entry ->
                if (Files.isRegularFile(entry) && Files.isReadable(entry)) {
                    result += CardSetFileEntry(entry)
                }
            }
        }
        return result
    }

}

sealed class CardListEntry

data class CardSetFileEntry(val file: Path) : CardListEntry() {
    fun readCards(): FlashcardSetResult = readFlashcards(file)
}
