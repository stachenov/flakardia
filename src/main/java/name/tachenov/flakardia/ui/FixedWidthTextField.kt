package name.tachenov.flakardia.ui

import java.awt.Dimension
import javax.swing.JTextField

class FixedWidthTextField : JTextField() {
    override fun getPreferredSize(): Dimension {
        return super.getPreferredSize().apply {
            width = getFontMetrics(font).stringWidth(WIDTH_STRING)
        }
    }
}
