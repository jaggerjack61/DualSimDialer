package com.dualsimdialer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.dualsimdialer.app.data.ContactsRepository
import com.dualsimdialer.app.model.ContactDestination
import com.dualsimdialer.app.model.ContactSaveFailure
import com.dualsimdialer.app.model.ContactSaveResult
import com.dualsimdialer.app.model.ContactSummary
import com.dualsimdialer.app.model.SimProfile
import com.dualsimdialer.app.model.WritableContactAccount
import com.dualsimdialer.app.util.PhoneNumberUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactFormScreen(
    initialNumber: String,
    profiles: List<SimProfile>,
    repository: ContactsRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var number by remember(initialNumber) { mutableStateOf(initialNumber) }
    var target by remember { mutableStateOf(Target.Device) }
    var selectedAccount by remember { mutableStateOf<WritableContactAccount?>(null) }
    var selectedSim by remember { mutableStateOf<SimProfile?>(null) }
    var selectedExisting by remember { mutableStateOf<ContactSummary?>(null) }
    var accounts by remember { mutableStateOf<List<WritableContactAccount>>(emptyList()) }
    var contacts by remember { mutableStateOf<List<ContactSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        loading = true
        accounts = repository.writableAccounts()
        contacts = repository.loadContacts()
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Save contact") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Name") },
                    supportingText = { Text("Required") },
                )
            }
            item {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Phone number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
            item {
                Text("Save to", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Target.entries.take(3).forEach { option ->
                        FilterChip(
                            selected = target == option,
                            onClick = {
                                target = option
                                error = null
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = target == Target.Existing,
                    onClick = { target = Target.Existing; error = null },
                    label = { Text("Add to existing") },
                )
            }
            when (target) {
                Target.Device -> item {
                    DestinationInfo("Phone storage", "Creates a local RawContact on this device.")
                }
                Target.Google -> item {
                    val googleAccounts = accounts.filter { it.isGoogle }
                    if (googleAccounts.isEmpty()) {
                        DestinationInfo("No Google account", "Add a writable Google account to Android first; DualSimDialer does not perform OAuth or sync itself.", isError = true)
                    } else {
                        Text("Google account", style = MaterialTheme.typography.labelLarge)
                        googleAccounts.forEach { account ->
                            FilterChip(
                                selected = selectedAccount == account,
                                onClick = { selectedAccount = account },
                                label = { Text(account.label) },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                Target.Sim -> item {
                    if (profiles.isEmpty()) {
                        DestinationInfo("No SIM available", "An active writable SIM phonebook is required.", isError = true)
                    } else {
                        Text("SIM phonebook", style = MaterialTheme.typography.labelLarge)
                        profiles.forEach { profile ->
                            FilterChip(
                                selected = selectedSim == profile,
                                onClick = { selectedSim = profile },
                                label = { Text(profile.displayName) },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Text("SIM records validate dialable characters, 20-character number limits, encoded names, and capacity before inserting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Target.Existing -> item {
                    if (loading) CircularProgressIndicator()
                    else if (contacts.isEmpty()) DestinationInfo("No contacts available", "Add a contact first, then append another number.", isError = true)
                    else {
                        Text("Choose a contact", style = MaterialTheme.typography.labelLarge)
                        contacts.take(20).forEach { contact ->
                            FilterChip(
                                selected = selectedExisting == contact,
                                onClick = { selectedExisting = contact },
                                label = { Text(contact.displayName) },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Text("Writable account (optional for local contacts)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        accounts.forEach { account ->
                            FilterChip(
                                selected = selectedAccount == account,
                                onClick = { selectedAccount = account },
                                label = { Text(account.label) },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Text("SIM-only contacts are not editable here; use the SIM create flow instead.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            item {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        saving = true
                        error = null
                        scope.launch {
                            val destination = when (target) {
                                Target.Device -> ContactDestination.Device
                                Target.Google -> selectedAccount?.let { ContactDestination.Cloud(it.accountName, it.accountType) }
                                Target.Sim -> selectedSim?.let { ContactDestination.Sim(it.subscriptionId) }
                                Target.Existing -> selectedExisting?.let {
                                    ContactDestination.Existing(it.id, selectedAccount?.accountName, selectedAccount?.accountType)
                                }
                            }
                            val result = if (destination == null) {
                                ContactSaveResult.Failure(
                                    if (target == Target.Google) ContactSaveFailure.NoGoogleAccount
                                    else ContactSaveFailure.ProviderFailure,
                                )
                            } else repository.saveContact(name, number, destination)
                            saving = false
                            when (result) {
                                is ContactSaveResult.Success -> onSaved()
                                is ContactSaveResult.Failure -> error = result.reason.message
                            }
                        }
                    },
                    enabled = !saving && PhoneNumberUtils.normalize(number) != null && name.isNotBlank() && when (target) {
                        Target.Device -> true
                        Target.Google -> selectedAccount != null
                        Target.Sim -> selectedSim != null
                        Target.Existing -> selectedExisting != null
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    else Text("Save contact")
                }
            }
        }
    }
}

private enum class Target(val label: String) {
    Device("Phone storage"),
    Google("Google"),
    Sim("SIM"),
    Existing("Add to existing"),
}

@Composable
private fun DestinationInfo(title: String, detail: String, isError: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val ContactSaveFailure.message: String
    get() = when (this) {
        ContactSaveFailure.InvalidName -> "Enter a name."
        ContactSaveFailure.InvalidNumber -> "Enter a valid phone number."
        ContactSaveFailure.PermissionDenied -> "Contacts permission was denied."
        ContactSaveFailure.NoGoogleAccount -> "No writable Google account is configured on this device."
        ContactSaveFailure.SimUnavailable -> "The selected SIM phonebook is unavailable."
        ContactSaveFailure.SimReadOnly -> "The selected SIM phonebook is read-only."
        ContactSaveFailure.SimFull -> "The selected SIM phonebook is full."
        ContactSaveFailure.UnsupportedEncoding -> "This name cannot be encoded by the SIM phonebook."
        ContactSaveFailure.ProviderFailure -> "Android could not save this contact. Try again."
    }
