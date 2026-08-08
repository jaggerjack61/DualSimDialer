package com.dualsimdialer.app.util

import com.dualsimdialer.app.model.SimValidation

/** Framework-independent number handling used by routing, search, and contact forms. */
object PhoneNumberUtils {
    private const val MAX_SIM_NUMBER_LENGTH = 20
    private const val MAX_NUMBER_LENGTH = 80

    fun normalize(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > MAX_NUMBER_LENGTH) return null

        val result = buildString {
            value.forEachIndexed { index, character ->
                when {
                    character.isDigit() -> append(character)
                    character == '+' && index == 0 -> append(character)
                    character == '*' || character == '#' -> append(character)
                    character == ',' || character == ';' -> append(character)
                    character == ' ' || character == '-' || character == '(' || character == ')' ||
                        character == '.' -> Unit
                    else -> return null
                }
            }
        }
        if (result.isEmpty()) return null
        if (result.startsWith("00") && result.length > 2) return "+${result.drop(2)}"
        if (result.count { it == '+' } > 1 || (result.contains('+') && !result.startsWith('+'))) return null
        if (result.all { it == '*' || it == '#' }) return result
        if (result.none { it.isDigit() }) return null
        return result
    }

    fun digitsOnly(raw: String): String = raw.filter(Char::isDigit)

    fun isEmergency(raw: String): Boolean {
        val digits = digitsOnly(raw)
        return digits in setOf("112", "911", "999", "993", "995")
    }

    fun validateSimNumber(raw: String): SimValidation {
        val normalized = normalize(raw) ?: return SimValidation.Invalid("Enter a valid phone number")
        if (normalized.length > MAX_SIM_NUMBER_LENGTH) {
            return SimValidation.Invalid("SIM numbers can contain at most 20 characters")
        }
        if (normalized.any { !it.isDigit() && it != '+' && it != '*' && it != '#' }) {
            return SimValidation.Invalid("SIM phonebooks do not support pauses or waits")
        }
        return SimValidation.Valid
    }

    fun validateSimName(name: String): SimValidation {
        if (name.trim().isEmpty()) return SimValidation.Invalid("Enter a name")
        if (name.any { it.isISOControl() }) return SimValidation.Invalid("Name contains an unsupported character")
        // The provider performs the final encoded-length check; this keeps an obviously oversized
        // value out of the provider on devices that do not expose its capacity metadata.
        if (name.trim().length > 40) return SimValidation.Invalid("SIM names can contain at most 40 characters")
        return SimValidation.Valid
    }
}
