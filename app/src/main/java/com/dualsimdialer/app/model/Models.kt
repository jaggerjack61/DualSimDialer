package com.dualsimdialer.app.model

import android.net.Uri

/** Stable identity shared by Telecom, call-log rows, and this app's preferences. */
data class PhoneAccountKey(
    val componentName: String,
    val accountId: String,
) {
    val serialized: String get() = "$componentName|$accountId"

    companion object {
        fun parse(serialized: String): PhoneAccountKey? {
            val separator = serialized.indexOf('|')
            if (separator <= 0 || separator == serialized.lastIndex) return null
            return PhoneAccountKey(
                componentName = serialized.substring(0, separator),
                accountId = serialized.substring(separator + 1),
            )
        }
    }
}

data class SimProfile(
    val key: PhoneAccountKey,
    val subscriptionId: Int,
    val slotIndex: Int,
    val systemLabel: String,
    val alias: String,
    val colorArgb: Int,
    val isActive: Boolean = true,
) {
    val displayName: String get() = alias.ifBlank { "SIM ${slotIndex + 1}" }
}

enum class CallType {
    Incoming,
    Outgoing,
    Missed,
    Rejected,
    Voicemail,
    Blocked,
    Unknown,
}

data class CallLogItem(
    val id: Long,
    val number: String?,
    val displayName: String?,
    val type: CallType,
    val timestamp: Long,
    val durationSeconds: Long,
    val accountKey: PhoneAccountKey?,
    val isPrivate: Boolean = false,
) {
    val title: String get() = displayName?.takeIf { it.isNotBlank() } ?: number ?: "Private number"
}

data class ContactPhone(
    val number: String,
    val label: String? = null,
)

data class ContactSummary(
    val id: Long,
    val lookupKey: String?,
    val displayName: String,
    val phoneNumbers: List<ContactPhone>,
    val photoUri: Uri? = null,
)

data class WritableContactAccount(
    val accountName: String,
    val accountType: String,
) {
    val isGoogle: Boolean get() = accountType.equals("com.google", ignoreCase = true)
    val label: String get() = if (isGoogle) "Google · $accountName" else accountName
}

sealed interface ContactDestination {
    data object Device : ContactDestination
    data class Cloud(val accountName: String, val accountType: String) : ContactDestination
    data class Sim(val subscriptionId: Int) : ContactDestination
    data class Existing(
        val contactId: Long,
        val accountName: String?,
        val accountType: String?,
    ) : ContactDestination
}

sealed interface CallResult {
    data object Placed : CallResult
    data object EmergencyDelegated : CallResult
    data class InvalidNumber(val message: String) : CallResult
    data object UnavailableAccount : CallResult
    data object PermissionDenied : CallResult
    data class Failed(val message: String) : CallResult
}

sealed interface ContactSaveResult {
    data class Success(val uri: Uri? = null) : ContactSaveResult
    data class Failure(val reason: ContactSaveFailure) : ContactSaveResult
}

enum class ContactSaveFailure {
    InvalidName,
    InvalidNumber,
    PermissionDenied,
    NoGoogleAccount,
    SimUnavailable,
    SimReadOnly,
    SimFull,
    UnsupportedEncoding,
    ProviderFailure,
}

data class CallControlState(
    val canMute: Boolean = false,
    val canHold: Boolean = false,
    val canMerge: Boolean = false,
    val canSwap: Boolean = false,
    val canAddParticipant: Boolean = false,
    val canRespondViaText: Boolean = false,
)

data class InCallItem(
    val id: String,
    val number: String?,
    val callerName: String?,
    val photoUri: Uri? = null,
    val accountKey: PhoneAccountKey?,
    val state: Int,
    val isIncoming: Boolean,
    val capabilities: Int,
    val creationTimeMillis: Long,
    val connectTimeMillis: Long,
)

data class AudioEndpointInfo(
    val id: String,
    val label: String,
    val type: Int,
    val isSelected: Boolean,
)

sealed interface SimValidation {
    data object Valid : SimValidation
    data class Invalid(val reason: String) : SimValidation
}
