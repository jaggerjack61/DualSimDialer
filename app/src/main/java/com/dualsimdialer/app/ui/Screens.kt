package com.dualsimdialer.app.ui

import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.dualsimdialer.app.model.CallLogItem
import com.dualsimdialer.app.model.ContactSummary
import com.dualsimdialer.app.model.SimProfile
import com.dualsimdialer.app.util.ColorUtils
import com.dualsimdialer.app.util.PhoneNumberUtils
import com.dualsimdialer.app.util.formatCallTime
import com.dualsimdialer.app.util.formatDuration
import com.dualsimdialer.app.util.label

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    number: String,
    profiles: List<SimProfile>,
    suggestions: List<ContactSummary>,
    onNumberChanged: (String) -> Unit,
    onCall: (SimProfile) -> Unit,
    onSuggestion: (ContactSummary, String) -> Unit,
    onSaveContact: () -> Unit,
    permissionMissing: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (permissionMissing) {
                PermissionCard("Contacts permission enables name search and save-contact actions.", onRequestPermissions, onOpenSettings)
            }
            OutlinedTextField(
                value = number,
                onValueChange = onNumberChanged,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Phone number or contact search" },
                label = { Text("Number or contact") },
                placeholder = { Text("Enter a name or number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                trailingIcon = {
                    Row {
                        if (number.isNotEmpty()) {
                            IconButton(
                                onClick = { onNumberChanged(number.dropLast(1)) },
                                modifier = Modifier.semantics { contentDescription = "Backspace" },
                            ) { Icon(Icons.Default.Backspace, contentDescription = null) }
                            IconButton(
                                onClick = { onNumberChanged("") },
                                modifier = Modifier.semantics { contentDescription = "Clear number" },
                            ) { Icon(Icons.Default.Clear, contentDescription = null) }
                        }
                        IconButton(
                            onClick = onSaveContact,
                            enabled = PhoneNumberUtils.normalize(number) != null,
                        ) { Icon(Icons.Default.PersonAdd, contentDescription = "Save contact") }
                    }
                },
            )
            if (suggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("Matching contacts", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(8.dp))
                        val queryDigits = PhoneNumberUtils.digitsOnly(number)
                        suggestions.take(2).forEach { contact ->
                            val phone = contact.phoneNumbers.firstOrNull { candidate ->
                                queryDigits.isEmpty() || PhoneNumberUtils.digitsOnly(candidate.number).contains(queryDigits)
                            } ?: contact.phoneNumbers.firstOrNull()
                            if (phone != null) {
                                TextButton(
                                    onClick = { onSuggestion(contact, phone.number) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(
                                            contact.displayName,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(phone.number, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Emergency numbers are always handed to the system Phone app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DialKeypad(number = number, onNumberChanged = onNumberChanged)
            if (profiles.isEmpty()) {
                EmptyState(
                    "No active SIM accounts",
                    "A call-capable SIM will appear here when Android exposes it to Telecom.",
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Call with", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        profiles.take(2).forEach { profile ->
                            SimCallButton(
                                profile = profile,
                                enabled = PhoneNumberUtils.normalize(number) != null,
                                onClick = { onCall(profile) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DialKeypad(number: String, onNumberChanged: (String) -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val hint = when (key) {
                        "2" -> "ABC"
                        "3" -> "DEF"
                        "4" -> "GHI"
                        "5" -> "JKL"
                        "6" -> "MNO"
                        "7" -> "PQRS"
                        "8" -> "TUV"
                        "9" -> "WXYZ"
                        "0" -> "+ long press"
                        else -> ""
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .combinedClickable(
                                onClick = { onNumberChanged(number + key) },
                                onLongClick = if (key == "0") {
                                    { onNumberChanged(number + "+") }
                                } else null,
                            ),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 1.dp,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(key, style = MaterialTheme.typography.titleLarge)
                            if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimCallButton(
    profile: SimProfile,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val background = Color(profile.colorArgb)
    val foreground = Color(ColorUtils.foregroundFor(profile.colorArgb))
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp).semantics {
            contentDescription = "Call using ${profile.displayName}"
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = foreground,
            disabledContainerColor = background.copy(alpha = 0.35f),
            disabledContentColor = foreground.copy(alpha = 0.7f),
        ),
    ) {
        Icon(Icons.Default.Call, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text(profile.displayName, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    logs: List<CallLogItem>,
    profiles: List<SimProfile>,
    existingContactNumbers: Set<String>,
    query: String,
    onQueryChanged: (String) -> Unit,
    permissionMissing: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaveContact: (String) -> Unit,
    onRedial: (CallLogItem) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            if (permissionMissing) PermissionCard("Call log permission is required to show your live call history.", onRequestPermissions, onOpenSettings)
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search call history") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
        }
        if (logs.isEmpty()) {
            item { EmptyState("No calls yet", "Call history is read live from Android and is never copied into app storage.") }
        } else {
            items(logs, key = { it.id }) { item ->
                CallLogCard(
                    item = item,
                    profiles = profiles,
                    isExistingContact = isExistingContact(item, existingContactNumbers),
                    onSaveContact = onSaveContact,
                    onRedial = onRedial,
                )
            }
        }
    }
}

@Composable
private fun CallLogCard(
    item: CallLogItem,
    profiles: List<SimProfile>,
    isExistingContact: Boolean,
    onSaveContact: (String) -> Unit,
    onRedial: (CallLogItem) -> Unit,
) {
    val exact = profiles.firstOrNull { it.key == item.accountKey }
    Card {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(Modifier.size(42.dp), shape = CircleShape, color = when (item.type) {
                com.dualsimdialer.app.model.CallType.Missed -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(callIcon(item), contentDescription = item.type.label(), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        item.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SimBadge(
                        exact,
                        unavailable = item.accountKey != null && exact == null,
                        compact = true,
                    )
                }
                Text(
                    "${item.type.label()} · ${formatCallTime(item.timestamp)} · ${formatDuration(item.durationSeconds)}",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (item.number != null) onRedial(item) },
                    enabled = item.number != null,
                    modifier = Modifier.semantics { contentDescription = "Call ${item.title}" },
                ) { Icon(Icons.Default.Call, contentDescription = null) }
                if (item.number != null && !isExistingContact) IconButton(
                    onClick = { onSaveContact(item.number) },
                    modifier = Modifier.semantics { contentDescription = "Save ${item.title} as contact" },
                ) { Icon(Icons.Default.PersonAdd, contentDescription = null) }
            }
        }
    }
}

private fun isExistingContact(item: CallLogItem, contactNumbers: Set<String>): Boolean {
    if (!item.displayName.isNullOrBlank()) return true
    val number = item.number ?: return false
    val normalized = PhoneNumberUtils.normalize(number)
    if (normalized != null && normalized in contactNumbers) return true
    val digits = PhoneNumberUtils.digitsOnly(number)
    return digits.isNotEmpty() && digits in contactNumbers
}

private fun callIcon(item: CallLogItem) = when (item.type) {
    com.dualsimdialer.app.model.CallType.Missed,
    com.dualsimdialer.app.model.CallType.Rejected -> Icons.Default.CallMissed
    com.dualsimdialer.app.model.CallType.Incoming -> Icons.Default.CallReceived
    else -> Icons.Default.CallMade
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    contacts: List<ContactSummary>,
    query: String,
    onQueryChanged: (String) -> Unit,
    permissionMissing: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onContactSelected: (ContactSummary) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            if (permissionMissing) PermissionCard("Contacts permission is required to read the system address book.", onRequestPermissions, onOpenSettings)
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search contacts") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
        }
        if (contacts.isEmpty()) {
            item { EmptyState("No contacts found", "Your system contacts appear here alphabetically and stay in Android's provider.") }
        } else {
            items(contacts, key = { it.id }) { contact ->
                ContactListCard(contact, onClick = { onContactSelected(contact) })
            }
        }
    }
}

@Composable
private fun ContactListCard(contact: ContactSummary, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Initials(contact.displayName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(contact.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    contact.phoneNumbers.joinToString(" · ") { it.number },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Initials(name: String) {
    val initials = name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
    Surface(Modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Text(initials.ifEmpty { "?" }, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ContactDetailScreen(
    contact: ContactSummary,
    profiles: List<SimProfile>,
    onBack: () -> Unit,
    onCall: (SimProfile, String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Contacts") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(contact.displayName)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(contact.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${contact.phoneNumbers.size} number(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(contact.phoneNumbers) { _, phone ->
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(phone.number, style = MaterialTheme.typography.titleMedium)
                    phone.label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (profiles.isEmpty()) Text("No active SIM accounts", color = MaterialTheme.colorScheme.error)
                    profiles.forEach { profile ->
                        SimCallButton(profile, enabled = true, onClick = { onCall(profile, phone.number) })
                    }
                }
            }
        }
    }
}

@Composable
fun FallbackSimDialog(
    item: CallLogItem,
    profiles: List<SimProfile>,
    onDismiss: () -> Unit,
    onSelect: (SimProfile) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Original SIM unavailable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This log was placed through an account that is no longer active. Choose a SIM to call ${item.title}; DualSimDialer will not switch silently.")
                if (profiles.isEmpty()) Text("No active SIM accounts are available.", color = MaterialTheme.colorScheme.error)
                profiles.forEach { profile ->
                    TextButton(onClick = { onSelect(profile) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(profile.displayName)
                            Text("Select")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
