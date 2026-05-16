package com.mccoy88f.gobbo

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PlaylistItem(
    val id: String = UUID.randomUUID().toString(),
    /** content://… o file://… o gobbo-internal://item/{uuid} */
    val uriStr: String,
    var displayName: String,
    /** Durata fissa in secondi impostata dall'utente nella playlist; null = usa tag file se presente. */
    var durationSeconds: Int? = null,
    /** Ultimo valore letto da <!-- gobbo-duration --> al caricamento. */
    var lastParsedTagSeconds: Int? = null,
    /**
     * Deprecato: non viene più ripristinato per far ripartire il timer; può essere presente nei JSON
     * vecchi solo per migrazione verso recordedElapsedSeconds.
     */
    var savedTimerElapsedMs: Long? = null,
    /**
     * Ultima durata totale registrata dalla sessione timer (dal Play fino allo stop tramite cambio
     * traccia, clic sul fumetto o stop da remoto), in secondi.
     */
    var recordedElapsedSeconds: Int? = null,
) {
    fun presetSecondsFallback(): Int = durationSeconds ?: lastParsedTagSeconds ?: 0

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("uri", uriStr)
        put("displayName", displayName)
        if (durationSeconds != null) put("durationSeconds", durationSeconds) else put("durationSeconds", JSONObject.NULL)
        if (lastParsedTagSeconds != null) put("lastParsedTagSeconds", lastParsedTagSeconds) else put("lastParsedTagSeconds", JSONObject.NULL)
        put("savedTimerElapsedMs", JSONObject.NULL)
        if (recordedElapsedSeconds != null) put("recordedElapsedSeconds", recordedElapsedSeconds) else put(
            "recordedElapsedSeconds",
            JSONObject.NULL,
        )
    }

    companion object {
        fun fromJson(o: JSONObject): PlaylistItem {
            val dSec = when {
                !o.has("durationSeconds") || o.isNull("durationSeconds") -> null
                else -> o.optInt("durationSeconds", -1).takeIf { it >= 0 }
            }
            val lastTag = when {
                !o.has("lastParsedTagSeconds") || o.isNull("lastParsedTagSeconds") -> null
                else -> o.optInt("lastParsedTagSeconds", -1).takeIf { it >= 0 }
            }
            val savedMs = when {
                !o.has("savedTimerElapsedMs") || o.isNull("savedTimerElapsedMs") -> null
                else -> o.optLong("savedTimerElapsedMs", -1L).takeIf { it >= 0 }
            }
            var rec = when {
                !o.has("recordedElapsedSeconds") || o.isNull("recordedElapsedSeconds") -> null
                else -> o.optInt("recordedElapsedSeconds", -1).takeIf { it >= 0 }
            }
            if (rec == null && savedMs != null && savedMs > 0L) {
                rec = (savedMs / 1000L).toInt().takeIf { it > 0 }
            }
            return PlaylistItem(
                id = o.optString("id", UUID.randomUUID().toString()),
                uriStr = o.getString("uri"),
                displayName = o.optString("displayName", "—"),
                durationSeconds = dSec,
                lastParsedTagSeconds = lastTag,
                savedTimerElapsedMs = null,
                recordedElapsedSeconds = rec,
            )
        }
    }

    /** Per somma playlist: durata misurata se disponibile, altrimenti preimpostata/tag. */
    fun effectiveSecondsForTotal(): Int =
        recordedElapsedSeconds ?: presetSecondsFallback()
}

data class Playlist(
    val formatVersion: Int = 1,
    var name: String,
    val items: MutableList<PlaylistItem> = mutableListOf(),
    var currentIndex: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("formatVersion", formatVersion)
        put("name", name)
        put("currentIndex", currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        put("items", JSONArray().also { arr -> items.forEach { arr.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(json: JSONObject): Playlist {
            val arr = json.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<PlaylistItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                runCatching { items.add(PlaylistItem.fromJson(o)) }
            }
            return Playlist(
                formatVersion = json.optInt("formatVersion", 1),
                name = json.optString("name", ""),
                items = items,
                currentIndex = json.optInt("currentIndex", 0)
            ).also { pl ->
                if (pl.items.isNotEmpty()) pl.currentIndex = pl.currentIndex.coerceIn(0, pl.items.lastIndex)
                else pl.currentIndex = 0
            }
        }
    }

    fun currentItem(): PlaylistItem? = items.getOrNull(currentIndex.coerceIn(0, items.lastIndex.takeIf { it >= 0 } ?: 0))
}
