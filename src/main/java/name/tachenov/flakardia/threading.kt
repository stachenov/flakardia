package name.tachenov.flakardia

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import name.tachenov.flakardia.presenter.Presenter
import name.tachenov.flakardia.ui.dialogIndicator
import javax.swing.SwingUtilities

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

fun assertBGT() {
    if (SwingUtilities.isEventDispatchThread()) {
        throw AssertionError("Shouldn't be called from UI")
    }
}

fun assertEDT() {
    if (!SwingUtilities.isEventDispatchThread()) {
        throw AssertionError("Should be called from the EDT")
    }
}

suspend fun <T> background(code: () -> T): T =
    withContext(Dispatchers.IO) {
        code()
    }

suspend fun <T> backgroundWithProgress(owner: Presenter<*, *>, code: suspend () -> T): T =
    coroutineScope {
        assertEDT()
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
