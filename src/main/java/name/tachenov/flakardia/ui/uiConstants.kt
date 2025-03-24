package name.tachenov.flakardia.ui

import java.awt.Color

const val WIDTH_STRING = "supercalifragilisticexpialidocious / supercalifragilisticexpialidocious / supercalifragilisticexpialidocious"
const val INFINITY = 100_000

/**
 * Extra width to add to a packed frame to avoid relying on tight calculations.
 */
const val FRAME_EXTRA_WIDTH = 50

/**
 * The space to leave between two windows when showing one above/below the other.
 */
const val GAP_BETWEEN_WINDOWS = 50

/**
 * The extra width to add to a label size to fight scaling issues.
 *
 * In multi-monitor configurations, Swing sometimes gets the size of a label wrong,
 * especially if it's not on the main screen. Especially on Windows with fractional scaling.
 * This leads to the text being cut off with "...".
 * We "fix" this by adding some extra pixels. Not the most elegant way, but simple and robust.
 */
const val LABEL_EXTRA_WIDTH = 2

val CORRECT_COLOR = Color(33, 169, 10)
val INCORRECT_COLOR = Color(222, 15, 15)
