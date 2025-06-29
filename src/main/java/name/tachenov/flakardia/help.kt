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

There is also some stuff in the settings that you can leave alone for now. That stuff includes lesson window positioning (try both options later to decide) and the Lesson tab which you can use to fine-tune the algorithm that selects words for a lesson.

You'll be able to open the settings dialog again at any time if you wish.

2. Organizing your flashcard library

The library is just a folder that should contain text files with flashcard sets. If you have a lot of those, you can put them into subfolders. You'll be able to navigate those freely from the main window. It's a good idea to have a few top-level subfolders, as you can then run lessons on the entire subfolder. To get started, try creating a single subfolder for the language you're going to learn.

A flashcard, as far as Flakardia is concerned, is simply a pair of phrases. You can use any languages you want, and there are no restrictions on phrases, except common sense. In every pair, one phrase is the front of the card, or the question, and the other one is the back of the card, or the answer. During lessons, you'll have to answer these question by typing the answers.

Starting with Flakardia 0.7, there are some built-in tools for adding and editing flashcard files, but for the sake of transparency the file format is described here.

A flashcard set file is simply a text file in UTF-8 encoding in any of the following two formats:

- blank-line-separated;
- delimiter-separated.

The blank-line-separated format is the main one used by the application. In this format, every pair of non-blank lines is a flashcard, the first line is the question, the second line is the answer. Flashcards must be separated by one or more blank lines. Every flashcard must contain exactly two lines, or else the file won't load. Examples:

hello
bonjour

goodbye
au revoir

The delimiter-separated format is mostly intended for importing data from other applications and files. In this format, every non-blank line is a flashcard, and it consists of the question and the answer separated by any delimiter. The delimiter will be detected automatically, and it must be some character that appears once and only once in every single non-blank line, or else detection will fail. Example:

hello | bonjour
goodbye | au revoir

Any whitespace around questions and answers will be ignored, and so will be double quotes, for compatibility with flashcards exported from software that adds those.

For delimiter detection to succeed, the flashcard set file must contain at least a couple of flashcards. By default, the file will be attempted to read as blank-line-separated. Specifically, if you want to have a file with just one flashcard in it, you should use that format.

3. Navigating the library

In the main window, the list of files and folders in the library will be displayed, with the path to the library above it. You can enter any subfolder by double-clicking it or selecting it and pressing Enter, and then the path to that folder will be displayed. You can leave the subfolder by pressing Backspace or by selecting and clicking (or pressing Enter) the ".." element. But you can't navigate outside the library folder that way, as that wouldn't make any sense.

If you select any file and press Enter or double-click it, then the list of flashcards in that file will be displayed in a new window. The same will happen if you press the View flashcards button. The button will actually work even if you select a folder, then you'll see the list of flashcards in the entire folder. The information about flashcards include both words (front and back), the path to the flashcard set, and some statistics used for the word selection algorithm. This statistics includes the time of the last lesson when the word was learned, the time interval between this lesson and the previous one, and the number of mistakes made in the last lesson.

4. Creating and editing files

You can create and edit files using any file manager and text editor, but there are some built-in tools for this as well. There are the new dir, new file and edit file buttons. No rename and delete functionality in the current version yet.

Once you create or edit an existing file, the editor will open. It should be intuitive to use, and it mimics the blank-line-separated format, showing pairs of text fields for question/answer pairs. You can add more cards by simply pressing Enter when the caret it at the end of an existing card, pretty much like you would add a new line to a text file. The file is saved automatically when you close the editor or after some small timeout when you stop editing. If you're editing an existing file, all accumulated lesson statistics will be kept as-is even if you edit some words. This can be useful if you make some small changes, for example, add an article to the word. If you're replacing some card with a completely new one and want to start leaning it from scratch, then remove the old one first and then add a new one instead of editing the existing one. This will erase the statistics.

Some spellchecking is supported when editing, but the built-in dictionary set is very limited. You can add more dictionaries by putting them to the .flakardia/spell (that's dot-flakardia) directory. On Windows, it would be %USERPROFILE%\.flakardia\spell\, on Unix systems it's the usual dot-dir in your home dir. A dictionary can be named anything and it must be a plain text UTF-8 file that simply lists all words that are considered correct. The dictionaries are loaded on app startup.

5. Staring a lesson

To start a lesson, press the Start lesson button. The lesson will be started in a new window. You can start a lesson on a file or a whole folder. Either way, the lesson will be limited to a fixed number of flashcards (configurable). These flashcards are chosen depending on how much time has passed since you saw a flashcard the last time, and how much mistakes you made the last time. The flashcards that you haven't seen for a while, and hard words you didn't get on the first try will be prioritized. For the first lesson, the choice of the flashcards is random.

If it's not the first lesson, then it may happen that the file where the learning stats are stored is renamed. Google Drive does that sometimes, for example. In this case you will be asked to confirm the file recovery. Or you can try to manually recover it. The file is named stats.json and is located within the directory named .flakardia in your library (yes, the name starts with a dot, which is a common convention for hidden directories and files).

You can fine-tune the algorithm for choosing flashcards in the settings dialog, the Lesson tab. You can configure how many flashcards can appear in a lesson (30 by default), and a couple of parameters for three cases: when you have made no mistakes in a given word, when you have made just one mistake or when you have made more than one. For each of these three cases you can set up an interval multiplier that determines how sooner (lower multiplier) or later (higher multiplier) a word will appear next time. These multipliers don't determine exact timing, instead they're used to prioritize some words over others. You can also set the minimum interval (in days) that determines a cooldown period when a word will never appear even if the algorithm would otherwise have chosen it. For example, if you set this interval to 2.0 for the case when no mistakes were made, then if you get a word correctly in a lesson, it won't appear in another lesson until two days have passed. The only exception is when there are no other valid words to choose from, which can happen if your library is small, then the algorithm will still pick recent words.

5. Going through a lesson

You'll see a question and an input field just below it. That's where you type your answer into and press Enter. If you don't remember the answer, press Esc to give up. If you typed the correct answer, it'll be highlighted green. Otherwise, it'll be highlighted red and the correct answer will be displayed just below the input field. After that, press Space to continue the lesson.

If you point the mouse at the question, you'll see the path to the flashcard set file that contains the displayed word. This can be used as a hint (you may remember the word once you've figured out which flashcard set it belongs to), but it's best to avoid this and try to learn honestly.

Note that your answer is compared letter-by-letter to the correct one. Whitespace, punctuation and whatever other crazy characters you can type, are ignored completely.

Sometimes you make a mistake, but realize that the card has a mistake or could be written better. For example, you may list most words with articles, but forgot to specify the article for a specific word, or you may want to add something that would indicate which of the numerous synonyms are expected as the answer here. In this case, the app provides you with an editor right in the lesson window. Simply press F2 after answering the question, and you can edit both the question and the answer there. Press Enter when you've finished editing.

Once you've gone through all the words, you'll start going through words you didn't get right on the first try, if any. if there are none, the lesson is over. Otherwise, once you've finished working on your mistakes, the second round starts. You'll have to go through all the words again. The lesson will be over once you get all the words right in a single round. The number of the round is displayed above the input field. If it's red, it means you're currently working on the mistakes made in that round.

6. Copyright and license information

MIT License

Copyright (c) 2024-2025 Sergei Tachenov

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
