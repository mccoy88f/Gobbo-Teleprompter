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
    /** Elenco file importati in app: (indice, nome file). */
    fun getRecentFiles(): List<Pair<Int, String>>
    /** Apre il file importato all'indice dato. */
    fun loadRecentFile(index: Int)
}

data class WebRemoteState(
    val playing: Boolean,
    val wpm: Int,
    val textSize: Float,
    val hasText: Boolean,
    val scrollMode: Int
)
