package name.tachenov.flakardia.ui

import java.awt.Dimension
import javax.swing.JTextField

open class FixedWidthTextField(text: String = "") : JTextField(text) {
    override fun getPreferredSize(): Dimension {
        return super.getPreferredSize().apply {
            width = getFontMetrics(font).stringWidth(WIDTH_STRING)
        }
    }
}
