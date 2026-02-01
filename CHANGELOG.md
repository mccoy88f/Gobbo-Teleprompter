# Changelog

All notable changes to Gobbo Teleprompter will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.0] - 2026-02-01

### Added
- **Velocità in WPM (parole/minuto)**: controllo principale della velocità di scorrimento
  - Slider Parole/min (60–250 WPM) per impostare il valore desiderato
  - Dialog "Imposta WPM" per inserire un valore esatto (anche senza testo caricato)
  - La velocità di scorrimento è calcolata automaticamente dal WPM e si adatta a dimensione testo e lunghezza documento
- Migrazione automatica dalle impostazioni precedenti (currentSpeed → targetWpm)

### Changed
- Lo slider velocità è stato sostituito dallo slider WPM (Parole/min)
- Telecomando "Aumenta/Diminuisci velocità" modifica il WPM di ±10

### Technical
- `targetWpm` salvato in SharedPreferences; `getScrollSpeedFromWpm()` calcola la velocità in pixel da WPM e dimensioni contenuto

---

## [1.0.0] - 2024-01-XX

### Added
- Initial release of Gobbo Teleprompter
- Text loading from multiple formats (TXT, MD, RTF, DOCX, PDF)
- Manual text input via dialog
- Automatic scrolling with variable speed (1-20)
- Play/Pause functionality
- Manual scrolling with three modes:
  - Full Page (minus one line)
  - Half Page
  - 3 Lines
- Speed control via slider
- Text size control via slider (12-72sp)
- Remote control support with customizable key mappings
- Dark/Light theme toggle
- Font selection (Default, Serif, Sans Serif, Monospace)
- Recent files history
- File persistence (last opened file/text)
- Settings persistence (speed, text size, scroll mode, font, remote mappings)
- Multi-language support (English, Italian)
- Material Design 3 interface
- Full screen mode during playback
- Tap to show/hide toolbar
- Markdown formatting support
- Loading progress dialog
- Credits dialog

### Technical
- Kotlin-based Android application
- Minimum SDK: 26 (Android 8.0)
- Target SDK: 34
- Material Design 3 components
- Apache POI for DOCX support
- PDFBox for PDF support

---

[1.4.0]: https://github.com/McCoy88f/Gobbo/releases/tag/v1.4.0
[1.0.0]: https://github.com/McCoy88f/Gobbo/releases/tag/v1.0.0
