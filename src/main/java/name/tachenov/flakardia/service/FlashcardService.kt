package name.tachenov.flakardia.service

import name.tachenov.flakardia.ProgressIndicator
import name.tachenov.flakardia.app.DirEnterResult
import name.tachenov.flakardia.app.Library
import name.tachenov.flakardia.background
import name.tachenov.flakardia.data.LessonDataResult
import name.tachenov.flakardia.data.LibraryStats
import name.tachenov.flakardia.data.StatsSaveResult
import name.tachenov.flakardia.threading
import name.tachenov.flakardia.ui

class FlashcardService {

    fun processEntries(
        indicator: ProgressIndicator,
        source: () -> DirEnterResult,
        processor: (DirEnterResult) -> Unit
    ) = process(indicator, source, processor)

    fun processLessonData(
        indicator: ProgressIndicator,
        source: () -> LessonDataResult,
        processor: (LessonDataResult) -> Unit,
    ) = process(indicator, source, processor)

    fun updateStats(indicator: ProgressIndicator, library: Library, stats: LibraryStats, processor: (StatsSaveResult) -> Unit) =
        process(indicator, { library.saveUpdatedStats(stats) }, processor)

    private fun <T : Any> process(
        indicator: ProgressIndicator,
        source: () -> T,
        processor: (T) -> Unit,
    ) {
        threading(indicator) {
            val result = background {
                source()
            }
            ui {
                processor(result)
            }
        }
    }

}
