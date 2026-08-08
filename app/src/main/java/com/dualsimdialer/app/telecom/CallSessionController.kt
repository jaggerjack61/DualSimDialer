package com.dualsimdialer.app.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.annotation.RequiresApi
import com.dualsimdialer.app.model.AudioEndpointInfo
import com.dualsimdialer.app.model.InCallItem
import com.dualsimdialer.app.model.PhoneAccountKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local bridge between Telecom's InCallService and the Compose call surface. */
class CallSessionController {
    private val activeCalls = linkedMapOf<String, Call>()
    private val callbacks = mutableMapOf<String, Call.Callback>()
    private val _calls = MutableStateFlow<List<InCallItem>>(emptyList())
    private val _muted = MutableStateFlow(false)
    private val _endpoints = MutableStateFlow<List<AudioEndpointInfo>>(emptyList())
    private var service: DualSimInCallService? = null

    val calls: StateFlow<List<InCallItem>> = _calls.asStateFlow()
    val muted: StateFlow<Boolean> = _muted.asStateFlow()
    val endpoints: StateFlow<List<AudioEndpointInfo>> = _endpoints.asStateFlow()

    fun attach(service: DualSimInCallService) {
        this.service = service
        service.getCalls().forEach(::addCall)
    }

    fun detach(service: DualSimInCallService) {
        if (this.service === service) this.service = null
    }

    fun addCall(call: Call) {
        val id = call.id()
        activeCalls[id] = call
        if (!callbacks.containsKey(id)) {
            val callback = object : Call.Callback() {
                override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
                override fun onStateChanged(call: Call, state: Int) = publish()
                override fun onCallDestroyed(call: Call) = removeCall(call)
                override fun onParentChanged(call: Call, parent: Call?) = publish()
            }
            callbacks[id] = callback
            call.registerCallback(callback)
        }
        publish()
    }

    fun removeCall(call: Call) {
        val id = call.id()
        callbacks.remove(id)?.let { call.unregisterCallback(it) }
        activeCalls.remove(id)
        publish()
    }

    fun currentCallId(): String? = activeCalls.values
        .sortedWith(compareBy<Call> { statePriority(it) }.thenByDescending { it.details.creationTimeMillis })
        .firstOrNull()?.id()

    fun answer(id: String? = currentCallId()) = find(id)?.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject(id: String? = currentCallId()) = find(id)?.disconnect()
    fun end(id: String? = currentCallId()) = find(id)?.disconnect()
    fun hold(id: String? = currentCallId()) = find(id)?.hold()
    fun resume(id: String? = currentCallId()) = find(id)?.unhold()
    fun merge(id: String? = currentCallId()) = find(id)?.mergeConference()
    fun swap(id: String? = currentCallId()) = find(id)?.swapConference()

    fun playDtmf(digit: Char, id: String? = currentCallId()) = find(id)?.playDtmfTone(digit)
    fun stopDtmf(id: String? = currentCallId()) = find(id)?.stopDtmfTone()

    fun toggleMute() {
        if (service != null) {
            service?.setMuted(!_muted.value)
            _muted.value = !_muted.value
        }
    }

    fun setLegacyAudioRoute(route: Int) {
        service?.setAudioRoute(route)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun selectEndpoint(id: String) {
        service?.requestEndpoint(id)
    }

    fun updateAudioState(state: CallAudioState) {
        _muted.value = state.isMuted
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val supported = state.supportedRouteMask
            val routes = buildList {
                if (supported and CallAudioState.ROUTE_EARPIECE != 0) add(AudioEndpointInfo("earpiece", "Earpiece", CallAudioState.ROUTE_EARPIECE, state.route == CallAudioState.ROUTE_EARPIECE))
                if (supported and CallAudioState.ROUTE_SPEAKER != 0) add(AudioEndpointInfo("speaker", "Speaker", CallAudioState.ROUTE_SPEAKER, state.route == CallAudioState.ROUTE_SPEAKER))
                if (supported and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(AudioEndpointInfo("wired", "Wired headset", CallAudioState.ROUTE_WIRED_HEADSET, state.route == CallAudioState.ROUTE_WIRED_HEADSET))
                if (supported and CallAudioState.ROUTE_BLUETOOTH != 0) add(AudioEndpointInfo("bluetooth", "Bluetooth", CallAudioState.ROUTE_BLUETOOTH, state.route == CallAudioState.ROUTE_BLUETOOTH))
            }
            _endpoints.value = routes
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun updateEndpoints(endpoints: List<android.telecom.CallEndpoint>, selected: android.telecom.CallEndpoint?) {
        _endpoints.value = endpoints.map {
            AudioEndpointInfo(
                id = it.identifier.toString(),
                label = it.endpointName.toString(),
                type = it.endpointType,
                isSelected = it == selected,
            )
        }
    }

    private fun find(id: String?): Call? = id?.let(activeCalls::get) ?: activeCalls.values.firstOrNull()

    private fun publish() {
        _calls.value = activeCalls.values.map { call ->
            val details = call.details
            val handle = details.accountHandle
            InCallItem(
                id = call.id(),
                number = details.handle?.schemeSpecificPart,
                callerName = details.contactDisplayName?.takeIf { it.isNotBlank() }
                    ?: details.callerDisplayName?.takeIf { it.isNotBlank() },
                photoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) details.photoUriApi34() else null,
                accountKey = handle?.let { PhoneAccountKey(it.componentName.flattenToString(), it.id) },
                state = call.state(),
                isIncoming = details.callDirection == Call.Details.DIRECTION_INCOMING || call.state() == Call.STATE_RINGING,
                capabilities = details.callCapabilities,
                creationTimeMillis = details.creationTimeMillis,
                connectTimeMillis = details.connectTimeMillis,
            )
        }
    }

    private fun Call.id(): String = "call-${hashCode()}"

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun Call.Details.photoUriApi34() = contactPhotoUri
    private fun Call.state(): Int = details.state
    private fun statePriority(call: Call): Int = when (call.state()) {
        Call.STATE_RINGING -> 0
        Call.STATE_ACTIVE -> 1
        Call.STATE_DIALING, Call.STATE_CONNECTING -> 2
        Call.STATE_HOLDING -> 3
        else -> 4
    }
}
