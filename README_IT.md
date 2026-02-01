# Gobbo Teleprompter

Un'applicazione teleprompter professionale per tablet e telefoni Android.

## Funzionalità

### 📄 Caricamento Testo
- **Inserimento Manuale**: Inserisci testo direttamente tramite dialog (massimo 10.000 caratteri)
- **Importazione File**: Supporto per più formati di file:
  - `.txt` - File di testo semplice
  - `.md` - File Markdown (con supporto formattazione)
  - `.rtf` - File Rich Text Format
  - `.docx` - Documenti Microsoft Word
  - `.pdf` - Documenti PDF
- **Limite Dimensione File**: Dimensione massima file 50MB (viene mostrato un avviso per file più grandi)
- **File Recenti**: Accesso rapido ai file aperti di recente con visualizzazione nome e percorso
- **Cronologia File**: Tracciamento automatico dei file aperti di recente

### 🎬 Controlli di Riproduzione
- **Scorrimento Automatico**: Scorrimento a velocità variabile (1-20)
- **Play/Pausa**: Avvia e ferma lo scorrimento automatico
- **Scorrimento Manuale**: Tre modalità di scorrimento:
  - Pagina Intera (meno una riga per il contesto)
  - Metà Pagina
  - 3 Righe
- **Controllo Velocità**: Regolazione della velocità di scorrimento tramite slider o telecomando
- **Controllo Dimensione Testo**: Regolazione della dimensione del testo (12-72sp) tramite slider o telecomando

### 🎮 Telecomando
- **Mappature Personalizzabili**: Assegnazioni completamente personalizzabili dei pulsanti del telecomando
- **Tasti Supportati**: 
  - Tasti direzionali (Frecce Su/Giù)
  - Tasti di controllo (Tab, Invio, Barra Spaziatrice)
  - Tasti volume (Volume Su/Giù)
  - Tasti funzione (F1-F12)
  - Tasti numerici (0-9)
  - Tasti lettera (A-Z)
- **Tipi di Azione**:
  - **Click Singolo**: Esegue l'azione una volta quando il tasto viene premuto
  - **Tieni Premuto (Hold)**: Esegue l'azione immediatamente, poi la ripete ogni secondo finché il tasto è premuto
- **Mappature Predefinite**:
  - **Frecce Su/Giù**: Scorrimento manuale
  - **Tab (Click)**: Play/Pause
  - **Tab (Hold)**: Cambia modalità scorrimento
  - **Volume Su (Click)**: Aumenta velocità di scorrimento
  - **Volume Giù (Click)**: Diminuisce velocità di scorrimento
  - **Volume Su (Hold)**: Aumenta dimensione testo (ripete ogni secondo)
  - **Volume Giù (Hold)**: Diminuisce dimensione testo (ripete ogni secondo)
- **Personalizzazione**: Assegna qualsiasi azione a qualsiasi tasto supportato (click singolo o tieni premuto) nelle Impostazioni

### 🎨 Aspetto
- **Modalità Scura/Chiara**: Passa tra temi scuri e chiari
- **Selezione Font**: Scegli tra 4 famiglie di font:
  - Predefinito
  - Serif
  - Sans Serif
  - Monospace
- **Material Design 3**: Interfaccia moderna Material Design
- **Modalità Schermo Intero**: Nascondimento automatico della toolbar durante la riproduzione
- **Tap per Mostrare/Nascondere**: Tocca lo schermo per mostrare/nascondere la toolbar

### ⚙️ Impostazioni e Persistenza
- **Menu Impostazioni**: Accedi a tutte le impostazioni dell'app dalla toolbar
- **Impostazioni Telecomando**: Personalizza le mappature dei pulsanti
- **Impostazioni Font**: Seleziona la famiglia di font preferita
- **Salvataggio Automatico**: Tutte le impostazioni vengono salvate automaticamente:
  - Ultimo file/testo aperto
  - Velocità di scorrimento
  - Dimensione testo
  - Modalità scorrimento
  - Selezione font
  - Mappature personalizzate del telecomando
- **Ripristino Stato**: L'app ripristina la tua ultima sessione al riavvio

### 🌍 Internazionalizzazione
- **Supporto Multi-lingua**: 
  - Inglese (predefinito)
  - Italiano
- **Rilevamento Automatico Lingua**: Usa le impostazioni di lingua del sistema

## Screenshot

*Screenshot in arrivo*

## Installazione

### Requisiti
- Android 8.0 (livello API 26) o superiore
- Tablet o telefono cellulare

### Compilazione dal Codice Sorgente

1. Clona il repository:
```bash
git clone https://github.com/McCoy88f/Gobbo.git
cd Gobbo
```

2. Apri il progetto in Android Studio

3. Compila l'APK:
```bash
./gradlew assembleDebug
```

L'APK verrà generato in `app/build/outputs/apk/debug/`

## Utilizzo

### Caricamento Testo

1. Tocca l'icona **File** nella toolbar
2. Scegli una delle opzioni:
   - **Apri File**: Seleziona un file dal tuo dispositivo
   - **Carica Testo**: Inserisci testo manualmente
   - **File Recenti**: Apri un file usato di recente

### Controllo della Riproduzione

- **Pulsante Play**: Avvia lo scorrimento automatico
- **Slider Velocità**: Regola la velocità di scorrimento (1-20)
- **Slider Dimensione Testo**: Regola la dimensione del testo (12-72sp)
- **Pulsante Modalità Scorrimento**: Cambia la quantità di scorrimento manuale

### Telecomando

Usa un telecomando Bluetooth o USB per:
- Navigare attraverso il testo
- Controllare la riproduzione
- Regolare velocità e dimensione testo
- Cambiare modalità di scorrimento

Personalizza le mappature dei pulsanti in **Impostazioni → Impostazioni Telecomando → Personalizza Telecomando**

### Impostazioni

Accedi alle impostazioni tramite l'icona **Impostazioni** nella toolbar:
- **Impostazioni Telecomando**: Personalizza le mappature dei pulsanti
- **Impostazioni Font**: Seleziona la famiglia di font
- **Credits**: Visualizza le informazioni sull'app

## Dettagli Tecnici

### Architettura
- **Linguaggio**: Kotlin
- **Framework UI**: Material Design 3
- **SDK Minimo**: 26 (Android 8.0)
- **SDK Target**: 34 (Android 14)
- **Versione Java**: 17
- **Sistema di Build**: Gradle con Kotlin DSL

### Tecnologie e Librerie

#### Core Android
- **AndroidX Core KTX**: 1.12.0 - Estensioni Kotlin per Android
- **AndroidX AppCompat**: 1.6.1 - Compatibilità retroattiva
- **AndroidX ConstraintLayout**: 2.1.4 - Gestione layout
- **AndroidX Lifecycle**: 2.7.0 - Componenti lifecycle-aware
- **AndroidX Preference**: 1.2.1 - Persistenza impostazioni

#### Componenti UI
- **Material Components**: 1.11.0 - Componenti UI Material Design 3
  - MaterialButton
  - MaterialSlider
  - MaterialAlertDialog
  - TextInputLayout/TextInputEditText

#### Supporto Formati File
- **Apache POI**: 5.2.5
  - `poi-ooxml` - Per file Microsoft Word (`.docx`)
  - `poi-scratchpad` - Per file Rich Text Format (`.rtf`)
- **PDFBox Android**: 2.0.27.0 - Per il supporto file PDF (`.pdf`)

#### Implementazione Personalizzata
- **Parser Markdown**: Implementazione personalizzata usando SpannableString per la formattazione Markdown
- **File Utils**: Utility personalizzate per la lettura di più formati

### Formati File Supportati

| Formato | Estensione | Libreria/Implementazione | Caratteristiche |
|---------|------------|-------------------------|-----------------|
| Testo Semplice | `.txt` | Android nativo | Supporto completo |
| Markdown | `.md` | Parser personalizzato | Formattazione per titoli, grassetto, corsivo |
| Rich Text Format | `.rtf` | Apache POI | Estrazione testo |
| Microsoft Word | `.docx` | Apache POI | Estrazione testo |
| PDF | `.pdf` | PDFBox Android | Estrazione testo |

### Dettagli Formati File
- **Testo Semplice (`.txt`)**: Supporto completo, codifica UTF-8
- **Markdown (`.md`)**: Parser personalizzato che supporta:
  - Titoli (`#`, `##`, `###`)
  - Testo grassetto (`**testo**`)
  - Testo corsivo (`*testo*`)
- **Rich Text Format (`.rtf`)**: Estrazione testo tramite Apache POI
- **Microsoft Word (`.docx`)**: Estrazione testo tramite Apache POI (formato Office Open XML)
- **PDF (`.pdf`)**: Estrazione testo tramite PDFBox Android

## Autore

**McCoy88f** (Antonello Migliorelli)

## Licenza

Questo progetto è rilasciato sotto la **Licenza Creative Commons Attribution-NonCommercial-ShareAlike 4.0 Internazionale** (CC BY-NC-SA 4.0).

**Sei libero di:**
- ✅ Usare il software per scopi personali, educativi o di ricerca
- ✅ Modificare il codice
- ✅ Distribuire il software (con attribuzione)
- ✅ Creare opere derivate

**NON sei autorizzato a:**
- ❌ Usare il materiale per scopi commerciali
- ❌ Vendere il software o opere derivate
- ❌ Usarlo in prodotti o servizi commerciali

**Devi:**
- 📝 Fornire credito appropriato (attribuzione)
- 🔄 Condividere le opere derivate sotto la stessa licenza (ShareAlike)

Vedi il file [LICENSE](LICENSE) per i dettagli completi, oppure visita [https://creativecommons.org/licenses/by-nc-sa/4.0/](https://creativecommons.org/licenses/by-nc-sa/4.0/).

Per richieste di licenza commerciale, contattare l'autore.

## Contributi

I contributi sono benvenuti! Sentiti libero di inviare una Pull Request.

## Versione

**Versione Corrente**: 1.0.0

## Supporto

Per problemi, richieste di funzionalità o domande, apri un issue su GitHub.

---

Fatto con ❤️ per utenti professionisti di teleprompter
