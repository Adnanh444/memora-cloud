package com.adnan.memora.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.adnan.memora.R
import com.adnan.memora.telegram.StorageStatus
import com.adnan.memora.telegram.TelegramClient
import com.adnan.memora.telegram.TelegramConfig
import com.adnan.memora.telegram.TgAuth
import com.adnan.memora.ui.theme.appear
import com.adnan.memora.ui.theme.glass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tg = remember { TelegramClient.get(ctx) }
    val auth by tg.auth.collectAsState()
    val me by tg.me.collectAsState()
    val storage by tg.storage.collectAsState()
    val status by tg.storageStatus.collectAsState()
    val busy by tg.busy.collectAsState()
    val error by tg.error.collectAsState()
    var confirmCreate by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { tg.clearError(); tg.start() }

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
                        Text(stringResource(R.string.tg_account), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(me?.name?.ifBlank { null } ?: "…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (!me?.phone.isNullOrBlank()) Text("+" + me?.phone?.trimStart('+'), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val st = storage
                    if (st == null) {
                        Column(Modifier.fillMaxWidth().appear(80).glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.tg_no_storage_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.tg_no_storage_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { confirmCreate = true }, enabled = !busy && me != null, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.tg_create))
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().appear(80).glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.tg_storage), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(st.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.tg_id_label) + ": " + st.storageId.take(8), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            when (val s = status) {
                                is StorageStatus.Verified -> StatusLine(Icons.Filled.CheckCircle, stringResource(R.string.tg_status_ok), MaterialTheme.colorScheme.primary)
                                is StorageStatus.Checking -> StatusLine(Icons.Filled.HourglassTop, stringResource(R.string.tg_status_checking), MaterialTheme.colorScheme.onSurfaceVariant)
                                is StorageStatus.Problem -> StatusLine(Icons.Filled.Error, stringResource(R.string.tg_status_problem, s.message), MaterialTheme.colorScheme.error)
                                else -> StatusLine(Icons.Filled.HourglassTop, stringResource(R.string.tg_status_unknown), MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { tg.verifyStorage() }) {
                                    Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.tg_verify_access))
                                }
                                TextButton(onClick = { confirmForget = true }) { Text(stringResource(R.string.tg_forget)) }
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

@Composable
private fun CredentialsStep(onSave: (String, String) -> Unit) {
    val ctx = LocalContext.current
    var id by rememberSaveable { mutableStateOf("") }
    var hash by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().appear().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tg_cred_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.tg_cred_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org"))) } catch (_: Exception) {}
        }) { Text(stringResource(R.string.tg_open_site)) }
        OutlinedTextField(
            value = id, onValueChange = { v -> id = v.filter { it.isDigit() } },
            label = { Text(stringResource(R.string.tg_api_id_label)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = hash, onValueChange = { hash = it.trim() },
            label = { Text(stringResource(R.string.tg_api_hash_label)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSave(id, hash) }, enabled = id.isNotEmpty() && hash.length >= 32, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.tg_cred_save))
        }
        Text(stringResource(R.string.tg_cred_privacy), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhoneStep(busy: Boolean, onSubmit: (String) -> Unit) {
    var phone by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().appear().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tg_connect_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.tg_connect_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = phone, onValueChange = { v -> phone = v.filter { it.isDigit() || it == '+' } },
            label = { Text(stringResource(R.string.tg_phone_label)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSubmit(phone) }, enabled = !busy && phone.length >= 6, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.tg_send_code))
        }
    }
}

@Composable
private fun CodeStep(busy: Boolean, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().appear().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tg_code_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.tg_code_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = code, onValueChange = { v -> code = v.filter { it.isDigit() } },
            label = { Text(stringResource(R.string.tg_code_label)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSubmit(code) }, enabled = !busy && code.length >= 4, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.tg_verify))
        }
    }
}

@Composable
private fun PasswordStep(hint: String, busy: Boolean, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().appear().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tg_password_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (hint.isNotBlank()) Text(stringResource(R.string.tg_password_hint, hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(stringResource(R.string.tg_password_label)) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSubmit(password) }, enabled = !busy && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.tg_verify))
        }
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
