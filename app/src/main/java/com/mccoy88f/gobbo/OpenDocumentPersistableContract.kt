package com.mccoy88f.gobbo

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/** Come [ActivityResultContracts.OpenDocument] ma con URI persistibile in lettura. */
class OpenDocumentPersistableContract : ActivityResultContracts.OpenDocument() {

    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }
}
