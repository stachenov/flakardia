package name.tachenov.flakardia

import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.IntelliJTheme
import com.github.weisj.darklaf.theme.OneDarkTheme
import com.github.weisj.darklaf.theme.info.DefaultThemeProvider
import name.tachenov.flakardia.app.CardManager
import name.tachenov.flakardia.service.FlashcardService
import name.tachenov.flakardia.ui.CardSetManagerFrame
import java.awt.Window
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

fun main() {
    SwingUtilities.invokeLater {
        LafManager.setThemeProvider(DefaultThemeProvider(IntelliJTheme(), OneDarkTheme()))
        LafManager.install(LafManager.themeForPreferredStyle(LafManager.getPreferredThemeStyle()))
        configureUiDefaults()
        if (getLibraryPath() == null) {
            showHelp {
                start()
            }
        }
        else {
            start()
        }
    }
}

private fun start() {
    val manager = CardManager()
    val service = FlashcardService()
    configureAndEnterLibrary(manager) {
        showManagerFrame(manager, service)
    }
}

private fun showManagerFrame(manager: CardManager, service: FlashcardService) {
    CardSetManagerFrame(manager, service).apply {
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }
}

fun getManagerFrame() = Window.getWindows().firstOrNull { it is CardSetManagerFrame }
