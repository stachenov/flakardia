package name.tachenov.flakardia.app

import name.tachenov.flakardia.assertModelAccessAllowed
import name.tachenov.flakardia.data.*

interface Duplicate {
    val path: RelativePath
    val question: String
    val answer: String
}

class DuplicateDetector(private val library: Library, private val draftFileEntry: FlashcardSetFileEntry) {
    private val cardsByQuestion = mutableMapOf<String, MutableSet<DuplicateImpl>>()
    private val cardsByAnswer = mutableMapOf<String, MutableSet<DuplicateImpl>>()

    var area: FlashcardSetDirEntry? = null
        set(value) {
            assertModelAccessAllowed()
            field = value
            removeFlashcardsFromOtherFiles()
            if (value != null) {
                addFlashcardsFromOtherFiles(value)
            }
        }

    private fun removeFlashcardsFromOtherFiles() {
        cardsByQuestion.values.forEach { duplicates -> duplicates.removeIf { it.id == null } }
        cardsByAnswer.values.forEach { duplicates -> duplicates.removeIf { it.id == null } }
    }

    private fun addFlashcardsFromOtherFiles(dir: FlashcardSetDirEntry) {
        when (val otherCards = library.readFlashcards(dir)) {
            is FlashcardSet -> {
                for (card in otherCards.cards) {
                    if (card.path != draftFileEntry.path) {
                        addCard(card)
                    }
                }
            }

            is FlashcardSetError -> {}
        }
    }

    fun removeCard(card: FlashcardDraft) {
        assertModelAccessAllowed()
        cardsByQuestion[card.question]?.remove(convert(card))
        cardsByAnswer[card.answer]?.remove(convert(card))
    }

    fun addCard(card: FlashcardDraft) {
        assertModelAccessAllowed()
        addCard(convert(card))
    }

    private fun addCard(card: FlashcardData) {
        assertModelAccessAllowed()
        addCard(convert(card))
    }

    private fun addCard(card: DuplicateImpl) {
        if (card.question.isNotBlank()) {
            cardsByQuestion.getOrPut(card.question) { mutableSetOf() }.add(card)
        }
        if (card.answer.isNotBlank()) {
            cardsByAnswer.getOrPut(card.answer) { mutableSetOf() }.add(card)
        }
    }

    fun getQuestionDuplicates(card: FlashcardDraft): List<Duplicate> {
        assertModelAccessAllowed()
        return cardsByQuestion[card.question]?.filter { it.id != card.id } ?: emptyList()
    }

    fun getAnswerDuplicates(card: FlashcardDraft): List<Duplicate> {
        assertModelAccessAllowed()
        return cardsByAnswer[card.answer]?.filter { it.id != card.id } ?: emptyList()
    }

    private fun convert(card: FlashcardDraft): DuplicateImpl =
        DuplicateImpl(card.id, card.path, card.question, card.answer)

    private fun convert(card: FlashcardData): DuplicateImpl =
        DuplicateImpl(id = null, card.path, card.flashcard.question.value, card.flashcard.answer.value)

    private data class DuplicateImpl(
        val id: FlashcardDraftId?,
        override val path: RelativePath,
        override val question: String,
        override val answer: String,
    ) : Duplicate
}
