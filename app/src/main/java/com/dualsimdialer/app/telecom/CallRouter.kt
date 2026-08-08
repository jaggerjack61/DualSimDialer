package com.dualsimdialer.app.telecom

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.dualsimdialer.app.model.CallLogItem
import com.dualsimdialer.app.model.CallResult
import com.dualsimdialer.app.model.PhoneAccountKey
import com.dualsimdialer.app.model.SimProfile
import com.dualsimdialer.app.util.PhoneNumberUtils

interface CallRouter {
    fun placeCall(number: String, profile: SimProfile): CallResult
    fun redial(item: CallLogItem, selectedFallback: SimProfile? = null): CallResult
}

class AndroidCallRouter(private val context: Context) : CallRouter {
    private val telecom: TelecomManager? = context.getSystemService(TelecomManager::class.java)
    private val roleManager: RoleManager? = context.getSystemService(RoleManager::class.java)

    override fun placeCall(number: String, profile: SimProfile): CallResult {
        val normalized = PhoneNumberUtils.normalize(number)
            ?: return CallResult.InvalidNumber("Enter a valid phone number")
        if (PhoneNumberUtils.isEmergency(normalized)) return delegateEmergency(normalized)
        val handle = profile.key.toHandle() ?: return CallResult.UnavailableAccount
        return placeCallWithHandle(normalized, handle)
    }

    override fun redial(item: CallLogItem, selectedFallback: SimProfile?): CallResult {
        val number = item.number ?: return CallResult.InvalidNumber("This call has no dialable number")
        if (selectedFallback != null) return placeCall(number, selectedFallback)
        val normalized = PhoneNumberUtils.normalize(number)
            ?: return CallResult.InvalidNumber("This call has no dialable number")
        if (PhoneNumberUtils.isEmergency(normalized)) return delegateEmergency(normalized)
        val handle = item.accountKey?.toHandle() ?: return CallResult.UnavailableAccount
        return placeCallWithHandle(normalized, handle)
    }

    private fun placeCallWithHandle(number: String, handle: PhoneAccountHandle): CallResult {
        if (!isDefaultDialer()) return CallResult.PermissionDenied
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return CallResult.PermissionDenied
        }
        val account = runCatching { telecom?.getPhoneAccount(handle) }.getOrNull()
        if (account == null || !account.isEnabled) return CallResult.UnavailableAccount
        return runCatching {
            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }
            telecom?.placeCall(Uri.fromParts("tel", number, null), extras)
                ?: return CallResult.Failed("Telecom is unavailable")
            CallResult.Placed
        }.getOrElse { error ->
            if (error is SecurityException) CallResult.PermissionDenied
            else CallResult.Failed(error.message ?: "Could not place call")
        }
    }

    private fun delegateEmergency(number: String): CallResult {
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val external = context.packageManager.queryIntentActivities(
            dialIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        ).firstOrNull { it.activityInfo.packageName != context.packageName }
        if (external != null) dialIntent.setPackage(external.activityInfo.packageName)
        return runCatching {
            context.startActivity(dialIntent)
            CallResult.EmergencyDelegated
        }.getOrElse { CallResult.Failed("No Phone app can handle emergency dialing") }
    }

    private fun isDefaultDialer(): Boolean = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
}

private fun PhoneAccountKey.toHandle(): PhoneAccountHandle? {
    val component = ComponentName.unflattenFromString(componentName) ?: return null
    return PhoneAccountHandle(component, accountId)
}
