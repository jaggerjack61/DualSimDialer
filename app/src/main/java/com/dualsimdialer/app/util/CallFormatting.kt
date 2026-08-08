package com.dualsimdialer.app.util

import com.dualsimdialer.app.model.CallType
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun CallType.label(): String = when (this) {
    CallType.Incoming -> "Incoming"
    CallType.Outgoing -> "Outgoing"
    CallType.Missed -> "Missed"
    CallType.Rejected -> "Rejected"
    CallType.Voicemail -> "Voicemail"
    CallType.Blocked -> "Blocked"
    CallType.Unknown -> "Call"
}

fun formatCallTime(timestamp: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.SHORT,
    DateFormat.SHORT,
    Locale.getDefault(),
).format(Date(timestamp))

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "—"
    val minutes = seconds / 60
    val remainder = seconds % 60
    return if (minutes > 0) "%dm %02ds".format(Locale.getDefault(), minutes, remainder)
    else "%ds".format(Locale.getDefault(), remainder)
}
