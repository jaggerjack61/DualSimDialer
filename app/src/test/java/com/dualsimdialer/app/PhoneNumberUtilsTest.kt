package com.dualsimdialer.app

import com.dualsimdialer.app.model.SimValidation
import com.dualsimdialer.app.util.PhoneNumberUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberUtilsTest {
    @Test fun normalizesInternationalAndDisplayFormatting() {
        assertEquals("+263771234567", PhoneNumberUtils.normalize("+263 (77) 123-4567"))
        assertEquals("+263771234567", PhoneNumberUtils.normalize("00263771234567"))
    }

    @Test fun preservesDtmfCharactersAndRejectsLetters() {
        assertEquals("*123#", PhoneNumberUtils.normalize("*123#"))
        assertNull(PhoneNumberUtils.normalize("call me"))
        assertNull(PhoneNumberUtils.normalize("+263+77"))
    }

    @Test fun validatesSimConstraints() {
        assertTrue(PhoneNumberUtils.validateSimNumber("+263771234567") is SimValidation.Valid)
        assertTrue(PhoneNumberUtils.validateSimNumber("123456789012345678901") is SimValidation.Invalid)
        assertTrue(PhoneNumberUtils.validateSimNumber("123,45") is SimValidation.Invalid)
        assertTrue(PhoneNumberUtils.validateSimName("") is SimValidation.Invalid)
    }
}
