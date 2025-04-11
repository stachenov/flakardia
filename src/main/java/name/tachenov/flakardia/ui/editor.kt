package name.tachenov.flakardia.ui

import name.tachenov.flakardia.app.Duplicate
import org.apache.commons.text.StringEscapeUtils

class WordTextField(text: String = "") : FixedWidthTextField(text) {
    var duplicates: List<Duplicate> = emptyList()
        set(value) {
            field = value
            toolTipText = if (value.isNotEmpty()) {
                "<html>Duplicates detected:<br>${value.joinToString("<br>") { card ->
                    val formattedCard = "${card.path}:${card.question}:${card.answer}"
                    StringEscapeUtils.escapeHtml3(formattedCard)
                }}</html>"
            }
            else {
                null
            }
            foreground = if (value.isNotEmpty()) INCORRECT_COLOR else null
        }

    init {
        enableSpellchecker(this)
    }
}
