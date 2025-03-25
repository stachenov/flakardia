package name.tachenov.flakardia

import kotlinx.coroutines.suspendCancellableCoroutine
import name.tachenov.flakardia.ui.FlakardiaFrame
import java.awt.BorderLayout
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

private const val TITLE = "Flakardia help"

suspend fun showHelp() {
    val existingFrame = Window.getWindows().firstOrNull {
        it.isVisible && (it as? JFrame)?.title == TITLE
    } as JFrame?
    if (existingFrame != null) {
        existingFrame.extendedState = JFrame.NORMAL
        existingFrame.toFront()
        return
    }
    val frame = FlakardiaFrame().apply { title = TITLE }
    val contentPane = JPanel(BorderLayout())
    val text = JTextArea(HELP).apply {
        isEditable = false
        wrapStyleWord = true
        lineWrap = true
    }
    contentPane.add(JScrollPane(text), BorderLayout.CENTER)
    frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
    frame.contentPane = contentPane
    frame.setSize(800, 600)
    frame.setLocationRelativeTo(getManagerFrame())
    frame.isVisible = true
    suspendCancellableCoroutine<Unit> { continuation ->
        val listener = object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                continuation.resume(Unit) { _, _, _ -> }
            }
        }
        frame.addWindowListener(listener)
        continuation.invokeOnCancellation {
            frame.removeWindowListener(listener)
        }
    }
}

private const val HELP = """
Welcome to Flakardia!

This old school plain text help may help you get started. At least read the first two sections to set up everything, the rest should be more or less intuitive.

You'll be able to have a look at this help again once you've selected your library folder and completed the initial configuration.

1. Setting up
    
If this is the first time you run Flakardia, then, as soon as you close this window, you should see a settings dialog, in which you should select a folder to use as your flashcard library. It can be any folder anywhere, and it doesn't have to actually contain any flashcard sets at this point, it just must exist and must be writable.

You can also optionally select the font to use and adjust its size and style. This font will be used everywhere in the app.

Once you've selected an existing folder or created a new one and selected it, you can close the settings dialog by clicking OK or pressing Enter.

There's also the Lesson tab which you can use to fine-tune the algorithm that selects words for a lesson. You can leave that tab alone for now.

You'll be able to open the settings dialog again at any time if you wish.

2. Organizing your flashcard library

The library is just a folder that should contain text files with flashcard sets. If you have a lot of those, you can put them into subfolders. You'll be able to navigate those freely from the main window. It's a good idea to have a few top-level subfolders, as you can then run lessons on the entire subfolder. To get started, try creating a single subfolder for the language you're going to learn.

A flashcard, as far as Flakardia is concerned, is simply a pair of phrases. You can use any languages you want, and there are no restrictions on phrases, except common sense. In every pair, one phrase is the front of the card, or the question, and the other one is the back of the card, or the answer. During lessons, you'll have to answer these question by typing the answers. 

A flashcard set file is simply a text file in UTF-8 encoding in any of the two formats:

- delimiter-separated;
- blank-line-separated.

With a delimiter-separated file, every non-blank line is a flashcard, and it consists of the question and the answer separated by any delimiter. The delimiter will be detected automatically, and it must be some character that appears once and only once in every single non-blank line, or else detection will fail. Example:

hello | bonjour
goodbye | au revoir

With a blank-line-separated file, every pair of non-blank lines is a flashcard, the first line is the question, the second line is the answer. Flashcards must be separated by one or more blank lines. Every flashcard must contain exactly two lines, or else detection will fail. Example:

hello
bonjour

goodbye
au revoir

Any whitespace around questions and answers will be ignored, and so will be double quotes, for compatibility with flashcards exported from software that adds those.

For detection to succeed, the flashcard set file must contain at least a couple of flashcards. Of course, it makes sense to have much more, but when you're just starting, you may be confused when you save your first flashcard and then nothing works. Don't worry, just add a few more and it should be detected all right.

3. Navigating the library

In the main window, the list of files and folders in the library will be displayed, with the path to the library above it. You can enter any subfolder by double-clicking it or selecting it and pressing Enter, and then the path to that folder will be displayed. You can leave the subfolder by pressing Backspace or by selecting and clicking (or pressing Enter) the ".." element. But you can't navigate outside the library folder that way, as that wouldn't make any sense.

If you select any file and press Enter or double-click it, then the list of flashcards in that file will be displayed in a new window. The same will happen if you press the View flashcards button. The button will actually work even if you select a folder, then you'll see the list of flashcards in the entire folder. The information about flashcards include both words (front and back), the path to the flashcard set, and some statistics used for the word selection algorithm. This statistics includes the time of the last lesson when the word was learned, the time interval between this lesson and the previous one, and the number of mistakes made in the last lesson.

To start a lesson, press the Start lesson button. The lesson will be started in a new window. You can start a lesson on a file or a whole folder. Either way, the lesson will be limited to a fixed number of flashcards (configurable). These flashcards are chosen depending on how much time has passed since you saw a flashcard the last time, and how much mistakes you made the last time. The flashcards that you haven't seen for a while, and hard words you didn't get on the first try will be prioritized. For the first lesson, the choice of the flashcards is random.

You can fine-tune the algorithm for choosing flashcards in the settings dialog, the Lesson tab. You can configure how many flashcards can appear in a lesson (30 by default), and a couple of parameters for three cases: when you have made no mistakes in a given word, when you have made just one mistake or when you have made more than one. For each of these three cases you can set up an interval multiplier that determines how sooner (lower multiplier) or later (higher multiplier) a word will appear next time. These multipliers don't determine exact timing, instead they're used to prioritize some words over others. You can also set the minimum interval (in days) that determines a cooldown period when a word will never appear even if the algorithm would otherwise have chosen it. For example, if you set this interval to 2.0 for the case when no mistakes were made, then if you get a word correctly in a lesson, it won't appear in another lesson until two days have passed. The only exception is when there are no other valid words to choose from, which can happen if your library is small, then the algorithm will still pick recent words.

4. Going through a lesson

You'll see a question and an input field just below it. That's where you type your answer into and press Enter. If you don't remember the answer, press Esc to give up. If you typed the correct answer, it'll be highlighted green. Otherwise, it'll be highlighted red and the correct answer will be displayed just below the input field. After that, press Space to continue the lesson.

If you point the mouse at the question, you'll see the path to the flashcard set file that contains the displayed word. While this can be used as a hint (you may remember the word once you've figured out which flashcard set it belongs to), it's best to avoid this and use this to correct mistakes in flashcard sets (naturally, you need to identify which set to correct first, which is where these tooltips come in handy).

Note that your answer is compared letter-by-letter to the correct one. Whitespace, punctuation and whatever other crazy characters you can type, are ignored completely.

Once you've gone through all the words, you'll start going through words you didn't get right on the first try, if any. if there are none, the lesson is over. Otherwise, once you've finished working on your mistakes, the second round starts. You'll have to go through all the words again. The lesson will be over once you get all the words right in a single round. The number of the round is displayed above the input field. If it's red, it means you're currently working on the mistakes made in that round.

5. Copyright and license information

MIT License

Copyright (c) 2024 Sergei Tachenov

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

"""
