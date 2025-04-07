package name.tachenov.flakardia

import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.IntelliJTheme
import com.github.weisj.darklaf.theme.OneDarkTheme
import com.github.weisj.darklaf.theme.info.DefaultThemeProvider
import kotlinx.coroutines.*
import name.tachenov.flakardia.app.CardManager
import name.tachenov.flakardia.presenter.CardSetManagerPresenter
import name.tachenov.flakardia.presenter.showPresenterFrame
import name.tachenov.flakardia.ui.CardSetManagerFrame
import name.tachenov.flakardia.ui.initializeSpellchecker
import java.awt.Window
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    debugMode = args.firstOrNull()?.let { DebugMode.valueOf(args[0].uppercase()) } ?: DebugMode.NO_DEBUG
    runBlocking(edtDispatcher) {
        initializeSpellchecker()
        LafManager.setThemeProvider(DefaultThemeProvider(IntelliJTheme(), OneDarkTheme()))
        LafManager.install(LafManager.themeForPreferredStyle(LafManager.getPreferredThemeStyle()))
        configureUiDefaults()
        if (getLibraryPath() == null) {
            showHelp()
        }
        runApp()
        exitProcess(0)
    }
}

private suspend fun runApp() {
    val manager = CardManager()
    configureAndEnterLibrary(manager)
    showManagerFrame(manager)
}

private suspend fun showManagerFrame(manager: CardManager) = coroutineScope {
    val showFrameJob = launch {
        showPresenterFrame(
            presenterFactory = { CardSetManagerPresenter(manager) },
            viewFactory = { CardSetManagerFrame(it) },
        )
    }
    showFrameJob.join()
}

fun getManagerFrame() = Window.getWindows().firstOrNull { it is CardSetManagerFrame }

private val edtDispatcher = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        SwingUtilities.invokeLater(block)
    }
}
