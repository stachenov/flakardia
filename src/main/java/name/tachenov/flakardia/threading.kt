package name.tachenov.flakardia

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import name.tachenov.flakardia.ui.dialogIndicator
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

abstract class ProgressIndicator {
    abstract val isCancelled: Boolean
    protected abstract fun cancel()
    abstract var currentOperation: String?
    abstract var currentOperationProgress: Int
    abstract fun init(coroutineScope: CoroutineScope)
    abstract fun close()
}

object EmptyProgressIndicator : ProgressIndicator() {
    override val isCancelled: Boolean = false
    override fun cancel() { }
    override var currentOperation: String? = null
    override var currentOperationProgress: Int = 0
    override fun init(coroutineScope: CoroutineScope) { }
    override fun close() { }
}

abstract class UiProgressIndicator : ProgressIndicator() {
    override var isCancelled: Boolean = false
    private val currentOperationFlow = MutableStateFlow<String?>(null)
    private val currentOperationProgressFlow = MutableStateFlow(0)
    private var currentOperationJob: Job? = null
    private var currentOperationProgressJob: Job? = null

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

    override fun cancel() {
        isCancelled = true
    }

    override fun init(coroutineScope: CoroutineScope) {
        currentOperationJob = coroutineScope.launch(edtDispatcher) {
            currentOperationFlow.collect {
                publishCurrentOperation(it)
            }
        }
        currentOperationProgressJob= coroutineScope.launch(edtDispatcher) {
            currentOperationProgressFlow.collect {
                publishProgress(it)
            }
        }
    }

    override fun close() {
        currentOperationJob?.cancel()
        currentOperationProgressJob?.cancel()
    }
}

fun reportCurrentOperation(currentOperation: String) {
    if (currentIndicator().isCancelled) {
        throw CancellationException("Cancelled")
    }
    currentIndicator().currentOperation = currentOperation
}

fun reportProgress(progress: Int) {
    if (currentIndicator().isCancelled) {
        throw CancellationException("Cancelled")
    }
    currentIndicator().currentOperationProgress = progress
}

private val currentIndicator = ThreadLocal<ProgressIndicator>()

private fun currentIndicator(): ProgressIndicator = currentIndicator.get() ?: EmptyProgressIndicator

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

private val taskChannel = Channel<suspend () -> Unit>(Channel.UNLIMITED)

fun launchUiTask(code: suspend () -> Unit) {
    check(taskChannel.trySend(code).isSuccess)
}

private val runningTasks = AtomicInteger()

suspend fun uiTaskLoop() = supervisorScope {
    for (task in taskChannel) {
        val job = launch(edtDispatcher) {
            try {
                task()
            }
            catch (e: Exception) {
                if (debugMode.isDebugEnabled && e !is CancellationException) {
                    e.printStackTrace()
                }
            }
        }
        val nowRunning = runningTasks.incrementAndGet()
        if (debugMode.isVerbose) {
            System.err.println("A task has started, now running tasks: $nowRunning")
        }
        job.invokeOnCompletion {
            val remainingTasks = runningTasks.decrementAndGet()
            if (debugMode.isVerbose) {
                System.err.println("A task has finished, remaining tasks: $remainingTasks")
            }
        }
    }
}

suspend fun <T> background(code: () -> T): T =
    withContext(Dispatchers.IO) {
        code()
    }

suspend fun <T> backgroundWithProgress(code: suspend () -> T): T =
    withContext(edtDispatcher) {
        val indicator = dialogIndicator()
        try {
            indicator.init(this)
            withContext(currentIndicator.asContextElement(value = indicator) + Dispatchers.IO) {
                code()
            }
        }
        finally {
            indicator.close()
        }
    }

private val edtDispatcher = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        SwingUtilities.invokeLater(block)
    }
}
