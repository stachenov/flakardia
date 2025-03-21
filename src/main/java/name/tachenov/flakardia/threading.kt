package name.tachenov.flakardia

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

object ProgressIndicatorKey: CoroutineContext.Key<ProgressIndicator>

abstract class ProgressIndicator : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = ProgressIndicatorKey
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
        coroutineScope.launch(edtDispatcher) {
            currentOperationFlow.collect {
                publishCurrentOperation(it)
            }
        }
        coroutineScope.launch(edtDispatcher) {
            currentOperationProgressFlow.collect {
                publishProgress(it)
            }
        }
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

fun threading(indicator: ProgressIndicator, code: suspend () -> Unit) {
    scope.launch(indicator) {
        indicator.init(scope)
        try {
            code()
        } catch (ignored: CancellationException) {
        } finally {
            indicator.close()
        }
    }
}

suspend fun <T> background(code: () -> T): T =
    withContext(Dispatchers.IO) {
        val indicator = currentCoroutineContext()[ProgressIndicatorKey]
        currentIndicator.set(indicator)
        try {
            code()
        } finally {
            currentIndicator.remove()
        }
    }

suspend fun <T> ui(code: () -> T) =
    withContext(edtDispatcher) {
        code()
    }

val scope = CoroutineScope(SupervisorJob())

val edtDispatcher = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        SwingUtilities.invokeLater(block)
    }
}
