package name.tachenov.flakardia.presenter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import name.tachenov.flakardia.*
import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.data.*

interface CardSetManagerView : View {
    suspend fun viewFlashcards(result: LessonData)
    suspend fun startLesson(library: Library, result: LessonData)
    suspend fun editFile(library: Library, fileEntry: FlashcardSetFileEntry)
    fun showWarnings(warnings: List<String>)
    fun showError(error: String)
}

data class CardSetManagerPresenterState(
    val currentPresentablePath: String?,
    val currentRelativePath: RelativePath?,
    val entries: List<CardListEntryPresenter>,
    val selectedEntry: CardListEntryPresenter?,
    val isScrollToSelectionRequested: Boolean,
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

data class CardSetManagerPresenterSavedState(
    val currentPath: RelativePath?,
    val selectedEntry: RelativePath?,
)

class CardSetManagerPresenter(
    private val manager: CardManager,
): Presenter<CardSetManagerPresenterState, CardSetManagerView>() {

    private val mutableState: MutableStateFlow<CardSetManagerPresenterState?> = MutableStateFlow(null)

    override val state: Flow<CardSetManagerPresenterState>
        get() = mutableState.asStateFlow().filterNotNull()

    override suspend fun initializeState() {
        underModelLock {
            val state = getCardSetManagerPresenterSavedState()
            var restoredPath = false
            if (state.currentPath != null) {
                val enterAttemptResult = backgroundWithProgress(this) {
                    manager.enter(state.currentPath)
                }
                restoredPath = enterAttemptResult is DirEnterSuccess
            }
            if (restoredPath && state.selectedEntry != null) {
                updateEntries(selectEntry = state.selectedEntry)
            }
            else {
                updateEntries()
            }
        }
    }

    fun configure() {
        launchUiTask {
            val library = underModelLock {
                background {
                    manager.library
                }
            }
            if (showSettingsDialog()) {
                val newLibrary = configureAndEnterLibrary(manager)
                if (newLibrary != library) {
                    updateEntries()
                }
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
        mutableState.value = mutableState.value?.copy(selectedEntry = item)
        saveState()
    }

    fun scrollRequestCompleted() {
        mutableState.value = mutableState.value?.copy(isScrollToSelectionRequested = false)
    }

    fun goUp() {
        val list = mutableState.value?.entries
        if (list.isNullOrEmpty()) {
            return
        }
        val firstEntry = list.firstOrNull()?.entry ?: return
        if (firstEntry !is FlashcardSetUpEntry) {
            return
        }
        enterDir(firstEntry.path, selectDir = mutableState.value?.currentRelativePath)
    }

    fun openElement() {
        val selectedEntry = mutableState.value?.selectedEntry?.entry ?: return
        when (selectedEntry) {
            is FlashcardSetFileEntry -> viewFlashcards(selectedEntry)
            is FlashcardSetDirEntry -> enterDir(selectedEntry.path)
            is FlashcardSetUpEntry -> goUp()
        }
    }

    private fun enterDir(dirPath: RelativePath, selectDir: RelativePath? = null) {
        launchUiTask {
            underModelLock {
                val result = backgroundWithProgress(this) {
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
    }

    private suspend fun updateEntries(selectEntry: RelativePath? = null) {
        mutableState.value = underModelLock {
            background {
                val newList = manager.entries.map { CardListEntryPresenter(it) }
                val newSelection: CardListEntryPresenter? = newList.firstOrNull { it.entry.path == selectEntry } ?: newList.firstOrNull()
                CardSetManagerPresenterState(
                    currentPresentablePath = manager.path?.toString(),
                    currentRelativePath = manager.path?.relativePath,
                    entries = newList,
                    selectedEntry = newSelection,
                    isScrollToSelectionRequested = newSelection != null,
                )
            }
        }
        saveState()
    }

    private fun saveState() {
        setCardSetManagerPresenterSavedState(
            CardSetManagerPresenterSavedState(
                currentPath = mutableState.value?.currentRelativePath,
                selectedEntry = mutableState.value?.selectedEntry?.entry?.path,
            )
        )
    }

    fun viewFlashcards(entry: FlashcardSetListEntry) {
        launchUiTask {
            val result = underModelLock {
                backgroundWithProgress(this) {
                    manager.library?.getAllFlashcards(entry)
                }
            } ?: return@launchUiTask
            processResult(result) { lessonData ->
                view.viewFlashcards(lessonData)
            }
        }
    }

    fun startLesson(entry: FlashcardSetListEntry) {
        launchUiTask {
            val result = underModelLock {
                backgroundWithProgress(this) {
                    manager.library?.let { library -> library to library.prepareLessonData(entry) }
                }
            } ?: return@launchUiTask
            processResult(result.second) { lessonData ->
                view.startLesson(result.first, lessonData)
            }
        }
    }

    private suspend fun processResult(result: LessonDataResult, onSuccess: suspend (LessonData) -> Unit) {
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

    fun newDir(name: String) {
        launchCreateAction(name) {
            createDir(name)
        }
    }

    fun newFile(name: String) {
        val realName = if (name.endsWith(".txt", ignoreCase = true)) {
            name
        }
        else {
            "$name.txt"
        }
        launchCreateAction(realName) {
            createFile(realName)
        }
    }

    private fun launchCreateAction(name: String, createWhatever: CardManager.() -> CreateResult) {
        launchUiTask {
            when (val result = underModelLock { background { manager.createWhatever() } }) {
                is CreateSuccess -> {
                    updateEntries(mutableState.value?.currentRelativePath?.plus(name))
                }
                is CreateError -> {
                    view.showError(result.message)
                }
            }
        }
    }

    fun editFile(entry: FlashcardSetListEntry) {
        if (entry !is FlashcardSetFileEntry) return
        launchUiTask {
            val library = underModelLock {
                background {
                    manager.library
                }
            } ?: return@launchUiTask
            view.editFile(library, entry)
        }
    }
}
