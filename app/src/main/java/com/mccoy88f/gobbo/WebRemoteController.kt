package com.mccoy88f.gobbo

/**
 * Callback per il controllo remoto web: l'app espone queste azioni e lo stato al server HTTP.
 */
interface WebRemoteController {
    fun onPlayPause()
    fun onScrollUp()
    fun onScrollDown()
    fun onSetWpm(wpm: Int)
    fun onSetTextSize(size: Float)
    fun onChangeScrollMode(mode: Int)
    /** Stato attuale (lettura da thread server). */
    fun getState(): WebRemoteState
    /** Voci playlist: (indice, nome visualizzato). Il browser sceglie l'indice, non il file sul disco. */
    fun getRecentFiles(): List<Pair<Int, String>>
    /** Apre la voce playlist all'indice dato. */
    fun loadRecentFile(index: Int)
    fun onPlaylistNext()
    fun onPlaylistPrev()
    fun onTimerResetRemote()
    /** Ferma timer, salva la durata sulla voce playlist corrente (come clic sul fumetto). */
    fun stopPresentationTimerCommitRemote()
    /** Alterna “blocca playlist” come l’icona nella barra playlist. */
    fun togglePlaylistDrawerPinRemote()
}

data class WebRemoteState(
    val playing: Boolean,
    val wpm: Int,
    val textSize: Float,
    val hasText: Boolean,
    val scrollMode: Int,
    val playlistCurrentIndex: Int,
    val playlistSize: Int,
    val playlistTotalSeconds: Int,
    val playlistCurrentTitle: String,
    /** Secondi rimanenti (null se countdown non configurato / non ancora avviabile). */
    val timerRemainingSeconds: Int?,
    val timerAllottedSeconds: Int,
    /** true dopo il primo avvio countdown (premuto Play una volta nella sessione). */
    val timerSessionStarted: Boolean,
    /** Testo del timer come in alto nell’app (MM:SS leggibile); stringa vuota se il timer è disattivo. */
    val timerBannerText: String,
    val playlistName: String,
    /** Pannello playlist bloccato aperto (non si chiude solo). */
    val playlistDrawerPinned: Boolean,
)
