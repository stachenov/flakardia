package name.tachenov.flakardia.presenter

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import name.tachenov.flakardia.launchUiTask

interface View {
    suspend fun run()
}

abstract class Presenter<T : View> {
    protected lateinit var view: T
        private set

    protected abstract fun initializeState()

    suspend fun run(view: T) = coroutineScope {
        this@Presenter.view = view
        launch {
            initializeState()
        }
        launch {
            view.run()
        }
    }
}

fun <P : Presenter<V>, V : View> showPresenterFrame(
    presenterFactory: () -> P,
    viewFactory: (P) -> V,
) {
    launchUiTask {
        val presenter = presenterFactory()
        val view = viewFactory(presenter)
        presenter.run(view)
    }
}
