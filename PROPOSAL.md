# NobodyGenerator — Project Proposal (v2: Android app)

**Status:** In progress · **Branch:** `experimental` · **Updated:** 2026-11-23

> v1 of this proposal (a Linux desktop/CLI voice engine) has been replaced by this one.
> Direction now: a simple Android app.

## 1. What it is

An Android app that reads documents aloud and saves the result as an MP3:

1. **Add** a PDF, Markdown (`.md`) file, text/HTML file, an article link, or pasted text.
   You can also *Share* a link or file to the app from your browser or file manager.
2. **Hit Go.**
3. Get a **notification when it's done**. Then **listen in the app**, **save the MP3**
   anywhere, or **share** it.

Nothing fancy, and no AI required.

## 2. Voice

- Uses the phone's built-in text-to-speech engine (Speech Services by Google on most phones).
  Its current voices are modern neural voices that sound natural, and they **work offline** once downloaded.
- **Default: Australian English.** The app picks an installed `en-AU` offline voice automatically.
  If none is installed, it shows how to install one (Settings → Text-to-speech → Install voice data → English (Australia)).
- Voice picker (Australian first, then NZ, British, American…), speed and pitch sliders, and a "Hear a sample" button.

## 3. Reading documents without breaking on formatting

| Input | How it's cleaned up |
|---|---|
| **Markdown** | Parsed properly (CommonMark + tables): no `#`, `**`, backticks or raw URLs read out. Headings get pauses; lists are read item by item; links read their text; tables read row by row; code blocks are skipped ("Code example skipped"); front matter is ignored |
| **PDF** | Text extracted page by page (PdfBox), then **reflowed**: hard line-wraps joined, hyphenated words re-joined, repeated headers/footers and page numbers removed, paragraphs rebuilt. Scanned (image-only) PDFs get a clear message |
| **Article links** | Page downloaded and the main article extracted (Readability), so menus, ads, footers and scripts are dropped. Read in order, with headings and lists |
| **Plain text / HTML files / pasted text** | Paragraphs kept; pasted Markdown is detected automatically |

Everywhere: URLs are read as the site name ("abc.net.au"), `&` becomes "and", ligatures and invisible characters are fixed, and long text is split on sentence boundaries to fit the speech engine's limits.

## 4. How it works

```
document ─▶ reader (PDF / Markdown / article / text) ─▶ clean segments
        ─▶ Android TextToSpeech (one chunk at a time, offline voice)
        ─▶ LAME MP3 encoder (bundled, 64 kbps mono)
        ─▶ MP3 in the app's Library  ─▶ play · save · share
```

- Generation runs in a **foreground service**, so it carries on with the screen off or the app closed, with a progress notification and a Cancel button.
- MP3 encoding uses the LAME library compiled into the app (LGPL, dynamically linked).
- Stack: Kotlin, Jetpack Compose (Material 3), minimum Android 8.0, targets Android 15.

## 5. Branches, builds and releases

- **`experimental`:** experimental builds only. Every push builds an APK on GitHub and publishes it as a **pre-release** (`exp-N`).
- **`main`:** full releases only, merged from `experimental` after sign-off. Every push publishes a full release.
- Claude does the merges.
- Install on your phone from the repo's **Releases** page: tap the `.apk`.

## 6. Testing

- **Automated (every build):** unit tests for the Markdown, PDF-reflow, article and text cleaners, chunking, WAV parsing and voice labels.
- **On-phone testing (you):** each build comes with a short checklist and the exact feedback needed.

## 7. Milestones

| Version | Branch | Deliverable |
|---|---|---|
| 0.1.0-exp | experimental | Add document/link/text → Go → notification → play/save/share MP3; Australian default voice |
| 0.2.0-exp | experimental | Fixes from your on-phone testing; reading position / resume; better PDF table handling |
| **1.0.0** | **main** | First full release after your sign-off |
| later | experimental → main | Optional extras (EPUB/DOCX, queue several documents, chapter markers) |
