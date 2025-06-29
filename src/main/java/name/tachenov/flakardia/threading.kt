package name.tachenov.flakardia

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import name.tachenov.flakardia.presenter.Presenter
import name.tachenov.flakardia.ui.dialogIndicator
import org.jetbrains.annotations.TestOnly
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

abstract class ProgressIndicator {
    abstract val job: Job?
    abstract var currentOperation: String?
    abstract var currentOperationProgress: Int
    abstract suspend fun run()
}

abstract class UiProgressIndicator(override val job: Job) : ProgressIndicator() {
    private val currentOperationFlow = MutableStateFlow<String?>(null)
    private val currentOperationProgressFlow = MutableStateFlow(0)

    override var currentOperation: String?
        get() = currentOperationFlow.value
        set(value) {
            currentOperationFlow.value = value
        }

    override var currentOperationProgress: Int
        get() = currentOperationProgressFlow.value
        set(value) {
            currentOperationProgressFlow.value = value
        }

    protected abstract fun publishCurrentOperation(currentOperation: String?)
    protected abstract fun publishProgress(progress: Int)
    protected abstract fun close()


    override suspend fun run(): Unit = coroutineScope {
        launch {
            currentOperationFlow.collect {
                publishCurrentOperation(it)
            }
        }
        launch {
            currentOperationProgressFlow.collect {
                publishProgress(it)
            }
        }
        try {
            awaitCancellation()
        }
        finally {
            close()
        }
    }
}

fun reportCurrentOperation(currentOperation: String) {
    currentIndicator()?.job?.ensureActive()
    currentIndicator()?.currentOperation = currentOperation
}

fun reportProgress(progress: Int) {
    currentIndicator()?.job?.ensureActive()
    currentIndicator()?.currentOperationProgress = progress
}

private val currentIndicator = ThreadLocal<ProgressIndicator>()

private fun currentIndicator(): ProgressIndicator? = currentIndicator.get()

private val isUnderModelLock: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

fun assertModelAccessAllowed() {
    if (!isUnderModelLock.get()) {
        throw AssertionError("Model access not allowed")
    }
}

fun assertUiAccessAllowed() {
    if (!SwingUtilities.isEventDispatchThread()) {
        throw AssertionError("Should be called from the EDT")
    }
}

/**
 * Starts the main coroutine on the EDT.
 */
internal inline fun mainCoroutine(crossinline block: suspend CoroutineScope.() -> Unit) {
    runBlocking(edtDispatcher) {
        block()
    }
}

/**
 * Executes a block of code with model access allowed.
 *
 * Reentrant, does not lock the lock if it's already locked.
 * The given code is always executed on a background thread.
 */
suspend fun <T> accessModel(code: suspend () -> T): T =
    underModelLock {
        background {
            code()
        }
    }

/**
 * Executes a block of code with model access allowed and a progress dialog.
 *
 * Unlike [accessModel], not reentrant, but it's allowed to call [accessModel] inside (not vice versa).
 * Can only be called from UI code because it needs UI access to show the dialog.
 * The given code is always executed on a background thread.
 */
suspend fun <T> accessModelWithProgress(owner: Presenter<*, *>, code: suspend () -> T): T =
    underModelLock {
        backgroundWithProgress(owner) {
            code()
        }
    }

/**
 * Executes a block of UI code within model code and returns its result.
 */
fun <T> askUi(block: () -> T): T {
    assertModelAccessAllowed()
    return runBlocking(edtDispatcher) {
        block()
    }
}

private suspend fun <T> background(code: suspend () -> T): T {
    assertModelAccessAllowed()
    return withContext(Dispatchers.IO) {
        code()
    }
}

private suspend fun <T> backgroundWithProgress(owner: Presenter<*, *>, code: suspend () -> T): T =
    coroutineScope {
        // This is the only place where both UI and model access are allowed,
        // as UI access is needed to show the progress bar.
        // But once the progress dialog is created, we transfer to background immediately.
        assertUiAccessAllowed()
        assertModelAccessAllowed()
        val indicator = dialogIndicator(owner, currentCoroutineContext().job)
        val indicatorJob = launch {
            indicator.run()
        }
        try {
            withContext(currentIndicator.asContextElement(value = indicator) + Dispatchers.IO) {
                code()
            }
        }
        finally {
            indicatorJob.cancel()
        }
    }

private suspend fun <T> underModelLock(code: suspend () -> T): T {
    return if (isUnderModelLock.get()) {
        code()
    }
    else {
        modelLock.withLock {
            coroutineScope {
                withContext(isUnderModelLock.asContextElement(value = true)) {
                    code()
                }
            }
        }
    }
}

private val modelLock = Mutex()

private val edtDispatcher = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        SwingUtilities.invokeLater(block)
    }
}

@get:TestOnly
val edtDispatcherForTesting: CoroutineDispatcher get() = edtDispatcher
