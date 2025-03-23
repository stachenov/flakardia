package name.tachenov.flakardia.presenter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import name.tachenov.flakardia.assertEDT
import name.tachenov.flakardia.debugMode
import java.util.concurrent.atomic.AtomicInteger

interface View {
    suspend fun run()
    fun adjustSize()
    fun showError(title: String, message: String)
}

interface PresenterState

abstract class Presenter<S : PresenterState, V : View> {
    lateinit var view: V
        private set

    abstract val state: Flow<S>

    private var currentRunScope: CoroutineScope? = null
    private val currentlyRunningTasks = AtomicInteger()

    protected abstract fun initializeState()

    suspend fun run(view: V) = coroutineScope {
        assertEDT()
        this@Presenter.view = view
        launch {
            initializeState()
        }
        currentRunScope = this@coroutineScope
        try {
            view.run()
        }
        finally {
            currentRunScope = null
        }
    }

    protected fun launchUiTask(task: suspend () -> Unit) {
        assertEDT()
        val taskJob = currentRunScope?.launch {
            task()
        } ?: return
        val presenterName = this@Presenter.javaClass.name
        val runningAfterStart = currentlyRunningTasks.incrementAndGet()
        if (debugMode.isVerbose) {
            System.err.println("Task started, $runningAfterStart tasks running in the presenter $presenterName")
        }
        taskJob.invokeOnCompletion {
            val runningAfterStop = currentlyRunningTasks.decrementAndGet()
            if (debugMode.isVerbose) {
                System.err.println("Task stopped, $runningAfterStop tasks running in the presenter $presenterName")
            }
        }
    }
}

suspend fun <S : PresenterState, P : Presenter<S, V>, V : View> showPresenterFrame(
    presenterFactory: () -> P,
    viewFactory: (P) -> V,
) {
    val presenter = presenterFactory()
    val view = viewFactory(presenter)
    presenter.run(view)
}
