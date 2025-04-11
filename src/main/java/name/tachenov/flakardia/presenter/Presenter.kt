package name.tachenov.flakardia.presenter

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import name.tachenov.flakardia.assertUiAccessAllowed
import name.tachenov.flakardia.debugMode
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicInteger

interface View {
    suspend fun run()
    fun adjustSize()
    fun showError(title: String, message: String)
    fun showWarnings(warnings: List<String>)
}

interface PresenterState

abstract class Presenter<S : PresenterState, V : View> {
    lateinit var view: V
        private set

    private val stateUpdateChannel = Channel<StateUpdate>(Channel.UNLIMITED)
    private val stateFlow = MutableSharedFlow<S>(replay = 1)

    val state: Flow<S> get() = stateFlow.asSharedFlow().distinctUntilChanged()

    private var currentRunScope: CoroutineScope? = null
    private val currentlyRunningTasks = AtomicInteger()

    private val updateEpoch = AtomicInteger(1) // 1 represents the initial state
    private val appliedUpdateEpoch = MutableStateFlow(0) // 0 means that the initial state has not been applied yet

    private inner class StateUpdate(
        val epoch: Int,
        val update: suspend (S) -> S?,
    ) {
        suspend operator fun invoke(state: S): S? = update(state)
    }

    protected abstract suspend fun computeInitialState(): S

    suspend fun run(view: V) = coroutineScope {
        assertUiAccessAllowed()
        this@Presenter.view = view
        launch {
            var state = computeInitialState()
            stateFlow.emit(state)
            appliedUpdateEpoch.value = 1
            try {
                for (update in stateUpdateChannel) {
                    val newState = update(state)
                    if (newState != null) {
                        state = newState
                        stateFlow.emit(state)
                    }
                    appliedUpdateEpoch.value = update.epoch
                }
            } finally {
                withContext(NonCancellable) {
                    saveLastState(state)
                }
            }
        }
        currentRunScope = this@coroutineScope
        try {
            view.run()
        }
        finally {
            cancel()
            currentRunScope = null
        }
    }

    protected open suspend fun saveLastState(state: S) { }

    protected fun updateState(update: suspend (S) -> S?) {
        check(stateUpdateChannel.trySend(StateUpdate(
            epoch = updateEpoch.incrementAndGet(),
            update = update,
        )).isSuccess)
    }

    protected fun launchUiTask(task: suspend () -> Unit) {
        assertUiAccessAllowed()
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

    @TestOnly
    suspend fun awaitStateUpdates(): S {
        val currentEpoch = updateEpoch.get()
        appliedUpdateEpoch.first { it >= currentEpoch }
        return stateFlow.replayCache.first()
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
