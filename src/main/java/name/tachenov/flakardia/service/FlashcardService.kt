package name.tachenov.flakardia.service

import name.tachenov.flakardia.background
import name.tachenov.flakardia.data.FlashcardSetResult
import name.tachenov.flakardia.threading
import name.tachenov.flakardia.ui

class FlashcardService {

    fun processFlashcards(
        source: () -> FlashcardSetResult,
        processor: (FlashcardSetResult) -> Unit,
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
