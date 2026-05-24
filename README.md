# EbookNT

EbookNT is an offline PDF/DjVu reader for Android.

Supported formats:
* PDF
* DjVu
* EPUB
* XPS (OpenXPS)
* CBZ (Comic Books; RAR-compressed CBR is not supported)
* FictionBook (fb2)

*Note: Currently, only PDF and DjVu are thoroughly tested.*

EbookNT was created primarily as a personal project for my own daily use. You are welcome to use it, but please note it is provided "as-is" and at your own risk.


## Development & History

EbookNT is a fork of [Document Viewer](https://github.com/SufficientlySecure/document-viewer), which in turn is based on the last GPL version of [EBookDroid](https://code.google.com/archive/p/ebookdroid/).

I'm not an Android expert - the development was done primarily with the assistance of Claude Code.

Major changes:
* **Adapted for modern Android:** Updated target SDKs and modernized the architecture.
* **Removed submodules:** applied necessary patches and merged them into a monolithic repo
* **100% Offline:** Removed OPDS support and the `INTERNET` permission entirely.
* **Redesigned UI:** Tailored specifically for an uninterrupted offline reading experience.
* **Manual & auto cropping:** Per-book default crop actions, conservative auto-crop clamping.
* **PDF/DjVu logical page numbers:** Automatic detection of page label offsets.
* **Various bug fixes and stability improvements.**


## Building

### Prerequisites

* Android SDK with build-tools and platform installed
* `ANDROID_HOME` environment variable pointing to your SDK root
* JDK 17 or 21
* NDK installed (Gradle will pick it up from the SDK automatically)

`gradle.properties` is tracked via `git add -f` despite being in `.gitignore`.
It sets `android.nonFinalResIds=false`, which is required because the code uses
`switch`/`case` on `R.id.*` values — these must be compile-time constants.

### One-time setup

Generate MuPDF font sources (only needed once, or when URW `.cff` fonts change):

```
./ebooknt/jni/mupdf/generate-fonts.sh
```

This converts URW base14 font files into C source arrays under
`ebooknt/jni/mupdf/mupdf/generated/`. The directory is git-ignored.
Other generated resources (CMap headers, ICC profiles, JS headers)
ship with the MuPDF 1.14 source tree and do not need regeneration.

### Debug build

Gradle handles the native build automatically via `externalNativeBuild`.
No manual `ndk-build` step is required:

```
./gradlew assembleDebug
```

To speed up development by building only arm64-v8a (skip the universal APK):

```
APP_ABI=arm64-v8a ./gradlew assembleDebug
```

### Release build

```
./gradlew assembleRelease
```

This produces two APKs under `ebooknt/build/outputs/apk/release/`:
* `ebooknt-armv8-v<version>.apk` — arm64-v8a only (smaller)
* `ebooknt-universal-v<version>.apk` — all ABIs

### Development with Android Studio

1. Clone the repository
2. File -> Open -> select the cloned top-level folder
3. Android Studio will import the Gradle project automatically

## Licenses

EbookNT is licensed under the GPLv3+.
The file `LICENSE` includes the full license text.

EbookNT is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

EbookNT is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with EbookNT. If not, see <http://www.gnu.org/licenses/>.

## C Libraries

* **MuPDF 1.14** — lightweight PDF, EPUB, CBZ and XPS renderer
  https://mupdf.com/ — AGPLv3+

* **DjVuLibre** — DjVu renderer
  https://djvu.sourceforge.net/ — GPLv2

## Images

* Application icon (`logo_ebooknt.svg`)
  Designed by Daiqi Zhang — [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
