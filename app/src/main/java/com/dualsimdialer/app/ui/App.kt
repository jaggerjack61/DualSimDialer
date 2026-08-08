package com.dualsimdialer.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dualsimdialer.app.DialerApplication
import com.dualsimdialer.app.data.ContactsRepository
import com.dualsimdialer.app.model.CallLogItem
import com.dualsimdialer.app.model.CallResult
import com.dualsimdialer.app.model.ContactSummary
import com.dualsimdialer.app.model.PhoneAccountKey
import com.dualsimdialer.app.model.SimProfile
import com.dualsimdialer.app.util.ColorUtils
import kotlinx.coroutines.launch

private enum class AppTab(val label: String) {
    Dialer("Dialer"),
    Logs("Logs"),
    Contacts("Contacts"),
}

@Composable
fun DualSimDialerRoot(
    application: DialerApplication,
    isDefaultDialer: Boolean,
    permissionMessage: String?,
    permissionsVersion: Int,
    initialNumber: String?,
    onRequestDefaultDialer: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onPermissionMessageConsumed: () -> Unit,
) {
    DualSimDialerTheme {
        if (!isDefaultDialer) {
            SetupScreen(
                onRequestDefaultDialer = onRequestDefaultDialer,
                onOpenSystemSettings = onOpenSystemSettings,
            )
        } else {
            MainShell(
                application = application,
                permissionMessage = permissionMessage,
                permissionsVersion = permissionsVersion,
                initialNumber = initialNumber,
                onRequestPermissions = onRequestPermissions,
                onOpenSystemSettings = onOpenSystemSettings,
                onPermissionMessageConsumed = onPermissionMessageConsumed,
            )
        }
    }
}

@Composable
private fun SetupScreen(
    onRequestDefaultDialer: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("DualSimDialer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Set up your phone",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Make DualSimDialer your default Phone app to route calls through the exact SIM you choose and present incoming calls safely.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SetupRow("Default Phone role", "Required for call routing and call screens")
                    SetupRow("Contacts and call log", "Requested only after the role is granted")
                    SetupRow("SIM aliases and colors", "Stored locally in app preferences only")
                }
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onRequestDefaultDialer,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Make default Phone app") }
            TextButton(onClick = onOpenSystemSettings) { Text("Open default app settings") }
        }
    }
}

@Composable
private fun SetupRow(title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Surface(Modifier.size(8.dp).padding(top = 5.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    application: DialerApplication,
    permissionMessage: String?,
    permissionsVersion: Int,
    initialNumber: String?,
    onRequestPermissions: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onPermissionMessageConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Dialer.name) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showContactForm by rememberSaveable { mutableStateOf(false) }
    var contactFormNumber by rememberSaveable { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<ContactSummary?>(null) }
    var fallbackLog by remember { mutableStateOf<CallLogItem?>(null) }
    var dialedNumber by rememberSaveable { mutableStateOf(initialNumber.orEmpty()) }
    var logQuery by rememberSaveable { mutableStateOf("") }
    var contactQuery by rememberSaveable { mutableStateOf("") }
    val tab = AppTab.entries.firstOrNull { it.name == selectedTab } ?: AppTab.Dialer
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(initialNumber) {
        if (!initialNumber.isNullOrBlank()) dialedNumber = initialNumber
    }
    LaunchedEffect(permissionMessage) {
        if (!permissionMessage.isNullOrBlank()) snackbar.showSnackbar(permissionMessage)
    }

    val profiles by produceState<List<SimProfile>>(emptyList(), application, permissionsVersion) {
        application.container.simRepository.observeProfiles().collect { value = it }
    }
    val logs by produceState<List<CallLogItem>>(emptyList(), application, logQuery, permissionsVersion) {
        application.container.callLogRepository.observe(logQuery).collect { value = it }
    }
    val contacts by produceState<List<ContactSummary>>(emptyList(), application, contactQuery, permissionsVersion) {
        application.container.contactsRepository.observeContacts(contactQuery).collect { value = it }
    }
    val suggestions by produceState<List<ContactSummary>>(emptyList(), application, dialedNumber, permissionsVersion) {
        if (dialedNumber.isBlank()) value = emptyList()
        else application.container.contactsRepository.loadContacts(dialedNumber).let { value = it.take(2) }
    }
    val hasContactsPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
    val hasCallLogPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALL_LOG,
    ) == PackageManager.PERMISSION_GRANTED

    BackHandler(enabled = showSettings || showContactForm || selectedContact != null) {
        when {
            selectedContact != null -> selectedContact = null
            showContactForm -> showContactForm = false
            else -> showSettings = false
        }
    }

    if (showSettings) {
        SimSettingsScreen(
            profiles = profiles,
            preferences = application.container.simPreferencesRepository,
            onBack = { showSettings = false },
        )
        return
    }
    if (showContactForm) {
        ContactFormScreen(
            initialNumber = contactFormNumber,
            profiles = profiles,
            repository = application.container.contactsRepository,
            onBack = { showContactForm = false },
            onSaved = {
                showContactForm = false
                scope.launch { snackbar.showSnackbar("Contact saved") }
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("DualSimDialer", fontWeight = FontWeight.Bold)
                        Text(tab.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.semantics { contentDescription = "SIM settings" },
                    ) { Icon(Icons.Default.Settings, contentDescription = null) }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { selectedTab = item.name; selectedContact = null },
                        icon = {
                            when (item) {
                                AppTab.Dialer -> Icon(Icons.Default.Phone, contentDescription = null)
                                AppTab.Logs -> Icon(Icons.Default.History, contentDescription = null)
                                AppTab.Contacts -> Icon(Icons.Default.Contacts, contentDescription = null)
                            }
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == AppTab.Contacts && selectedContact == null) {
                FloatingActionButton(
                    onClick = { contactFormNumber = ""; showContactForm = true },
                    modifier = Modifier.semantics { contentDescription = "Add contact" },
                ) { Text("+") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                selectedContact != null -> ContactDetailScreen(
                    contact = selectedContact!!,
                    profiles = profiles,
                    onBack = { selectedContact = null },
                    onCall = { profile, number ->
                        handleCall(application, profile, number) { message -> scope.launch { snackbar.showSnackbar(message) } }
                    },
                )
                tab == AppTab.Dialer -> DialerScreen(
                    number = dialedNumber,
                    profiles = profiles,
                    suggestions = suggestions,
                    onNumberChanged = { dialedNumber = it },
                    onCall = { profile ->
                        handleCall(application, profile, dialedNumber) { message -> scope.launch { snackbar.showSnackbar(message) } }
                    },
                    onSuggestion = { contact, phone -> dialedNumber = phone },
                    onSaveContact = { contactFormNumber = dialedNumber; showContactForm = true },
                    permissionMissing = !hasContactsPermission,
                    onRequestPermissions = onRequestPermissions,
                    onOpenSettings = onOpenSystemSettings,
                )
                tab == AppTab.Logs -> LogsScreen(
                    logs = logs,
                    profiles = profiles,
                    query = logQuery,
                    onQueryChanged = { logQuery = it },
                    permissionMissing = !hasCallLogPermission,
                    onRequestPermissions = onRequestPermissions,
                    onOpenSettings = onOpenSystemSettings,
                    onSaveContact = { number -> contactFormNumber = number; showContactForm = true },
                    onRedial = { item ->
                        val exact = profiles.firstOrNull { it.key == item.accountKey }
                        if (exact != null) {
                            handleCall(application, exact, item.number.orEmpty()) { message -> scope.launch { snackbar.showSnackbar(message) } }
                        } else {
                            fallbackLog = item
                        }
                    },
                )
                else -> ContactsScreen(
                    contacts = contacts,
                    query = contactQuery,
                    onQueryChanged = { contactQuery = it },
                    permissionMissing = !hasContactsPermission,
                    onRequestPermissions = onRequestPermissions,
                    onOpenSettings = onOpenSystemSettings,
                    onContactSelected = { selectedContact = it },
                )
            }
        }
    }

    fallbackLog?.let { item ->
        FallbackSimDialog(
            item = item,
            profiles = profiles,
            onDismiss = { fallbackLog = null },
            onSelect = { profile ->
                fallbackLog = null
                handleCall(application, profile, item.number.orEmpty()) { message -> scope.launch { snackbar.showSnackbar(message) } }
            },
        )
    }
}

private fun handleCall(
    application: DialerApplication,
    profile: SimProfile,
    number: String,
    showMessage: (String) -> Unit,
) {
    when (val result = application.container.callRouter.placeCall(number, profile)) {
        CallResult.Placed -> showMessage("Calling on ${profile.displayName}")
        CallResult.EmergencyDelegated -> showMessage("Emergency dialing delegated to the system Phone app")
        is CallResult.InvalidNumber -> showMessage(result.message)
        CallResult.UnavailableAccount -> showMessage("${profile.displayName} is no longer available")
        CallResult.PermissionDenied -> showMessage("Allow Phone permissions and keep DualSimDialer as the default Phone app")
        is CallResult.Failed -> showMessage(result.message)
    }
}

@Composable
fun SimBadge(profile: SimProfile?, unavailable: Boolean = false) {
    val color = profile?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.outline
    val label = when {
        unavailable -> "Unavailable"
        profile != null -> profile.displayName
        else -> "Other provider"
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        leadingIcon = {
            Surface(Modifier.size(12.dp), shape = CircleShape, color = color) {}
        },
    )
}

@Composable
fun PermissionCard(
    text: String,
    onRequest: () -> Unit,
    onSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequest) { Text("Allow") }
                TextButton(onClick = onSettings) { Text("Settings") }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
