package name.tachenov.flakardia

import com.google.common.jimfs.Jimfs
import name.tachenov.flakardia.data.*
import name.tachenov.flakardia.storage.FlashcardStorageImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.FileSystem
import java.nio.file.Files

private const val DEFAULT_NAME = "test.cards"

class ParseTest {

    private lateinit var fs: FileSystem
    private lateinit var storage: FlashcardStorageImpl

    @BeforeEach
    fun setUp() {
        fs = Jimfs.newFileSystem()
        storage = FlashcardStorageImpl(fs.getPath("."))
    }

    @AfterEach
    fun tearDown() {
        fs.close()
    }

    @Test
    fun `using empty line as delimiter`() {
        parse("a\nb\n\nc\nd\n", expect("a" to "b", "c" to "d"))
    }

    @Test
    fun `using empty line as delimiter, with BOM`() {
        parse("\uFEFFa\nb\n\nc\nd\n", expect("a" to "b", "c" to "d"))
    }

    @Test
    fun `using empty line as delimiter, no newline at the end`() {
        parse("a\nb\n\nc\nd", expect("a" to "b", "c" to "d"))
    }

    @Test
    fun `using empty line as delimiter, multiple newlines`() {
        parse("a\nb\n\n\n\nc\nd\n\n\n", expect("a" to "b", "c" to "d"))
    }

    @Test
    fun `using regular delimiter`() {
        parse("a:b\nc:d\ne:f\n", expect("a" to "b", "c" to "d", "e" to "f"))
    }

    @Test
    fun `using regular delimiter, with BOM`() {
        parse("\uFEFFa:b\nc:d\ne:f\n", expect("a" to "b", "c" to "d", "e" to "f"))
    }

    @Test
    fun `using regular delimiter, no newline at the end`() {
        parse("a:b\nc:d\ne:f", expect("a" to "b", "c" to "d", "e" to "f"))
    }

    @Test
    fun `using regular delimiter, plenty of empty lines`() {
        parse("a:b\n\n\nc:d\n", expect("a" to "b", "c" to "d"))
    }

    @Test
    fun `using regular delimiter, different delimiters`() {
        parse("a:b\n\n\nc,d\n", expect("delimiter"))
    }

    @Test
    fun `IO error`() {
        backgroundModelTest {
            val result = storage.readFlashcards(RelativePath(listOf(DEFAULT_NAME)))
            expect("file").match(result)
        }
    }

    private fun parse(input: String, expectation: Expectation) {
        backgroundModelTest {
            val file = fs.getPath(DEFAULT_NAME)
            Files.write(file, input.toByteArray())
            expectation.match(storage.readFlashcards(RelativePath(listOf(DEFAULT_NAME))))
        }
    }

    private abstract class Expectation {
        abstract fun match(flashcardSetResult: FlashcardSetResult)
    }

    private fun expect(vararg cards: Pair<String, String>): Expectation = object : Expectation() {

        private val expected = FlashcardSet(
            cards.map { FlashcardData(RelativePath(listOf(DEFAULT_NAME)),  Flashcard(Word(it.first), Word(it.second))) },
        )

        override fun match(flashcardSetResult: FlashcardSetResult) {
            assertThat(flashcardSetResult).isEqualTo(expected)
        }
    }

    private fun expect(errorMessage: String): Expectation = object : Expectation() {
        override fun match(flashcardSetResult: FlashcardSetResult) {
            assertThat(flashcardSetResult).isInstanceOf(FlashcardSetError::class.java)
            flashcardSetResult as FlashcardSetError
            assertThat(flashcardSetResult.message).containsIgnoringCase(errorMessage)
        }
    }

}
