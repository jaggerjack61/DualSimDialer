package com.dualsimdialer.app

import com.dualsimdialer.app.model.PhoneAccountKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneAccountKeyTest {
    @Test fun serializationRoundTripsComponentAndAccountId() {
        val key = PhoneAccountKey("com.android.phone/.TelephonyConnectionService", "3")
        assertEquals(key, PhoneAccountKey.parse(key.serialized))
    }

    @Test fun malformedStorageKeyIsIgnored() {
        assertNull(PhoneAccountKey.parse("missing-separator"))
        assertNull(PhoneAccountKey.parse("component|"))
    }
}
