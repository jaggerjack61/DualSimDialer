package com.dualsimdialer.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.dualsimdialer.app.model.CallLogItem
import com.dualsimdialer.app.model.CallType
import com.dualsimdialer.app.model.PhoneAccountKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

interface CallLogRepository {
    fun observe(query: String = ""): Flow<List<CallLogItem>>
    suspend fun load(query: String = ""): List<CallLogItem>
}

class AndroidCallLogRepository(private val context: Context) : CallLogRepository {
    private val resolver = context.contentResolver

    override fun observe(query: String): Flow<List<CallLogItem>> = callbackFlow {
        fun emitLatest() { trySend(loadInternal(query)) }
        emitLatest()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { emitLatest() }
        }
        if (hasPermission()) resolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        awaitClose { if (hasPermission()) resolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    override suspend fun load(query: String): List<CallLogItem> = loadInternal(query)

    private fun loadInternal(query: String): List<CallLogItem> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            CallLog.Calls.PHONE_ACCOUNT_ID,
            CallLog.Calls.NUMBER_PRESENTATION,
        )
        val selection = if (query.isBlank()) null else {
            "(${CallLog.Calls.CACHED_NAME} LIKE ? OR ${CallLog.Calls.NUMBER} LIKE ?)"
        }
        val args = if (query.isBlank()) null else arrayOf("%$query%", "%$query%")
        val uri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter("limit", "200")
            .build()
        return runCatching {
            resolver.query(uri, projection, selection, args, "${CallLog.Calls.DATE} DESC")?.use { cursor ->
                buildList {
                    val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
                    val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                    val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                    val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
                    val componentIndex = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)
                    val accountIdIndex = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                    val presentationIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)
                    while (cursor.moveToNext()) {
                        val presentation = cursor.getIntOrNull(presentationIndex)
                        val isPrivate = presentation == CallLog.Calls.PRESENTATION_RESTRICTED ||
                            presentation == CallLog.Calls.PRESENTATION_UNKNOWN ||
                            presentation == CallLog.Calls.PRESENTATION_PAYPHONE
                        val number = cursor.getStringOrNull(numberIndex).takeUnless { isPrivate }
                        val component = cursor.getStringOrNull(componentIndex)
                        val accountId = cursor.getStringOrNull(accountIdIndex)
                        add(
                            CallLogItem(
                                id = cursor.getLong(idIndex),
                                number = number,
                                displayName = cursor.getStringOrNull(nameIndex).takeUnless { isPrivate },
                                type = callType(cursor.getInt(typeIndex)),
                                timestamp = cursor.getLong(dateIndex),
                                durationSeconds = cursor.getLong(durationIndex),
                                accountKey = if (!component.isNullOrBlank() && !accountId.isNullOrBlank()) {
                                    PhoneAccountKey(component, accountId)
                                } else null,
                                isPrivate = isPrivate,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun callType(value: Int): CallType = when (value) {
        CallLog.Calls.INCOMING_TYPE -> CallType.Incoming
        CallLog.Calls.OUTGOING_TYPE -> CallType.Outgoing
        CallLog.Calls.MISSED_TYPE -> CallType.Missed
        CallLog.Calls.VOICEMAIL_TYPE -> CallType.Voicemail
        CallLog.Calls.REJECTED_TYPE -> CallType.Rejected
        CallLog.Calls.BLOCKED_TYPE -> CallType.Blocked
        else -> CallType.Unknown
    }

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALL_LOG,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? =
    if (index < 0 || isNull(index)) null else getString(index)

private fun android.database.Cursor.getIntOrNull(index: Int): Int? =
    if (index < 0 || isNull(index)) null else getInt(index)
