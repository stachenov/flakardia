package name.tachenov.flakardia

import com.google.common.jimfs.Jimfs
import name.tachenov.flakardia.data.Flashcard
import name.tachenov.flakardia.data.FlashcardSet
import name.tachenov.flakardia.data.Word
import name.tachenov.flakardia.data.readFlashcards
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.FileSystem
import java.nio.file.Files

private const val DEFAULT_NAME = "test.cards"

class ParseTest {

    private lateinit var fs: FileSystem

    @BeforeEach
    fun setUp() {
        fs = Jimfs.newFileSystem()
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
    fun `using empty line as delimiter, no newline at the end`() {
        parse("a\nb\n\nc\nd", expect("a" to "b", "c" to "d"))
    }

    @Test
    fun `using empty line as delimiter, multiple newlines`() {
        parse("a\nb\n\n\n\nc\nd\n\n\n", expect("a" to "b", "c" to "d"))
    }

    private fun parse(input: String, expected: FlashcardSet) {
        val file = fs.getPath(DEFAULT_NAME)
        Files.write(file, input.toByteArray())
        assertThat(readFlashcards(file)).isEqualTo(expected)
    }

    private fun expect(vararg cards: Pair<String, String>): FlashcardSet = FlashcardSet(
        DEFAULT_NAME,
        cards.map { Flashcard(Word(it.first), Word(it.second)) },
    )

}
