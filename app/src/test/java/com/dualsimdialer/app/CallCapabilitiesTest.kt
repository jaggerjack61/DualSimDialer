package com.dualsimdialer.app

import android.telecom.Call
import com.dualsimdialer.app.util.callControlState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallCapabilitiesTest {
    @Test fun controlsFollowTelecomCapabilities() {
        val capabilities = Call.Details.CAPABILITY_MUTE or
            Call.Details.CAPABILITY_HOLD or
            Call.Details.CAPABILITY_MERGE_CONFERENCE
        val controls = callControlState(capabilities)
        assertTrue(controls.canMute)
        assertTrue(controls.canHold)
        assertTrue(controls.canMerge)
        assertFalse(controls.canSwap)
    }
}
