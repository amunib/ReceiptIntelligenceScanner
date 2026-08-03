# Receipt Intelligence Scanner

A production-grade Android application that converts physical and digital receipts into structured CSV data for business accounting and analysis. Built specifically for Ethiopian fiscal receipt formats (ERCA/EFDA-style cash register receipts).

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Opening the Project in Android Studio](#opening-the-project-in-android-studio)
3. [First Build](#first-build)
4. [Running on a Device or Emulator](#running-on-a-device-or-emulator)
5. [Generating a Release APK](#generating-a-release-apk)
6. [Architecture Overview](#architecture-overview)
7. [Project Structure](#project-structure)
8. [How the Parser Works](#how-the-parser-works)
9. [Adding a New Receipt Format](#adding-a-new-receipt-format)
10. [Enabling Excel Export](#enabling-excel-export)
11. [Running Tests](#running-tests)
12. [Performance Notes (1000+ Receipts)](#performance-notes-1000-receipts)
13. [Known Limitations & Future Work](#known-limitations--future-work)

---

## Prerequisites

| Tool | Required Version | Notes |
|---|---|---|
| Android Studio | Hedgehog 2023.1.1 or newer | Giraffe also works |
| JDK | 17 | Bundled with recent Android Studio |
| Android SDK | API 34 (compile), API 24 min | Install via SDK Manager |
| Gradle | 8.7 (wrapper downloads automatically) | Do not need a local Gradle install |
| Device / Emulator | Android 7.0 (API 24) or higher | Physical device recommended for camera |

---

## Opening the Project in Android Studio

```bash
# Clone or place the project folder, then open Android Studio and choose:
# File → Open → select the ReceiptIntelligenceScanner/ root folder
```

Android Studio will:
1. Detect the `settings.gradle.kts` and configure the project.
2. Download Gradle 8.7 via the wrapper (first time only, ~100 MB).
3. Sync all Maven dependencies from Google + Maven Central (~500 MB cache, first time only).

> **Offline machines:** Run the sync once on a machine with internet, then copy the entire `~/.gradle/caches` directory to the offline machine. The project will build fully offline afterward — matching the app's own offline-first design.

---

## First Build

```bash
# From the project root (or use the Android Studio Build menu):
./gradlew assembleDebug
```

The built APK lands at:
```
app/build/outputs/apk/debug/app-debug.apk
```

Install it directly:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Running on a Device or Emulator

1. Enable **Developer Options** and **USB Debugging** on your Android device.
2. Connect via USB.
3. In Android Studio press ▶ (Run) or:

```bash
./gradlew installDebug
```

**ML Kit model download:** The manifest declares `com.google.mlkit.vision.DEPENDENCIES = ocr` which forces the on-device OCR model to install at app-install time (not lazily on first scan). This means:
- After install the app works fully offline.
- Install on a slow connection may take an extra 10–30 seconds.

---

## Generating a Release APK

### 1. Create a keystore (first time only)

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias receipt_scanner \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

Store `release.keystore` somewhere safe — **not** inside the project folder (do not commit it to Git).

### 2. Add signing config to `app/build.gradle.kts`

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("/path/to/release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "receipt_scanner"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // isMinifyEnabled and proguardFiles already set
        }
    }
}
```

Using environment variables keeps credentials out of source code.

### 3. Build

```bash
KEYSTORE_PASSWORD=your_pass KEY_PASSWORD=your_pass ./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### 4. Build an AAB for Google Play (recommended over APK for Play Store submissions)

```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

---

## Architecture Overview

```
┌───────────────────────────────────────────────────────────┐
│                        UI Layer                           │
│  Compose Screens  ←→  ViewModels  (MVVM, StateFlow)       │
└──────────────────────────────┬────────────────────────────┘
                               │
┌──────────────────────────────▼────────────────────────────┐
│                     Repository Layer                       │
│  ReceiptRepository — orchestrates OCR + Parser + DB       │
│  PendingBatchHolder — in-memory batch handoff             │
└───────┬───────────────┬──────────────────┬────────────────┘
        │               │                  │
   ┌────▼────┐    ┌─────▼──────┐    ┌─────▼──────┐
   │  OCR    │    │   Parser   │    │    Room    │
   │ Engine  │    │ (regex /   │    │  Database  │
   │(ML Kit) │    │ extensible)│    │(WAL mode)  │
   └────┬────┘    └─────┬──────┘    └─────┬──────┘
        │               │                  │
   ┌────▼───────────────▼──────────────────▼──────┐
   │   Camera (CameraX)  /  Files (SAF / URI)      │
   └──────────────────────────────────────────────┘
```

**Key design decisions:**

- **Single Activity, Compose Navigation** — all six screens share one `MainActivity`; navigation is handled by `NavHost` with a persistent bottom bar.
- **No DI framework** — `ServiceLocator` provides a singleton `ReceiptRepository` to all ViewModels via `AppViewModelFactory`. This keeps the dependency graph transparent and easy for a single developer to reason about. Migrating to Hilt is a mechanical change (annotate classes, replace factory) if the team grows.
- **Repository as orchestration boundary** — the OCR engine, parser, and database are all internal to `ReceiptRepository`. ViewModels never touch bitmaps or SQL directly.
- **Pluggable parser** — `ReceiptParser` delegates to `List<ReceiptFormat>`, trying each in order and using the highest-confidence result. Adding support for a new receipt layout means implementing the two-function `ReceiptFormat` interface and adding it to the list.
- **Coroutines on Dispatchers.Default for OCR/parsing** — never blocks the main thread even for 1000+ receipt batches.
- **Room WAL mode** — Write-Ahead Logging allows concurrent reads during a batch insert, so the History screen stays responsive while the Processing screen is still running.

---

## Project Structure

```
app/src/main/java/com/receiptintel/scanner/
├── ReceiptScannerApp.kt           Application class; owns the DB instance
├── MainActivity.kt                Single activity; Compose entry point
│
├── data/
│   ├── local/
│   │   ├── entity/                Room entities: ReceiptEntity, ItemEntity,
│   │   │                          ReceiptWithItems (relation), DashboardSummary
│   │   ├── dao/                   ReceiptDao, ItemDao
│   │   └── AppDatabase.kt
│   └── repository/
│       ├── ReceiptRepository.kt   Main orchestration layer
│       ├── ReceiptSource.kt       Sealed input type (Camera | Image | Text)
│       └── PendingBatchHolder.kt  Transient batch handoff between screens
│
├── ocr/
│   ├── TextRecognitionEngine.kt   ML Kit coroutine wrapper
│   └── ReceiptImageProcessor.kt   Grayscale + contrast + auto-crop
│
├── parser/
│   ├── ParsedReceipt.kt           Output data model
│   ├── ReceiptParser.kt           Entry point + EthiopianFiscalFormat strategy
│   └── DuplicateHasher.kt         SHA-256 based content hash
│
├── csv/
│   ├── CsvExporter.kt             Working CSV export (OpenCSV)
│   └── ExcelExporter.kt           Stub with clear integration instructions
│
├── util/
│   ├── TxtReceiptReader.kt        UTF-8 / Windows-1252 TXT reader + splitter
│   ├── PdfPageExtractor.kt        Native PdfRenderer → Bitmap
│   └── UserPreferences.kt         DataStore: dark mode, language, export folder
│
├── di/
│   └── ServiceLocator.kt          Manual DI; creates ReceiptRepository
│
└── ui/
    ├── AppViewModelFactory.kt     One factory for all ViewModels
    ├── theme/                     Color, Type, Theme (light + dark)
    ├── navigation/                AppNavigation (NavHost + bottom bar)
    └── screens/
        ├── dashboard/             DashboardViewModel + DashboardScreen
        ├── scan/                  ScanViewModel + ScanScreen
        │                          CameraPreviewContent + CropOverlay
        ├── importfiles/           ImportViewModel + ImportScreen
        ├── processing/            ProcessingViewModel + ProcessingScreen
        ├── history/               HistoryViewModel + HistoryScreen
        └── export/                ExportViewModel + ExportScreen

app/src/test/
└── parser/
    ├── ReceiptParserTest.kt       12 unit tests for the core parser
    └── DuplicateHasherTest.kt     3 tests for duplicate detection
```

---

## How the Parser Works

The parser converts raw text (from OCR or a TXT import) into a `ParsedReceipt` in four passes:

```
Raw text
   │
   ▼
normalizeOcrNoise()        ← fixes "94, 944.00" → "94,944.00", stray 'O'/'l' in numbers
   │
   ▼
EthiopianFiscalFormat.tryParse()
   ├─ extractSellerTin()   regex: TIN preceded by non-"BUYER'S"
   ├─ extractBuyerTin()    regex: BUYER'S TIN
   ├─ extractReceiptNumber regex: FS No.XXXXXXXX
   ├─ extractDateTime()    regex: DD/MM/YYYY HH:mm  →  normalizeDate() → yyyy-MM-dd
   ├─ extractTax()         regex: TXBL and TAX(X%) patterns
   ├─ extractTotal()       regex: TOTAL *X
   ├─ extractPaymentMethod keyword list: CASH, CARD, TELEBIRR, CHEQUE, …
   └─ extractItems()       two-line pattern: "<unitPrice> x <qty>" then "<NAME> *<subtotal>"
   │
   ▼
missingFields → confidence score (fraction of important fields found)
   │
   ▼
ParsedReceipt (stored whether hasErrors or not, flagged for manual review)
```

**Sample receipt → parsed output:**

```
Input:
  TIN: 0063936942
  FS No.00000259
  21/07/2025 11:46
  BUYER'S TIN 0003603905
  34400 x 2.400
  BIRTHDAY-DICORAN *82,560.00
  TXBL 1 *82,560.00
  TAX 1(15.00%) *12,384.00
  TOTAL *94,944.00
  CASH *94,944.00

Output:
  sellerTin      = "0063936942"
  receiptNumber  = "00000259"
  date           = "2025-07-21"
  time           = "11:46"
  buyerTin       = "0003603905"
  items[0]       = { name:"BIRTHDAY-DICORAN", qty:2.4, unitPrice:34400, subtotal:82560 }
  taxableAmount  = 82560.00
  vatPercentage  = 15.0
  vatAmount      = 12384.00
  total          = 94944.00
  paymentMethod  = "CASH"
  confidence     = 1.0
  hasErrors      = false
```

---

## Adding a New Receipt Format

Suppose you encounter receipts from a different POS system that labels things differently:

```
RECEIPT NO: 1234
DATE: 2026-01-15  TIME: 08:30
...
GRAND TOTAL: 500.00
PAID BY: CARD
```

**Step 1** — implement `ReceiptFormat`:

```kotlin
object MyNewPosFormat : ReceiptFormat {
    override fun tryParse(text: String): ParsedReceipt {
        val receiptNumber = Regex("""RECEIPT NO:\s*(\d+)""").find(text)?.groupValues?.get(1)
        val total = Regex("""GRAND TOTAL:\s*([\d,]+\.\d{2})""").find(text)?.groupValues?.get(1)
            ?.replace(",", "")?.toDoubleOrNull()
        val paymentMethod = Regex("""PAID BY:\s*(\w+)""").find(text)?.groupValues?.get(1)?.uppercase()

        val missing = mutableListOf<String>()
        if (receiptNumber == null) missing += "receiptNumber"
        if (total == null) missing += "total"

        return ParsedReceipt(
            receiptNumber = receiptNumber,
            total = total,
            paymentMethod = paymentMethod,
            // ... fill other fields
            confidence = (2f - missing.size.coerceAtMost(2)) / 2f,
            missingFields = missing,
            rawText = text
        )
    }
}
```

**Step 2** — register it in `ReceiptParser.kt`:

```kotlin
private val FORMATS: List<ReceiptFormat> = listOf(
    EthiopianFiscalFormat,
    MyNewPosFormat       // ← add here
)
```

The parser tries both and uses whichever has the higher `confidence`. Done.

---

## Enabling Excel Export

The `ExcelExporter` stub explains this in its source, but for convenience:

**Step 1** — add the dependency to `app/build.gradle.kts`:

```kotlin
implementation("org.dhatim:fastexcel:0.17.0")
```

`fastexcel` is purpose-built for Android (no AWT/Swing deps, streaming row writer, handles 100k+ rows without OOM).

**Step 2** — replace the body of `ExcelExporter.export()`:

```kotlin
fun export(receipts: List<ReceiptWithItems>, outFile: File): File {
    org.dhatim.fastexcel.Workbook(
        java.io.FileOutputStream(outFile),
        "Receipt Intelligence Scanner",
        "1.0"
    ).use { wb ->
        val ws = wb.newWorksheet("Receipts")
        CsvExporter.HEADER.forEachIndexed { col, title -> ws.value(0, col, title) }
        var row = 1
        receipts.forEach { rwi ->
            val r = rwi.receipt
            if (rwi.items.isEmpty()) {
                ws.value(row, 0, r.receiptNumber ?: ""); row++
            } else {
                rwi.items.forEach { item ->
                    ws.value(row, 0, r.receiptNumber ?: "")
                    ws.value(row, 5, item.itemName)
                    ws.value(row, 6, item.quantity ?: 0.0)
                    // ... fill remaining columns matching CsvExporter.HEADER order
                    row++
                }
            }
        }
    }
    return outFile
}
```

**Step 3** — remove the `throw NotConfiguredException()` line. The Export screen will automatically show the Share button after a successful export.

---

## Running Tests

```bash
# Unit tests (parser, hasher — run on the JVM, no device needed):
./gradlew :app:test

# Instrumented tests (requires connected device or emulator):
./gradlew :app:connectedAndroidTest

# See HTML report at:
# app/build/reports/tests/testDebugUnitTest/index.html
```

The 15 unit tests in `ReceiptParserTest` and `DuplicateHasherTest` cover:
- All fields from the standard Ethiopian fiscal format
- Multi-item receipts
- Missing-field graceful handling (no crash)
- OCR noise normalization
- Duplicate hash stability and differentiation

---

## Performance Notes (1000+ Receipts)

The app is built specifically to handle large batches without freezing:

| Concern | Solution |
|---|---|
| UI freeze during batch OCR | Processing runs on `Dispatchers.Default`; progress updates flow back via `mutableStateOf` on the main thread in small increments |
| Memory for 1000+ bitmaps | Bitmaps are processed one at a time and not retained in memory — `PendingBatchHolder` holds the source list (URIs/text, not decoded bitmaps), and `ImportViewModel` decodes each file in the same loop that hands it to the repository |
| Database write throughput | Room in WAL mode allows concurrent reads. 1000 sequential inserts take roughly 3–8 seconds on a mid-range device (SSD-backed SQLite is fast; the bottleneck is OCR, not the DB) |
| Progress loss on process kill | Each receipt is committed individually inside `processOne()` — killing the app mid-batch does not lose already-processed receipts, only the ones not yet reached |
| Batch > 3000 receipts | Consider splitting into sub-batches of 500 and triggering via WorkManager instead of a foreground coroutine. The `ProcessingViewModel.start()` signature is unchanged; only the caller changes |

Realistic throughput on a mid-range device (e.g. Samsung A series):
- TXT receipts: ~400–800/minute (no OCR, just parsing)
- Camera/image receipts: ~30–60/minute (ML Kit OCR is the bottleneck)

---

## Known Limitations & Future Work

### Amharic-script OCR
ML Kit's Latin recognizer cannot read Ge'ez script. If your sellers' names or addresses are printed in Amharic on the receipt, those fields will OCR incorrectly. **Fix:** replace `TextRecognitionEngine` internals with Tesseract4Android + the `amh` language pack. The `TextRecognitionEngine` class is the only change required — the rest of the pipeline is script-agnostic.

### PDF text layer
`PdfPageExtractor` renders PDF pages to bitmaps and runs OCR on them. If a PDF has a real embedded text layer (e.g. a digitally generated receipt from accounting software), extracting the text layer directly would be faster and more accurate. Add PDFBox-Android for this case; fall back to the current renderer for scanned/image PDFs.

### Perspective distortion
`ReceiptImageProcessor.autoCrop()` finds a bounding box but does not correct perspective warping (e.g. a receipt photo taken at an angle). For production use on angled photos, add OpenCV's `warpPerspective` using the four crop-overlay corner points as the source quadrilateral.

### Backup/restore UI
`UserPreferences` stores an export folder URI and the DataStore schema is in place, but a dedicated Settings screen with backup/restore buttons was left out of scope. The database file at `databases/receipt_scanner.db` can be copied to/from external storage via standard `File` operations; a Settings screen just needs to wire a button to that logic.

### Language switching at runtime
`values-am/strings.xml` and `values-ar/strings.xml` (with RTL support via `android:supportsRtl="true"`) are complete. The `UserPreferences.language` preference is stored. Applying it at runtime on Android 13+ requires `context.createConfigurationContext(...)` or a process restart — the wiring of the language-change event to an activity recreate is a two-line addition to `MainActivity`.
