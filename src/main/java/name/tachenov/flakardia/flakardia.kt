package name.tachenov.flakardia

import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.IntelliJTheme
import com.github.weisj.darklaf.theme.OneDarkTheme
import com.github.weisj.darklaf.theme.info.DefaultThemeProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import name.tachenov.flakardia.app.CardManager
import name.tachenov.flakardia.presenter.CardSetManagerPresenter
import name.tachenov.flakardia.service.FlashcardService
import name.tachenov.flakardia.ui.CardSetManagerFrame
import java.awt.Window
import javax.swing.SwingUtilities

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
    scope.launch {
        withContext(edtDispatcher) {
            CardSetManagerPresenter(manager, service) {
                CardSetManagerFrame(it, service)
            }.run()
        }
    }
}

fun getManagerFrame() = Window.getWindows().firstOrNull { it is CardSetManagerFrame }
