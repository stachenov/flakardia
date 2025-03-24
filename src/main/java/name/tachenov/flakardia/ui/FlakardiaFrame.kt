package name.tachenov.flakardia.ui

import javax.swing.JFrame

open class FlakardiaFrame : JFrame() {

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        rootPane.setupShortcuts(this)
    }
}
