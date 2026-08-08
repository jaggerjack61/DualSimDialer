package com.dualsimdialer.app.telecom

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.dualsimdialer.app.DialerApplication
import com.dualsimdialer.app.model.AudioEndpointInfo
import com.dualsimdialer.app.model.InCallItem
import com.dualsimdialer.app.model.SimProfile
import com.dualsimdialer.app.ui.DualSimDialerTheme
import com.dualsimdialer.app.ui.SimBadge
import com.dualsimdialer.app.util.ColorUtils
import com.dualsimdialer.app.util.callControlState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InCallActivity : ComponentActivity() {
    private var pendingAction by mutableStateOf<String?>(null)
    private var pendingCallId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readAction(intent)
        val application = application as DialerApplication
        setContent {
            DualSimDialerTheme {
                InCallScreen(
                    application = application,
                    pendingAction = pendingAction,
                    pendingCallId = pendingCallId,
                    onActionHandled = { pendingAction = null; pendingCallId = null },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readAction(intent)
    }

    private fun readAction(intent: android.content.Intent?) {
        pendingAction = intent?.getStringExtra(EXTRA_ACTION)
        pendingCallId = intent?.getStringExtra(EXTRA_CALL_ID)
    }

    companion object {
        const val EXTRA_ACTION = "call_action"
        const val EXTRA_CALL_ID = "call_id"
        const val ACTION_ANSWER = "com.dualsimdialer.app.action.ANSWER"
        const val ACTION_DECLINE = "com.dualsimdialer.app.action.DECLINE"
    }
}

@Composable
private fun InCallScreen(
    application: DialerApplication,
    pendingAction: String?,
    pendingCallId: String?,
    onActionHandled: () -> Unit,
    onClose: () -> Unit,
) {
    val controller = application.container.callSessionController
    val calls by controller.calls.collectAsState()
    val muted by controller.muted.collectAsState()
    val endpoints by controller.endpoints.collectAsState()
    val profiles by produceState<List<SimProfile>>(emptyList(), application) {
        application.container.simRepository.observeProfiles().collect { value = it }
    }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var keypad by remember { mutableStateOf(false) }
    var endpointMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selected = calls.firstOrNull { it.id == selectedId } ?: calls.firstOrNull()

    LaunchedEffect(calls, pendingAction) {
        if (pendingAction != null && selected != null) {
            when (pendingAction) {
                InCallActivity.ACTION_ANSWER -> controller.answer(pendingCallId ?: selected.id)
                InCallActivity.ACTION_DECLINE -> controller.reject(pendingCallId ?: selected.id)
            }
            onActionHandled()
        }
        if (calls.isEmpty()) onClose()
    }

    if (selected == null) return
    val profile = profiles.firstOrNull { it.key == selected.accountKey }
    val controls = callControlState(selected.capabilities)
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TextButton(onClick = onClose) { Text("← DualSimDialer") }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CallerAvatar(selected)
                    Spacer(Modifier.height(14.dp))
                    Text(selected.callerName ?: selected.number ?: "Private number", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    selected.number?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.height(8.dp))
                    SimBadge(profile, unavailable = selected.accountKey != null && profile == null)
                    Text(callStateLabel(selected.state), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (calls.size > 1) {
                item {
                    Text("Multiple calls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(calls, key = { it.id }) { call ->
                    Card(
                        onClick = { selectedId = call.id },
                        colors = CardDefaults.cardColors(
                            containerColor = if (call.id == selected.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(call.callerName ?: call.number ?: "Unknown")
                                Text(callStateLabel(call.state), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(if (call.id == selected.id) "Selected" else "Switch", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (selected.state == android.telecom.Call.STATE_RINGING) {
                item {
                    Button(
                        onClick = { controller.answer(selected.id) },
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                    ) {
                        Text("Answer", fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallActionButton(
                        icon = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (muted) "Unmute" else "Mute",
                        enabled = controls.canMute,
                        onClick = { controller.toggleMute() },
                    )
                    CallActionButton(
                        icon = if (keypad) Icons.Default.Keyboard else Icons.Default.Keyboard,
                        label = "Keypad",
                        enabled = true,
                        onClick = { keypad = !keypad },
                    )
                    AudioButton(
                        endpoints = endpoints,
                        expanded = endpointMenu,
                        onExpand = { endpointMenu = true },
                        onDismiss = { endpointMenu = false },
                        onSelect = { endpoint ->
                            endpointMenu = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) controller.selectEndpoint(endpoint.id)
                            else controller.setLegacyAudioRoute(endpoint.type)
                        },
                    )
                }
            }
            if (keypad) {
                item {
                    DtmfKeypad(
                        onPress = { digit ->
                            controller.playDtmf(digit, selected.id)
                            scope.launch { delay(180); controller.stopDtmf(selected.id) }
                        },
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (controls.canHold) OutlinedButton(
                        onClick = { if (selected.state == android.telecom.Call.STATE_HOLDING) controller.resume(selected.id) else controller.hold(selected.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(if (selected.state == android.telecom.Call.STATE_HOLDING) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (selected.state == android.telecom.Call.STATE_HOLDING) "Resume" else "Hold")
                    }
                    if (controls.canSwap) OutlinedButton(onClick = { controller.swap(selected.id) }, modifier = Modifier.weight(1f)) { Text("Swap") }
                    if (controls.canMerge) OutlinedButton(onClick = { controller.merge(selected.id) }, modifier = Modifier.weight(1f)) { Text("Merge") }
                }
            }
            item {
                Button(
                    onClick = { controller.end(selected.id) },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selected.state == android.telecom.Call.STATE_RINGING) "Decline" else "End call")
                }
            }
        }
    }
}

@Composable
private fun CallerAvatar(item: InCallItem) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, item.photoUri) {
        value = item.photoUri?.let { uri ->
            runCatching {
                // Photos remain provider-backed; this bitmap is only held while the call screen is visible.
                context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }
    Surface(Modifier.size(112.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = "Caller photo", contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) {
            Text(
                item.callerName?.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun CallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = label) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AudioButton(
    endpoints: List<AudioEndpointInfo>,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (AudioEndpointInfo) -> Unit,
) {
    Box {
        CallActionButton(Icons.Default.VolumeUp, "Audio", endpoints.isNotEmpty(), onExpand)
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            endpoints.forEach { endpoint ->
                DropdownMenuItem(
                    text = { Text(if (endpoint.isSelected) "✓ ${endpoint.label}" else endpoint.label) },
                    onClick = { onSelect(endpoint) },
                )
            }
        }
    }
}

@Composable
private fun DtmfKeypad(onPress: (Char) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    OutlinedButton(onClick = { onPress(key.single()) }, modifier = Modifier.weight(1f)) { Text(key) }
                }
            }
        }
    }
}

private fun callStateLabel(state: Int): String = when (state) {
    android.telecom.Call.STATE_RINGING -> "Incoming call"
    android.telecom.Call.STATE_DIALING -> "Dialing"
    android.telecom.Call.STATE_CONNECTING -> "Connecting"
    android.telecom.Call.STATE_ACTIVE -> "Connected"
    android.telecom.Call.STATE_HOLDING -> "On hold"
    android.telecom.Call.STATE_DISCONNECTING -> "Ending"
    else -> "Call"
}
