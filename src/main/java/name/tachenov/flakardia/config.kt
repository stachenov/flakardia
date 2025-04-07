package name.tachenov.flakardia

import com.github.weisj.darklaf.LafManager
import name.tachenov.flakardia.app.*
import name.tachenov.flakardia.data.parseRelativePath
import name.tachenov.flakardia.presenter.CardSetManagerPresenterSavedState
import name.tachenov.flakardia.storage.FlashcardStorageImpl
import name.tachenov.flakardia.ui.FlashcardSetViewColumn
import name.tachenov.flakardia.ui.InitFrame
import name.tachenov.flakardia.ui.LessonFramePosition
import name.tachenov.flakardia.ui.SettingsDialog
import java.awt.*
import java.nio.file.Path
import java.time.Duration
import java.util.prefs.Preferences
import javax.swing.JOptionPane
import javax.swing.RowSorter
import javax.swing.SortOrder
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
private const val RANDOMNESS_KEY = "randomness"
private val DEFAULT_FONT = Font("Verdana", 0, 16)

enum class DebugMode(
    val isDebugEnabled: Boolean,
    val isVerbose: Boolean,
) {
    NO_DEBUG(false, false),
    DEBUG(true, false),
    VERBOSE(true, true),
}

var debugMode: DebugMode = DebugMode.NO_DEBUG

private val preferences: Preferences get() =
    Preferences.userNodeForPackage(PackageReference::class.java).let {
        if (debugMode.isDebugEnabled) {
            it.node("debug")
        }
        else {
            it
        }
    }

fun getLibraryPath(): Path? = preferences.get(LIBRARY_PATH_KEY, null)?.let { Path.of(it) }

fun setLibraryPath(path: Path) {
    preferences.put(LIBRARY_PATH_KEY, path.toString())
}

fun getAppFont(): Font = preferences.get(FONT_KEY, null)?.let { parseFont(it) } ?: DEFAULT_FONT

fun setAppFont(font: Font) {
    preferences.put(FONT_KEY, "${font.family} ${font.size}${if (font.isBold) " Bold" else ""}${if (font.isItalic) " Italic" else ""}")
}

fun getLessonSettings(): LessonSettings {
    val defaultLessonSettings = LessonSettings()
    return LessonSettings(
        maxWordsPerLesson = preferences.getInt(
            MAX_WORDS_PER_LESSON_KEY,
            defaultLessonSettings.maxWordsPerLesson.value
        ),
        intervalMultiplierWithoutMistakes = preferences.getDouble(
            INTERVAL_MULTIPLIER_WITHOUT_MISTAKES_KEY,
            defaultLessonSettings.intervalMultiplierWithoutMistakes.value
        ),
        minIntervalWithoutMistakes = Duration.ofHours(preferences.getLong(
            MIN_INTERVAL_WITHOUT_MISTAKES_KEY,
            defaultLessonSettings.minIntervalWithoutMistakes.value.toHours()
        )),
        intervalMultiplierWithMistake = preferences.getDouble(
            INTERVAL_MULTIPLIER_WITH_MISTAKE_KEY,
            defaultLessonSettings.intervalMultiplierWithMistake.value
        ),
        minIntervalWithMistake = Duration.ofHours(preferences.getLong(
            MIN_INTERVAL_WITH_MISTAKE_KEY,
            defaultLessonSettings.minIntervalWithMistake.value.toHours()
        )),
        intervalMultiplierWithManyMistakes = preferences.getDouble(
            INTERVAL_MULTIPLIER_WITH_MANY_MISTAKES_KEY,
            defaultLessonSettings.intervalMultiplierWithManyMistakes.value
        ),
        minIntervalWithManyMistakes = Duration.ofHours(preferences.getLong(
            MIN_INTERVAL_WITH_MANY_MISTAKES_KEY,
            defaultLessonSettings.minIntervalWithManyMistakes.value.toHours()
        )),
        randomness = preferences.getDouble(
            RANDOMNESS_KEY,
            defaultLessonSettings.randomness.value
        ),
    )
}

fun setLessonSettings(settings: LessonSettings) {
    preferences.putInt(MAX_WORDS_PER_LESSON_KEY, settings.maxWordsPerLesson.value)
    preferences.putDouble(INTERVAL_MULTIPLIER_WITHOUT_MISTAKES_KEY, settings.intervalMultiplierWithoutMistakes.value)
    preferences.putLong(MIN_INTERVAL_WITHOUT_MISTAKES_KEY, settings.minIntervalWithoutMistakes.value.toHours())
    preferences.putDouble(INTERVAL_MULTIPLIER_WITH_MISTAKE_KEY, settings.intervalMultiplierWithMistake.value)
    preferences.putLong(MIN_INTERVAL_WITH_MISTAKE_KEY, settings.minIntervalWithMistake.value.toHours())
    preferences.putDouble(INTERVAL_MULTIPLIER_WITH_MANY_MISTAKES_KEY, settings.intervalMultiplierWithManyMistakes.value)
    preferences.putLong(MIN_INTERVAL_WITH_MANY_MISTAKES_KEY, settings.minIntervalWithManyMistakes.value.toHours())
    preferences.putDouble(RANDOMNESS_KEY, settings.randomness.value)
}

suspend fun configureAndEnterLibrary(manager: CardManager): Library {
    while (true) {
        val libraryPath = getLibraryPath()
        if (libraryPath == null) {
            if (!showSettingsDialog()) {
                exitProcess(0)
            }
        }
        else {
            val library = Library(FlashcardStorageImpl(libraryPath))
            when (val result = underModelLock { background { manager.enterLibrary(library) } }) {
                is DirEnterError -> {
                    JOptionPane.showMessageDialog(
                        null,
                        "The following error occurred during an attempt to read the library:\n" +
                            result.message,
                        "Error",
                        JOptionPane.ERROR_MESSAGE,
                    )
                    if (!showSettingsDialog()) {
                        exitProcess(0)
                    }
                }
                DirEnterSuccess -> {
                    return library
                }
            }
        }
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
    uiDefaults["RadioButton.font"] = font
    uiDefaults["Spinner.font"] = font
    uiDefaults["TitledBorder.font"] = font
    uiDefaults["ComboBox.font"] = font
    uiDefaults["TextArea.font"] = font
    uiDefaults["ToolTip.font"] = font
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

fun getManagerFrameLocation(): Point? = getLocation("main")

fun setManagerFrameLocation(point: Point) {
    putLocation("main", point)
}

fun getLessonFrameLocation(): Point? =
    when (getLessonFramePosition()) {
        LessonFramePosition.RELATIVE -> null
        LessonFramePosition.SAVED -> getLocation("lesson")
    }

fun getLessonFramePosition(): LessonFramePosition =
    preferences.getEnum("lesson_frame_position", LessonFramePosition.default())

fun setLessonFramePosition(position: LessonFramePosition) {
    preferences.putEnum("lesson_frame_position", position)
}

fun setLessonFrameLocation(point: Point) {
    putLocation("lesson", point)
}

data class FlashcardSetViewFrameState(
    val bounds: Rectangle?,
    val columnWidths: Map<FlashcardSetViewColumn, Int>,
    val sortKey: RowSorter.SortKey?,
)

fun getFlashcardSetViewFrameState(): FlashcardSetViewFrameState {
    val location = getLocation("flashcards_view")
    val size = getSize("flashcards_view")
    val screenPreferences = screenConfigPreferences()
    val columnWidths = FlashcardSetViewColumn.entries
        .map { column -> column to screenPreferences.getInt("${column.name.lowercase()}_width", 0) }
        .filter { it.second > 0 }
        .toMap()
    val sortColumn = preferences.getEnum("sort_column", FlashcardSetViewColumn.defaultSort())
    val defaultSort = SortOrder.ASCENDING
    val sortOrder = preferences.getEnum("sort_order", defaultSort)
    val bounds = if (location != null && size != null) Rectangle(location, size) else null
    return FlashcardSetViewFrameState(bounds, columnWidths, RowSorter.SortKey(sortColumn.ordinal, sortOrder))
}

fun setFlashcardSetViewFrameState(state: FlashcardSetViewFrameState) {
    putLocation("flashcards_view", state.bounds?.location)
    putSize("flashcards_view", state.bounds?.size)
    val screenPreferences = screenConfigPreferences()
    for ((column, width) in state.columnWidths) {
        screenPreferences.putInt("${column.name.lowercase()}_width", width)
    }
    if (state.sortKey != null) {
        preferences.putEnum("sort_column", FlashcardSetViewColumn.entries[state.sortKey.column])
        preferences.putEnum("sort_order", state.sortKey.sortOrder)
    }
}

fun getCardSetManagerPresenterSavedState(): CardSetManagerPresenterSavedState {
    val lastPath = preferences.get("last_path", null)
    val selectedPath = preferences.get("selected_path", null)
    return CardSetManagerPresenterSavedState(
        currentPath = lastPath?.parseRelativePath(),
        selectedEntry = selectedPath?.parseRelativePath(),
    )
}

fun setCardSetManagerPresenterSavedState(state: CardSetManagerPresenterSavedState) {
    val lastPath = state.currentPath?.toString()
    val selectedPath = state.selectedEntry?.toString()
    if (lastPath != null) {
        preferences.put("last_path", lastPath)
    }
    if (selectedPath != null) {
        preferences.put("selected_path", selectedPath)
    }
}

fun setEditorBounds(bounds: Rectangle) {
    putLocation("editor", bounds.location)
    putSize("editor", bounds.size)
}

fun getEditorBounds(): Rectangle? {
    val location = getLocation("editor")
    val size = getSize("editor")
    return if (location != null && size != null) Rectangle(location, size) else null
}

private inline fun <reified T : Enum<T>> Preferences.getEnum(name: String, defaultValue: T): T =
    try {
        enumValueOf<T>(get(name, defaultValue.name.lowercase()).uppercase())
    }
    catch (_: Exception) {
        defaultValue
    }

private inline fun <reified T : Enum<T>> Preferences.putEnum(name: String, value: T) {
    put(name, value.name.lowercase())
}

private fun getLocation(name: String): Point? {
    val node = screenConfigPreferences()
    val x = node.getInt("${name}_x", Int.MIN_VALUE)
    val y = node.getInt("${name}_y", Int.MIN_VALUE)
    return if (x != Int.MIN_VALUE && y != Int.MIN_VALUE) Point(x, y) else null
}

private fun putLocation(name: String, point: Point?) {
    if (point == null) return
    val node = screenConfigPreferences()
    node.putInt("${name}_x", point.x)
    node.putInt("${name}_y", point.y)
}

private fun getSize(name: String): Dimension? {
    val node = screenConfigPreferences()
    val width = node.getInt("${name}_width", Int.MIN_VALUE)
    val height = node.getInt("${name}_height", Int.MIN_VALUE)
    return if (width != Int.MIN_VALUE && height != Int.MIN_VALUE) Dimension(width, height) else null
}

private fun putSize(name: String, size: Dimension?) {
    if (size == null) return
    val node = screenConfigPreferences()
    node.putInt("${name}_width", size.width)
    node.putInt("${name}_height", size.height)
}

private fun screenConfigPreferences(): Preferences = preferences.node(currentScreenConfig().toString())

private data class ScreenConfig(
    private val screens: List<Screen>,
) {
    override fun toString(): String = screens.joinToString("_")
}

private data class Screen(
    private val bounds: Rectangle,
) {
    override fun toString(): String = "${bounds.width}x${bounds.height}_${bounds.x}_${bounds.y}"
}

private fun currentScreenConfig() = ScreenConfig(
    screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { device ->
        Screen(bounds = device.defaultConfiguration.bounds)
    }
)
