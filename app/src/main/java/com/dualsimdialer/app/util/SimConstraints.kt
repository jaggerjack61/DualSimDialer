package com.dualsimdialer.app.util

import com.dualsimdialer.app.model.SimValidation

object SimConstraints {
    fun validate(name: String, number: String): SimValidation {
        return when (val nameResult = PhoneNumberUtils.validateSimName(name)) {
            SimValidation.Valid -> PhoneNumberUtils.validateSimNumber(number)
            is SimValidation.Invalid -> nameResult
        }
    }
}
