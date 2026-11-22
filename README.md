# Paw Pages

Paw Pages a customizable bookshelf where you can organize and store your own files and books in style.    
It is an offline Kotlin Android bookshelf with a cozy pastel color and kittens interface. It runs on Android 8.1 (API 27) and later.

## Try the app

Install the included `PawPages-debug.apk` on an Android device or emulator. This is a development build.

## Run in Android Studio

1. Extract the project and open the **PawPages** directory in Android Studio.
2. Use JDK 17 or 21 for Gradle and install Android SDK Platform 35.
3. Let Gradle sync, then select a device or emulator and click **Run**.

To build from a terminal, run `./gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`). The APK lands in `app/build/outputs/apk/debug/`.

## Read a book

- Tap **Add book**, choose a PDF through Android’s file picker, and confirm its title.
- Open the book and scroll. Tap the page counter to jump to a page.
- Tap **Highlight** and drag across a passage. This works on scanned and text PDFs.
- Tap **Note**, then tap a spot on a page and write a note. Tap a saved pin to edit it. **Marks** lists saved highlights and notes and lets you jump to or delete them.
- Tap the bookmark icon to save the current page. Reopening the book starts there. Tap a filled bookmark for options to jump to, update, or remove it.

## Local files

Each book has a private PDF copy and small JSON files for metadata and annotations. Paw Pages displays highlights and notes as app-local annotations over the imported PDF. You can use the app offline without an account. Android’s file picker grants access to the document you select. A separate Android process handles PDF parsing and rendering. The app renders visible pages as needed and bounds its bitmap cache.

Password-protected PDFs require an unlocked copy. Uninstalling Paw Pages removes its local bookshelf and annotations.
