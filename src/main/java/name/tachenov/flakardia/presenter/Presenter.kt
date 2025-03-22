package name.tachenov.flakardia.presenter

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import name.tachenov.flakardia.launchUiTask

interface View {
    suspend fun run()
    fun adjustSize()
}

interface PresenterState

abstract class Presenter<S : PresenterState, V : View> {
    protected lateinit var view: V
        private set

    abstract val state: Flow<S>

    protected abstract fun initializeState()

    suspend fun run(view: V) = coroutineScope {
        this@Presenter.view = view
        launch {
            initializeState()
        }
        view.run()
    }
}

fun <S : PresenterState, P : Presenter<S, V>, V : View> showPresenterFrame(
    presenterFactory: () -> P,
    viewFactory: (P) -> V,
) {
    launchUiTask {
        val presenter = presenterFactory()
        val view = viewFactory(presenter)
        presenter.run(view)
    }
}
