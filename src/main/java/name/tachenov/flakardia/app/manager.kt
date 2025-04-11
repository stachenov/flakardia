package name.tachenov.flakardia.app

import name.tachenov.flakardia.assertModelAccessAllowed
import name.tachenov.flakardia.data.FullPath
import name.tachenov.flakardia.data.RelativePath
import name.tachenov.flakardia.data.plus

class CardManager {

    var library: Library? = null
        get() {
            assertModelAccessAllowed()
            return field
        }
        private set

    var path: FullPath? = null
        get() {
            assertModelAccessAllowed()
            return field
        }
        private set

    var entries: List<FlashcardSetListEntry> = emptyList()
        get() {
            assertModelAccessAllowed()
            return field
        }
        private set

    fun enterLibrary(library: Library): DirEnterResult {
        assertModelAccessAllowed()
        if (library == this.library) return DirEnterSuccess
        this.library = library
        return enter(RelativePath())
    }

    fun enter(path: RelativePath): DirEnterResult {
        assertModelAccessAllowed()
        try {
            val library = this.library ?: return DirEnterError("No library selected")
            entries = when (val result = library.listEntries(path)) {
                is EntryList -> result.entries
                is EntryListError -> return DirEnterError(result.message)
            }
            this.path = library.fullPath(path)
            return DirEnterSuccess
        }
        catch (e: Exception) {
            return DirEnterError(e.toString())
        }
    }

    fun createDir(name: String): CreateResult = runCreateAction(name) { path ->
        createDir(path)
    }

    fun createFile(name: String): CreateResult = runCreateAction(name) { path ->
        createFile(path)
    }

    private fun runCreateAction(name: String, createWhatever: Library.(RelativePath) -> Unit): CreateResult {
        assertModelAccessAllowed()
        return try {
            val library = this.library ?: return CreateError("No library selected")
            val currentPath = this.path ?: return CreateError("No path selected")
            val newElementPath = currentPath + name
            library.createWhatever(newElementPath.relativePath)
            when (val newEntries = library.listEntries(currentPath.relativePath)) {
                is EntryList -> entries = newEntries.entries
                is EntryListError -> return CreateWarning(newEntries.message)
            }
            CreateSuccess
        } catch (e: Exception) {
            CreateError(e.toString())
        }
    }

}

sealed class DirEnterResult

data object DirEnterSuccess : DirEnterResult()

data class DirEnterError(val message: String) : DirEnterResult()

sealed class EntryListResult

data class EntryList(
    val entries: List<FlashcardSetListEntry>
) : EntryListResult()

data class EntryListError(val message: String) : EntryListResult()

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

sealed class CreateResult

data object CreateSuccess : CreateResult()

data class CreateWarning(val message: String) : CreateResult()

data class CreateError(val message: String) : CreateResult()
