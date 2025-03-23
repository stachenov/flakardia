package name.tachenov.flakardia.ui

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import name.tachenov.flakardia.assertEDT
import name.tachenov.flakardia.presenter.Presenter
import name.tachenov.flakardia.presenter.PresenterState
import name.tachenov.flakardia.presenter.View
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JOptionPane

abstract class FrameView<S : PresenterState, V : View, P : Presenter<S, V>>(
    protected val presenter: P,
) : JFrame(), View {

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
    }

    override suspend fun run() = coroutineScope {
        assertEDT()
        val job = coroutineContext.job
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                saveLocation()
            }

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
    }

    private fun onFirstStateInit() {
        pack()
        applyInitialLocation()
        isVisible = true
    }

    protected abstract fun applyInitialLocation()

    protected abstract fun saveLocation()

    protected abstract fun applyState(state: S)

    override fun adjustSize() {
        assertEDT()
        pack()
    }

    override fun showError(title: String, message: String) {
        assertEDT()
        JOptionPane.showMessageDialog(
            this,
            message,
            title,
            JOptionPane.ERROR_MESSAGE,
        )
    }
}
