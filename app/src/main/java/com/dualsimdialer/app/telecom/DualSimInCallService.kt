package com.dualsimdialer.app.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import android.telecom.PhoneAccount
import androidx.annotation.RequiresApi
import com.dualsimdialer.app.DialerApplication
import com.dualsimdialer.app.R
import com.dualsimdialer.app.model.InCallItem
import com.dualsimdialer.app.model.SimProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DualSimInCallService : InCallService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: CallSessionController
    private lateinit var notifications: NotificationManager
    private var availableEndpoints: List<CallEndpoint> = emptyList()
    private var selectedEndpoint: CallEndpoint? = null

    override fun onCreate() {
        super.onCreate()
        controller = (application as DialerApplication).container.callSessionController
        controller.attach(this)
        notifications = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        controller.addCall(call)
        val item = controller.calls.value.firstOrNull { it.id == call.idForNotification() }
        // The default dialer owns the visible call surface for both incoming and outgoing calls.
        startCallActivity()
        if (item != null) postNotification(item)
        else refreshNotification()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        controller.removeCall(call)
        refreshNotification()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        controller.updateAudioState(audioState)
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        startCallActivity()
    }

    override fun onDestroy() {
        notifications.cancel(NOTIFICATION_ID)
        controller.detach(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onAvailableCallEndpointsChanged(endpoints: MutableList<CallEndpoint>) {
        availableEndpoints = endpoints.toList()
        controller.updateEndpoints(availableEndpoints, selectedEndpoint)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(endpoint: CallEndpoint) {
        selectedEndpoint = endpoint
        controller.updateEndpoints(availableEndpoints, selectedEndpoint)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun requestEndpoint(id: String) {
        val endpoint = availableEndpoints.firstOrNull { it.identifier.toString() == id } ?: return
        requestCallEndpointChange(endpoint, mainExecutor, object : OutcomeReceiver<Void, CallEndpointException> {
            override fun onResult(result: Void?) = Unit
            override fun onError(error: CallEndpointException) = Unit
        })
    }

    private fun refreshNotification() {
        val item = controller.calls.value.firstOrNull()
        if (item == null) notifications.cancel(NOTIFICATION_ID) else postNotification(item)
    }

    private fun postNotification(item: InCallItem) {
        serviceScope.launch {
            val profile = item.accountKey?.let { key ->
                withContext(Dispatchers.IO) {
                    (application as DialerApplication).container.simRepository.refresh().firstOrNull { it.key == key }
                }
            }
            val personBuilder = android.app.Person.Builder()
                .setName(item.callerName ?: item.number ?: "Unknown caller")
                .setImportant(true)
            item.photoUri?.let { personBuilder.setIcon(android.graphics.drawable.Icon.createWithContentUri(it.toString())) }
            val person = personBuilder.build()
            val openIntent = PendingIntent.getActivity(
                this@DualSimInCallService,
                item.id.hashCode(),
                Intent(this@DualSimInCallService, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val answerIntent = actionPendingIntent(item, InCallActivity.ACTION_ANSWER)
            val declineIntent = actionPendingIntent(item, InCallActivity.ACTION_DECLINE)
            val isRinging = item.isIncoming && item.state == Call.STATE_RINGING
            val style = if (isRinging) {
                Notification.CallStyle.forIncomingCall(person, answerIntent, declineIntent)
                    .setAnswerButtonColorHint(profile?.colorArgb ?: 0xFF386A20.toInt())
                    .setDeclineButtonColorHint(0xFFBA1A1A.toInt())
            } else {
                Notification.CallStyle.forOngoingCall(person, openIntent)
            }
            style.setVerificationText(profile?.displayName ?: "Other provider")
            val notification = Notification.Builder(
                this@DualSimInCallService,
                if (isRinging) RINGING_CHANNEL_ID else CHANNEL_ID,
            )
                .setSmallIcon(R.drawable.ic_launcher)
                .setColor(profile?.colorArgb ?: 0xFF6B6B6B.toInt())
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(item.state != Call.STATE_DISCONNECTED)
                .setContentIntent(openIntent)
                // The service opens InCallActivity directly. Keep the companion CallStyle
                // notification available on the lock screen without a duplicate heads-up card.
                .setStyle(style)
                .build()
            runCatching { notifications.notify(NOTIFICATION_ID, notification) }
        }
    }

    private fun actionPendingIntent(item: InCallItem, action: String): PendingIntent = PendingIntent.getActivity(
        this,
        (item.id.hashCode() * 31) + action.hashCode(),
        Intent(this, InCallActivity::class.java)
            .putExtra(InCallActivity.EXTRA_ACTION, action)
            .putExtra(InCallActivity.EXTRA_CALL_ID, item.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun startCallActivity() {
        startActivity(
            Intent(this, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    private fun createNotificationChannel() {
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Calls",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Ongoing calls" },
        )
        notifications.createNotificationChannel(
            NotificationChannel(
                RINGING_CHANNEL_ID,
                "Incoming calls",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Incoming call details while the full-screen call UI is open"
                setSound(null, null)
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "calls"
        const val RINGING_CHANNEL_ID = "incoming_calls_silent"
        const val NOTIFICATION_ID = 4401
    }
}

private fun Call.idForNotification(): String = "call-${hashCode()}"
