package name.tachenov.flakardia.service

import name.tachenov.flakardia.app.DirEnterResult
import name.tachenov.flakardia.background
import name.tachenov.flakardia.data.FlashcardSetResult
import name.tachenov.flakardia.threading
import name.tachenov.flakardia.ui

class FlashcardService {

    fun processEntries(
        source: () -> DirEnterResult,
        processor: (DirEnterResult) -> Unit
    ) = process(source, processor)

    fun processFlashcards(
        source: () -> FlashcardSetResult,
        processor: (FlashcardSetResult) -> Unit,
    ) = process(source, processor)

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
