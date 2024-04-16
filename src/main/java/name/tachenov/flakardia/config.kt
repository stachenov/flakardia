package name.tachenov.flakardia

import com.github.weisj.darklaf.LafManager
import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.storage.FlashcardStorage
import name.tachenov.flakardia.ui.InitFrame
import name.tachenov.flakardia.ui.SettingsDialog
import java.awt.Font
import java.nio.file.Path
import java.time.Duration
import java.util.prefs.Preferences
import javax.swing.JOptionPane
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource
import kotlin.system.exitProcess

private object PackageReference

private const val LIBRARY_PATH_KEY = "library_path"
private const val FONT_KEY = "font"
private const val MAX_WORDS_PER_LESSON_KEY = "max_words_per_lesson"
private const val INTERVAL_MULTIPLIER_WITHOUT_MISTAKES_KEY = "interval_multiplier_without_mistakes"
private const val MIN_INTERVAL_WITHOUT_MISTAKES_KEY = "min_interval_without_mistakes"
private const val INTERVAL_MULTIPLIER_WITH_MISTAKE_KEY = "interval_multiplier_with_mistake"
private const val MIN_INTERVAL_WITH_MISTAKE_KEY = "min_interval_with_mistake"
private const val INTERVAL_MULTIPLIER_WITH_MANY_MISTAKES_KEY = "interval_multiplier_with_many_mistakes"
private const val MIN_INTERVAL_WITH_MANY_MISTAKES_KEY = "min_interval_with_many_mistakes"
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

fun getLessonSettings(): LessonSettings {
    return LessonSettings(
        maxWordsPerLesson = preferences.getInt(MAX_WORDS_PER_LESSON_KEY, 30),
        intervalMultiplierWithoutMistakes = preferences.getDouble(INTERVAL_MULTIPLIER_WITHOUT_MISTAKES_KEY, 1.5),
        minIntervalWithoutMistakes = preferences.getDouble(MIN_INTERVAL_WITHOUT_MISTAKES_KEY, 1.0).fromDoubleDays(),
        intervalMultiplierWithMistake = preferences.getDouble(INTERVAL_MULTIPLIER_WITH_MISTAKE_KEY, 1.0),
        minIntervalWithMistake = preferences.getDouble(MIN_INTERVAL_WITH_MISTAKE_KEY, 0.25).fromDoubleDays(),
        intervalMultiplierWithManyMistakes = preferences.getDouble(INTERVAL_MULTIPLIER_WITH_MANY_MISTAKES_KEY, 0.5),
        minIntervalWithManyMistakes = preferences.getDouble(MIN_INTERVAL_WITH_MANY_MISTAKES_KEY, 0.1).fromDoubleDays(),
    )
}

fun setLessonSettings(settings: LessonSettings) {
    preferences.putInt(MAX_WORDS_PER_LESSON_KEY, settings.maxWordsPerLesson)
    preferences.putDouble(INTERVAL_MULTIPLIER_WITHOUT_MISTAKES_KEY, settings.intervalMultiplierWithoutMistakes)
    preferences.putDouble(MIN_INTERVAL_WITHOUT_MISTAKES_KEY, settings.minIntervalWithoutMistakes.toDoubleDays())
    preferences.putDouble(INTERVAL_MULTIPLIER_WITH_MISTAKE_KEY, settings.intervalMultiplierWithMistake)
    preferences.putDouble(MIN_INTERVAL_WITH_MISTAKE_KEY, settings.minIntervalWithMistake.toDoubleDays())
    preferences.putDouble(INTERVAL_MULTIPLIER_WITH_MANY_MISTAKES_KEY, settings.intervalMultiplierWithManyMistakes)
    preferences.putDouble(MIN_INTERVAL_WITH_MANY_MISTAKES_KEY, settings.minIntervalWithManyMistakes.toDoubleDays())
}

private fun Duration.toDoubleDays(): Double = toMillis() / 86_400_000.0

private fun Double.fromDoubleDays(): Duration = Duration.ofMillis((this * 86_400_000.0).toLong())

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
