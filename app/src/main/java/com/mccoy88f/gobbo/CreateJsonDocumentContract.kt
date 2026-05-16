package com.mccoy88f.gobbo

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/** Crea file per salvare playlist JSON (MIME application/json). */
class CreateJsonDocumentContract : ActivityResultContracts.CreateDocument("application/json") {

    override fun createIntent(context: Context, input: String): Intent {
        return super.createIntent(context, input.ifBlank { "gobbo-playlist.json" }).apply {
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }
}
