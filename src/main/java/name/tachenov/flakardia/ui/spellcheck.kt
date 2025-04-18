package name.tachenov.flakardia.ui

import io.github.geniot.jortho.SpellChecker
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.text.JTextComponent
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

fun enableSpellchecker(component: JTextComponent) {
    component.addHierarchyListener(SpellCheckerRegistrar(component))
}

private class SpellCheckerRegistrar(private val component: JTextComponent) : HierarchyListener {
    private var active = false

    init {
        checkShowing()
    }

    override fun hierarchyChanged(e: HierarchyEvent?) {
        checkShowing()
    }

    private fun checkShowing() {
        if (!active && component.isShowing) {
            SpellChecker.register(component)
            active = true
        }
        else if (active && !component.isShowing) {
            SpellChecker.unregister(component)
            active = false
        }
    }
}

fun initializeSpellchecker() {
    SpellChecker.registerDictionaries(null, null)
    SpellChecker.setCustomDictionaryProvider {
        try {
            loadDir(appDirPath()).iterator()
        } catch (e: Exception) {
            emptyList<String>().iterator()
        }
    }
}

private fun appDirPath(): Path = Path.of("spell")

private fun loadDir(dir: Path): List<String> =
    if (dir.isDirectory()) {
        Files.newDirectoryStream(dir).use { files ->
            files.filter { path -> path.isRegularFile() }
                .flatMap { file ->
                    loadFile(file)
                }
        }
    }
    else {
        emptyList()
    }

private fun loadFile(file: Path): List<String> =
    try {
        Files.readAllLines(file)
    } catch (e: Exception) {
        emptyList()
    }
