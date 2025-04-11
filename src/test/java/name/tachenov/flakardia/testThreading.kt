package name.tachenov.flakardia

import kotlinx.coroutines.runBlocking

fun backgroundModelTest(code: suspend () -> Unit) {
    runBlocking {
        accessModel(code)
    }
}
