# Flakardia

## A simple desktop app to work with flashcards

Developed as-is for personal use, with very limited functionality and probably full of bugs, but works. I needed something like this when I started to learn modern Greek, tried a couple of websites and one desktop app, didn't like them, decided to make my own for my own purposes.

If you're looking for a PC app to learn new words and phrases using flashcards, this may be just what you're looking for.

What it can do:

- Load flashcards from text files (a flashcard is just a pair of any phrases).
- Display the list of files in your flashcard library (any folder you choose).
- Display the list of flashcards from a selected file or folder.
- Run a lesson on up to 1000 (30 by default) flashcards in one go, repeating them again and again until you get them all right.
- Keep track of when you learned a word the last time and how mistakes you made, to use this information to select flashcards for next lessons.
- Detect the system theme on startup and try to follow it (four themes are supported: dark, light, dark high contrast and light high contrast).
- Change the app font.

What it can't do:

- Create and edit flashcard sets (any plain text editor will do instead, even Notepad).
- Automatically refresh the list of flashcard files (just restart the app instead, or navigate between subfolders back and forth inside the app).
- Display any kind of icons anywhere, not even the app icon (I'm simply not an artist).
- Store anything on your PC except for some settings and lesson statistics.
- Store flashcards in some sort of cloud (but of course you can use a 3rd party solution for that).

## How to install and run

It's a Java Swing app (a rare beast), so it needs a Java Runtime Environment (17 or later) to run. Flakardia comes packaged in two flavors:
- a Windows installer that, at least in theory, includes everything already and should just work out of the box (it doesn't seem to bother to tell you when the installation is complete, but if you just look in the Start menu, it should be there);
- and a fat JAR that needs a separate JRE, though since it's not exactly a thing anymore, you might as well just install the whole JDK, and it should work simply if you just run `java -jar flakardia.jar`, or even just double-click the JAR.

When you start it the first time, it'll pop up a settings dialog in which you'll have to choose a directory to use as the flashcard library. It can be anywhere you want, and it can even be empty at first, doesn't matter, it just must exist and be writable.

Once you've set up your library, you're ready to add flashcard sets to it and start lessons. Read the built-in documentation to figure out how.
