package name.tachenov.flakardia.presenter

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.configureAndEnterLibrary
import name.tachenov.flakardia.data.LessonData
import name.tachenov.flakardia.data.LessonDataError
import name.tachenov.flakardia.data.LessonDataResult
import name.tachenov.flakardia.data.LessonDataWarnings
import name.tachenov.flakardia.data.RelativePath
import name.tachenov.flakardia.showSettingsDialog
import name.tachenov.flakardia.launchUiTask
import name.tachenov.flakardia.backgroundWithProgress

interface CardSetManagerView {
    suspend fun run()
    fun adjustSize()
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
) {
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
    view: (CardSetManagerPresenter) -> CardSetManagerView,
) {

    val state: MutableStateFlow<CardSetManagerPresenterState> = MutableStateFlow(CardSetManagerPresenterState())

    private val view = view(this)

    suspend fun run() = coroutineScope {
        launch {
            updateEntries()
        }
        launch {
            view.run()
        }
    }

    fun configure() {
        if (showSettingsDialog()) {
            configureAndEnterLibrary(manager) {
                updateEntries()
                view.adjustSize()
            }
        }
    }

    fun selectItem(item: CardListEntryPresenter?) {
        state.value = state.value.copy(selectedEntry = item)
    }

    fun scrollRequestCompleted() {
        state.value = state.value.copy(isScrollToSelectionRequested = false)
    }

    fun goUp() {
        val list = state.value.entries
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
        val selectedEntry = state.value.selectedEntry?.entry ?: return
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
        state.value = state.value.copy(
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
