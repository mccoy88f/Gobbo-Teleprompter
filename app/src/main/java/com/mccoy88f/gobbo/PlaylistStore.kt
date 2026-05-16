package com.mccoy88f.gobbo

import android.content.Context
import org.json.JSONObject
import java.io.File

class PlaylistStore(private val context: Context) {

    private val playlistFile: File get() = File(context.filesDir, PLAYLIST_FILENAME)

    fun load(): Playlist {
        return try {
            if (!playlistFile.exists()) return Playlist(name = DEFAULT_NAME)
            val json = JSONObject(playlistFile.readText(Charsets.UTF_8))
            Playlist.fromJson(json)
        } catch (_: Exception) {
            Playlist(name = DEFAULT_NAME)
        }
    }

    fun save(playlist: Playlist) {
        try {
            playlistFile.writeText(playlist.toJson().toString(), Charsets.UTF_8)
        } catch (_: Exception) { }
    }

    companion object {
        const val PLAYLIST_FILENAME = "gobbo_playlist.json"
        const val DEFAULT_NAME = "Playlist"
        const val INTERNAL_URI_PREFIX = "gobbo-internal://item/"

        fun isInternalUri(uriStr: String) = uriStr.startsWith(INTERNAL_URI_PREFIX)

        fun internalIdFromUri(uriStr: String): String? =
            if (isInternalUri(uriStr)) uriStr.removePrefix(INTERNAL_URI_PREFIX) else null
    }
}
