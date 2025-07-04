package name.tachenov.flakardia.ui

import name.tachenov.flakardia.askUi
import name.tachenov.flakardia.storage.StatsFileRecoveryOptions
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.JOptionPane.QUESTION_MESSAGE
import javax.swing.JOptionPane.YES_NO_OPTION
import kotlin.math.abs

fun JRootPane.setupShortcuts(window: Window) {
    if (System.getProperty("os.name").startsWith("mac", ignoreCase = true)) {
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.META_DOWN_MASK), "close")
        rootPane.actionMap.put("close", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                window.dispose()
            }
        })
    }
}

fun Window.setLocationAboveOrBelowOf(owner: Window?) {
    val location = owner?.findLocationAboveOrBelow(width, height)
    if (location == null) {
        setLocationRelativeTo(null)
    }
    else {
        setLocation(location)
    }
}

private fun Window.findLocationAboveOrBelow(width: Int, height: Int): Point? {
    val screen = this.graphicsConfiguration?.bounds ?: return null
    val y = findYAboveOrBelow(height, screen)
    val x = centerX(width, screen)
    return if (y == null) {
        null
    }
    else {
        Point(x, y)
    }
}

private fun Window.centerX(width: Int, screen: Rectangle): Int {
    val xRange = screen.x until screen.x + screen.width - width
    val x = if (xRange.isEmpty()) { // unlikely case, the screen is too small to fit the new window
        screen.x
    } else {
        (this.x - (width - this.width) / 2).coerceIn(xRange)
    }
    return x
}

private fun Window.findYAboveOrBelow(height: Int, screen: Rectangle): Int? {
    val ourLocation = this.locationOnScreen
    val ourSize = this.size
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
            } else {
                yBelow
            }
        }

        yAboveFits -> yAbove
        yBelowFits -> yBelow
        else -> null
    }
    return y
}

inline fun <reified T : Component> Component.findParentOfType(): T? {
    var parent: Component? = this.parent
    while (parent != null) {
        if (parent is T) return parent
        parent = parent.parent
    }
    return null
}

class StatsFileRecoveryOptionsDialog : StatsFileRecoveryOptions {
    private val owner: Window?
        get() = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow

    override fun requestRecovery(message: String): Boolean =
        askUi {
            JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(
                owner,
                message,
                "Recover the stats file?",
                YES_NO_OPTION,
                QUESTION_MESSAGE
            )
        }

    override fun notifyRecoveryImpossible(message: String) {
        askUi {
            JOptionPane.showMessageDialog(owner, message, "The stats file recovery is impossible", JOptionPane.WARNING_MESSAGE)
        }
    }
}
