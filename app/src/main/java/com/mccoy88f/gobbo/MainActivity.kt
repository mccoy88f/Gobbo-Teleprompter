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
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Environment
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.io.File

class MainActivity : AppCompatActivity(), WebRemoteController {
    
    private lateinit var scrollView: NestedScrollView
    private lateinit var textView: android.widget.TextView
    private lateinit var btnPlayPause: MaterialButton
    private lateinit var btnScrollMode: MaterialButton
    private lateinit var speedSlider: Slider
    private lateinit var textSizeSlider: Slider
    private lateinit var controlsLayout: android.widget.LinearLayout
    private lateinit var wpmText: android.widget.TextView
    private lateinit var btnSetWpm: MaterialButton
    
    private var isPlaying = false
    private var scrollHandler: Handler? = null
    private var scrollRunnable: Runnable? = null
    private var targetWpm = 120
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
    private var currentImportedFileId: String? = null
    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    
    
    // Remote control mappings
    private enum class RemoteAction {
        SCROLL_UP, SCROLL_DOWN, PLAY_PAUSE, CHANGE_SCROLL_MODE,
        INCREASE_SPEED, DECREASE_SPEED, INCREASE_TEXT_SIZE, DECREASE_TEXT_SIZE
    }
    
    private data class RemoteKeyMapping(
        val keyCode: Int,
        val action: RemoteAction
    )
    
    private var remoteMappings = mutableListOf<RemoteKeyMapping>()
    private var currentFont = "default" // default, serif, sans_serif, monospace
    
    private var webServer: WebServer? = null
    private var webRemoteEnabled = false
    private var webRemotePort = 8080
    private var webRemoteDeviceName = ""
    private var webRemotePinHash = "" // SHA-256 hex; vuoto = nessun PIN
    
    private enum class WebServerIndicatorState { STARTING, ACTIVE, NO_NETWORK }
    private var webServerIndicatorState = WebServerIndicatorState.ACTIVE
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
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
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            loadFileContent(it)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inizializza SharedPreferences PRIMA di setContentView
        sharedPreferences = getDefaultSharedPreferences(this)
        
        // Carica solo isDarkMode PRIMA di setContentView per applicare il tema correttamente
        isDarkMode = sharedPreferences.getBoolean("isDarkMode", false)
        // Applica il tema salvato solo se necessario (evita ricreazioni inutili)
        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        val targetNightMode = if (isDarkMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        // Applica il tema solo se è diverso da quello corrente
        if (currentNightMode != targetNightMode) {
            AppCompatDelegate.setDefaultNightMode(targetNightMode)
        }
        
        setContentView(R.layout.activity_main)
        
        // Configura la toolbar standard con i pulsanti
        supportActionBar?.let {
            it.setDisplayShowTitleEnabled(true)
            it.title = getString(R.string.app_name)
            // Forza la visibilità della toolbar
            it.show()
        }
        
        initViews()
        setupListeners()
        
        // Ripristina lo stato salvato
        if (savedInstanceState != null) {
            savedText = savedInstanceState.getString("savedText")
            savedTextExtension = savedInstanceState.getString("savedTextExtension")
            // Ripristina il testo se salvato
            savedText?.let { text ->
                if (text.isNotEmpty() && text != getString(R.string.empty_state_hint)) {
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
        
        // Assicurati che la toolbar sia sempre visibile dopo la ricreazione (es. cambio tema)
        // A meno che non sia in modalità di riproduzione
        // Mostra la toolbar immediatamente e anche dopo diversi delay per sicurezza
        // Questo è necessario perché l'Activity può essere ricreata più volte all'avvio
        if (!isPlaying) {
            showToolbar()
        }
        // Primo delay per la prima ricreazione
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isPlaying) {
                showToolbar()
            }
        }, 200)
        // Secondo delay per eventuali ricreazioni successive
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isPlaying) {
                showToolbar()
            }
        }, 500)
    }
    
    override fun onResume() {
        super.onResume()
        if (webRemoteEnabled) {
            startWebServerWithIndicator()
            registerNetworkCallback()
        }
        // Assicurati che la toolbar sia sempre visibile quando l'Activity riprende
        if (!isPlaying) {
            showToolbar()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isPlaying) {
                showToolbar()
            }
        }, 100)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isPlaying) {
                showToolbar()
            }
        }, 300)
    }
    
    override fun onPause() {
        super.onPause()
        unregisterNetworkCallback()
        stopWebServer()
    }
    
    private fun updateWebServerIndicatorFromNetwork() {
        if (!webRemoteEnabled || webServerIndicatorState == WebServerIndicatorState.STARTING) return
        val hasIp = getLocalIpAddress() != null
        val newState = if (hasIp) WebServerIndicatorState.ACTIVE else WebServerIndicatorState.NO_NETWORK
        if (webServerIndicatorState != newState) {
            webServerIndicatorState = newState
            invalidateOptionsMenu()
        }
    }
    
    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = runOnUiThread { updateWebServerIndicatorFromNetwork() }
            override fun onLost(network: Network) = runOnUiThread { updateWebServerIndicatorFromNetwork() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = runOnUiThread { updateWebServerIndicatorFromNetwork() }
        }
        try {
            cm.registerDefaultNetworkCallback(networkCallback!!)
        } catch (e: SecurityException) {
            networkCallback = null
        }
    }
    
    private fun unregisterNetworkCallback() {
        networkCallback?.let { callback ->
            try {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.unregisterNetworkCallback(callback)
            } catch (_: Exception) { }
            networkCallback = null
        }
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
    
    override fun onPrepareOptionsMenu(menu: android.view.Menu?): Boolean {
        super.onPrepareOptionsMenu(menu)
        val item = menu?.findItem(R.id.menu_web_server_indicator) ?: return true
        item.isVisible = webRemoteEnabled
        if (webRemoteEnabled) {
            if (webServerIndicatorState == WebServerIndicatorState.ACTIVE && getLocalIpAddress() == null) {
                webServerIndicatorState = WebServerIndicatorState.NO_NETWORK
            }
            val iconRes = when (webServerIndicatorState) {
                WebServerIndicatorState.STARTING -> R.drawable.ic_web_server_orange
                WebServerIndicatorState.ACTIVE -> R.drawable.ic_web_server
                WebServerIndicatorState.NO_NETWORK -> R.drawable.ic_web_server_red
            }
            item.setIcon(iconRes)
        }
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
            R.id.menu_web_server_indicator -> {
                when (webServerIndicatorState) {
                    WebServerIndicatorState.STARTING -> Toast.makeText(this, getString(R.string.web_remote_starting), Toast.LENGTH_SHORT).show()
                    WebServerIndicatorState.ACTIVE -> {
                        val ip = getLocalIpAddress()
                        if (ip != null) {
                            Toast.makeText(this, "http://$ip:$webRemotePort", Toast.LENGTH_LONG).show()
                        } else {
                            webServerIndicatorState = WebServerIndicatorState.NO_NETWORK
                            invalidateOptionsMenu()
                            Toast.makeText(this, getString(R.string.web_remote_no_ip), Toast.LENGTH_LONG).show()
                        }
                    }
                    WebServerIndicatorState.NO_NETWORK -> Toast.makeText(this, getString(R.string.web_remote_no_ip), Toast.LENGTH_LONG).show()
                }
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
        wpmText = findViewById(R.id.wpmText)
        btnSetWpm = findViewById(R.id.btnSetWpm)
        
        // Disabilita la navigazione con Tab per i controlli
        // Questo impedisce che Tab cambi il focus tra i controlli
        btnPlayPause.isFocusable = false
        btnScrollMode.isFocusable = false
        speedSlider.isFocusable = false
        textSizeSlider.isFocusable = false
        btnSetWpm.isFocusable = false
        controlsLayout.isFocusable = false
        controlsLayout.isFocusableInTouchMode = false
        scrollView.isFocusable = false
        
        scrollHandler = Handler(Looper.getMainLooper())
        volumeKeyHandler = Handler(Looper.getMainLooper())
        toolbarHideHandler = Handler(Looper.getMainLooper())
        textView.textSize = currentTextSize
        textView.isClickable = true
        textView.isFocusable = false // Disabilita focus per evitare che Tab cambi il focus
        
        // Ripristina il testo salvato se presente
        savedText?.let { text ->
            if (text.isNotEmpty() && text != getString(R.string.empty_state_hint)) {
                // Se era un file Markdown, riformatta
                if (savedTextExtension?.lowercase() == "md") {
                    textView.text = MarkdownFormatter.formatMarkdownSimple(text)
                } else {
                    textView.text = text
                }
            }
        }
        
        // Imposta stepSize per gli slider
        speedSlider.stepSize = 1f // WPM: valori interi 60-250
        textSizeSlider.stepSize = 2f
        
        // Carica impostazioni salvate (tranne isDarkMode che è già stato caricato)
        loadSettings()
        
        // Inizializza modalità scorrimento
        updateScrollModeButton()
        
        // Applica font
        applyFont()
        
        // Aggiorna WPM dopo che il layout è pronto (altezze disponibili)
        scrollView.post { updateWpmDisplay() }
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
            targetWpm = value.toInt().coerceIn(60, 250)
            saveSettings()
            updateWpmDisplay()
            if (isPlaying) {
                stopAutoScroll()
                startAutoScroll()
            }
        }
        
        btnSetWpm.setOnClickListener { showSetWpmDialog() }
        
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
                    scrollView.post { updateWpmDisplay() }
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
        // Limita ai tipi di file supportati
        val mimeTypes = arrayOf(
            "text/plain",                    // .txt
            "text/markdown",                 // .md
            "application/rtf",               // .rtf
            "text/rtf",                      // .rtf (alternativo)
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/pdf"                // .pdf
        )
        filePickerLauncher.launch(mimeTypes)
    }
    
    private fun loadFileContent(uri: android.net.Uri) {
        // Controlla la dimensione del file prima di caricarlo
        val fileSize = getFileSize(uri)
        val maxFileSize = 50 * 1024 * 1024 // 50MB in bytes
        
        if (fileSize > maxFileSize) {
            // Mostra avviso per file troppo grande
            val fileSizeMB = String.format("%.2f", fileSize / (1024.0 * 1024.0))
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.file_too_large))
                .setMessage(getString(R.string.file_too_large_message, "${fileSizeMB} MB"))
                .setPositiveButton(getString(R.string.continue_anyway)) { _, _ ->
                    // Procedi con il caricamento
                    loadFileContentInternal(uri)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
            return
        }
        
        // Se il file è di dimensioni accettabili, carica direttamente
        loadFileContentInternal(uri)
    }
    
    private fun loadFileContentInternal(uri: android.net.Uri) {
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
                    
                    when {
                        text == null -> Toast.makeText(this@MainActivity, getString(R.string.error_loading_file), Toast.LENGTH_LONG).show()
                        text.isEmpty() -> Toast.makeText(this@MainActivity, getString(R.string.no_text_loaded), Toast.LENGTH_SHORT).show()
                        else -> {
                            val displayName = getFileNameFromUri(uri)
                            val id = saveImportedFile(text, displayName, extension)
                            currentImportedFileId = id
                            currentFileUri = null
                            savedText = text
                            savedTextExtension = extension
                            sharedPreferences.edit()
                                .putString("savedText", text)
                                .putString("savedTextExtension", extension)
                                .putString("currentImportedFileId", id)
                                .remove("currentFileUri")
                                .remove("currentFileExtension")
                                .apply()
                            if (extension.lowercase() == "md") {
                                textView.text = MarkdownFormatter.formatMarkdownSimple(text)
                            } else {
                                textView.text = text
                            }
                            scrollView.scrollTo(0, 0)
                            scrollView.post { updateWpmDisplay() }
                            Toast.makeText(this@MainActivity, getString(R.string.file_imported), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                runOnUiThread {
                    progressDialog?.dismiss()
                    Toast.makeText(this@MainActivity, getString(R.string.error_loading_file), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
    
    private fun togglePlayPause() {
        if (isPlaying) {
            stopAutoScroll()
        } else {
            if (textView.text.isNotEmpty() && textView.text != getString(R.string.empty_state_hint)) {
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
        
        val scrollSpeed = getScrollSpeedFromWpm()
        scrollRunnable = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    val scrollAmount = (scrollSpeed * 2).toInt()
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
        supportActionBar?.let {
            it.show()
            isToolbarVisible = true
        } ?: run {
            // Se supportActionBar è null, proviamo a forzare la creazione
            // Questo può accadere se l'Activity viene ricreata durante il cambio tema
            Handler(Looper.getMainLooper()).postDelayed({
                supportActionBar?.let { actionBar ->
                    actionBar.setDisplayShowTitleEnabled(true)
                    actionBar.title = getString(R.string.app_name)
                    actionBar.show()
                    isToolbarVisible = true
                }
            }, 100)
        }
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
        // Salva lo stato del tema prima della ricreazione
        saveSettings()
        
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
        if (event?.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            // Gestisci Tab PRIMA di tutto, come i tasti volume, per evitare che Android lo usi per la navigazione
            if (keyCode == KeyEvent.KEYCODE_TAB) {
                val mapping = findRemoteMapping(KeyEvent.KEYCODE_TAB)
                // Se la mappatura è CHANGE_SCROLL_MODE (comportamento errato), usa Play/Pause invece
                if (mapping != null && mapping.action != RemoteAction.CHANGE_SCROLL_MODE) {
                    executeRemoteAction(mapping.action)
                } else {
                    // Default: Play/Pause (anche se c'è una mappatura errata)
                    togglePlayPause()
                }
                return true // Consuma l'evento, impedendo la navigazione di default
            }
            
            // Gestisci i tasti volume come Tab
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                val mapping = findRemoteMapping(keyCode)
                if (mapping != null) {
                    executeRemoteAction(mapping.action)
                } else {
                    // Fallback al comportamento predefinito
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        adjustSpeed(1)
                    } else {
                        adjustSpeed(-1)
                    }
                }
                return true // Consuma l'evento, impedendo la modifica del volume di sistema
            }
            
            // Cerca una mappatura personalizzata per gli altri tasti
            val mapping = findRemoteMapping(keyCode)
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
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    adjustTextSize(-1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    adjustTextSize(1)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        // Intercetta Tab e Volume PRIMA che Android li usi per navigazione/volume
        if (event?.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_TAB -> {
                    val mapping = findRemoteMapping(KeyEvent.KEYCODE_TAB)
                    // Se la mappatura è CHANGE_SCROLL_MODE (comportamento errato), usa Play/Pause invece
                    if (mapping != null && mapping.action != RemoteAction.CHANGE_SCROLL_MODE) {
                        executeRemoteAction(mapping.action)
                    } else {
                        // Default: Play/Pause (anche se c'è una mappatura errata)
                        togglePlayPause()
                    }
                    return true // Consuma l'evento, impedendo la navigazione di default
                }
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    val mapping = findRemoteMapping(event.keyCode)
                    if (mapping != null) {
                        executeRemoteAction(mapping.action)
                    } else {
                        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                            adjustSpeed(1)
                        } else {
                            adjustSpeed(-1)
                        }
                    }
                    return true // Consuma l'evento
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
    
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyUp(keyCode, event)
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
    
    private fun findRemoteMapping(keyCode: Int): RemoteKeyMapping? {
        return remoteMappings.find { it.keyCode == keyCode }
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
    
    
    private fun adjustSpeed(direction: Int) {
        val step = 10
        val newWpm = (targetWpm + direction * step).coerceIn(60, 250)
        if (newWpm != targetWpm) {
            targetWpm = newWpm
            speedSlider.value = targetWpm.toFloat()
            saveSettings()
            updateWpmDisplay()
            if (isPlaying) {
                stopAutoScroll()
                startAutoScroll()
            }
        }
    }
    
    /** Conta le parole nel testo corrente (raw text, per calcolo WPM). */
    private fun countWords(): Int {
        val raw = savedText ?: textView.text?.toString().orEmpty()
        if (raw.isEmpty() || raw == getString(R.string.empty_state_hint)) return 0
        return raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
    }
    
    /**
     * Restituisce la velocità di scorrimento (float) in unità slider-equivalenti:
     * scroll = speed * 2 px ogni 50 ms = 40 * speed px/s.
     * Calcolata da targetWpm: speed = targetWpm * totalScrollDistance / (words * 60 * 40).
     * Se non c'è testo o distanza nulla, restituisce un default (5f).
     */
    private fun getScrollSpeedFromWpm(): Float {
        val words = countWords()
        if (words == 0) return 5f
        val contentHeight = scrollView.getChildAt(0)?.height ?: return 5f
        val viewportHeight = scrollView.height
        val totalScrollDistance = (contentHeight - viewportHeight).coerceAtLeast(0)
        if (totalScrollDistance <= 0) return 5f
        return (targetWpm * totalScrollDistance) / (words * 60 * 40f)
    }
    
    /** WPM attuale: con testo caricato coincide con targetWpm (valore impostato dall'utente). */
    private fun getCurrentWpm(): Int? {
        val words = countWords()
        if (words == 0) return null
        val contentHeight = scrollView.getChildAt(0)?.height ?: return null
        val viewportHeight = scrollView.height
        val totalScrollDistance = (contentHeight - viewportHeight).coerceAtLeast(0)
        if (totalScrollDistance <= 0) return null
        return targetWpm
    }
    
    private fun updateWpmDisplay() {
        val wpm = getCurrentWpm()
        wpmText.text = if (wpm != null) getString(R.string.wpm_display, wpm) else getString(R.string.wpm_na)
    }
    
    private fun showSetWpmDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_text_input, null)
        val textInputEditText = dialogView.findViewById<TextInputEditText>(R.id.textInputEditText)
        val textInputLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputLayout)
        textInputLayout?.hint = getString(R.string.target_wpm)
        textInputLayout?.isCounterEnabled = false
        textInputEditText.hint = getString(R.string.wpm_hint)
        textInputEditText.setText(targetWpm.toString())
        textInputEditText.setSelection(textInputEditText.text?.length ?: 0)
        textInputEditText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        textInputEditText.setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER)
        textInputEditText.setLines(1)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.set_target_wpm))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val input = textInputEditText.text?.toString()?.trim() ?: ""
                val wpm = input.toIntOrNull()?.coerceIn(60, 500) ?: return@setPositiveButton
                targetWpm = wpm.coerceIn(60, 250)
                speedSlider.value = targetWpm.toFloat()
                saveSettings()
                updateWpmDisplay()
                if (isPlaying) {
                    stopAutoScroll()
                    startAutoScroll()
                }
                Toast.makeText(this, getString(R.string.wpm_display, targetWpm), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showFileMenu() {
        val options = arrayOf(
            getString(R.string.new_document),
            getString(R.string.import_file),
            getString(R.string.imported_files)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.file_menu))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showNewDocument()
                    1 -> checkPermissionAndOpenFilePicker()
                    2 -> showImportedFilesDialog()
                }
            }
            .show()
    }
    
    private fun showNewDocument() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_document, null)
        val editName = dialogView.findViewById<TextInputEditText>(R.id.newDocName)
        val editContent = dialogView.findViewById<TextInputEditText>(R.id.newDocContent)
        val newDocDialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.new_document_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        newDocDialog.setOnShowListener {
            newDocDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                var name = editName.text?.toString()?.trim() ?: ""
                val content = editContent.text?.toString() ?: ""
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.new_document_name_hint), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!name.lowercase().endsWith(".md")) name += ".md"
                val id = saveImportedFile(content, name, "md")
                currentImportedFileId = id
                savedText = content
                savedTextExtension = "md"
                sharedPreferences.edit()
                    .putString("savedText", content)
                    .putString("savedTextExtension", "md")
                    .putString("currentImportedFileId", id)
                    .remove("currentFileUri")
                    .remove("currentFileExtension")
                    .apply()
                textView.text = MarkdownFormatter.formatMarkdownSimple(content)
                scrollView.scrollTo(0, 0)
                scrollView.post { updateWpmDisplay() }
                Toast.makeText(this, getString(R.string.document_created), Toast.LENGTH_SHORT).show()
                newDocDialog.dismiss()
            }
        }
        newDocDialog.show()
    }
    
    private fun showImportedFilesDialog() {
        val list = getImportedFileList().toMutableList()
        if (list.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.imported_files))
                .setMessage(getString(R.string.no_imported_files))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
            return
        }
        val listView = android.widget.ListView(this)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.imported_files))
            .setView(listView)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = list.size
            override fun getItem(position: Int) = list[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_imported_file, parent, false)
                val item = list[position]
                view.findViewById<android.widget.TextView>(R.id.itemFileName).text = item.second
                view.findViewById<MaterialButton>(R.id.itemDeleteBtn).setOnClickListener {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setMessage(getString(R.string.delete_file_confirm, item.second))
                        .setPositiveButton(getString(R.string.ok)) { _, _ ->
                            removeImportedFile(item.first)
                            list.removeAll { it.first == item.first }
                            notifyDataSetChanged()
                            if (list.isEmpty()) dialog.dismiss()
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
                view.setOnClickListener { openImportedFile(item.first, item.third); dialog.dismiss() }
                return view
            }
        }
        listView.adapter = adapter
        dialog.show()
    }
    
    private fun openImportedFile(id: String, extension: String) {
        val content = loadImportedFileContent(id) ?: run {
            Toast.makeText(this, getString(R.string.error_loading_file), Toast.LENGTH_SHORT).show()
            return
        }
        currentImportedFileId = id
        currentFileUri = null
        savedText = content
        savedTextExtension = extension
        sharedPreferences.edit()
            .putString("savedText", content)
            .putString("savedTextExtension", extension)
            .putString("currentImportedFileId", id)
            .remove("currentFileUri")
            .remove("currentFileExtension")
            .apply()
        if (extension.lowercase() == "md") {
            textView.text = MarkdownFormatter.formatMarkdownSimple(content)
        } else {
            textView.text = content
        }
        scrollView.scrollTo(0, 0)
        scrollView.post { updateWpmDisplay() }
    }
    
    private fun getFileNameFromUri(uri: Uri): String {
        return try {
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
            result ?: "File sconosciuto"
        } catch (e: Exception) {
            e.printStackTrace()
            uri.path?.let {
                val cut = it.lastIndexOf('/')
                if (cut != -1) {
                    it.substring(cut + 1)
                } else {
                    it
                }
            } ?: "File sconosciuto"
        }
    }
    
    private fun getFileDisplayName(uri: Uri): String {
        return try {
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
            result ?: uri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            uri.path ?: uri.toString()
        }
    }
    
    private fun getFileSize(uri: Uri): Long {
        return try {
            when (uri.scheme) {
                "file" -> {
                    // Per URI file://, ottieni la dimensione direttamente
                    val path = uri.path
                    if (path != null) {
                        val file = java.io.File(path)
                        if (file.exists()) {
                            file.length()
                        } else {
                            0L
                        }
                    } else {
                        0L
                    }
                }
                "content" -> {
                    // Per URI content://, usa ContentResolver
                    try {
                        val cursor = contentResolver.query(uri, null, null, null, null)
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (sizeIndex >= 0) {
                                    val size = it.getLong(sizeIndex)
                                    if (size > 0) {
                                        size
                                    } else {
                                        // Se SIZE non è disponibile, restituiamo 0 e procediamo comunque
                                        0L
                                    }
                                } else {
                                    0L
                                }
                            } else {
                                0L
                            }
                        } ?: 0L
                    } catch (e: Exception) {
                        e.printStackTrace()
                        0L
                    }
                }
                else -> 0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }
    
    private fun getFolderPathFromUri(uri: Uri): String {
        return try {
            when (uri.scheme) {
                "file" -> {
                    // Per URI file://, estrai il percorso della cartella
                    val path = uri.path
                    if (path != null) {
                        val file = java.io.File(path)
                        val parent = file.parent
                        if (parent != null) {
                            // Accorcia il percorso se troppo lungo
                            if (parent.length > 50) {
                                "..." + parent.takeLast(47)
                            } else {
                                parent
                            }
                        } else {
                            ""
                        }
                    } else {
                        ""
                    }
                }
                "content" -> {
                    // Per URI content://, prova a ottenere il percorso usando ContentResolver
                    try {
                        val cursor = contentResolver.query(uri, null, null, null, null)
                        cursor?.use {
                            if (it.moveToFirst()) {
                                // Prova a ottenere il percorso completo usando MediaStore.DATA
                                val pathIndex = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                                if (pathIndex >= 0) {
                                    val fullPath = it.getString(pathIndex)
                                    if (fullPath != null && fullPath.isNotEmpty()) {
                                        val file = java.io.File(fullPath)
                                        val parent = file.parent
                                        if (parent != null) {
                                            if (parent.length > 50) {
                                                "..." + parent.takeLast(47)
                                            } else {
                                                parent
                                            }
                                        } else {
                                            ""
                                        }
                                    } else {
                                        ""
                                    }
                                } else {
                                    ""
                                }
                            } else {
                                ""
                            }
                        } ?: ""
                    } catch (e: Exception) {
                        // Se non riesce a ottenere il percorso, ritorna stringa vuota
                        ""
                    }
                }
                else -> ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
    
    private fun isUriValid(uri: Uri): Boolean {
        return try {
            if (uri.scheme == "content") {
                // Prova ad aprire l'URI per verificare che sia ancora valido
                val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    it.moveToFirst()
                } ?: false
            } else if (uri.scheme == "file") {
                // Per file URI, verifica che il file esista
                val file = java.io.File(uri.path ?: "")
                file.exists() && file.canRead()
            } else {
                // Per altri schemi, assumiamo che siano validi
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun showSettingsMenu() {
        val options = arrayOf(
            getString(R.string.remote_settings),
            getString(R.string.font_settings),
            getString(R.string.web_remote_settings),
            getString(R.string.credits)
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.settings))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRemoteSettingsDialog()
                    1 -> showFontSettingsDialog()
                    2 -> showWebRemoteSettingsDialog()
                    3 -> showCreditsDialog()
                }
            }
            .show()
    }
    
    private fun showRemoteSettingsDialog() {
        showCustomizeRemoteDialog()
    }
    
    private fun showWebRemoteSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_web_remote, null)
        val switchWebRemote = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchWebRemote)
        val textWebRemoteUrl = dialogView.findViewById<android.widget.TextView>(R.id.textWebRemoteUrl)
        val editDeviceName = dialogView.findViewById<TextInputEditText>(R.id.editDeviceName)
        val editPort = dialogView.findViewById<TextInputEditText>(R.id.editPort)
        val editPin = dialogView.findViewById<TextInputEditText>(R.id.editPin)
        
        switchWebRemote.isChecked = webRemoteEnabled
        editDeviceName.setText(webRemoteDeviceName)
        editPort.setText(webRemotePort.toString())
        editPin.setText("")
        dialogView.findViewById<TextInputLayout>(R.id.textInputLayoutPin)?.hint = if (webRemotePinHash.isEmpty()) getString(R.string.web_remote_pin_hint) else getString(R.string.web_remote_pin_set)
        
        fun updateUrlVisibility() {
            val enabled = switchWebRemote.isChecked
            textWebRemoteUrl.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
            if (enabled) {
                val ip = getLocalIpAddress()
                val port = editPort.text?.toString()?.toIntOrNull()?.coerceIn(1024, 65535) ?: webRemotePort
                textWebRemoteUrl.text = getString(R.string.web_remote_url) + "\nhttp://${ip ?: "…"}:$port"
            }
        }
        switchWebRemote.setOnCheckedChangeListener { _, _ -> updateUrlVisibility() }
        updateUrlVisibility()
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.web_remote_settings))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                webRemoteEnabled = switchWebRemote.isChecked
                webRemoteDeviceName = editDeviceName.text?.toString()?.trim().orEmpty()
                webRemotePort = editPort.text?.toString()?.toIntOrNull()?.coerceIn(1024, 65535) ?: 8080
                val pinInput = editPin.text?.toString()?.trim().orEmpty()
                webRemotePinHash = when {
                    pinInput.isEmpty() -> webRemotePinHash // mantieni il PIN precedente
                    pinInput.length in 4..8 -> hashPin(pinInput)
                    else -> "" // valore non valido = rimuovi PIN
                }
                saveSettings()
                if (webRemoteEnabled) {
                    startWebServerWithIndicator()
                    Handler(Looper.getMainLooper()).postDelayed({
                        when (webServerIndicatorState) {
                            WebServerIndicatorState.ACTIVE -> {
                                val ip = getLocalIpAddress()
                                if (ip != null) Toast.makeText(this, "http://$ip:$webRemotePort", Toast.LENGTH_LONG).show()
                                else Toast.makeText(this, getString(R.string.web_remote_no_ip), Toast.LENGTH_SHORT).show()
                            }
                            WebServerIndicatorState.NO_NETWORK -> Toast.makeText(this, getString(R.string.web_remote_no_ip), Toast.LENGTH_SHORT).show()
                            else -> { }
                        }
                    }, 500)
                } else {
                    stopWebServer()
                }
                invalidateOptionsMenu()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun hashPin(pin: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }
    
    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address)
                        return addr.hostAddress
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
    
    /** Avvia il web server e aggiorna l'indicatore: arancione (avvio), poi verde (ok) o rosso (nessuna rete). */
    private fun startWebServerWithIndicator() {
        webServerIndicatorState = WebServerIndicatorState.STARTING
        invalidateOptionsMenu()
        stopWebServer()
        try {
            webServer = WebServer(webRemotePort, this, webRemoteDeviceName, webRemotePinHash)
            webServer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            webServerIndicatorState = WebServerIndicatorState.NO_NETWORK
            Toast.makeText(this, "Porta $webRemotePort non disponibile", Toast.LENGTH_SHORT).show()
            invalidateOptionsMenu()
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            webServerIndicatorState = if (getLocalIpAddress() != null) WebServerIndicatorState.ACTIVE else WebServerIndicatorState.NO_NETWORK
            invalidateOptionsMenu()
        }, 400)
    }
    
    private fun startWebServer() {
        stopWebServer()
        try {
            webServer = WebServer(webRemotePort, this, webRemoteDeviceName, webRemotePinHash)
            webServer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Porta $webRemotePort non disponibile", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopWebServer() {
        try {
            webServer?.stop()
        } catch (_: Exception) { }
        webServer = null
    }
    
    override fun onPlayPause() = runOnUiThread { togglePlayPause() }
    override fun onScrollUp() = runOnUiThread { scrollByMode(-1) }
    override fun onScrollDown() = runOnUiThread { scrollByMode(1) }
    override fun onSetWpm(wpm: Int) = runOnUiThread {
        targetWpm = wpm.coerceIn(60, 250)
        if (::speedSlider.isInitialized) speedSlider.value = targetWpm.toFloat()
        saveSettings()
        updateWpmDisplay()
        if (isPlaying) { stopAutoScroll(); startAutoScroll() }
    }
    override fun onSetTextSize(size: Float) = runOnUiThread {
        val s = size.coerceIn(12f, 48f)
        currentTextSize = s
        if (::textView.isInitialized) textView.textSize = s
        if (::textSizeSlider.isInitialized) textSizeSlider.value = s
        saveSettings()
    }
    override fun onChangeScrollMode(mode: Int) = runOnUiThread {
        scrollMode = mode.coerceIn(0, 2)
        updateScrollModeButton()
    }
    override fun getState(): WebRemoteState = WebRemoteState(
        playing = isPlaying,
        wpm = targetWpm,
        textSize = currentTextSize,
        hasText = countWords() > 0,
        scrollMode = scrollMode
    )
    
    override fun getRecentFiles(): List<Pair<Int, String>> {
        val list = getImportedFileList()
        if (list.isEmpty()) return emptyList()
        return list.mapIndexed { index, triple -> Pair(index, triple.second) }
    }
    
    override fun loadRecentFile(index: Int) = runOnUiThread {
        val list = getImportedFileList()
        if (index in list.indices) {
            val (id, _, ext) = list[index]
            openImportedFile(id, ext)
        }
    }
    
    private fun showCustomizeRemoteDialog() {
        val actions = RemoteAction.values()
        val actionNames = actions.map { action ->
            val currentMapping = remoteMappings.find { it.action == action }
            val currentKey = if (currentMapping != null) {
                val keyName = getKeyName(currentMapping.keyCode)
                " ($keyName)"
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
                    showKeySelectionDialog(actions[which])
                } else {
                    // Ripristina impostazioni predefinite
                    showRestoreDefaultsDialog()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showKeySelectionDialog(action: RemoteAction) {
        // Lista di tasti disponibili
        val availableKeys = mutableListOf<Pair<Int, String>>().apply {
            // Tasti direzionali e controllo
            add(Pair(KeyEvent.KEYCODE_DPAD_UP, getString(R.string.key_dpad_up)))
            add(Pair(KeyEvent.KEYCODE_DPAD_DOWN, getString(R.string.key_dpad_down)))
            add(Pair(KeyEvent.KEYCODE_DPAD_LEFT, getString(R.string.key_dpad_left)))
            add(Pair(KeyEvent.KEYCODE_DPAD_RIGHT, getString(R.string.key_dpad_right)))
            add(Pair(KeyEvent.KEYCODE_TAB, getString(R.string.key_tab)))
            add(Pair(KeyEvent.KEYCODE_ENTER, getString(R.string.key_enter)))
            add(Pair(KeyEvent.KEYCODE_SPACE, getString(R.string.key_space)))
            add(Pair(KeyEvent.KEYCODE_VOLUME_UP, getString(R.string.key_volume_up)))
            add(Pair(KeyEvent.KEYCODE_VOLUME_DOWN, getString(R.string.key_volume_down)))
            
            // Tasti funzione F1-F12
            add(Pair(KeyEvent.KEYCODE_F1, getString(R.string.key_f1)))
            add(Pair(KeyEvent.KEYCODE_F2, getString(R.string.key_f2)))
            add(Pair(KeyEvent.KEYCODE_F3, getString(R.string.key_f3)))
            add(Pair(KeyEvent.KEYCODE_F4, getString(R.string.key_f4)))
            add(Pair(KeyEvent.KEYCODE_F5, getString(R.string.key_f5)))
            add(Pair(KeyEvent.KEYCODE_F6, getString(R.string.key_f6)))
            add(Pair(KeyEvent.KEYCODE_F7, getString(R.string.key_f7)))
            add(Pair(KeyEvent.KEYCODE_F8, getString(R.string.key_f8)))
            add(Pair(KeyEvent.KEYCODE_F9, getString(R.string.key_f9)))
            add(Pair(KeyEvent.KEYCODE_F10, getString(R.string.key_f10)))
            add(Pair(KeyEvent.KEYCODE_F11, getString(R.string.key_f11)))
            add(Pair(KeyEvent.KEYCODE_F12, getString(R.string.key_f12)))
            
            // Tasti numerici 0-9
            add(Pair(KeyEvent.KEYCODE_0, getString(R.string.key_0)))
            add(Pair(KeyEvent.KEYCODE_1, getString(R.string.key_1)))
            add(Pair(KeyEvent.KEYCODE_2, getString(R.string.key_2)))
            add(Pair(KeyEvent.KEYCODE_3, getString(R.string.key_3)))
            add(Pair(KeyEvent.KEYCODE_4, getString(R.string.key_4)))
            add(Pair(KeyEvent.KEYCODE_5, getString(R.string.key_5)))
            add(Pair(KeyEvent.KEYCODE_6, getString(R.string.key_6)))
            add(Pair(KeyEvent.KEYCODE_7, getString(R.string.key_7)))
            add(Pair(KeyEvent.KEYCODE_8, getString(R.string.key_8)))
            add(Pair(KeyEvent.KEYCODE_9, getString(R.string.key_9)))
            
            // Lettere A-Z
            add(Pair(KeyEvent.KEYCODE_A, getString(R.string.key_a)))
            add(Pair(KeyEvent.KEYCODE_B, getString(R.string.key_b)))
            add(Pair(KeyEvent.KEYCODE_C, getString(R.string.key_c)))
            add(Pair(KeyEvent.KEYCODE_D, getString(R.string.key_d)))
            add(Pair(KeyEvent.KEYCODE_E, getString(R.string.key_e)))
            add(Pair(KeyEvent.KEYCODE_F, getString(R.string.key_f)))
            add(Pair(KeyEvent.KEYCODE_G, getString(R.string.key_g)))
            add(Pair(KeyEvent.KEYCODE_H, getString(R.string.key_h)))
            add(Pair(KeyEvent.KEYCODE_I, getString(R.string.key_i)))
            add(Pair(KeyEvent.KEYCODE_J, getString(R.string.key_j)))
            add(Pair(KeyEvent.KEYCODE_K, getString(R.string.key_k)))
            add(Pair(KeyEvent.KEYCODE_L, getString(R.string.key_l)))
            add(Pair(KeyEvent.KEYCODE_M, getString(R.string.key_m)))
            add(Pair(KeyEvent.KEYCODE_N, getString(R.string.key_n)))
            add(Pair(KeyEvent.KEYCODE_O, getString(R.string.key_o)))
            add(Pair(KeyEvent.KEYCODE_P, getString(R.string.key_p)))
            add(Pair(KeyEvent.KEYCODE_Q, getString(R.string.key_q)))
            add(Pair(KeyEvent.KEYCODE_R, getString(R.string.key_r)))
            add(Pair(KeyEvent.KEYCODE_S, getString(R.string.key_s)))
            add(Pair(KeyEvent.KEYCODE_T, getString(R.string.key_t)))
            add(Pair(KeyEvent.KEYCODE_U, getString(R.string.key_u)))
            add(Pair(KeyEvent.KEYCODE_V, getString(R.string.key_v)))
            add(Pair(KeyEvent.KEYCODE_W, getString(R.string.key_w)))
            add(Pair(KeyEvent.KEYCODE_X, getString(R.string.key_x)))
            add(Pair(KeyEvent.KEYCODE_Y, getString(R.string.key_y)))
            add(Pair(KeyEvent.KEYCODE_Z, getString(R.string.key_z)))
        }
        
        // Aggiungi l'opzione "Non mappare" come prima voce
        val keyNames = mutableListOf<String>().apply {
            add(getString(R.string.unmap)) // Prima voce: "Non mappare"
            addAll(availableKeys.map { it.second })
        }.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_key))
            .setItems(keyNames) { _, which ->
                if (which == 0) {
                    // Selezionato "Non mappare": rimuovi la mappatura per questa azione
                    removeKeyMapping(action)
                } else {
                    // Selezionato un tasto: salva la mappatura
                    val selectedKey = availableKeys[which - 1] // -1 perché la prima voce è "Non mappare"
                    saveKeyMapping(action, selectedKey.first)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun getKeyName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> getString(R.string.key_dpad_up)
            KeyEvent.KEYCODE_DPAD_DOWN -> getString(R.string.key_dpad_down)
            KeyEvent.KEYCODE_DPAD_LEFT -> getString(R.string.key_dpad_left)
            KeyEvent.KEYCODE_DPAD_RIGHT -> getString(R.string.key_dpad_right)
            KeyEvent.KEYCODE_TAB -> getString(R.string.key_tab)
            KeyEvent.KEYCODE_ENTER -> getString(R.string.key_enter)
            KeyEvent.KEYCODE_SPACE -> getString(R.string.key_space)
            KeyEvent.KEYCODE_VOLUME_UP -> getString(R.string.key_volume_up)
            KeyEvent.KEYCODE_VOLUME_DOWN -> getString(R.string.key_volume_down)
            KeyEvent.KEYCODE_F1 -> getString(R.string.key_f1)
            KeyEvent.KEYCODE_F2 -> getString(R.string.key_f2)
            KeyEvent.KEYCODE_F3 -> getString(R.string.key_f3)
            KeyEvent.KEYCODE_F4 -> getString(R.string.key_f4)
            KeyEvent.KEYCODE_F5 -> getString(R.string.key_f5)
            KeyEvent.KEYCODE_F6 -> getString(R.string.key_f6)
            KeyEvent.KEYCODE_F7 -> getString(R.string.key_f7)
            KeyEvent.KEYCODE_F8 -> getString(R.string.key_f8)
            KeyEvent.KEYCODE_F9 -> getString(R.string.key_f9)
            KeyEvent.KEYCODE_F10 -> getString(R.string.key_f10)
            KeyEvent.KEYCODE_F11 -> getString(R.string.key_f11)
            KeyEvent.KEYCODE_F12 -> getString(R.string.key_f12)
            KeyEvent.KEYCODE_0 -> getString(R.string.key_0)
            KeyEvent.KEYCODE_1 -> getString(R.string.key_1)
            KeyEvent.KEYCODE_2 -> getString(R.string.key_2)
            KeyEvent.KEYCODE_3 -> getString(R.string.key_3)
            KeyEvent.KEYCODE_4 -> getString(R.string.key_4)
            KeyEvent.KEYCODE_5 -> getString(R.string.key_5)
            KeyEvent.KEYCODE_6 -> getString(R.string.key_6)
            KeyEvent.KEYCODE_7 -> getString(R.string.key_7)
            KeyEvent.KEYCODE_8 -> getString(R.string.key_8)
            KeyEvent.KEYCODE_9 -> getString(R.string.key_9)
            KeyEvent.KEYCODE_A -> getString(R.string.key_a)
            KeyEvent.KEYCODE_B -> getString(R.string.key_b)
            KeyEvent.KEYCODE_C -> getString(R.string.key_c)
            KeyEvent.KEYCODE_D -> getString(R.string.key_d)
            KeyEvent.KEYCODE_E -> getString(R.string.key_e)
            KeyEvent.KEYCODE_F -> getString(R.string.key_f)
            KeyEvent.KEYCODE_G -> getString(R.string.key_g)
            KeyEvent.KEYCODE_H -> getString(R.string.key_h)
            KeyEvent.KEYCODE_I -> getString(R.string.key_i)
            KeyEvent.KEYCODE_J -> getString(R.string.key_j)
            KeyEvent.KEYCODE_K -> getString(R.string.key_k)
            KeyEvent.KEYCODE_L -> getString(R.string.key_l)
            KeyEvent.KEYCODE_M -> getString(R.string.key_m)
            KeyEvent.KEYCODE_N -> getString(R.string.key_n)
            KeyEvent.KEYCODE_O -> getString(R.string.key_o)
            KeyEvent.KEYCODE_P -> getString(R.string.key_p)
            KeyEvent.KEYCODE_Q -> getString(R.string.key_q)
            KeyEvent.KEYCODE_R -> getString(R.string.key_r)
            KeyEvent.KEYCODE_S -> getString(R.string.key_s)
            KeyEvent.KEYCODE_T -> getString(R.string.key_t)
            KeyEvent.KEYCODE_U -> getString(R.string.key_u)
            KeyEvent.KEYCODE_V -> getString(R.string.key_v)
            KeyEvent.KEYCODE_W -> getString(R.string.key_w)
            KeyEvent.KEYCODE_X -> getString(R.string.key_x)
            KeyEvent.KEYCODE_Y -> getString(R.string.key_y)
            KeyEvent.KEYCODE_Z -> getString(R.string.key_z)
            else -> "Key $keyCode"
        }
    }
    
    private fun saveKeyMapping(action: RemoteAction, keyCode: Int) {
        // Rimuovi mappature esistenti per questa azione
        remoteMappings.removeAll { it.action == action }
        
        // Aggiungi nuova mappatura
        remoteMappings.add(RemoteKeyMapping(keyCode, action))
        saveRemoteMappings()
        
        val keyName = getKeyName(keyCode)
        Toast.makeText(this, "$keyName - ${getString(R.string.save)}", Toast.LENGTH_SHORT).show()
    }
    
    private fun removeKeyMapping(action: RemoteAction) {
        // Rimuovi mappature esistenti per questa azione
        remoteMappings.removeAll { it.action == action }
        saveRemoteMappings()
        
        // Mostra il nome dell'azione rimossa
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
        Toast.makeText(this, "$actionName - ${getString(R.string.unmap)}", Toast.LENGTH_SHORT).show()
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
            RemoteKeyMapping(KeyEvent.KEYCODE_DPAD_UP, RemoteAction.SCROLL_UP),
            RemoteKeyMapping(KeyEvent.KEYCODE_DPAD_DOWN, RemoteAction.SCROLL_DOWN),
            RemoteKeyMapping(KeyEvent.KEYCODE_DPAD_LEFT, RemoteAction.DECREASE_TEXT_SIZE),
            RemoteKeyMapping(KeyEvent.KEYCODE_DPAD_RIGHT, RemoteAction.INCREASE_TEXT_SIZE),
            RemoteKeyMapping(KeyEvent.KEYCODE_TAB, RemoteAction.PLAY_PAUSE),
            RemoteKeyMapping(KeyEvent.KEYCODE_VOLUME_UP, RemoteAction.INCREASE_SPEED),
            RemoteKeyMapping(KeyEvent.KEYCODE_VOLUME_DOWN, RemoteAction.DECREASE_SPEED)
        )
        saveRemoteMappings()
    }
    
    private fun showRemoteInfoDialog() {
        val remoteControls = """
            • Frecce Su/Giù: Scorrimento manuale
            • Freccia Sinistra: Diminuisce dimensione testo
            • Freccia Destra: Aumenta dimensione testo
            • Tab: Play/Pause
            • Volume Su: Aumenta velocità
            • Volume Giù: Diminuisce velocità
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
            .putInt("targetWpm", targetWpm)
            .putFloat("currentTextSize", currentTextSize)
            .putInt("scrollMode", scrollMode)
            .putString("currentFont", currentFont)
            .putBoolean("isDarkMode", isDarkMode)
            .putBoolean("webRemoteEnabled", webRemoteEnabled)
            .putInt("webRemotePort", webRemotePort)
            .putString("webRemoteDeviceName", webRemoteDeviceName)
            .putString("webRemotePinHash", webRemotePinHash)
            .apply()
    }
    
    private fun loadSettings() {
        // Migrazione: se esiste ancora currentSpeed (vecchia versione), converti in targetWpm approssimativo
        targetWpm = if (sharedPreferences.contains("targetWpm")) {
            sharedPreferences.getInt("targetWpm", 120).coerceIn(60, 250)
        } else if (sharedPreferences.contains("currentSpeed")) {
            val oldSpeed = sharedPreferences.getFloat("currentSpeed", 5f)
            (60 + (oldSpeed - 1) * (200 - 60) / 19).toInt().coerceIn(60, 250)
        } else {
            120
        }
        currentTextSize = sharedPreferences.getFloat("currentTextSize", 24f).coerceIn(12f, 48f)
        scrollMode = sharedPreferences.getInt("scrollMode", 0)
        currentFont = sharedPreferences.getString("currentFont", "default") ?: "default"
        webRemoteEnabled = sharedPreferences.getBoolean("webRemoteEnabled", false)
        webRemotePort = sharedPreferences.getInt("webRemotePort", 8080).coerceIn(1024, 65535)
        webRemoteDeviceName = sharedPreferences.getString("webRemoteDeviceName", "") ?: ""
        webRemotePinHash = sharedPreferences.getString("webRemotePinHash", "") ?: ""
        currentImportedFileId = sharedPreferences.getString("currentImportedFileId", null)
        
        speedSlider.value = targetWpm.toFloat()
        textSizeSlider.value = currentTextSize
        textView.textSize = currentTextSize
        
        scrollView.post { updateWpmDisplay() }
        
        // Carica mappature remote
        loadRemoteMappings()
    }
    
    private fun saveRemoteMappings() {
        val mappingsJson = remoteMappings.joinToString("|") { mapping ->
            "${mapping.keyCode}:${mapping.action.ordinal}"
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
                if (parts.size == 2) {
                    try {
                        val keyCode = parts[0].toInt()
                        val actionOrdinal = parts[1].toInt()
                        if (actionOrdinal in RemoteAction.values().indices) {
                            RemoteKeyMapping(keyCode, RemoteAction.values()[actionOrdinal])
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                } else if (parts.size == 3) {
                    // Compatibilità con vecchio formato (keyCode:isHold:action)
                    try {
                        val keyCode = parts[0].toInt()
                        val actionOrdinal = parts[2].toInt()
                        if (actionOrdinal in RemoteAction.values().indices) {
                            RemoteKeyMapping(keyCode, RemoteAction.values()[actionOrdinal])
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }.toMutableList()
            
            // Correggi eventuali mappature errate per Tab
            // Se Tab è mappato a CHANGE_SCROLL_MODE (comportamento errato), rimuovilo e usa il default
            val tabMapping = remoteMappings.find { it.keyCode == KeyEvent.KEYCODE_TAB }
            if (tabMapping != null && tabMapping.action == RemoteAction.CHANGE_SCROLL_MODE) {
                // Rimuovi la mappatura errata
                remoteMappings.remove(tabMapping)
                // Aggiungi la mappatura corretta (PLAY_PAUSE)
                remoteMappings.add(RemoteKeyMapping(KeyEvent.KEYCODE_TAB, RemoteAction.PLAY_PAUSE))
                saveRemoteMappings()
            } else if (tabMapping == null) {
                // Se Tab non ha mappatura, aggiungi quella di default
                remoteMappings.add(RemoteKeyMapping(KeyEvent.KEYCODE_TAB, RemoteAction.PLAY_PAUSE))
                saveRemoteMappings()
            }
        }
    }
    
    private fun showCreditsDialog() {
        try {
            val creditsText = getString(R.string.credits_text)
            val dialogView = layoutInflater.inflate(R.layout.dialog_credits, null)
            val textView = dialogView.findViewById<android.widget.TextView>(R.id.creditsText)
            
            textView.text = creditsText
            textView.textSize = 14f
            Linkify.addLinks(textView, Linkify.WEB_URLS)
            textView.movementMethod = LinkMovementMethod.getInstance()
            
            // Il MaterialAlertDialogBuilder ha un padding di 24dp per il contenuto
            // Il titolo ha un padding di 24dp a sinistra, quindi il testo deve avere lo stesso padding
            // Ma il TextView nel layout ha già padding 0, quindi il dialog aggiungerà il suo padding
            // Per allineare con il titolo, dobbiamo rimuovere il padding aggiuntivo del dialog
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.credits))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.ok), null)
                .setNeutralButton(getString(R.string.check_updates)) { _, _ ->
                    try {
                        checkForUpdates()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@MainActivity, "Errore: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .create()
            
            dialog.show()
            
            // Il padding è già impostato nel layout XML, non serve modificarlo dinamicamente
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Errore nel dialog: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun checkForUpdates() {
        try {
            // Mostra dialog di caricamento
            val progressView = layoutInflater.inflate(R.layout.dialog_progress, null)
            val progressText = progressView.findViewById<android.widget.TextView>(R.id.progressText)
            progressText?.text = getString(R.string.checking_updates)
            
            val updateDialog = MaterialAlertDialogBuilder(this)
                .setView(progressView)
                .setCancelable(false)
                .create()
            updateDialog.show()
            
            // Esegui la verifica in un thread separato
            Thread {
                try {
                    val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName
                    val latestVersion = getLatestVersionFromGitHub()
                    
                    runOnUiThread {
                        try {
                            updateDialog.dismiss()
                            
                            if (latestVersion != null && isNewerVersion(latestVersion, currentVersion)) {
                                // Nuova versione disponibile
                                MaterialAlertDialogBuilder(this@MainActivity)
                                    .setTitle(getString(R.string.update_available))
                                    .setMessage(getString(R.string.update_available_message, latestVersion))
                                    .setPositiveButton(getString(R.string.download_update)) { _, _ ->
                                        downloadUpdate(latestVersion)
                                    }
                                    .setNegativeButton(getString(R.string.cancel), null)
                                    .show()
                            } else {
                                // Nessun aggiornamento disponibile
                                MaterialAlertDialogBuilder(this@MainActivity)
                                    .setTitle(getString(R.string.no_update_available))
                                    .setMessage(getString(R.string.no_update_message))
                                    .setPositiveButton(getString(R.string.ok), null)
                                    .show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this@MainActivity, "Errore UI: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        try {
                            updateDialog.dismiss()
                            MaterialAlertDialogBuilder(this@MainActivity)
                                .setTitle(getString(R.string.update_error))
                                .setMessage(e.message ?: "Errore sconosciuto")
                                .setPositiveButton(getString(R.string.ok), null)
                                .show()
                        } catch (uiException: Exception) {
                            uiException.printStackTrace()
                            Toast.makeText(this@MainActivity, "Errore: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Errore iniziale: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun getLatestVersionFromGitHub(): String? {
        return try {
            val url = URL("https://api.github.com/repos/mccoy88f/Gobbo-Teleprompter/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val tagName = json.getString("tag_name")
                // Rimuovi il prefisso "v" se presente (es. "v1.1.0" -> "1.1.0")
                tagName.removePrefix("v")
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }
            
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        
        return false
    }
    
    private fun downloadUpdate(version: String) {
        // Mostra dialog di download
        val progressView = layoutInflater.inflate(R.layout.dialog_progress, null)
        val progressText = progressView.findViewById<android.widget.TextView>(R.id.progressText)
        progressText?.text = getString(R.string.downloading)
        
        val downloadDialog = MaterialAlertDialogBuilder(this)
            .setView(progressView)
            .setCancelable(false)
            .create()
        downloadDialog.show()
        
        Thread {
            try {
                // Ottieni l'URL dell'APK dalla release GitHub
                val url = URL("https://api.github.com/repos/mccoy88f/Gobbo-Teleprompter/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val assets = json.getJSONArray("assets")
                
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                
                if (apkUrl != null) {
                    runOnUiThread {
                        downloadDialog.dismiss()
                        // Usa DownloadManager per scaricare l'APK
                        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val request = DownloadManager.Request(Uri.parse(apkUrl))
                            .setTitle("Gobbo Teleprompter $version")
                            .setDescription("Downloading update")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Gobbo-Teleprompter-$version.apk")
                            .setAllowedOverMetered(true)
                            .setAllowedOverRoaming(true)
                        
                        downloadId = downloadManager.enqueue(request)
                        
                        // Registra un receiver per quando il download è completato
                        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                        downloadReceiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                                if (id == downloadId) {
                                    showInstallDialog(version)
                                    try {
                                        unregisterReceiver(this)
                                    } catch (e: Exception) {
                                        // Già rimosso
                                    }
                                    downloadReceiver = null
                                }
                            }
                        }
                        registerReceiver(downloadReceiver, filter)
                        
                        Toast.makeText(this, getString(R.string.downloading), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        downloadDialog.dismiss()
                        Toast.makeText(this, getString(R.string.update_error), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    downloadDialog.dismiss()
                    Toast.makeText(this, getString(R.string.update_error), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun showInstallDialog(version: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.download_complete))
            .setMessage("L'aggiornamento $version è stato scaricato. Vuoi installarlo ora?")
            .setPositiveButton(getString(R.string.install_update)) { _, _ ->
                installUpdate(version)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun installUpdate(version: String) {
        try {
            val file = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Gobbo-Teleprompter-$version.apk"
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(installIntent)
            } else {
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.fromFile(file),
                        "application/vnd.android.package-archive"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(installIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.update_error), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun clearCurrentFile() {
        sharedPreferences.edit()
            .remove("currentFileUri")
            .remove("currentFileExtension")
            .remove("currentImportedFileId")
            .apply()
        currentImportedFileId = null
        currentFileUri = null
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
    
    private fun getImportedFilesDir(): File {
        val dir = File(filesDir, "imported")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    /** Lista file importati: (id, displayName, extension). Usa \t come separatore campi (i nomi file non contengono tab). */
    private fun getImportedFileList(): List<Triple<String, String, String>> {
        val json = sharedPreferences.getString("importedFiles", "") ?: ""
        if (json.isEmpty()) return emptyList()
        return json.split("|").mapNotNull { entry ->
            val parts = entry.split("\t", limit = 3)
            if (parts.size == 3) Triple(parts[0], parts[1], parts[2]) else null
        }
    }
    
    private fun saveImportedFileList(list: List<Triple<String, String, String>>) {
        val json = list.joinToString("|") { "${it.first}\t${it.second}\t${it.third}" }
        sharedPreferences.edit().putString("importedFiles", json).apply()
    }
    
    /** Salva il contenuto in app e aggiunge alla lista. Ritorna l'id. */
    private fun saveImportedFile(content: String, displayName: String, extension: String): String {
        val id = UUID.randomUUID().toString()
        val dir = getImportedFilesDir()
        File(dir, id).writeText(content, Charsets.UTF_8)
        val list = getImportedFileList().toMutableList()
        list.add(0, Triple(id, displayName, extension))
        saveImportedFileList(list)
        return id
    }
    
    private fun loadImportedFileContent(id: String): String? {
        return try {
            val file = File(getImportedFilesDir(), id)
            if (file.exists()) file.readText(Charsets.UTF_8) else null
        } catch (_: Exception) { null }
    }
    
    /** Rimuove il file importato dalla lista e dal disco. */
    private fun removeImportedFile(id: String) {
        val list = getImportedFileList().filter { it.first != id }
        saveImportedFileList(list)
        try {
            File(getImportedFilesDir(), id).delete()
        } catch (_: Exception) { }
        if (currentImportedFileId == id) {
            currentImportedFileId = null
            savedText = ""
            savedTextExtension = ""
            sharedPreferences.edit()
                .remove("currentImportedFileId")
                .putString("savedText", "")
                .putString("savedTextExtension", "")
                .apply()
            textView.text = getString(R.string.empty_state_hint)
            scrollView.post { updateWpmDisplay() }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAutoScroll()
        scrollHandler?.removeCallbacksAndMessages(null)
        volumeKeyHandler?.removeCallbacksAndMessages(null)
        toolbarHideHandler?.removeCallbacksAndMessages(null)
        progressDialog?.dismiss()
    }
    
}
