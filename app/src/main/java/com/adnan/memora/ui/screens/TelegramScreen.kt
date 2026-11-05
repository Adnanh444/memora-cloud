package com.adnan.memora.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adnan.memora.R
import com.adnan.memora.telegram.StorageStatus
import com.adnan.memora.telegram.TelegramClient
import com.adnan.memora.telegram.TelegramConfig
import com.adnan.memora.telegram.TgAuth
import com.adnan.memora.ui.theme.appear
import com.adnan.memora.ui.theme.glass
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramScreen(onBack: () -> Unit, onRecover: () -> Unit) {
    val ctx = LocalContext.current
    val tg = remember { TelegramClient.get(ctx) }
    val auth by tg.auth.collectAsState()
    val me by tg.me.collectAsState()
    val storage by tg.storage.collectAsState()
    val status by tg.storageStatus.collectAsState()
    val busy by tg.busy.collectAsState()
    val error by tg.error.collectAsState()
    val actionScope = rememberCoroutineScope()
    var confirmQuiet by remember { mutableStateOf(false) }
    var quietBusy by remember { mutableStateOf(false) }
    var quietResult by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    var confirmCreate by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    // Account and channel details stay hidden until the user confirms with the phone's screen lock.
    var revealed by remember { mutableStateOf(false) }
    val revealLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) revealed = true
    }
    fun askReveal() {
        val km = ctx.getSystemService(KeyguardManager::class.java)
        val i = km?.createConfirmDeviceCredentialIntent(ctx.getString(R.string.tg_show_details), ctx.getString(R.string.tg_reveal_desc))
        if (i != null) revealLauncher.launch(i) else Toast.makeText(ctx, R.string.tg_need_lock, Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(Unit) { tg.clearError(); tg.start() }

    if (confirmQuiet) ConfirmSheet(
        title = stringResource(R.string.mx_archive_mute),
        body = stringResource(R.string.mx_archive_confirm),
        action = stringResource(R.string.mx_archive_mute),
        onConfirm = {
            actionScope.launch {
                quietBusy = true
                try { quietResult = tg.archiveMuteBackupChannel() }
                finally { quietBusy = false }
            }
        },
        onDismiss = { confirmQuiet = false },
    )
    if (confirmCreate) ConfirmSheet(
        title = stringResource(R.string.tg_create_confirm_title),
        body = stringResource(R.string.tg_create_confirm_body),
        action = stringResource(R.string.tg_create_action),
        onConfirm = { tg.createStorage() },
        onDismiss = { confirmCreate = false },
    )
    if (confirmForget) ConfirmSheet(
        title = stringResource(R.string.tg_forget),
        body = stringResource(R.string.tg_forget_body),
        action = stringResource(R.string.tg_forget),
        onConfirm = { tg.forgetStorage() },
        onDismiss = { confirmForget = false },
    )
    if (confirmLogout) ConfirmSheet(
        title = stringResource(R.string.tg_logout),
        body = stringResource(R.string.tg_logout_body),
        action = stringResource(R.string.tg_logout),
        onConfirm = { tg.logOut() },
        onDismiss = { confirmLogout = false },
    )

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.telegram_storage)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val a = auth) {
                is TgAuth.NeedCredentials -> CredentialsStep { id, hash -> tg.saveCredentials(id, hash) }
                is TgAuth.Starting -> Row(Modifier.fillMaxWidth().glass().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.tg_starting))
                }
                is TgAuth.Failed -> InfoCard(Icons.Filled.Error, R.string.tg_failed_title, a.message)
                is TgAuth.NeedPhone -> PhoneStep(busy) { tg.submitPhone(it) }
                is TgAuth.NeedCode -> CodeStep(busy) { tg.submitCode(it) }
                is TgAuth.NeedPassword -> PasswordStep(a.hint, busy) { tg.submitPassword(it) }
                is TgAuth.Ready -> {
                    Column(Modifier.fillMaxWidth().appear().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusLine(Icons.Filled.CheckCircle, stringResource(R.string.tg_connected_msg), MaterialTheme.colorScheme.primary)
                        if (revealed) {
                            Text(stringResource(R.string.tg_account), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                            Text(me?.name?.ifBlank { null } ?: "…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (!me?.phone.isNullOrBlank()) Text("+" + me?.phone?.trimStart('+'), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            OutlinedButton(onClick = { askReveal() }, modifier = Modifier.padding(top = 8.dp)) {
                                Text(stringResource(R.string.tg_show_details))
                            }
                        }
                    }
                    val st = storage
                    if (st == null) {
                        Column(Modifier.fillMaxWidth().appear(80).glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.tg_no_storage_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.tg_no_storage_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { confirmCreate = true }, enabled = !busy && me != null, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.tg_create))
                            }
                            OutlinedButton(onClick = onRecover, enabled = !busy && me != null, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.rc_restore_existing))
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().appear(80).glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.tg_storage), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            if (revealed) {
                                Text(st.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.tg_id_label) + ": " + st.storageId.take(8), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            when (val s = status) {
                                is StorageStatus.Verified -> StatusLine(Icons.Filled.CheckCircle, stringResource(R.string.tg_status_ok), MaterialTheme.colorScheme.primary)
                                is StorageStatus.Checking -> StatusLine(Icons.Filled.HourglassTop, stringResource(R.string.tg_status_checking), MaterialTheme.colorScheme.onSurfaceVariant)
                                is StorageStatus.Problem -> StatusLine(Icons.Filled.Error, stringResource(R.string.tg_status_problem, s.message), MaterialTheme.colorScheme.error)
                                else -> StatusLine(Icons.Filled.HourglassTop, stringResource(R.string.tg_status_unknown), MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(stringResource(R.string.mx_channel_warning), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface)
                            OutlinedButton(onClick = { confirmQuiet = true }, enabled = !quietBusy && status is StorageStatus.Verified) {
                                Icon(Icons.Filled.Archive, null); Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.mx_archive_mute))
                            }
                            quietResult?.let { result ->
                                Text(stringResource(R.string.mx_channel_done,
                                    stringResource(if (result.first) R.string.mx_ok else R.string.mx_not_confirmed),
                                    stringResource(if (result.second) R.string.mx_ok else R.string.mx_not_confirmed)),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { tg.verifyStorage() }) {
                                    Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.tg_verify_access))
                                }
                                TextButton(onClick = { confirmForget = true }) { Text(stringResource(R.string.tg_forget)) }
                            }
                            TextButton(onClick = { com.adnan.memora.backup.RecoveryEngine.rebuildCurrent(ctx); onRecover() }) {
                                Text(stringResource(R.string.rc_rebuild))
                            }
                        }
                    }
                    TextButton(onClick = { confirmLogout = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tg_logout), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if ((auth is TgAuth.NeedPhone || auth is TgAuth.NeedCode) && TelegramConfig.hasUserCredentials(ctx)) {
                TextButton(onClick = { tg.changeCredentials() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.tg_cred_change))
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, body: String) {
    Column(Modifier.fillMaxWidth().appear().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

/** Three-segment progress indicator shown while connecting. Purely visual, no state. */
@Composable
private fun StepDots(step: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val active = i <= step
            Box(
                Modifier.height(4.dp).weight(1f).clip(RoundedCornerShape(50))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            )
        }
    }
}

/**
 * Shared frame for the sign-in steps: step indicator, one big icon, a title/body pair and the
 * form content in a soft card. Layout only; every action is supplied by the caller.
 */
@Composable
private fun StepShell(
    step: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: Int,
    body: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().appear(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepDots(step)
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                Alignment.Center,
            ) { Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
        Column(Modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}

/** Primary action button; disabled until the form is valid and shows its own progress while working. */
@Composable
private fun StepButton(label: Int, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (busy) {
            CircularProgressIndicator(
                Modifier.size(18.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun PasteButton(onPaste: (String) -> Unit) {
    val cb = androidx.compose.ui.platform.LocalClipboardManager.current
    TextButton(onClick = {
        val text = cb.getText()?.text?.trim().orEmpty()
        if (text.isNotEmpty()) onPaste(text)
    }) { Text(stringResource(R.string.tg_paste)) }
}

@Composable
private fun CredentialsStep(onSave: (String, String) -> Unit) {
    val ctx = LocalContext.current
    var id by rememberSaveable { mutableStateOf("") }
    var hash by rememberSaveable { mutableStateOf("") }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val idOk = id.length in 3..12
    val hashOk = hash.length >= 32
    StepShell(
        step = 0, icon = Icons.Filled.Key,
        title = R.string.tg_cred_title, body = R.string.tg_cred_body,
    ) {
        OutlinedTextField(
            value = id, onValueChange = { v -> id = v.filter { it.isDigit() }.take(12) },
            label = { Text(stringResource(R.string.tg_api_id_label)) }, singleLine = true,
            isError = id.isNotEmpty() && !idOk,
            supportingText = { if (id.isNotEmpty() && !idOk) Text(stringResource(R.string.tg_id_invalid)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            trailingIcon = { PasteButton { id = it.filter { c -> c.isDigit() }.take(12) } },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = hash, onValueChange = { v -> hash = v.trim().take(64) },
            label = { Text(stringResource(R.string.tg_api_hash_label)) }, singleLine = true,
            isError = hash.isNotEmpty() && !hashOk,
            supportingText = { if (hash.isNotEmpty() && !hashOk) Text(stringResource(R.string.tg_hash_invalid)) },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            trailingIcon = { PasteButton { hash = it.trim().take(64) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showHelp = !showHelp }) { Text(stringResource(R.string.tg_cred_how)) }
            TextButton(onClick = {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org"))) } catch (_: Exception) {}
            }) { Text(stringResource(R.string.tg_open_site)) }
        }
        if (showHelp) {
            Text(
                stringResource(R.string.tg_cred_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StepButton(R.string.tg_cred_save, enabled = idOk && hashOk, busy = false) { onSave(id, hash) }
    }
}

@Composable
private fun PhoneStep(busy: Boolean, onSubmit: (String) -> Unit) {
    var phone by rememberSaveable { mutableStateOf("") }
    val ok = phone.count { it.isDigit() } >= 6
    StepShell(
        step = 1, icon = Icons.Filled.PhoneAndroid,
        title = R.string.tg_connect_title, body = R.string.tg_connect_body,
    ) {
        OutlinedTextField(
            value = phone, onValueChange = { v -> phone = v.filter { it.isDigit() || it == '+' } },
            label = { Text(stringResource(R.string.tg_phone_label)) }, singleLine = true,
            isError = phone.isNotEmpty() && !ok,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.tg_phone_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        StepButton(R.string.tg_send_code, enabled = ok, busy = busy) { onSubmit(phone) }
    }
}

@Composable
private fun CodeStep(busy: Boolean, onSubmit: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val ok = code.length >= 4
    StepShell(
        step = 1, icon = Icons.Filled.Sms,
        title = R.string.tg_code_title, body = R.string.tg_code_body,
    ) {
        OutlinedTextField(
            value = code, onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
            label = { Text(stringResource(R.string.tg_code_label)) }, singleLine = true,
            isError = code.isNotEmpty() && !ok,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, letterSpacing = 6.sp),
            trailingIcon = { PasteButton { code = it.filter { c -> c.isDigit() }.take(6) } },
            modifier = Modifier.fillMaxWidth(),
        )
        StepButton(R.string.tg_verify, enabled = ok, busy = busy) { onSubmit(code) }
    }
}

@Composable
private fun PasswordStep(hint: String, busy: Boolean, onSubmit: (String) -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    StepShell(
        step = 1, icon = Icons.Filled.Lock,
        title = R.string.tg_password_title, body = R.string.tg_password_body,
    ) {
        if (hint.isNotBlank()) {
            Text(stringResource(R.string.tg_password_hint, hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(stringResource(R.string.tg_password_label)) }, singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        stringResource(if (visible) R.string.tg_hide_password else R.string.tg_show_password),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        StepButton(R.string.tg_verify, enabled = password.isNotEmpty(), busy = busy) { onSubmit(password) }
    }
}

@Composable
private fun ConfirmSheet(title: String, body: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MemoraSheet(onDismiss) { close ->
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp))
        Button(onClick = { close(onConfirm) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { Text(action) }
        TextButton(onClick = { close {} }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.cancel))
        }
    }
}
