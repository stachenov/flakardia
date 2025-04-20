package name.tachenov.flakardia

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

fun backgroundModelTest(code: suspend () -> Unit) {
    runBlocking {
        accessModel(code)
    }
}

fun uiTest(block: suspend CoroutineScope.() -> Unit) {
    var done = false
    try {
        runBlocking {
            withContext(edtDispatcherForTesting) {
                block()
                done = true
                cancel()
            }
        }
    } catch (e: CancellationException) {
        if (!done) throw e
    }
}
