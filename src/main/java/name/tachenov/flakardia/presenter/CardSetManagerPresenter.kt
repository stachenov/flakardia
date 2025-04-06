package name.tachenov.flakardia.presenter

import name.tachenov.flakardia.*
import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.data.*

interface CardSetManagerView : View {
    suspend fun viewFlashcards(result: LessonData)
    suspend fun startLesson(library: Library, result: LessonData)
    suspend fun editFile(library: Library, fileEntry: FlashcardSetFileEntry, cards: List<Flashcard>)
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

    override suspend fun computeInitialState(): CardSetManagerPresenterState =
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
                captureManagerState(selectEntry = state.selectedEntry)
            }
            else {
                captureManagerState()
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
                    updateState {
                        captureManagerState()
                    }
                }
                view.adjustSize()
            }
        }
    }

    private suspend fun captureManagerState(selectEntry: RelativePath? = null): CardSetManagerPresenterState {
        val result = underModelLock {
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
        saveState(result)
        return result
    }

    private fun saveState(state: CardSetManagerPresenterState) {
        setCardSetManagerPresenterSavedState(
            CardSetManagerPresenterSavedState(
                currentPath = state.currentRelativePath,
                selectedEntry = state.selectedEntry?.entry?.path,
            )
        )
    }

    fun showHelpFrame() {
        launchUiTask {
            showHelp()
        }
    }

    fun selectItem(item: CardListEntryPresenter?) {
        updateState { state ->
            val result = state.copy(selectedEntry = item)
            saveState(result)
            result
        }
    }

    fun scrollRequestCompleted() {
        updateState { state -> state.copy(isScrollToSelectionRequested = false) }
    }

    fun goUp() {
        updateState { state ->
            val list = state.entries
            if (list.isEmpty()) {
                return@updateState null
            }
            val firstEntry = list.firstOrNull()?.entry ?: return@updateState null
            if (firstEntry !is FlashcardSetUpEntry) {
                return@updateState null
            }
            enterDir(firstEntry.path)
        }
    }

    fun openElement() {
        updateState { state ->
            val selectedEntry = state.selectedEntry?.entry ?: return@updateState null
            when (selectedEntry) {
                is FlashcardSetFileEntry -> {
                    viewFlashcards(selectedEntry)
                    null
                }
                is FlashcardSetDirEntry, is FlashcardSetUpEntry -> {
                    enterDir(selectedEntry.path)
                }
            }
        }
    }

    private suspend fun enterDir(dirPath: RelativePath): CardSetManagerPresenterState? {
        val (newState, error) = underModelLock {
            val (oldPath, enterResult) = backgroundWithProgress(this) {
                manager.path?.relativePath to manager.enter(dirPath)
            }
            when (enterResult) {
                is DirEnterSuccess -> {
                    captureManagerState(selectEntry = if (dirPath == oldPath?.parent) oldPath else null) to null
                }
                is DirEnterError -> {
                    null to enterResult
                }
            }
        }
        if (error != null) {
            view.showError(error.message)
        }
        return newState
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
        updateState { state ->
            val (newState, error) = underModelLock {
                when (val result = background { manager.createWhatever() }) {
                    is CreateSuccess -> {
                        captureManagerState(selectEntry = state.currentRelativePath?.plus(name)) to null
                    }
                    is CreateError -> {
                        null to result
                    }
                }
            }
            val newFileEntry = newState?.entries?.map { it.entry }?.find { it is FlashcardSetFileEntry && it.name == name }
            if (newFileEntry != null) {
                editFile(newFileEntry)
            }
            if (error != null) {
                view.showError(error.message)
            }
            newState
        }
    }

    fun editFile(entry: FlashcardSetListEntry) {
        if (entry !is FlashcardSetFileEntry) return
        launchUiTask {
            val data = underModelLock {
                backgroundWithProgress(this@CardSetManagerPresenter) {
                    manager.library?.let { library -> library to library.readFlashcards(entry) }
                }
            } ?: return@launchUiTask
            val library = data.first
            val cards = when (val cardsResult = data.second) {
                is FlashcardSet -> cardsResult.cards.map { it.flashcard }
                is FlashcardSetError -> {
                    view.showError("Error opening the file", cardsResult.message)
                    return@launchUiTask
                }
            }
            view.editFile(library, entry, cards)
        }
    }
}
