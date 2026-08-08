package com.dualsimdialer.app.util

import android.telecom.Call
import com.dualsimdialer.app.model.CallControlState

private fun Int.hasCapability(capability: Int): Boolean = (this and capability) == capability

fun callControlState(capabilities: Int): CallControlState = CallControlState(
    canMute = capabilities.hasCapability(Call.Details.CAPABILITY_MUTE),
    canHold = capabilities.hasCapability(Call.Details.CAPABILITY_HOLD) ||
        capabilities.hasCapability(Call.Details.CAPABILITY_SUPPORT_HOLD),
    canMerge = capabilities.hasCapability(Call.Details.CAPABILITY_MERGE_CONFERENCE) ||
        capabilities.hasCapability(Call.Details.CAPABILITY_MANAGE_CONFERENCE),
    canSwap = capabilities.hasCapability(Call.Details.CAPABILITY_SWAP_CONFERENCE),
    canAddParticipant = capabilities.hasCapability(Call.Details.CAPABILITY_ADD_PARTICIPANT),
    canRespondViaText = capabilities.hasCapability(Call.Details.CAPABILITY_RESPOND_VIA_TEXT),
)
