package name.tachenov.flakardia

import com.github.weisj.darklaf.LafManager
import name.tachenov.flakardia.app.CardManager
import name.tachenov.flakardia.app.DirEnterError
import name.tachenov.flakardia.app.DirEnterResult
import name.tachenov.flakardia.app.DirEnterSuccess
import name.tachenov.flakardia.data.Library
import name.tachenov.flakardia.storage.FlashcardStorage
import name.tachenov.flakardia.ui.InitFrame
import name.tachenov.flakardia.ui.SettingsDialog
import java.awt.Font
import java.nio.file.Path
import java.util.prefs.Preferences
import javax.swing.JOptionPane
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource
import kotlin.system.exitProcess

private object PackageReference

private const val LIBRARY_PATH_KEY = "library_path"
private const val FONT_KEY = "font"
private val DEFAULT_FONT = Font("Verdana", 0, 16)

private val preferences: Preferences = Preferences.userNodeForPackage(PackageReference::class.java)

fun getLibraryPath(): Path? = preferences.get(LIBRARY_PATH_KEY, null)?.let { Path.of(it) }

fun setLibraryPath(path: Path) {
    preferences.put(LIBRARY_PATH_KEY, path.toString())
}

fun getAppFont(): Font = preferences.get(FONT_KEY, null)?.let { parseFont(it) } ?: DEFAULT_FONT

fun setAppFont(font: Font) {
    preferences.put(FONT_KEY, "${font.family} ${font.size}${if (font.isBold) " Bold" else ""}${if (font.isItalic) " Italic" else ""}")
}

fun configureAndEnterLibrary(manager: CardManager, whenDone: () -> Unit) {
    threading {
        var dirEnterResult: DirEnterResult? = null
        while (dirEnterResult !is DirEnterSuccess) {
            val libraryPath = getLibraryPath()
            if (libraryPath == null) {
                if (!ui { showSettingsDialog() }) {
                    exitProcess(0)
                }
            }
            else {
                dirEnterResult = background { manager.enterLibrary(Library(FlashcardStorage(libraryPath)) ) }
                if (dirEnterResult is DirEnterError) {
                    ui {
                        JOptionPane.showMessageDialog(
                            null,
                            "The following error occurred during an attempt to read the library:\n" +
                                dirEnterResult.message,
                            "Error",
                            JOptionPane.ERROR_MESSAGE,
                        )
                    }
                    if (!ui { showSettingsDialog() }) {
                        exitProcess(0)
                    }
                }
            }
        }
        whenDone()
    }
}

fun showSettingsDialog(): Boolean {
    val managerFrame = getManagerFrame()
    val initFrame = if (managerFrame == null) InitFrame() else null
    val owner = managerFrame ?: initFrame
    if (initFrame != null) {
        initFrame.pack()
        initFrame.setLocationRelativeTo(null)
        initFrame.isVisible = true
    }
    val dialog = SettingsDialog()
    dialog.pack()
    dialog.setLocationRelativeTo(owner)
    dialog.isVisible = true
    if (dialog.isAccepted) {
        configureUiDefaults()
    }
    initFrame?.dispose()
    return dialog.isAccepted
}

fun configureUiDefaults() {
    val uiDefaults = UIManager.getDefaults()
    val font = FontUIResource(getAppFont())
    uiDefaults["Button.font"] = font
    uiDefaults["List.font"] = font
    uiDefaults["TableHeader.font"] = font
    uiDefaults["Table.font"] = font
    uiDefaults["Label.font"] = font
    uiDefaults["TextField.font"] = font
    uiDefaults["CheckBox.font"] = font
    uiDefaults["Spinner.font"] = font
    uiDefaults["TitledBorder.font"] = font
    uiDefaults["ComboBox.font"] = font
    uiDefaults["TextArea.font"] = font
    LafManager.updateLaf()
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
