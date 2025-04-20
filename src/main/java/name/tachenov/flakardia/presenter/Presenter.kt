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
    val isApplyingStateNow: Boolean
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
        // A common problem with bridging the reactive world of coroutines and flows
        // with synchronous observable states like Swing: outdated state updates from the model
        // may be propagated back to the model, causing an infinite update loop.
        // The simplest possible example: imagine a boolean flag represented by a checkbox.
        // Suppose the user checked and immediately unchecked the box.
        // If the unchecking happened before the update caused by checking arrived,
        // then, when it finally arrives, it'll cause the checkbox to be checked again,
        // which will be sent back to the model, but then the unchecking update arrives,
        // causing the checkbox to be unchecked and so on forever.
        // Because we can never rely on the update from the model to be up-to-date,
        // but only on the eventual consistency (that some update will finally make things right),
        // we should never ever send any state updates from the view caused by model state events.
        // If that update is the latest, then there's no reason to send that state back to the model,
        // as it's exactly the same state we've just applied.
        // If it's not the latest, then we shouldn't send it to the model for the reasons described above.
        // Simply put, only state updates caused by the user's actions should ever be applied to the model.
        if (view.isApplyingStateNow) return
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
