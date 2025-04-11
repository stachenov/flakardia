package name.tachenov.flakardia.app

import name.tachenov.flakardia.assertModelAccessAllowed
import name.tachenov.flakardia.data.FlashcardDraft

class DuplicateDetector {
    private val cardsByQuestion = mutableMapOf<String, MutableSet<FlashcardDraft>>()
    private val cardsByAnswer = mutableMapOf<String, MutableSet<FlashcardDraft>>()

    fun removeCard(card: FlashcardDraft) {
        assertModelAccessAllowed()
        cardsByQuestion[card.question]?.remove(card)
        cardsByAnswer[card.answer]?.remove(card)
    }

    fun addCard(card: FlashcardDraft) {
        assertModelAccessAllowed()
        if (card.question.isNotBlank()) {
            cardsByQuestion.getOrPut(card.question) { mutableSetOf() }.add(card)
        }
        if (card.answer.isNotBlank()) {
            cardsByAnswer.getOrPut(card.answer) { mutableSetOf() }.add(card)
        }
    }

    fun getQuestionDuplicates(card: FlashcardDraft): List<FlashcardDraft> {
        assertModelAccessAllowed()
        return cardsByQuestion[card.question]?.filter { it.id != card.id } ?: emptyList()
    }

    fun getAnswerDuplicates(card: FlashcardDraft): List<FlashcardDraft> {
        assertModelAccessAllowed()
        return cardsByAnswer[card.answer]?.filter { it.id != card.id } ?: emptyList()
    }
}
