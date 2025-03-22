package name.tachenov.flakardia.presenter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import name.tachenov.flakardia.*
import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.data.*

interface CardSetManagerView : View {
    fun viewFlashcards(result: LessonData)
    fun startLesson(library: Library, result: LessonData)
    fun showWarnings(warnings: List<String>)
    fun showError(error: String)
}

data class CardSetManagerPresenterState(
    val currentPath: String? = null,
    val entries: List<CardListEntryPresenter> = emptyList(),
    val selectedEntry: CardListEntryPresenter? = null,
    val isScrollToSelectionRequested: Boolean = false,
) : PresenterState {
    private val isSomethingSelected: Boolean
        get() = selectedEntry?.entry?.let { it is FlashcardSetFileEntry || it is FlashcardSetDirEntry } ?: false
    val isViewButtonEnabled: Boolean
        get() = isSomethingSelected
    val isLessonButtonEnabled: Boolean
        get() = isSomethingSelected
}

data class CardListEntryPresenter(
    val entry: FlashcardSetListEntry,
) {
    override fun toString(): String = entry.name
}

class CardSetManagerPresenter(
    private val manager: CardManager,
): Presenter<CardSetManagerPresenterState, CardSetManagerView>() {

    private val mutableState: MutableStateFlow<CardSetManagerPresenterState> = MutableStateFlow(CardSetManagerPresenterState())

    override val state: Flow<CardSetManagerPresenterState>
        get() = mutableState.asStateFlow()

    override fun initializeState() {
        updateEntries()
    }

    fun configure() {
        launchUiTask {
            if (showSettingsDialog()) {
                configureAndEnterLibrary(manager)
                updateEntries()
                view.adjustSize()
            }
        }
    }

    fun showHelpFrame() {
        launchUiTask {
            showHelp()
        }
    }

    fun selectItem(item: CardListEntryPresenter?) {
        mutableState.value = mutableState.value.copy(selectedEntry = item)
    }

    fun scrollRequestCompleted() {
        mutableState.value = mutableState.value.copy(isScrollToSelectionRequested = false)
    }

    fun goUp() {
        val list = mutableState.value.entries
        if (list.isEmpty()) {
            return
        }
        val firstEntry = list.firstOrNull()?.entry ?: return
        if (firstEntry !is FlashcardSetUpEntry) {
            return
        }
        enterDir(firstEntry.path, selectDir = manager.path?.relativePath)
    }

    fun openElement() {
        val selectedEntry = mutableState.value.selectedEntry?.entry ?: return
        when (selectedEntry) {
            is FlashcardSetFileEntry -> viewFlashcards(selectedEntry)
            is FlashcardSetDirEntry -> enterDir(selectedEntry.path)
            is FlashcardSetUpEntry -> goUp()
        }
    }

    private fun enterDir(dirPath: RelativePath, selectDir: RelativePath? = null) {
        launchUiTask {
            val result = backgroundWithProgress {
                manager.enter(dirPath)
            }
            when (result) {
                is DirEnterSuccess -> {
                    updateEntries(selectDir)
                }
                is DirEnterError -> {
                    view.showError(result.message)
                }
            }
        }
    }

    private fun updateEntries(selectDir: RelativePath? = null) {
        val newList = manager.entries.map { CardListEntryPresenter(it) }
        val newSelection: CardListEntryPresenter?
        val shouldScroll: Boolean
        if (selectDir == null) {
            newSelection = newList.firstOrNull()
            shouldScroll = false
        } else {
            newSelection = CardListEntryPresenter(FlashcardSetDirEntry(selectDir))
            shouldScroll = true
        }
        mutableState.value = mutableState.value.copy(
            currentPath = manager.path?.toString(),
            entries = newList,
            selectedEntry = newSelection,
            isScrollToSelectionRequested = shouldScroll,
        )
    }

    fun viewFlashcards(entry: FlashcardSetListEntry) {
        val library = manager.library ?: return
        launchUiTask {
            val result = backgroundWithProgress {
                library.getAllFlashcards(entry)
            }
            processResult(result) { lessonData ->
                view.viewFlashcards(lessonData)
            }
        }
    }

    fun startLesson(entry: FlashcardSetListEntry) {
        val library = manager.library ?: return
        launchUiTask {
            val result = backgroundWithProgress {
                library.prepareLessonData(entry)
            }
            processResult(result) { lessonData ->
                view.startLesson(library, lessonData)
            }
        }
    }

    private fun processResult(result: LessonDataResult, onSuccess: (LessonData) -> Unit) {
        when (result) {
            is LessonData -> {
                onSuccess(result)
            }
            is LessonDataWarnings -> {
                view.showWarnings(result.warnings)
                onSuccess(result.data)
            }
            is LessonDataError -> {
                view.showError(result.message)
            }
        }
    }
}
