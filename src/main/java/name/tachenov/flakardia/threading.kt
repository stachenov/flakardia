package name.tachenov.flakardia

import kotlinx.coroutines.*
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

fun assertBGT() {
    if (SwingUtilities.isEventDispatchThread()) {
        throw AssertionError("Shouldn't be called from UI")
    }
}

fun threading(code: suspend () -> Unit) {
    scope.launch {
        code()
    }
}

suspend fun <T> background(code: () -> T) =
    withContext(Dispatchers.IO) {
        code()
    }

suspend fun <T> ui(code: () -> T) =
    withContext(edtDispatcher) {
        code()
    }

private val scope = CoroutineScope(SupervisorJob())

private val edtDispatcher = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        SwingUtilities.invokeLater(block)
    }
}
