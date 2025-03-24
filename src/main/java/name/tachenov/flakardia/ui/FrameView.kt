package name.tachenov.flakardia.ui

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import name.tachenov.flakardia.assertEDT
import name.tachenov.flakardia.presenter.Presenter
import name.tachenov.flakardia.presenter.PresenterState
import name.tachenov.flakardia.presenter.View
import java.awt.Point
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

    @OptIn(FlowPreview::class)
    override suspend fun run() = coroutineScope {
        assertEDT()
        val job = coroutineContext.job
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                job.cancel()
            }
        })
        val locationFlow = MutableStateFlow<Point?>(null)
        launch {
            locationFlow.filterNotNull().debounce(50).collectLatest { location ->
                saveLocation(location)
            }
        }
        addComponentListener(object : ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent?) {
                locationFlow.value = location
            }
        })
        var isFirstState = true
        presenter.state.collect { state ->
            if (isFirstState) {
                beforeFirstStateInit()
            }
            applyState(state)
            if (isFirstState) {
                afterFirstStateInit()
                isFirstState = false
            }
        }
    }

    private fun beforeFirstStateInit() {
        if (packFrame == PackFrame.BEFORE_STATE_INIT) {
            pack()
        }
    }

    private fun afterFirstStateInit() {
        if (packFrame == PackFrame.AFTER_STATE_INIT) {
            pack()
        }
        applyInitialLocation()
        isVisible = true
    }

    protected abstract fun applyInitialLocation()

    protected abstract fun saveLocation(location: Point)

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
