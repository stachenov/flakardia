package name.tachenov.flakardia

import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.IntelliJTheme
import com.github.weisj.darklaf.theme.OneDarkTheme
import com.github.weisj.darklaf.theme.info.DefaultThemeProvider
import name.tachenov.flakardia.app.CardManager
import name.tachenov.flakardia.app.DirEnterError
import name.tachenov.flakardia.app.DirEnterResult
import name.tachenov.flakardia.app.DirEnterSuccess
import name.tachenov.flakardia.ui.CardSetManagerFrame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.system.exitProcess

fun main() {
    SwingUtilities.invokeLater {
        LafManager.setThemeProvider(DefaultThemeProvider(IntelliJTheme(), OneDarkTheme()))
        LafManager.install(LafManager.themeForPreferredStyle(LafManager.getPreferredThemeStyle()))
        configureUiDefaults()
        val manager = CardManager()
        configureAndEnterLibrary(manager)
        showManagerFrame(manager)
    }
}

private fun configureAndEnterLibrary(manager: CardManager) {
    var dirEnterResult: DirEnterResult? = null
    while (dirEnterResult !is DirEnterSuccess) {
        val libraryPath = getLibraryPath()
        if (libraryPath == null) {
            if (!showSettingsDialog()) {
                exitProcess(0)
            }
        } else {
            dirEnterResult = manager.enter(libraryPath)
            if (dirEnterResult is DirEnterError) {
                JOptionPane.showMessageDialog(
                    null,
                    "The following error occurred during an attempt to read the library:\n" +
                        dirEnterResult.message,
                    "Error",
                    JOptionPane.ERROR_MESSAGE,
                )
                if (!showSettingsDialog()) {
                    exitProcess(0)
                }
            }
        }
    }
}

private fun showManagerFrame(manager: CardManager) {
    CardSetManagerFrame(manager).apply {
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }
}
