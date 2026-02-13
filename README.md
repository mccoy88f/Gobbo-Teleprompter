[🇮🇹 Leggi in Italiano](README_IT.md)

# Gobbo Teleprompter

A professional teleprompter application for Android tablets and mobile phones.

## Features

### 📄 Documents & Files (v2.0)
- **New Document**: Start with a blank document
- **Import File**: Copy a file from your device into the app (files are stored inside the app)
  - Supported formats: `.txt`, `.md`, `.rtf`, `.docx`, `.pdf`
  - File size limit: 50MB (warning for larger files)
- **Imported Files**: Open any file previously imported (stored in app); no more "recent files" by URI
- **Manual Text Input**: Enter text directly via dialog (max 10,000 characters)

### 🎬 Playback Controls
- **Automatic Scrolling**: Variable speed scrolling (1-20)
- **Play/Pause**: Start and stop automatic scrolling
- **Manual Scrolling**: Three scroll modes:
  - Full Page (minus one line for context)
  - Half Page
  - 3 Lines
- **Speed Control**: Adjustable scroll speed (60–250 WPM) via slider or remote control
- **Text Size Control**: Adjustable text size (12–48sp) via slider or remote control

### 🎮 Remote Control
- **Customizable Key Mappings**: Fully customizable remote control button assignments
- **Supported Keys**: 
  - Directional keys (Arrow Up/Down)
  - Control keys (Tab, Enter, Space)
  - Volume keys (Volume Up/Down)
  - Function keys (F1-F12)
  - Number keys (0-9)
  - Letter keys (A-Z)
- **Action Types**:
  - **Single Click**: Executes action once when key is pressed
  - **Hold**: Executes action immediately, then repeats every second while key is held down
- **Default Mappings**:
  - **Arrow Up/Down**: Manual scrolling
  - **Tab (Click)**: Play/Pause
  - **Tab (Hold)**: Change scroll mode
  - **Volume Up (Click)**: Increase scroll speed
  - **Volume Down (Click)**: Decrease scroll speed
  - **Volume Up (Hold)**: Increase text size (repeats every second)
  - **Volume Down (Hold)**: Decrease text size (repeats every second)
- **Customization**: Assign any action to any supported key (single click or hold) in Settings

### 🎨 Appearance
- **Dark/Light Mode**: Toggle between dark and light themes
- **Font Selection**: Choose from 4 font families:
  - Default
  - Serif
  - Sans Serif
  - Monospace
- **Material Design 3**: Modern Material Design interface
- **Full Screen Mode**: Automatic toolbar hiding during playback
- **Tap to Show/Hide**: Tap screen to toggle toolbar visibility

### 🌐 Web Remote Control (v2.0)
- **Browser Control**: Control the teleprompter from any device on the same Wi‑Fi (Material UI page)
- **Optional PIN**: Protect access with a 4–8 digit PIN (configurable in settings)
- **Actions**: Play/Pause, scroll up/down, set WPM, text size, open imported files
- **Status Indicator**: Toolbar icon (globe) shows server state: green (active), orange (starting), red (no network)

### ⚙️ Settings & Persistence
- **Settings Menu**: Access all app settings from the toolbar
- **Web Remote Settings**: Enable/disable server, port, device name, PIN
- **Remote Control Settings**: Customize button mappings
- **Font Settings**: Select preferred font family
- **Auto-Save**: All settings are automatically saved:
  - Last opened file/text
  - Scroll speed
  - Text size
  - Scroll mode
  - Font selection
  - Custom remote control mappings
- **State Restoration**: App restores your last session on restart

### 🌍 Internationalization
- **Multi-language Support**: 
  - English (default)
  - Italian (Italiano)
- **Automatic Language Detection**: Uses system language settings

## Screenshots

*Screenshots coming soon*

## Installation

### Requirements
- Android 8.0 (API level 26) or higher
- Tablet or mobile phone

### Build from Source

1. Clone the repository:
```bash
git clone https://github.com/McCoy88f/Gobbo.git
cd Gobbo
```

2. Open the project in Android Studio

3. Build the APK:
```bash
./gradlew assembleDebug
```

The APK will be generated in `app/build/outputs/apk/debug/`

## Usage

### Loading Text

1. Tap the **File** icon in the toolbar
2. Choose one of:
   - **New document**: Start with empty text
   - **Import file**: Select a file from your device (it is copied into the app and opened)
   - **Imported files**: Open one of the files already stored in the app
   - **Load Text** (manual): Enter text directly via dialog

### Controlling Playback

- **Play Button**: Start automatic scrolling
- **Speed Slider**: Adjust scroll speed (60–250 WPM)
- **Text Size Slider**: Adjust text size (12–48sp)
- **Scroll Mode Button**: Change manual scroll amount

### Remote Control

Use a Bluetooth or USB remote control to:
- Navigate through text
- Control playback
- Adjust speed and text size
- Change scroll modes

Customize button mappings in **Settings → Remote Control Settings → Customize Remote**

### Settings

Access settings via the **Settings** icon in the toolbar:
- **Web Remote Settings**: Enable server, set port, device name, optional PIN
- **Remote Control Settings**: Customize button mappings
- **Font Settings**: Select font family
- **Credits**: View app information

## Technical Details

### Architecture
- **Language**: Kotlin
- **UI Framework**: Material Design 3
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Java Version**: 17
- **Build System**: Gradle with Kotlin DSL

### Technologies & Libraries

#### Core Android
- **AndroidX Core KTX**: 1.12.0 - Kotlin extensions for Android
- **AndroidX AppCompat**: 1.6.1 - Backward compatibility
- **AndroidX ConstraintLayout**: 2.1.4 - Layout management
- **AndroidX Lifecycle**: 2.7.0 - Lifecycle-aware components
- **AndroidX Preference**: 1.2.1 - Settings persistence

#### UI Components
- **Material Components**: 1.11.0 - Material Design 3 UI components
  - MaterialButton
  - MaterialSlider
  - MaterialAlertDialog
  - TextInputLayout/TextInputEditText

#### File Format Support
- **Apache POI**: 5.2.5
  - `poi-ooxml` - For Microsoft Word (`.docx`) files
  - `poi-scratchpad` - For Rich Text Format (`.rtf`) files
- **PDFBox Android**: 2.0.27.0 - For PDF (`.pdf`) file support

#### Custom Implementation
- **Markdown Parser**: Custom implementation using SpannableString for Markdown formatting
- **File Utils**: Custom file reading utilities for multiple formats

### Supported File Formats

| Format | Extension | Library/Implementation | Features |
|--------|-----------|----------------------|----------|
| Plain Text | `.txt` | Native Android | Full support |
| Markdown | `.md` | Custom parser | Headings, bold, italic formatting |
| Rich Text Format | `.rtf` | Apache POI | Text extraction |
| Microsoft Word | `.docx` | Apache POI | Text extraction |
| PDF | `.pdf` | PDFBox Android | Text extraction |

### File Format Details
- **Plain Text (`.txt`)**: Full support, UTF-8 encoding
- **Markdown (`.md`)**: Custom parser supporting:
  - Headings (`#`, `##`, `###`)
  - Bold text (`**text**`)
  - Italic text (`*text*`)
- **Rich Text Format (`.rtf`)**: Text extraction via Apache POI
- **Microsoft Word (`.docx`)**: Text extraction via Apache POI (Office Open XML format)
- **PDF (`.pdf`)**: Text extraction via PDFBox Android

## Author

**McCoy88f** (Antonello Migliorelli)

## ⚠️ Please read carefully!

Working on this addon and keeping it updated has taken countless hours and dedication ❤️
A coffee ☕ or a beer 🍺 is a much appreciated gesture of recognition and helps me continue to maintain this project active!

**With a donation, you'll be added to a dedicated Telegram group where you'll receive new versions in advance! I'll be waiting for you!**

<a href="https://www.buymeacoffee.com/mccoy88f"><img src="https://img.buymeacoffee.com/button-api/?text=Buy me a beer&emoji=🍺&slug=mccoy88f&button_colour=FFDD00&font_colour=000000&font_family=Bree&outline_colour=000000&coffee_colour=ffffff" /></a>

[You can also buy me a beer with PayPal 🍻](https://paypal.me/mccoy88f?country.x=US&locale.x=en_US)

## License

This project is licensed under the **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License** (CC BY-NC-SA 4.0).

**You are free to:**
- ✅ Use the software for personal, educational, or research purposes
- ✅ Modify the code
- ✅ Distribute the software (with attribution)
- ✅ Create derivative works

**You are NOT allowed to:**
- ❌ Use the material for commercial purposes
- ❌ Sell the software or any derivative works
- ❌ Use it in commercial products or services

**You must:**
- 📝 Give appropriate credit (attribution)
- 🔄 Share derivative works under the same license (ShareAlike)

See the [LICENSE](LICENSE) file for full details, or visit [https://creativecommons.org/licenses/by-nc-sa/4.0/](https://creativecommons.org/licenses/by-nc-sa/4.0/).

For commercial licensing inquiries, please contact the author.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Changelog

### 2.0.0
- **Documents & files**: New flow: New document / Import file / Imported files (files are copied into the app)
- **Web remote**: Optional PIN protection; open imported files from the web page
- **UI**: Compact control bar (play + scroll mode on one row; WPM row with slider + value + "Set WPM"; text size on one row)
- **Network indicator**: Globe icon in toolbar (green/orange/red); updates when Wi‑Fi is lost; requires `ACCESS_NETWORK_STATE`
- **PDF/errors**: Safer PDF handling (no crash on missing glyph list); show error message instead of crashing
- **Fixes**: Slider text size range 12–48 everywhere; WPM not overwritten while typing in web UI

### 1.x
- Initial features: file loading, remote control, themes, font settings, web remote (no PIN), recent files by URI

## Version

**Current Version**: 2.0.0

## Support

For issues, feature requests, or questions, please open an issue on GitHub.

---

Made with ❤️ for professional teleprompter users
