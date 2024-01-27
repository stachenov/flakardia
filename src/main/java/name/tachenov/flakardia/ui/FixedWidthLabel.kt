package name.tachenov.flakardia.ui

import java.awt.Dimension
import javax.swing.JLabel

private const val TEXT_FOR_PREFERRED_HEIGHT = "M"

class FixedWidthLabel : JLabel(TEXT_FOR_PREFERRED_HEIGHT) {
    override fun getPreferredSize(): Dimension {
        return super.getPreferredSize().apply {
            width = getFontMetrics(font).stringWidth(WIDTH_STRING)
        }
    }
}
