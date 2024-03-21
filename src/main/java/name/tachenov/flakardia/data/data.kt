package name.tachenov.flakardia.data

import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.FlashcardSetListEntry
import name.tachenov.flakardia.app.FlashcardSetUpEntry
import name.tachenov.flakardia.assertBGT
import name.tachenov.flakardia.storage.FlashcardStorage

sealed class FlashcardSetResult

data class FlashcardSet(
    val name: String,
    val cards: List<Flashcard>,
) : FlashcardSetResult()

data class FlashcardSetError(val message: String) : FlashcardSetResult()

data class Flashcard(
    val front: Word,
    val back: Word,
)

data class Word(
    val value: String,
)

data class RelativePath(
    val elements: List<String>,
) {
    constructor() : this(emptyList())

    val parent: RelativePath?
        get() = if (elements.isEmpty()) null else RelativePath(elements.subList(0, elements.size - 1))

    val fileName: String
        get() = elements.last()

    fun isEmpty(): Boolean = elements.isEmpty()

    override fun toString(): String = elements.joinToString("/")
}

data class FullPath(
    val library: Library,
    val relativePath: RelativePath,
) {
    override fun toString(): String = if (relativePath.isEmpty()) library.path else library.path + "/" + relativePath.toString()
}

data class Library(val storage: FlashcardStorage) {

    val path: String
        get() = storage.path

    fun readEntries(path: RelativePath): List<FlashcardSetListEntry> {
        assertBGT()
        val result = mutableListOf<FlashcardSetListEntry>()
        val parent = path.parent
        if (parent != null) {
            result += FlashcardSetUpEntry(parent)
        }
        result += storage.readEntries(this, path)
        return result.sortedWith(compareBy({ it is FlashcardSetFileEntry }, { it.name }))
    }

    fun readFlashcards(file: RelativePath): FlashcardSetResult = storage.readFlashcards(file)

    fun fullPath(path: RelativePath): FullPath = FullPath(this, path)
}
