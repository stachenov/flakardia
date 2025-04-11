package name.tachenov.flakardia.ui

import name.tachenov.flakardia.assertUiAccessAllowed
import javax.swing.JFrame

open class FlakardiaFrame : JFrame() {

    init {
        assertUiAccessAllowed()
        defaultCloseOperation = DISPOSE_ON_CLOSE
        rootPane.setupShortcuts(this)
    }
}
