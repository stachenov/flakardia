package name.tachenov.flakardia.ui

import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JRootPane
import javax.swing.KeyStroke

open class FlakardiaFrame : JFrame() {

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        rootPane.setupShortcuts(this)
    }
}

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
