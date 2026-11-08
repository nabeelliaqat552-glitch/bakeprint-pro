# BakePrint Pro – Smart Bakery Label Maker

Android app for printing bakery labels: bakery name, product name, **MFG** and **EXP** dates.
Works fully offline. English and Urdu (RTL).

## Install on your phone (APK)

1. Open **Releases** on this GitHub page (or the link `…/releases/latest`).
2. Download the file **BakePrintPro-v1.0.x.apk**.
3. Open the downloaded file. If Android asks, allow **"Install unknown apps"** for your browser / Files app.
4. Tap **Install**, then **Open**.
5. The first time, the app asks for your **bakery name**.

Updates: install the newer APK over the old one. Your products and history stay.

**Roman Urdu:** GitHub par *Releases* kholein → `.apk` file download karein → file kholein → agar poochay to
"Install unknown apps" ki ijazat dein → **Install** dabayein. Pehli dafa app bakery ka naam poochegi.

## Printers

| Printer | Connection | Language to choose |
|---|---|---|
| 2-inch label printer (e.g. Xprinter XP-236B) | Bluetooth / USB | TSPL |
| 3–4 inch label printer (e.g. Xprinter XP-365B / XP-420B, TSC, Gprinter) | Bluetooth / USB / Wi-Fi | TSPL |
| 58 / 80 mm receipt printer | Bluetooth / USB / Wi-Fi | ESC/POS |
| Any printer Android can use | Phone printing / PDF | – |

* Bluetooth: pair the printer in the phone's Bluetooth settings first (PIN usually `0000` or `1234`), then
  **Settings → Bluetooth** in the app and tap the printer.
* Wi-Fi: the printer must be on the same Wi-Fi. Enter its IP; port is usually `9100`.
* USB: connect with a USB OTG cable, **Settings → USB → Detect**, tap the printer, tap **Allow**.
* Mini printers that only work with their own app (e.g. Niimbot, Phomemo) are **not** supported.
  Use **Save PDF / Save PNG** with those.
* Calibration (shift, gap, darkness) is in **Settings → Print calibration**.

## What's inside

* Kotlin, Jetpack Compose, Material 3, Room, MVVM (`MainViewModel`).
* `label/LabelRenderer.kt` – draws all 8 label designs on a Canvas. The same drawing is used for the preview,
  the printer (8 dots/mm = 203 dpi), PDF pages (exact mm size) and PNG files.
* `print/Printing.kt` – TSPL and ESC/POS raster commands; Bluetooth SPP, TCP 9100, USB bulk, Android Print.
* `data/` – Room database (products, categories, history, printers, settings), seed catalogue, JSON backup.

## Build

Every push to `main` builds a signed release APK with GitHub Actions and publishes it under **Releases**.
To build yourself: open the folder in Android Studio and choose *Build › Build APK(s)*, or run
`gradle assembleRelease` with JDK 17 and the Android SDK installed.

The signing key `app/bakeprint.jks` is kept in this private repository so updates install over older versions.
Keep the repository private.
