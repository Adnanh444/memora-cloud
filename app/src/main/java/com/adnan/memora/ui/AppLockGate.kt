package com.adnan.memora.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.adnan.memora.R
import com.adnan.memora.data.AppLockPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppLockSettings {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled
    var sessionUnlocked = false
        private set
    var backgroundAt = 0L
    fun requireUnlock() { sessionUnlocked = false }
    fun initialize(ctx: Context) { _enabled.value = ctx.getSharedPreferences("memora_security", Context.MODE_PRIVATE).getBoolean("app_lock", false) }
    fun authenticated() { sessionUnlocked = true; backgroundAt = 0L }
    fun setEnabled(ctx: Context, value: Boolean) {
        authenticated()
        ctx.getSharedPreferences("memora_security", Context.MODE_PRIVATE).edit().putBoolean("app_lock", value).apply()
        _enabled.value = value
    }
}
private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx.activity()
    val enabled by AppLockSettings.enabled.collectAsState()
    val expired = AppLockSettings.backgroundAt > 0 && AppLockPolicy.afterBackground(enabled, SystemClock.elapsedRealtime() - AppLockSettings.backgroundAt)
    var locked by remember { mutableStateOf(enabled && (!AppLockSettings.sessionUnlocked || expired)) }
    var authenticating by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        authenticating = false
        if (result.resultCode == Activity.RESULT_OK) { AppLockSettings.authenticated(); locked = false }
    }
    LaunchedEffect(enabled) {
        if (enabled && expired) AppLockSettings.requireUnlock()
        locked = enabled && !AppLockSettings.sessionUnlocked
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (enabled && !authenticating && activity?.isChangingConfigurations != true) AppLockSettings.backgroundAt = SystemClock.elapsedRealtime()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        val leftAt = AppLockSettings.backgroundAt
        if (leftAt > 0 && AppLockPolicy.afterBackground(enabled, SystemClock.elapsedRealtime() - leftAt)) {
            AppLockSettings.requireUnlock(); locked = true
        }
        AppLockSettings.backgroundAt = 0L
    }
    DisposableEffect(enabled, activity) {
        val window = activity?.window
        val wasSecure = window != null && window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (enabled && !wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    content() // Preserve navigation and registered Android consent launchers behind the modal lock.
    if (enabled && locked) Dialog(
        onDismissRequest = { activity?.moveTaskToBack(true) },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.lp_app_lock), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.lp_lock_description), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                Button(enabled = !authenticating, onClick = {
                    val manager = ctx.getSystemService(KeyguardManager::class.java)
                    val intent = manager?.createConfirmDeviceCredentialIntent(ctx.getString(R.string.lp_app_lock), ctx.getString(R.string.lp_unlock))
                    if (intent != null) { authenticating = true; launcher.launch(intent) }
                    else Toast.makeText(ctx, R.string.tg_need_lock, Toast.LENGTH_LONG).show()
                }) { Text(stringResource(R.string.lp_unlock)) }
            }
        }
    }
}

/** Changing the lock setting also needs the phone owner's credential; no separate PIN is stored. */
@Composable
fun rememberAppLockToggle(): () -> Unit {
    val ctx = LocalContext.current
    var requested by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) AppLockSettings.setEnabled(ctx, requested)
    }
    return {
        requested = !AppLockSettings.enabled.value
        val manager = ctx.getSystemService(KeyguardManager::class.java)
        val intent = manager?.createConfirmDeviceCredentialIntent(ctx.getString(R.string.lp_app_lock), ctx.getString(R.string.lp_unlock))
        if (intent != null) launcher.launch(intent) else Toast.makeText(ctx, R.string.tg_need_lock, Toast.LENGTH_LONG).show()
    }
}
