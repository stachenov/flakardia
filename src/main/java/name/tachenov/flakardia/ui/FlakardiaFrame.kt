package name.tachenov.flakardia.ui

import name.tachenov.flakardia.assertEDT
import javax.swing.JFrame

open class FlakardiaFrame : JFrame() {

    init {
        assertEDT()
        defaultCloseOperation = DISPOSE_ON_CLOSE
        rootPane.setupShortcuts(this)
    }
}
