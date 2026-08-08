package com.dualsimdialer.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dualsimdialer.app.ui.DualSimDialerRoot

class MainActivity : ComponentActivity() {
    private var isDefaultDialer by mutableStateOf(false)
    private var permissionMessage by mutableStateOf<String?>(null)
    private var permissionsVersion by mutableStateOf(0)
    private var permissionsRequestedForRole = false
    private var incomingNumber by mutableStateOf<String?>(null)

    private val roleManager by lazy { getSystemService(RoleManager::class.java) }
    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshRole()
        if (isDefaultDialer) requestMissingPermissions()
        else {
            permissionsRequestedForRole = false
            permissionMessage = "DualSimDialer must be the default Phone app to continue."
        }
    }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permissionsVersion++
        permissionMessage = if (results.values.any { !it }) {
            "Some permissions were declined. You can grant them later in Settings."
        } else null
        refreshRole()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingNumber = intent?.data?.schemeSpecificPart?.takeIf { intent?.action == Intent.ACTION_DIAL }
        refreshRole()
        setContent {
            DualSimDialerRoot(
                application = application as DialerApplication,
                isDefaultDialer = isDefaultDialer,
                permissionMessage = permissionMessage,
                permissionsVersion = permissionsVersion,
                initialNumber = incomingNumber,
                onRequestDefaultDialer = ::requestDefaultDialer,
                onRequestPermissions = { requestMissingPermissions(force = true) },
                onOpenSystemSettings = ::openDefaultAppsSettings,
                onPermissionMessageConsumed = { permissionMessage = null },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRole()
        permissionsVersion++
        if (isDefaultDialer) requestMissingPermissions()
        else permissionsRequestedForRole = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.action == Intent.ACTION_DIAL) {
            incomingNumber = intent.data?.schemeSpecificPart
        }
    }

    private fun refreshRole() {
        isDefaultDialer = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }

    private fun requestDefaultDialer() {
        val manager = roleManager
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            openDefaultAppsSettings()
            return
        }
        roleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
    }

    private fun requestMissingPermissions(force: Boolean = false) {
        if (!isDefaultDialer || (permissionsRequestedForRole && !force)) return
        permissionsRequestedForRole = true
        val permissions = buildList {
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_PHONE_NUMBERS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        permissionsVersion++
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun openDefaultAppsSettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }
}
