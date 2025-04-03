package name.tachenov.flakardia.presenter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import name.tachenov.flakardia.app.FlashcardSetFileEntry
import name.tachenov.flakardia.app.Library
import name.tachenov.flakardia.background
import name.tachenov.flakardia.data.Flashcard
import name.tachenov.flakardia.data.FlashcardSet
import name.tachenov.flakardia.data.FlashcardSetError
import name.tachenov.flakardia.underModelLock
import java.util.concurrent.atomic.AtomicLong

interface CardSetFileEditorView : View {

}

sealed class CardSetFileEditorStateUpdate : PresenterState {
    abstract val epoch: Long
}

sealed class CardSetFileEditorFullStateUpdate : CardSetFileEditorStateUpdate()

data class CardSetFileEditorFullState(
    override val epoch: Long,
    val cards: List<CardPresenter>,
) : CardSetFileEditorFullStateUpdate()

data class CardSetFileEditorErrorState(
    override val epoch: Long,
    val message: String,
) : CardSetFileEditorFullStateUpdate()

sealed class CardSetFileEditorIncrementalStateUpdate : CardSetFileEditorStateUpdate()

data class CardAdded(
    override val epoch: Long,
    private val index: Int,
    private val card: CardPresenter,
) : CardSetFileEditorIncrementalStateUpdate()

data class CardChanged(
    override val epoch: Long,
    private val index: Int,
    private val card: CardPresenter,
) : CardSetFileEditorIncrementalStateUpdate()

data class CardRemoved(
    override val epoch: Long,
    private val index: Int,
) : CardSetFileEditorIncrementalStateUpdate()

data class CardPresenter(private val card: Flashcard) {
    val question: String get() = card.front.value
    val answer: String get() = card.back.value
}

class CardSetFileEditorPresenter(
    private val library: Library,
    private val fileEntry: FlashcardSetFileEntry,
) : Presenter<CardSetFileEditorStateUpdate, CardSetFileEditorView>() {
    private val fullState: MutableStateFlow<CardSetFileEditorStateUpdate?> = MutableStateFlow(null)
    private val allStateUpdates: MutableStateFlow<CardSetFileEditorStateUpdate?> = MutableStateFlow(null)
    private val epoch = AtomicLong()

    override val state: Flow<CardSetFileEditorStateUpdate>
        get() = flow {
            val thisFlow = this
            var isFirstUpdate = true
            allStateUpdates.collect { update ->
                val shouldEmit = when (update) {
                    null -> false
                    is CardSetFileEditorFullState -> isFirstUpdate
                    is CardSetFileEditorErrorState -> true
                    is CardSetFileEditorIncrementalStateUpdate -> !isFirstUpdate
                }
                if (shouldEmit && update != null) {
                    isFirstUpdate = false
                    thisFlow.emit(update)
                }
            }
        }

    override suspend fun initializeState() {
        updateState(
            fullUpdate = underModelLock {
                when (val cardsResult = background { library.readFlashcards(fileEntry) }) {
                    is FlashcardSet -> CardSetFileEditorFullState(
                        nextEpoch(),
                        cardsResult.cards.map { CardPresenter(it.flashcard) },
                    )
                    is FlashcardSetError -> CardSetFileEditorErrorState(nextEpoch(), cardsResult.message)
                }
            }
        )
    }

    private fun updateState(fullUpdate: CardSetFileEditorFullStateUpdate, incrementalUpdate: CardSetFileEditorIncrementalStateUpdate? = null) {
        if (incrementalUpdate != null) {
            allStateUpdates.value = incrementalUpdate
        }
        fullState.value = fullUpdate
        allStateUpdates.value = fullUpdate
    }

    private fun nextEpoch(): Long = epoch.incrementAndGet()
}
