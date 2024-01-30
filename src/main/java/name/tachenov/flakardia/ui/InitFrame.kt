package name.tachenov.flakardia.ui

import javax.swing.JFrame
import javax.swing.JLabel

class InitFrame : JFrame("Flakardia") {

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        add(JLabel("Configuring..."))
    }

}
