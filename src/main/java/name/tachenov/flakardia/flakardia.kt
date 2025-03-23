package name.tachenov.flakardia

import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.IntelliJTheme
import com.github.weisj.darklaf.theme.OneDarkTheme
import com.github.weisj.darklaf.theme.info.DefaultThemeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import name.tachenov.flakardia.app.CardManager
import name.tachenov.flakardia.presenter.CardSetManagerPresenter
import name.tachenov.flakardia.presenter.showPresenterFrame
import name.tachenov.flakardia.ui.CardSetManagerFrame
import java.awt.Window
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

fun main(args: Array<String>) {
    debugMode = args.firstOrNull()?.let { DebugMode.valueOf(args[0].uppercase()) } ?: DebugMode.NO_DEBUG
    runBlocking(edtDispatcher) {
        LafManager.setThemeProvider(DefaultThemeProvider(IntelliJTheme(), OneDarkTheme()))
        LafManager.install(LafManager.themeForPreferredStyle(LafManager.getPreferredThemeStyle()))
        configureUiDefaults()
        if (getLibraryPath() == null) {
            showHelp()
        }
        start()
    }
}

private suspend fun start() {
    val manager = CardManager()
    configureAndEnterLibrary(manager)
    showManagerFrame(manager)
}

private suspend fun showManagerFrame(manager: CardManager) {
    showPresenterFrame(
        presenterFactory = { CardSetManagerPresenter(manager) },
        viewFactory = { CardSetManagerFrame(it) },
    )
}

fun getManagerFrame() = Window.getWindows().firstOrNull { it is CardSetManagerFrame }

private val edtDispatcher = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        SwingUtilities.invokeLater(block)
    }
}
