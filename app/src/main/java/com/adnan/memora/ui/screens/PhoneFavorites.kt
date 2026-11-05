package com.adnan.memora.ui.screens

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.adnan.memora.R
import com.adnan.memora.gallery.LocalMedia

internal object PhoneFavoriteRules {
    fun isLocal(scheme: String?) = scheme == "content"
}
private class FavoriteRequestState {
    var groups: List<List<Uri>> = emptyList()
    var favorite = true
    var next: () -> Unit = {}
}

/** Uses the Android-owned favorite flag, never the legacy SHA favorite database. */
@Composable
fun rememberPhoneFavoriteAction(onDone: () -> Unit): (List<LocalMedia>, Boolean) -> Unit {
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    val state = remember { FavoriteRequestState() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            done()
            state.groups = state.groups.drop(1)
            if (state.groups.isNotEmpty()) state.next()
        } else { state.groups = emptyList(); done() }
    }
    state.next = {
        if (Build.VERSION.SDK_INT >= 30 && state.groups.isNotEmpty()) {
            try {
                val pending = MediaStore.createFavoriteRequest(context.contentResolver, state.groups.first(), state.favorite)
                launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            } catch (_: Exception) {
                state.groups = emptyList()
                Toast.makeText(context, R.string.rf_favorite_error, Toast.LENGTH_LONG).show()
                done()
            }
        }
    }
    return { items, favorite ->
        if (Build.VERSION.SDK_INT < 30) Toast.makeText(context, R.string.rf_favorite_unavailable, Toast.LENGTH_LONG).show()
        else if (state.groups.isEmpty()) {
            val local = items.filter { PhoneFavoriteRules.isLocal(it.uri.scheme) }.map { it.uri }.distinct()
            if (local.size != items.distinctBy { it.key }.size) Toast.makeText(context, R.string.rf_favorite_cloud, Toast.LENGTH_LONG).show()
            state.favorite = favorite
            state.groups = local.chunked(500)
            state.next()
        }
    }
}
