package name.tachenov.flakardia.service

import name.tachenov.flakardia.app.DirEnterResult
import name.tachenov.flakardia.background
import name.tachenov.flakardia.data.LessonDataResult
import name.tachenov.flakardia.data.Library
import name.tachenov.flakardia.data.LibraryStats
import name.tachenov.flakardia.data.StatsSaveResult
import name.tachenov.flakardia.threading
import name.tachenov.flakardia.ui

class FlashcardService {

    fun processEntries(
        source: () -> DirEnterResult,
        processor: (DirEnterResult) -> Unit
    ) = process(source, processor)

    fun processLessonData(
        source: () -> LessonDataResult,
        processor: (LessonDataResult) -> Unit,
    ) = process(source, processor)

    fun updateStats(library: Library, stats: LibraryStats, processor: (StatsSaveResult) -> Unit) =
        process({ library.saveUpdatedStats(stats) }, processor)

    private fun <T : Any> process(
        source: () -> T,
        processor: (T) -> Unit,
    ) {
        threading {
            val result = background {
                source()
            }
            ui {
                processor(result)
            }
        }
    }

}
