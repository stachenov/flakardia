package name.tachenov.flakardia

import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.IntelliJTheme
import com.github.weisj.darklaf.theme.OneDarkTheme
import com.github.weisj.darklaf.theme.info.DefaultThemeProvider
import kotlinx.coroutines.runBlocking
import name.tachenov.flakardia.app.CardManager
import name.tachenov.flakardia.presenter.CardSetManagerPresenter
import name.tachenov.flakardia.presenter.showPresenterFrame
import name.tachenov.flakardia.ui.CardSetManagerFrame
import java.awt.Window

fun main(args: Array<String>) {
    debugMode = args.firstOrNull()?.let { DebugMode.valueOf(args[0].uppercase()) } ?: DebugMode.NO_DEBUG
    runBlocking {
        launchUiTask {
            LafManager.setThemeProvider(DefaultThemeProvider(IntelliJTheme(), OneDarkTheme()))
            LafManager.install(LafManager.themeForPreferredStyle(LafManager.getPreferredThemeStyle()))
            configureUiDefaults()
            if (getLibraryPath() == null) {
                showHelp {
                    start()
                }
            } else {
                start()
            }
        }
        uiTaskLoop()
    }
}

private fun start() {
    val manager = CardManager()
    configureAndEnterLibrary(manager) {
        showManagerFrame(manager)
    }
}

private fun showManagerFrame(manager: CardManager) {
    showPresenterFrame(
        presenterFactory = { CardSetManagerPresenter(manager) },
        viewFactory = { CardSetManagerFrame(it) },
    )
}

fun getManagerFrame() = Window.getWindows().firstOrNull { it is CardSetManagerFrame }
