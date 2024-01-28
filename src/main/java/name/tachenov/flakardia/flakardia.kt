package name.tachenov.flakardia

import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.IntelliJTheme
import com.github.weisj.darklaf.theme.OneDarkTheme
import com.github.weisj.darklaf.theme.info.DefaultThemeProvider
import name.tachenov.flakardia.ui.CardSetManagerFrame
import java.awt.Font
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants

fun main() {
    SwingUtilities.invokeLater {
        LafManager.setThemeProvider(DefaultThemeProvider(IntelliJTheme(), OneDarkTheme()))
        LafManager.install(LafManager.themeForPreferredStyle(LafManager.getPreferredThemeStyle()))
        configureUiDefaults()
        CardSetManagerFrame().apply {
            defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
            pack()
            setLocationRelativeTo(null)
            isVisible = true
        }
    }
}

private fun configureUiDefaults() {
    val uiDefaults = UIManager.getDefaults()
    uiDefaults["Button.font"] = Font("Verdana", Font.PLAIN, 16)
    uiDefaults["List.font"] = Font("Verdana", Font.PLAIN, 16)
    uiDefaults["TableHeader.font"] = Font("Verdana", Font.PLAIN, 16)
    uiDefaults["Table.font"] = Font("Verdana", Font.PLAIN, 16)
    uiDefaults["Label.font"] = Font("Verdana", Font.PLAIN, 16)
    uiDefaults["TextField.font"] = Font("Verdana", Font.PLAIN, 16)
}
