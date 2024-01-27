package name.tachenov.flakardia

import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

fun main() {
    SwingUtilities.invokeLater {
        JFrame().apply {
            defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
            isVisible = true
        }
    }
}
