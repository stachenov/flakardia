package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.FullPath
import name.tachenov.flakardia.data.Library
import name.tachenov.flakardia.data.RelativePath

class CardManager {

    var library: Library? = null
        private set

    var path: FullPath? = null
        private set

    var entries: List<FlashcardSetListEntry> = emptyList()
        private set

    fun enterLibrary(library: Library): DirEnterResult {
        this.library = library
        return enter(RelativePath())
    }

    fun enter(path: RelativePath): DirEnterResult {
        try {
            val library = this.library ?: return DirEnterError("No library selected")
            entries = library.readEntries(path)
            this.path = library.fullPath(path)
            return DirEnterSuccess
        }
        catch (e: Exception) {
            return DirEnterError(e.toString())
        }
    }

}

sealed class DirEnterResult

data object DirEnterSuccess : DirEnterResult()

data class DirEnterError(val message: String) : DirEnterResult()

sealed class FlashcardSetListEntry {
    abstract val name: String
}

data class FlashcardSetFileEntry(val library: Library, val file: RelativePath) : FlashcardSetListEntry() {
    override val name: String
        get() = file.fileName
}

data class FlashcardSetDirEntry(val dir: RelativePath) : FlashcardSetListEntry() {
    override val name: String
        get() = dir.fileName
}

data class FlashcardSetUpEntry(val dir: RelativePath) : FlashcardSetListEntry() {
    override val name: String
        get() = ".."
}
