# Istruzioni per creare la Release v1.0.0 su GitHub

## APK generato
L'APK release è stato generato in:
- `app/build/outputs/apk/release/app-release-unsigned.apk`
- Copia locale: `app-release-v1.0.0.apk`

## Passi per creare la release su GitHub

1. Vai su https://github.com/mccoy88f/Gobbo-Teleprompter/releases/new

2. Compila i campi:
   - **Tag**: `v1.0.0` (già creato)
   - **Release title**: `Gobbo Teleprompter v1.0.0`
   - **Description**: 
     ```
     ## 🎉 Prima Release - Gobbo Teleprompter v1.0.0
     
     ### Funzionalità Principali
     - 📄 Caricamento testo da file (TXT, MD, RTF, DOCX, PDF)
     - 🎬 Scorrimento automatico con velocità variabile
     - 🎮 Telecomando personalizzabile
     - 🎨 Modalità scura/chiara
     - 🔤 Selezione font
     - 🌍 Supporto multilingua (Italiano/Inglese)
     - ⚙️ Persistenza impostazioni
     
     ### Installazione
     Scarica l'APK e installalo sul tuo dispositivo Android (8.0+).
     
     ### Note
     - APK non firmato (per uso personale)
     - Per distribuzione pubblica, firma l'APK con la tua chiave
     ```

3. **Carica l'APK**:
   - Trascina il file `app-release-v1.0.0.apk` nella sezione "Attach binaries"
   - Oppure carica `app/build/outputs/apk/release/app-release-unsigned.apk`

4. Clicca su **"Publish release"**

## Note sulla firma APK

L'APK generato è **non firmato** (unsigned). Per distribuzione pubblica:

1. Genera una keystore:
```bash
keytool -genkey -v -keystore gobbo-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias gobbo
```

2. Configura la firma in `app/build.gradle.kts`:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("path/to/gobbo-release-key.jks")
            storePassword = "your-password"
            keyAlias = "gobbo"
            keyPassword = "your-password"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ...
        }
    }
}
```

3. Ricompila:
```bash
./gradlew assembleRelease
```

L'APK firmato sarà in `app/build/outputs/apk/release/app-release.apk`
