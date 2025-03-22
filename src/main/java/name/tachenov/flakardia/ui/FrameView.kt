package name.tachenov.flakardia.ui

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import name.tachenov.flakardia.assertEDT
import name.tachenov.flakardia.presenter.Presenter
import name.tachenov.flakardia.presenter.PresenterState
import name.tachenov.flakardia.presenter.View
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame

abstract class FrameView<S : PresenterState, V : View, P : Presenter<S, V>>(
    protected val presenter: P,
) : JFrame(), View {

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
    }

    override suspend fun run() = coroutineScope {
        val job = coroutineContext.job
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                job.cancel()
            }
        })
        var isFirstState = true
        presenter.state.collect { state ->
            applyState(state)
            if (isFirstState) {
                onFirstStateInit()
                isFirstState = false
            }
        }
        awaitCancellation()
    }

    private fun onFirstStateInit() {
        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }

    protected abstract fun applyState(state: S)

    override fun adjustSize() {
        assertEDT()
        pack()
    }
}
