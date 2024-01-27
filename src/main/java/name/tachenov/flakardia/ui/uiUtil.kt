package name.tachenov.flakardia.ui

import java.awt.Component

var Component.preferredWidth: Int
    get() = preferredSize.width
    set(value) {
        preferredSize = null
        preferredSize = preferredSize.apply { width = value }
    }

fun Component.setPreferredWidthString(s: String) {
    preferredWidth = getFontMetrics(font).stringWidth(s)
}
