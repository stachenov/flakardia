package name.tachenov.flakardia.ui

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import name.tachenov.flakardia.assertEDT
import name.tachenov.flakardia.presenter.Presenter
import name.tachenov.flakardia.presenter.PresenterState
import name.tachenov.flakardia.presenter.View
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JOptionPane

enum class PackFrame {
    BEFORE_STATE_INIT,
    AFTER_STATE_INIT,
}

abstract class FrameView<S : PresenterState, V : View, P : Presenter<S, V>>(
    protected val presenter: P,
    private val packFrame: PackFrame = PackFrame.AFTER_STATE_INIT,
) : FlakardiaFrame(), View {

    private val saveViewStateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @OptIn(FlowPreview::class)
    override suspend fun run() = coroutineScope {
        assertEDT()
        val job = coroutineContext.job
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                job.cancel()
            }
        })
        launch {
            saveViewStateRequests.filterNotNull().debounce(50).collectLatest {
                saveViewState()
            }
        }
        addComponentListener(object : ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent?) {
                requestSaveViewState()
            }

            override fun componentResized(e: ComponentEvent?) {
                requestSaveViewState()
            }
        })
        var isFirstState = true
        presenter.state.collect { state ->
            if (isFirstState) {
                beforeFirstStateInit()
            }
            applyPresenterState(state)
            if (isFirstState) {
                afterFirstStateInit()
                isFirstState = false
            }
        }
    }

    private fun beforeFirstStateInit() {
        assertEDT()
        if (packFrame == PackFrame.BEFORE_STATE_INIT) {
            pack()
        }
    }

    private fun afterFirstStateInit() {
        assertEDT()
        if (packFrame == PackFrame.AFTER_STATE_INIT) {
            pack()
        }
        restoreSavedViewState()
        isVisible = true
    }

    protected fun requestSaveViewState() {
        check(saveViewStateRequests.tryEmit(Unit))
    }

    protected abstract fun restoreSavedViewState()

    protected abstract fun saveViewState()

    protected abstract fun applyPresenterState(state: S)

    override fun adjustSize() {
        assertEDT()
        pack()
    }

    override fun showWarnings(warnings: List<String>) {
        assertEDT()
        JOptionPane.showMessageDialog(
            this,
            "<html>" + warnings.joinToString("<br>"),
            "Warning",
            JOptionPane.WARNING_MESSAGE,
        )
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
