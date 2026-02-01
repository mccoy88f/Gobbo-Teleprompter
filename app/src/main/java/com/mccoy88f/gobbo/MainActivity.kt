package com.mccoy88f.gobbo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.text.SpannableString
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.text.util.Linkify
import android.text.method.LinkMovementMethod
import android.graphics.Typeface
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import android.provider.OpenableColumns
import android.database.Cursor

class MainActivity : AppCompatActivity() {
    
    private lateinit var scrollView: NestedScrollView
    private lateinit var textView: android.widget.TextView
    private lateinit var btnPlayPause: MaterialButton
    private lateinit var btnScrollMode: MaterialButton
    private lateinit var speedSlider: Slider
    private lateinit var textSizeSlider: Slider
    private lateinit var controlsLayout: android.widget.LinearLayout
    
    private var isPlaying = false
    private var scrollHandler: Handler? = null
    private var scrollRunnable: Runnable? = null
    private var currentSpeed = 5f
    private var currentTextSize = 24f
    private var isDarkMode = false
    private var scrollMode = 0 // 0 = pagina intera, 1 = metà pagina, 2 = 3 righe
    private var savedText: String? = null
    private var savedTextExtension: String? = null
    private var volumeKeyHandler: Handler? = null
    private var toolbarHideHandler: Handler? = null
    private var toolbarHideRunnable: Runnable? = null
    private var isToolbarVisible = true
    private var progressDialog: androidx.appcompat.app.AlertDialog? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var currentFileUri: String? = null
    private var volumeKeyRunnable: Runnable? = null
    private var isVolumeKeyPressed = false
    private var volumeKeyDirection = 0 // 1 = su, -1 = giù
    private var lastVolumeKeyPressTime = 0L
    private var lastVolumeKeyCode = 0
    private var lastTabPressTime = 0L
    private val DOUBLE_CLICK_TIME_DELTA = 300 // millisecondi
    
    // Key capture for customization
    private var isCapturingKey = false
    private var capturedKeyCode: Int? = null
    private var capturedIsDoubleClick = false
    private var keyCaptureAction: RemoteAction? = null
    private var keyCaptureDialog: androidx.appcompat.app.AlertDialog? = null
    private var keyCaptureHandler: Handler? = null
    private var keyCaptureRunnable: Runnable? = null
    private var keyCaptureTimer = 10 // seconds
    
    // Remote control mappings
    private enum class RemoteAction {
        SCROLL_UP, SCROLL_DOWN, PLAY_PAUSE, CHANGE_SCROLL_MODE,
        INCREASE_SPEED, DECREASE_SPEED, INCREASE_TEXT_SIZE, DECREASE_TEXT_SIZE
    }
    
    private data class RemoteKeyMapping(
        val keyCode: Int,
        val isDoubleClick: Boolean,
        val action: RemoteAction
    )
    
    private var remoteMappings = mutableListOf<RemoteKeyMapping>()
    private var currentFont = "default" // default, serif, sans_serif, monospace
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openFilePicker()
        } else {
            Toast.makeText(this, getString(R.string.error_loading_file), Toast.LENGTH_SHORT).show()
        }
    }
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            loadFileContent(it)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Inizializza SharedPreferences
        sharedPreferences = getDefaultSharedPreferences(this)
        
        // Configura la toolbar standard con i pulsanti
        supportActionBar?.let {
            it.setDisplayShowTitleEnabled(true)
            it.title = getString(R.string.app_name)
        }
        
        initViews()
        setupListeners()
        
        // Ripristina lo stato salvato
        if (savedInstanceState != null) {
            savedText = savedInstanceState.getString("savedText")
            savedTextExtension = savedInstanceState.getString("savedTextExtension")
            // Ripristina il testo se salvato
            savedText?.let { text ->
                if (text.isNotEmpty() && text != getString(R.string.enter_text)) {
                    if (savedTextExtension?.lowercase() == "md") {
                        textView.text = MarkdownFormatter.formatMarkdownSimple(text)
                    } else {
                        textView.text = text
                    }
                }
            }
        } else {
            // Ripristina dal SharedPreferences se non c'è savedInstanceState
            restoreLastFile()
        }
        
        handleIntent(intent)
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Salva il testo e l'estensione prima della ricreazione
        savedText?.let { outState.putString("savedText", it) }
        savedTextExtension?.let { outState.putString("savedTextExtension", it) }
    }
    
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_file -> {
                showFileMenu()
                true
            }
            R.id.menu_toggle_controls -> {
                toggleControlsVisibility()
                true
            }
            R.id.menu_theme -> {
                toggleTheme()
                true
            }
            R.id.menu_settings -> {
                showSettingsMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun initViews() {
        scrollView = findViewById(R.id.scrollView)
        textView = findViewById(R.id.textView)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnScrollMode = findViewById(R.id.btnScrollMode)
        speedSlider = findViewById(R.id.speedSlider)
        textSizeSlider = findViewById(R.id.textSizeSlider)
        controlsLayout = findViewById(R.id.controlsLayout)
        
        scrollHandler = Handler(Looper.getMainLooper())
        volumeKeyHandler = Handler(Looper.getMainLooper())
        toolbarHideHandler = Handler(Looper.getMainLooper())
        keyCaptureHandler = Handler(Looper.getMainLooper())
        textView.textSize = currentTextSize
        textView.isClickable = true
        textView.isFocusable = true
        
        // Ripristina il testo salvato se presente
        savedText?.let { text ->
            if (text.isNotEmpty() && text != getString(R.string.enter_text)) {
                // Se era un file Markdown, riformatta
                if (savedTextExtension?.lowercase() == "md") {
                    textView.text = MarkdownFormatter.formatMarkdownSimple(text)
                } else {
                    textView.text = text
                }
            }
        }
        
        // Imposta stepSize per gli slider
        speedSlider.stepSize = 1f
        textSizeSlider.stepSize = 2f
        
        // Carica impostazioni salvate
        loadSettings()
        
        // Inizializza modalità scorrimento
        updateScrollModeButton()
        
        // Applica font
        applyFont()
    }
    
    private fun setupListeners() {
        // Aggiungi listener per tap sullo schermo per mostrare/nascondere toolbar
        textView.setOnClickListener {
            toggleToolbarVisibility()
        }
        
        btnPlayPause.setOnClickListener {
            togglePlayPause()
        }
        
        // I pulsanti Controlli e Aspetto sono ora nel menu della toolbar
        // btnToggleControls.setOnClickListener {
        //     toggleControlsVisibility()
        // }
        
        btnScrollMode.setOnClickListener {
            showScrollModeDialog()
        }
        
        speedSlider.addOnChangeListener { _, value, _ ->
            currentSpeed = value
            saveSettings() // Salva immediatamente quando cambia
            if (isPlaying) {
                stopAutoScroll()
                startAutoScroll()
            }
        }
        
        textSizeSlider.addOnChangeListener { _, value, _ ->
            currentTextSize = value
            textView.textSize = value
            saveSettings() // Salva immediatamente quando cambia
        }
    }
    
    private fun showTextInputDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_text_input, null)
        val textInputEditText = dialogView.findViewById<TextInputEditText>(R.id.textInputEditText)
        
        textInputEditText.setText(textView.text.toString())
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.load_text))
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val text = textInputEditText.text?.toString() ?: ""
                if (text.isNotEmpty()) {
                    textView.text = text
                    savedText = text // Salva il testo inserito
                    savedTextExtension = "txt" // Testo normale
                    currentFileUri = null // Testo manuale, nessun file
                    clearCurrentFile() // Rimuovi il file corrente
                    sharedPreferences.edit()
                        .putString("savedText", text)
                        .putString("savedTextExtension", "txt")
                        .apply()
                    scrollView.scrollTo(0, 0)
                } else {
                    Toast.makeText(this, getString(R.string.no_text_loaded), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .create()
        
        dialog.show()
    }
    
    private fun checkPermissionAndOpenFilePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ non richiede permessi espliciti per il file picker del sistema
            openFilePicker()
        } else {
            // Android 12 e precedenti
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openFilePicker()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
    
    private fun openFilePicker() {
        filePickerLauncher.launch("*/*")
    }
    
    private fun loadFileContent(uri: android.net.Uri) {
        // Mostra dialog di caricamento
        val progressView = layoutInflater.inflate(R.layout.dialog_progress, null)
        progressDialog = MaterialAlertDialogBuilder(this)
            .setView(progressView)
            .setCancelable(false)
            .create()
        progressDialog?.show()
        
        // Esegui il caricamento in un thread separato
        Thread {
            try {
                val extension = FileUtils.getFileExtension(this@MainActivity, uri)
                val text = FileUtils.readTextFile(this@MainActivity, uri, extension)
                
                runOnUiThread {
                    progressDialog?.dismiss()
                    
                    if (!text.isNullOrEmpty()) {
                        savedText = text // Salva il testo caricato
                        savedTextExtension = extension // Salva l'estensione per il ripristino
                        currentFileUri = uri.toString() // Salva l'URI del file corrente
                        
                        // Salva il file corrente e aggiungi alla cronologia
                        saveCurrentFile(uri.toString(), extension)
                        addToFileHistory(uri.toString(), extension)
                        
                        // Salva anche il testo per il ripristino
                        savedText = text
                        savedTextExtension = extension
                        sharedPreferences.edit()
                            .putString("savedText", text)
                            .putString("savedTextExtension", extension)
                            .apply()
                        
                        // Se è un file Markdown, formatta con il parser Markdown
                        if (extension.lowercase() == "md") {
                            textView.text = MarkdownFormatter.formatMarkdownSimple(text)
                        } else {
                            textView.text = text
                        }
                        
                        scrollView.scrollTo(0, 0)
                        Toast.makeText(this@MainActivity, getString(R.string.file_imported), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.no_text_loaded), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressDialog?.dismiss()
                    Toast.makeText(this@MainActivity, getString(R.string.error_loading_file), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun togglePlayPause() {
        if (isPlaying) {
            stopAutoScroll()
        } else {
            if (textView.text.isNotEmpty() && textView.text != getString(R.string.enter_text)) {
                startAutoScroll()
            } else {
                Toast.makeText(this, getString(R.string.no_text_loaded), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startAutoScroll() {
        isPlaying = true
        btnPlayPause.text = getString(R.string.pause)
        btnPlayPause.setIconResource(android.R.drawable.ic_media_pause)
        
        // Nascondi automaticamente i controlli e la toolbar quando inizia lo scorrimento
        if (controlsLayout.visibility == android.view.View.VISIBLE) {
            controlsLayout.visibility = android.view.View.GONE
        }
        hideToolbar()
        
        scrollRunnable = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    val scrollAmount = (currentSpeed * 2).toInt()
                    scrollView.smoothScrollBy(0, scrollAmount)
                    
                    // Controlla se abbiamo raggiunto la fine
                    val maxScroll = scrollView.getChildAt(0).height - scrollView.height
                    if (scrollView.scrollY >= maxScroll) {
                        stopAutoScroll()
                    } else {
                        scrollHandler?.postDelayed(this, 50)
                    }
                }
            }
        }
        scrollHandler?.post(scrollRunnable!!)
    }
    
    private fun stopAutoScroll() {
        isPlaying = false
        btnPlayPause.text = getString(R.string.play)
        btnPlayPause.setIconResource(android.R.drawable.ic_media_play)
        scrollRunnable?.let { scrollHandler?.removeCallbacks(it) }
        
        // Mostra la toolbar quando si ferma lo scorrimento
        showToolbar()
    }
    
    private fun hideToolbar() {
        supportActionBar?.hide()
        isToolbarVisible = false
        toolbarHideRunnable?.let { toolbarHideHandler?.removeCallbacks(it) }
    }
    
    private fun showToolbar() {
        supportActionBar?.show()
        isToolbarVisible = true
    }
    
    private fun toggleToolbarVisibility() {
        if (isToolbarVisible) {
            hideToolbar()
        } else {
            showToolbar()
            // Nascondi automaticamente dopo 3 secondi se lo scorrimento è attivo
            if (isPlaying) {
                toolbarHideRunnable = Runnable {
                    if (isPlaying) {
                        hideToolbar()
                    }
                }
                toolbarHideHandler?.postDelayed(toolbarHideRunnable!!, 3000)
            }
        }
    }
    
    private fun toggleTheme() {
        // Non serve salvare di nuovo il testo: è già salvato quando viene caricato
        // Il testo verrà ripristinato automaticamente in onCreate dopo la ricreazione
        
        isDarkMode = !isDarkMode
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    
    private fun toggleControlsVisibility() {
        if (controlsLayout.visibility == android.view.View.VISIBLE) {
            controlsLayout.visibility = android.view.View.GONE
        } else {
            controlsLayout.visibility = android.view.View.VISIBLE
        }
    }
    
    private fun showScrollModeDialog() {
        val modes = arrayOf(
            getString(R.string.page_full),
            getString(R.string.page_half),
            getString(R.string.page_lines)
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.scroll_mode))
            .setSingleChoiceItems(modes, scrollMode) { dialog, which ->
                scrollMode = which
                updateScrollModeButton()
                dialog.dismiss()
            }
            .show()
    }
    
    private fun updateScrollModeButton() {
        val modeText = when (scrollMode) {
            0 -> getString(R.string.page_full)
            1 -> getString(R.string.page_half)
            2 -> getString(R.string.page_lines)
            else -> getString(R.string.page_full)
        }
        btnScrollMode.text = modeText
        saveSettings()
    }
    
    private fun scrollByMode(direction: Int) {
        val viewHeight = scrollView.height
        val lineHeight = textView.lineHeight
        
        val scrollAmount = when (scrollMode) {
            0 -> { // Pagina intera (quasi tutta l'altezza visibile, lasciando una riga per il contesto)
                // Usa circa il 90% dell'altezza per assicurarsi che sia chiaramente diverso da metà pagina
                (viewHeight * 0.9f).toInt() * direction
            }
            1 -> { // Metà pagina
                (viewHeight / 2) * direction
            }
            2 -> { // 3 righe
                (lineHeight * 3) * direction
            }
            else -> 100 * direction
        }
        
        scrollView.smoothScrollBy(0, scrollAmount)
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Il layout si adatta automaticamente grazie a match_parent
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Se stiamo catturando un tasto per la personalizzazione, intercetta TUTTI i tasti
        if (isCapturingKey && keyCaptureAction != null && event?.action == KeyEvent.ACTION_DOWN) {
            // Ignora alcuni tasti speciali durante la cattura
            if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_MENU) {
                capturedKeyCode = keyCode
                capturedIsDoubleClick = false
                finishKeyCapture()
                return true
            }
        }
        
        if (event?.action == KeyEvent.ACTION_DOWN) {
            // Cerca una mappatura personalizzata
            val mapping = findRemoteMapping(keyCode, false)
            if (mapping != null) {
                executeRemoteAction(mapping.action)
                return true
            }
            
            // Fallback al comportamento predefinito se non c'è mappatura personalizzata
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    scrollByMode(-1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    scrollByMode(1)
                    return true
                }
                KeyEvent.KEYCODE_TAB -> {
                    handleTabKey()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    handleVolumeKey(KeyEvent.KEYCODE_VOLUME_UP, 1)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    handleVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN, -1)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        // Intercetta i tasti anche durante la cattura usando dispatchKeyEvent
        // Questo viene chiamato PRIMA di onKeyDown, quindi è più affidabile per la cattura
        if (isCapturingKey && keyCaptureAction != null && event?.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            // Ignora alcuni tasti speciali durante la cattura
            if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_MENU && 
                keyCode != KeyEvent.KEYCODE_HOME) {
                capturedKeyCode = keyCode
                capturedIsDoubleClick = false
                finishKeyCapture()
                return true // Intercetta il tasto
            }
        }
        return super.dispatchKeyEvent(event)
    }
    
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                stopVolumeKeyRepeat()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            loadFileContent(intent.data!!)
        }
    }
    
    private fun handleVolumeKey(keyCode: Int, direction: Int) {
        val currentTime = System.currentTimeMillis()
        
        // Controlla se è un doppio clic
        if (keyCode == lastVolumeKeyCode && 
            (currentTime - lastVolumeKeyPressTime) < DOUBLE_CLICK_TIME_DELTA) {
            // Doppio clic: cerca mappatura personalizzata
            val mapping = findRemoteMapping(keyCode, true)
            if (mapping != null) {
                executeRemoteAction(mapping.action)
            } else {
                // Fallback: cambia dimensione testo
                adjustTextSize(direction)
            }
            lastVolumeKeyPressTime = 0 // Reset per evitare triple clic
            return
        }
        
        // Singolo clic: cerca mappatura personalizzata
        val mapping = findRemoteMapping(keyCode, false)
        if (mapping != null) {
            executeRemoteAction(mapping.action)
        } else {
            // Fallback: cambia velocità
            lastVolumeKeyPressTime = currentTime
            lastVolumeKeyCode = keyCode
            startVolumeKeyRepeat(direction)
        }
    }
    
    private fun handleTabKey() {
        val currentTime = System.currentTimeMillis()
        
        // Controlla se è un doppio clic
        if ((currentTime - lastTabPressTime) < DOUBLE_CLICK_TIME_DELTA) {
            // Doppio clic: cerca mappatura personalizzata
            val mapping = findRemoteMapping(KeyEvent.KEYCODE_TAB, true)
            if (mapping != null) {
                executeRemoteAction(mapping.action)
            } else {
                // Fallback: cambia modalità di scorrimento
                scrollMode = (scrollMode + 1) % 3 // Cicla tra 0, 1, 2
                updateScrollModeButton()
            }
            lastTabPressTime = 0 // Reset per evitare triple clic
            return
        }
        
        // Singolo clic: cerca mappatura personalizzata
        val mapping = findRemoteMapping(KeyEvent.KEYCODE_TAB, false)
        if (mapping != null) {
            executeRemoteAction(mapping.action)
        } else {
            // Fallback: play/pause
            togglePlayPause()
        }
        lastTabPressTime = currentTime
    }
    
    private fun findRemoteMapping(keyCode: Int, isDoubleClick: Boolean): RemoteKeyMapping? {
        return remoteMappings.find { it.keyCode == keyCode && it.isDoubleClick == isDoubleClick }
    }
    
    private fun executeRemoteAction(action: RemoteAction) {
        when (action) {
            RemoteAction.SCROLL_UP -> scrollByMode(-1)
            RemoteAction.SCROLL_DOWN -> scrollByMode(1)
            RemoteAction.PLAY_PAUSE -> togglePlayPause()
            RemoteAction.CHANGE_SCROLL_MODE -> {
                scrollMode = (scrollMode + 1) % 3
                updateScrollModeButton()
            }
            RemoteAction.INCREASE_SPEED -> adjustSpeed(1)
            RemoteAction.DECREASE_SPEED -> adjustSpeed(-1)
            RemoteAction.INCREASE_TEXT_SIZE -> adjustTextSize(1)
            RemoteAction.DECREASE_TEXT_SIZE -> adjustTextSize(-1)
        }
    }
    
    private fun adjustTextSize(direction: Int) {
        val newSize = (currentTextSize + (direction * 2)).coerceIn(
            textSizeSlider.valueFrom, 
            textSizeSlider.valueTo
        )
        if (newSize != currentTextSize) {
            currentTextSize = newSize
            textSizeSlider.value = currentTextSize
            textView.textSize = currentTextSize
            saveSettings() // Salva quando cambia tramite telecomando
        }
    }
    
    private fun startVolumeKeyRepeat(direction: Int) {
        volumeKeyDirection = direction
        isVolumeKeyPressed = true
        
        // Prima modifica immediata
        adjustSpeed(direction)
        
        // Poi continua ogni secondo
        volumeKeyRunnable = object : Runnable {
            override fun run() {
                if (isVolumeKeyPressed) {
                    adjustSpeed(volumeKeyDirection)
                    volumeKeyHandler?.postDelayed(this, 1000) // Ogni secondo
                }
            }
        }
        volumeKeyHandler?.postDelayed(volumeKeyRunnable!!, 1000)
    }
    
    private fun stopVolumeKeyRepeat() {
        isVolumeKeyPressed = false
        volumeKeyRunnable?.let { volumeKeyHandler?.removeCallbacks(it) }
    }
    
    private fun adjustSpeed(direction: Int) {
        val newSpeed = (currentSpeed + direction).coerceIn(speedSlider.valueFrom, speedSlider.valueTo)
        if (newSpeed != currentSpeed) {
            currentSpeed = newSpeed
            speedSlider.value = currentSpeed
            saveSettings() // Salva quando cambia tramite telecomando
            
            // Se lo scorrimento è attivo, riavvialo con la nuova velocità
            if (isPlaying) {
                stopAutoScroll()
                startAutoScroll()
            }
        }
    }
    
    private fun showFileMenu() {
        val options = arrayOf(
            getString(R.string.open_file),
            getString(R.string.load_text_manual),
            getString(R.string.recent_files)
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.file_menu))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkPermissionAndOpenFilePicker()
                    1 -> showTextInputDialog()
                    2 -> showRecentFilesDialog()
                }
            }
            .show()
    }
    
    private fun showRecentFilesDialog() {
        val fileHistory = getFileHistory()
        
        if (fileHistory.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.recent_files))
                .setMessage(getString(R.string.no_recent_files))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
            return
        }
        
        // Crea una lista personalizzata per mostrare nome e percorso
        val items = fileHistory.map { entry ->
            val uri = Uri.parse(entry.first)
            val fileName = getFileNameFromUri(uri)
            // Ottieni il percorso in modo più leggibile
            val filePath = try {
                val displayName = getFileDisplayName(uri)
                if (displayName.length > 50) {
                    "..." + displayName.takeLast(47)
                } else {
                    displayName
                }
            } catch (e: Exception) {
                uri.toString()
            }
            Pair(fileName, filePath)
        }
        
        // Crea un adapter personalizzato per mostrare nome e percorso su righe separate
        val adapter = object : android.widget.ArrayAdapter<Pair<String, String>>(
            this,
            android.R.layout.simple_list_item_2,
            android.R.id.text1,
            items
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val item = items[position]
                
                val text1 = view.findViewById<android.widget.TextView>(android.R.id.text1)
                val text2 = view.findViewById<android.widget.TextView>(android.R.id.text2)
                
                text1.text = item.first
                text2.text = item.second
                text2.textSize = 12f
                text2.setTextColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
                
                return view
            }
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.recent_files))
            .setAdapter(adapter) { _, which ->
                val selectedUri = Uri.parse(fileHistory[which].first)
                loadFileContent(selectedUri)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .setNeutralButton(getString(R.string.clear_recent_files)) { _, _ ->
                showClearRecentFilesDialog()
            }
            .show()
    }
    
    private fun getFileNameFromUri(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.let {
                val cut = it.lastIndexOf('/')
                if (cut != -1) {
                    it.substring(cut + 1)
                } else {
                    it
                }
            } ?: uri.toString()
        }
        return result ?: "File sconosciuto"
    }
    
    private fun getFileDisplayName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path ?: uri.toString()
        }
        return result ?: uri.toString()
    }
    
    private fun showClearRecentFilesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.clear_recent_files))
            .setMessage(getString(R.string.clear_recent_confirm))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                clearFileHistory()
                Toast.makeText(this, getString(R.string.save), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun clearFileHistory() {
        sharedPreferences.edit()
            .putString("fileHistory", "")
            .apply()
    }
    
    private fun showSettingsMenu() {
        val options = arrayOf(
            getString(R.string.remote_settings),
            getString(R.string.font_settings),
            getString(R.string.credits)
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.settings))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRemoteSettingsDialog()
                    1 -> showFontSettingsDialog()
                    2 -> showCreditsDialog()
                }
            }
            .show()
    }
    
    private fun showRemoteSettingsDialog() {
        // Apri direttamente la personalizzazione
        showCustomizeRemoteDialog()
    }
    
    private fun showCustomizeRemoteDialog() {
        val actions = RemoteAction.values()
        val actionNames = actions.map { action ->
            val currentMapping = remoteMappings.find { it.action == action }
            val currentKey = if (currentMapping != null) {
                val keyName = when (currentMapping.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> getString(R.string.key_dpad_up)
                    KeyEvent.KEYCODE_DPAD_DOWN -> getString(R.string.key_dpad_down)
                    KeyEvent.KEYCODE_TAB -> getString(R.string.key_tab)
                    KeyEvent.KEYCODE_VOLUME_UP -> getString(R.string.key_volume_up)
                    KeyEvent.KEYCODE_VOLUME_DOWN -> getString(R.string.key_volume_down)
                    else -> "Key ${currentMapping.keyCode}"
                }
                val clickType = if (currentMapping.isDoubleClick) getString(R.string.key_double_click) else getString(R.string.key_single_click)
                " ($keyName - $clickType)"
            } else {
                ""
            }
            
            when (action) {
                RemoteAction.SCROLL_UP -> getString(R.string.action_scroll_up) + currentKey
                RemoteAction.SCROLL_DOWN -> getString(R.string.action_scroll_down) + currentKey
                RemoteAction.PLAY_PAUSE -> getString(R.string.action_play_pause) + currentKey
                RemoteAction.CHANGE_SCROLL_MODE -> getString(R.string.action_change_scroll_mode) + currentKey
                RemoteAction.INCREASE_SPEED -> getString(R.string.action_increase_speed) + currentKey
                RemoteAction.DECREASE_SPEED -> getString(R.string.action_decrease_speed) + currentKey
                RemoteAction.INCREASE_TEXT_SIZE -> getString(R.string.action_increase_text_size) + currentKey
                RemoteAction.DECREASE_TEXT_SIZE -> getString(R.string.action_decrease_text_size) + currentKey
            }
        }.toMutableList()
        
        // Aggiungi opzione per ripristinare le impostazioni predefinite
        actionNames.add(getString(R.string.restore_defaults))
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.customize_remote))
            .setItems(actionNames.toTypedArray()) { _, which ->
                if (which < actions.size) {
                    // Seleziona un'azione da personalizzare
                    startKeyCapture(actions[which])
                } else {
                    // Ripristina impostazioni predefinite
                    showRestoreDefaultsDialog()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun startKeyCapture(action: RemoteAction) {
        keyCaptureAction = action
        isCapturingKey = true
        keyCaptureTimer = 10
        capturedKeyCode = null
        
        val actionName = when (action) {
            RemoteAction.SCROLL_UP -> getString(R.string.action_scroll_up)
            RemoteAction.SCROLL_DOWN -> getString(R.string.action_scroll_down)
            RemoteAction.PLAY_PAUSE -> getString(R.string.action_play_pause)
            RemoteAction.CHANGE_SCROLL_MODE -> getString(R.string.action_change_scroll_mode)
            RemoteAction.INCREASE_SPEED -> getString(R.string.action_increase_speed)
            RemoteAction.DECREASE_SPEED -> getString(R.string.action_decrease_speed)
            RemoteAction.INCREASE_TEXT_SIZE -> getString(R.string.action_increase_text_size)
            RemoteAction.DECREASE_TEXT_SIZE -> getString(R.string.action_decrease_text_size)
        }
        
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
        val textView = dialogView.findViewById<android.widget.TextView>(android.R.id.text1)
        textView.text = getString(R.string.press_key_for_action, actionName)
        textView.textSize = 18f
        textView.gravity = android.view.Gravity.CENTER
        textView.setPadding(32, 32, 32, 32)
        
        val timerView = android.widget.TextView(this)
        timerView.text = getString(R.string.timeout_in, keyCaptureTimer)
        timerView.textSize = 16f
        timerView.gravity = android.view.Gravity.CENTER
        timerView.setPadding(32, 16, 32, 32)
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            addView(textView)
            addView(timerView)
        }
        
        keyCaptureDialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.customize_remote))
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                cancelKeyCapture()
            }
            .create()
        
        // Imposta un listener per intercettare i tasti nel dialog
        keyCaptureDialog?.setOnKeyListener { _, keyCode, event ->
            if (isCapturingKey && keyCaptureAction != null && event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_MENU) {
                    capturedKeyCode = keyCode
                    capturedIsDoubleClick = false
                    finishKeyCapture()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        
        keyCaptureDialog?.show()
        
        // Avvia il timer
        keyCaptureRunnable = object : Runnable {
            override fun run() {
                if (isCapturingKey && keyCaptureTimer > 0) {
                    timerView.text = getString(R.string.timeout_in, keyCaptureTimer)
                    keyCaptureTimer--
                    keyCaptureHandler?.postDelayed(this, 1000)
                } else if (isCapturingKey) {
                    // Timeout scaduto
                    keyCaptureDialog?.dismiss()
                    Toast.makeText(this@MainActivity, getString(R.string.timeout_expired), Toast.LENGTH_SHORT).show()
                    isCapturingKey = false
                    keyCaptureAction = null
                }
            }
        }
        keyCaptureHandler?.post(keyCaptureRunnable!!)
    }
    
    private fun finishKeyCapture() {
        if (!isCapturingKey || keyCaptureAction == null || capturedKeyCode == null) {
            return
        }
        
        isCapturingKey = false
        keyCaptureHandler?.removeCallbacks(keyCaptureRunnable!!)
        keyCaptureDialog?.dismiss()
        
        // Rimuovi mappature esistenti per questa azione
        remoteMappings.removeAll { it.action == keyCaptureAction }
        
        // Aggiungi nuova mappatura
        remoteMappings.add(RemoteKeyMapping(capturedKeyCode!!, capturedIsDoubleClick, keyCaptureAction!!))
        saveRemoteMappings()
        
        val keyName = when (capturedKeyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> getString(R.string.key_dpad_up)
            KeyEvent.KEYCODE_DPAD_DOWN -> getString(R.string.key_dpad_down)
            KeyEvent.KEYCODE_TAB -> getString(R.string.key_tab)
            KeyEvent.KEYCODE_VOLUME_UP -> getString(R.string.key_volume_up)
            KeyEvent.KEYCODE_VOLUME_DOWN -> getString(R.string.key_volume_down)
            else -> "Key $capturedKeyCode"
        }
        
        Toast.makeText(this, getString(R.string.key_captured, keyName), Toast.LENGTH_SHORT).show()
        
        keyCaptureAction = null
        capturedKeyCode = null
    }
    
    private fun cancelKeyCapture() {
        isCapturingKey = false
        keyCaptureHandler?.removeCallbacks(keyCaptureRunnable!!)
        keyCaptureAction = null
        capturedKeyCode = null
    }
    
    private fun showRestoreDefaultsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.restore_defaults))
            .setMessage(getString(R.string.restore_confirm))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                restoreDefaultRemoteMappings()
                Toast.makeText(this, getString(R.string.save), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun restoreDefaultRemoteMappings() {
        remoteMappings = mutableListOf(
            RemoteKeyMapping(KeyEvent.KEYCODE_DPAD_UP, false, RemoteAction.SCROLL_UP),
            RemoteKeyMapping(KeyEvent.KEYCODE_DPAD_DOWN, false, RemoteAction.SCROLL_DOWN),
            RemoteKeyMapping(KeyEvent.KEYCODE_TAB, false, RemoteAction.PLAY_PAUSE),
            RemoteKeyMapping(KeyEvent.KEYCODE_TAB, true, RemoteAction.CHANGE_SCROLL_MODE),
            RemoteKeyMapping(KeyEvent.KEYCODE_VOLUME_UP, false, RemoteAction.INCREASE_SPEED),
            RemoteKeyMapping(KeyEvent.KEYCODE_VOLUME_DOWN, false, RemoteAction.DECREASE_SPEED),
            RemoteKeyMapping(KeyEvent.KEYCODE_VOLUME_UP, true, RemoteAction.INCREASE_TEXT_SIZE),
            RemoteKeyMapping(KeyEvent.KEYCODE_VOLUME_DOWN, true, RemoteAction.DECREASE_TEXT_SIZE)
        )
        saveRemoteMappings()
    }
    
    private fun showRemoteInfoDialog() {
        val remoteControls = """
            • Frecce Su/Giù: Scorrimento manuale
            • Tab (singolo): Play/Pause
            • Tab (doppio): Cambia modalità scorrimento
            • Volume Su (singolo): Aumenta velocità
            • Volume Giù (singolo): Diminuisce velocità
            • Volume Su (doppio): Aumenta dimensione testo
            • Volume Giù (doppio): Diminuisce dimensione testo
        """.trimIndent()
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.remote_settings))
            .setMessage(remoteControls)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }
    
    private fun showFontSettingsDialog() {
        val fonts = arrayOf(
            getString(R.string.font_default),
            getString(R.string.font_serif),
            getString(R.string.font_sans_serif),
            getString(R.string.font_monospace)
        )
        
        val currentIndex = when (currentFont) {
            "default" -> 0
            "serif" -> 1
            "sans_serif" -> 2
            "monospace" -> 3
            else -> 0
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_font))
            .setSingleChoiceItems(fonts, currentIndex) { dialog, which ->
                currentFont = when (which) {
                    0 -> "default"
                    1 -> "serif"
                    2 -> "sans_serif"
                    3 -> "monospace"
                    else -> "default"
                }
                applyFont()
                saveSettings()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun applyFont() {
        val typeface = when (currentFont) {
            "serif" -> Typeface.SERIF
            "sans_serif" -> Typeface.SANS_SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
        textView.typeface = typeface
    }
    
    private fun saveSettings() {
        sharedPreferences.edit()
            .putFloat("currentSpeed", currentSpeed)
            .putFloat("currentTextSize", currentTextSize)
            .putInt("scrollMode", scrollMode)
            .putString("currentFont", currentFont)
            .apply()
    }
    
    private fun loadSettings() {
        // Carica velocità e dimensione testo
        currentSpeed = sharedPreferences.getFloat("currentSpeed", 5f)
        currentTextSize = sharedPreferences.getFloat("currentTextSize", 24f)
        scrollMode = sharedPreferences.getInt("scrollMode", 0)
        currentFont = sharedPreferences.getString("currentFont", "default") ?: "default"
        
        // Applica i valori agli slider
        speedSlider.value = currentSpeed
        textSizeSlider.value = currentTextSize
        textView.textSize = currentTextSize
        
        // Carica mappature remote
        loadRemoteMappings()
    }
    
    private fun saveRemoteMappings() {
        val mappingsJson = remoteMappings.joinToString("|") { mapping ->
            "${mapping.keyCode}:${mapping.isDoubleClick}:${mapping.action.ordinal}"
        }
        sharedPreferences.edit()
            .putString("remoteMappings", mappingsJson)
            .apply()
    }
    
    private fun loadRemoteMappings() {
        val mappingsJson = sharedPreferences.getString("remoteMappings", null)
        if (mappingsJson.isNullOrEmpty()) {
            // Imposta mappature di default
            restoreDefaultRemoteMappings()
        } else {
            remoteMappings = mappingsJson.split("|").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 3) {
                    try {
                        val keyCode = parts[0].toInt()
                        val isDoubleClick = parts[1].toBoolean()
                        val actionOrdinal = parts[2].toInt()
                        if (actionOrdinal in RemoteAction.values().indices) {
                            RemoteKeyMapping(keyCode, isDoubleClick, RemoteAction.values()[actionOrdinal])
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }.toMutableList()
        }
    }
    
    private fun showCreditsDialog() {
        val creditsText = getString(R.string.credits_text)
        val textView = android.widget.TextView(this).apply {
            text = creditsText
            setPadding(32, 16, 32, 16)
            textSize = 14f
            Linkify.addLinks(this, Linkify.WEB_URLS)
            movementMethod = LinkMovementMethod.getInstance()
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.credits))
            .setView(textView)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }
    
    private fun saveCurrentFile(uri: String, extension: String) {
        sharedPreferences.edit()
            .putString("currentFileUri", uri)
            .putString("currentFileExtension", extension)
            .apply()
    }
    
    private fun clearCurrentFile() {
        sharedPreferences.edit()
            .remove("currentFileUri")
            .remove("currentFileExtension")
            .apply()
    }
    
    private fun restoreLastFile() {
        // Prima prova a ripristinare il testo salvato direttamente
        val savedTextValue = sharedPreferences.getString("savedText", null)
        val savedExtensionValue = sharedPreferences.getString("savedTextExtension", null)
        
        if (savedTextValue != null && savedTextValue.isNotEmpty() && savedExtensionValue != null) {
            savedText = savedTextValue
            savedTextExtension = savedExtensionValue
            if (savedExtensionValue.lowercase() == "md") {
                textView.text = MarkdownFormatter.formatMarkdownSimple(savedTextValue)
            } else {
                textView.text = savedTextValue
            }
            return
        }
        
        // Se non c'è testo salvato, prova a caricare il file
        val savedUri = sharedPreferences.getString("currentFileUri", null)
        val savedExtension = sharedPreferences.getString("currentFileExtension", null)
        
        if (savedUri != null && savedExtension != null) {
            try {
                val uri = Uri.parse(savedUri)
                // Prova a caricare il file salvato
                loadFileContent(uri)
            } catch (e: Exception) {
                // Se il file non è più disponibile, ignora
                e.printStackTrace()
            }
        }
    }
    
    private fun addToFileHistory(uri: String, extension: String) {
        val MAX_HISTORY_SIZE = 10
        val history = getFileHistory().toMutableList()
        
        // Rimuovi se già presente
        history.removeAll { it.first == uri }
        
        // Aggiungi all'inizio
        history.add(0, Pair(uri, extension))
        
        // Mantieni solo gli ultimi MAX_HISTORY_SIZE file
        val limitedHistory = history.take(MAX_HISTORY_SIZE)
        
        // Salva la cronologia
        val historyJson = limitedHistory.joinToString("|") { "${it.first}::${it.second}" }
        sharedPreferences.edit()
            .putString("fileHistory", historyJson)
            .apply()
    }
    
    private fun getFileHistory(): List<Pair<String, String>> {
        val historyJson = sharedPreferences.getString("fileHistory", "") ?: ""
        if (historyJson.isEmpty()) return emptyList()
        
        return historyJson.split("|")
            .mapNotNull { entry ->
                val parts = entry.split("::")
                if (parts.size == 2) {
                    Pair(parts[0], parts[1])
                } else {
                    null
                }
            }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAutoScroll()
        stopVolumeKeyRepeat()
        cancelKeyCapture()
        scrollHandler?.removeCallbacksAndMessages(null)
        volumeKeyHandler?.removeCallbacksAndMessages(null)
        toolbarHideHandler?.removeCallbacksAndMessages(null)
        keyCaptureHandler?.removeCallbacksAndMessages(null)
        progressDialog?.dismiss()
        keyCaptureDialog?.dismiss()
    }
}
