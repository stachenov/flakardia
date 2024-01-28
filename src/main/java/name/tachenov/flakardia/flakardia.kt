package name.tachenov.flakardia

import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.IntelliJTheme
import com.github.weisj.darklaf.theme.OneDarkTheme
import com.github.weisj.darklaf.theme.info.DefaultThemeProvider
import name.tachenov.flakardia.app.CardManager
import name.tachenov.flakardia.app.DirEnterError
import name.tachenov.flakardia.app.DirEnterSuccess
import name.tachenov.flakardia.ui.CardSetManagerFrame
import java.awt.Font
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants
import kotlin.system.exitProcess

fun main() {
    SwingUtilities.invokeLater {
        LafManager.setThemeProvider(DefaultThemeProvider(IntelliJTheme(), OneDarkTheme()))
        LafManager.install(LafManager.themeForPreferredStyle(LafManager.getPreferredThemeStyle()))
        configureUiDefaults()
        val manager = CardManager()
        when (val result = manager.enter(Path.of("cards"))) {
            is DirEnterSuccess -> {
                CardSetManagerFrame(manager).apply {
                    defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
                    pack()
                    setLocationRelativeTo(null)
                    isVisible = true
                }
            }
            is DirEnterError -> {
                JOptionPane.showMessageDialog(
                    null,
                    "There must be a subdirectory named 'cards' in the current directory.\n" +
                        "The current directory is '${Paths.get("").toAbsolutePath()}'.\n" +
                        "The following error occurred during an attempt to read the 'cards' subdirectory:\n" +
                        result.message
                )
                exitProcess(1)
            }
        }
    }
}

private fun configureUiDefaults() {
    val configuration = Properties()
    val configPath = Path.of("flakardia.properties")
    if (Files.isReadable(configPath)) {
        Files.newBufferedReader(configPath).use {
            configuration.load(it)
        }
    }
    val uiDefaults = UIManager.getDefaults()
    val font = parseFont(configuration.getProperty("font", "Verdana 16"))
    uiDefaults["Button.font"] = font
    uiDefaults["List.font"] = font
    uiDefaults["TableHeader.font"] = font
    uiDefaults["Table.font"] = font
    uiDefaults["Label.font"] = font
    uiDefaults["TextField.font"] = font
}

fun parseFont(s: String): Font {
    var trimmed = s.trim()
    var styleValue = 0
    while (true) {
        val style = FontStyle.entries.firstOrNull { style -> trimmed.uppercase().endsWith(" $style")}
        if (style == null) {
            break
        }
        styleValue = styleValue or style.value
        trimmed = trimmed.removeRange(trimmed.length - style.name.length, trimmed.length).trim()
    }
    val lastSpace = trimmed.lastIndexOf(' ')
    val size = trimmed.substring(lastSpace + 1).toInt()
    return Font(trimmed.substring(0, lastSpace).trim(), styleValue, size)
}

private enum class FontStyle(val value: Int) {
    BOLD(Font.BOLD),
    ITALIC(Font.ITALIC);
}
