package name.tachenov.flakardia.app

import name.tachenov.flakardia.data.FullPath
import name.tachenov.flakardia.data.RelativePath

class CardManager {

    var library: Library? = null
        private set

    var path: FullPath? = null
        private set

    var entries: List<FlashcardSetListEntry> = emptyList()
        private set

    fun enterLibrary(library: Library): DirEnterResult {
        if (library == this.library) return DirEnterSuccess
        this.library = library
        return enter(RelativePath())
    }

    fun enter(path: RelativePath): DirEnterResult {
        try {
            val library = this.library ?: return DirEnterError("No library selected")
            entries = library.listEntries(path)
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
    abstract val path: RelativePath
    abstract val name: String
}

data class FlashcardSetFileEntry(override val path: RelativePath) : FlashcardSetListEntry() {
    override val name: String
        get() = path.fileName
}

data class FlashcardSetDirEntry(override val path: RelativePath) : FlashcardSetListEntry() {
    override val name: String
        get() = path.fileName
}

data class FlashcardSetUpEntry(override val path: RelativePath) : FlashcardSetListEntry() {
    override val name: String
        get() = ".."
}
