package name.tachenov.flakardia.ui

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import name.tachenov.flakardia.assertEDT
import name.tachenov.flakardia.presenter.Presenter
import name.tachenov.flakardia.presenter.PresenterState
import name.tachenov.flakardia.presenter.View
import java.awt.Point
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JOptionPane
import kotlin.math.abs

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

    fun findLocationNearby(width: Int, height: Int): Point? {
        val ourLocation = this.locationOnScreen
        val ourSize = this.size
        val screen = this.graphicsConfiguration.bounds
        val yBelow = ourLocation.y + ourSize.height + GAP_BETWEEN_WINDOWS
        val yAbove = ourLocation.y - GAP_BETWEEN_WINDOWS - height
        val yBelowFits = yBelow + height <= screen.y + screen.height
        val yAboveFits = yAbove >= screen.y
        val yScreenCenter = screen.y + screen.height / 2
        val y = when {
            yBelowFits && yAboveFits -> {
                val yAboveCenter = yAbove + height / 2
                val yBelowCenter = yBelow + height / 2
                if (abs(yAboveCenter - yScreenCenter) < abs(yBelowCenter - yScreenCenter)) {
                    yAbove
                }
                else {
                    yBelow
                }
            }
            yAboveFits -> yAbove
            yBelowFits -> yBelow
            else -> null
        }
        val x = (this.x - (width - this.width) / 2).coerceIn(screen.x until screen.x + screen.width)
        return if (y == null) {
            null
        }
        else {
            Point(x, y)
        }
    }
}

fun Window.setLocationRelativeTo(owner: Presenter<*, *>) {
    val location = (owner.view as? FrameView<*, *, *>)?.findLocationNearby(width, height)
    if (location == null) {
        setLocationRelativeTo(null)
    }
    else {
        setLocation(location)
    }
}

private const val GAP_BETWEEN_WINDOWS = 50
