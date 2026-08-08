package com.dualsimdialer.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.dualsimdialer.app.model.PhoneAccountKey
import com.dualsimdialer.app.model.SimProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

interface SimRepository {
    fun observeProfiles(): Flow<List<SimProfile>>
    suspend fun refresh(): List<SimProfile>
}

class AndroidSimRepository(
    private val context: Context,
    private val preferences: SimPreferencesRepository,
) : SimRepository {
    private val telecom: TelecomManager? = context.getSystemService(TelecomManager::class.java)
    private val subscriptions: SubscriptionManager? = context.getSystemService(SubscriptionManager::class.java)

    override fun observeProfiles(): Flow<List<SimProfile>> = callbackFlow {
        val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                launch { trySend(queryProfiles()) }
            }
        }
        val preferencesJob = launch {
            preferences.observe().collect { trySend(queryProfiles()) }
        }
        trySend(queryProfiles())
        subscriptions?.addOnSubscriptionsChangedListener(ContextCompat.getMainExecutor(context), listener)
        awaitClose {
            preferencesJob.cancel()
            subscriptions?.removeOnSubscriptionsChangedListener(listener)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun refresh(): List<SimProfile> = queryProfiles()

    private suspend fun queryProfiles(): List<SimProfile> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val accountHandles = runCatching { telecom?.callCapablePhoneAccounts.orEmpty() }.getOrElse { emptyList() }
        val activeSubscriptions = runCatching { subscriptions?.activeSubscriptionInfoList.orEmpty() }.getOrElse { emptyList() }
        return accountHandles.mapNotNull { handle ->
            val account = runCatching { telecom?.getPhoneAccount(handle) }.getOrNull() ?: return@mapNotNull null
            if (!account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) ||
                !account.supportsUriScheme(PhoneAccount.SCHEME_TEL)
            ) return@mapNotNull null

            val key = PhoneAccountKey(handle.componentName.flattenToString(), handle.id)
            val subscription = findSubscription(handle, activeSubscriptions)
            val slot = subscription?.simSlotIndex?.takeIf { it >= 0 }
                ?: accountHandles.indexOf(handle).coerceAtLeast(0)
            val stored = runCatching { preferences.read(key) }.getOrDefault(StoredSimPreferences())
            val defaultAlias = "SIM ${slot + 1}"
            SimProfile(
                key = key,
                subscriptionId = subscription?.subscriptionId ?: handle.id.toIntOrNull()
                    ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                slotIndex = slot,
                systemLabel = account.label?.toString()?.takeIf { it.isNotBlank() }
                    ?: subscription?.displayName?.toString()?.takeIf { it.isNotBlank() }
                    ?: defaultAlias,
                alias = stored.alias?.takeIf { it.isNotBlank() } ?: defaultAlias,
                colorArgb = stored.colorArgb ?: defaultColor(slot),
                isActive = true,
            )
        }.sortedBy { it.slotIndex }
    }

    private fun findSubscription(
        handle: android.telecom.PhoneAccountHandle,
        subscriptions: List<SubscriptionInfo>,
    ): SubscriptionInfo? {
        val id = handle.id.toIntOrNull()
        return subscriptions.firstOrNull { it.subscriptionId == id }
            ?: subscriptions.singleOrNull().takeIf { id == null }
    }

    private fun defaultColor(slotIndex: Int): Int = when (slotIndex % 2) {
        0 -> 0xFF6750A4.toInt()
        else -> 0xFF006A6A.toInt()
    }
}
