package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.FlashcardSetResult
import name.tachenov.flakardia.data.readFlashcards
import java.nio.file.Files
import java.nio.file.Path

class CardManager {

    private var library: Path? = null

    var path: Path? = null
        private set

    var entries: List<FlashcardSetListEntry> = emptyList()
        private set

    fun enter(path: Path): DirEnterResult {
        try {
            entries = readEntries(path)
            this.path = path
            if (this.library == null) {
                library = path
            }
            return DirEnterSuccess
        }
        catch (e: Exception) {
            return DirEnterError(e.toString())
        }
    }

    private fun readEntries(path: Path): List<FlashcardSetListEntry> {
        val result = mutableListOf<FlashcardSetListEntry>()
        if (library != null && path != library) {
            result += FlashcardSetUpEntry(path.parent)
        }
        Files.newDirectoryStream(path).use { dir ->
            dir.forEach { entry ->
                if (Files.isRegularFile(entry) && Files.isReadable(entry)) {
                    result += FlashcardSetFileEntry(entry)
                }
                else if (Files.isDirectory(entry)) {
                    result += FlashcardSetDirEntry(entry)
                }
            }
        }
        return result.sortedWith(compareBy({ it is FlashcardSetFileEntry}, { it.name }))
    }

}

sealed class DirEnterResult

data object DirEnterSuccess : DirEnterResult()

data class DirEnterError(val message: String) : DirEnterResult()

sealed class FlashcardSetListEntry {
    abstract val name: String
}

data class FlashcardSetFileEntry(val file: Path) : FlashcardSetListEntry() {
    override val name: String
        get() = file.fileName.toString()

    fun readCards(): FlashcardSetResult = readFlashcards(file)
}

data class FlashcardSetDirEntry(val dir: Path) : FlashcardSetListEntry() {
    override val name: String
        get() = dir.fileName.toString()
}

data class FlashcardSetUpEntry(val dir: Path) : FlashcardSetListEntry() {
    override val name: String
        get() = ".."
}
