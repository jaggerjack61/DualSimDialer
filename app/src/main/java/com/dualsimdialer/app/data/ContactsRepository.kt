package com.dualsimdialer.app.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.SimPhonebookContract
import androidx.core.content.ContextCompat
import com.dualsimdialer.app.model.ContactDestination
import com.dualsimdialer.app.model.ContactPhone
import com.dualsimdialer.app.model.ContactSaveFailure
import com.dualsimdialer.app.model.ContactSaveResult
import com.dualsimdialer.app.model.ContactSummary
import com.dualsimdialer.app.model.WritableContactAccount
import com.dualsimdialer.app.util.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

interface ContactsRepository {
    fun observeContacts(query: String = ""): Flow<List<ContactSummary>>
    suspend fun loadContacts(query: String = ""): List<ContactSummary>
    suspend fun writableAccounts(): List<WritableContactAccount>
    suspend fun saveContact(
        name: String,
        number: String,
        destination: ContactDestination,
    ): ContactSaveResult
}

class AndroidContactsRepository(private val context: Context) : ContactsRepository {
    private val resolver: ContentResolver = context.contentResolver

    override fun observeContacts(query: String): Flow<List<ContactSummary>> = callbackFlow {
        fun emitLatest() { trySend(loadContactsInternal(query)) }
        emitLatest()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { emitLatest() }
        }
        if (hasReadPermission()) resolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            observer,
        )
        awaitClose { if (hasReadPermission()) resolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    override suspend fun loadContacts(query: String): List<ContactSummary> = loadContactsInternal(query)

    private fun loadContactsInternal(query: String): List<ContactSummary> {
        if (!hasReadPermission()) return emptyList()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        val selection = if (query.isBlank()) null else {
            "(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?)"
        }
        val args = if (query.isBlank()) null else arrayOf("%$query%", "%$query%")
        return runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                args,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                val contacts = linkedMapOf<Long, MutableContact>()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY))
                        ?.takeIf { it.isNotBlank() } ?: "Unnamed contact"
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: continue
                    val lookupIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                    val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                    val item = contacts.getOrPut(id) {
                        MutableContact(
                            id = id,
                            lookupKey = if (lookupIndex >= 0) cursor.getString(lookupIndex) else null,
                            name = name,
                            photo = if (photoIndex >= 0) cursor.getString(photoIndex)?.let(Uri::parse) else null,
                        )
                    }
                    item.phones += ContactPhone(
                        number = number,
                        label = if (typeIndex >= 0) phoneLabel(cursor.getInt(typeIndex)) else null,
                    )
                }
                contacts.values.map { it.toSummary() }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    override suspend fun writableAccounts(): List<WritableContactAccount> {
        if (!hasReadPermission()) return emptyList()
        val result = linkedSetOf<WritableContactAccount>()
        runCatching {
            resolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.RawContacts.ACCOUNT_NAME,
                    ContactsContract.RawContacts.ACCOUNT_TYPE,
                ),
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NOT NULL",
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val type = cursor.getString(1) ?: continue
                    if (!isSimAccount(type)) result += WritableContactAccount(name, type)
                }
            }
        }
        // Settings also contains configured sync accounts that have no RawContact rows yet.
        runCatching {
            resolver.query(
                ContactsContract.Settings.CONTENT_URI,
                arrayOf(ContactsContract.Settings.ACCOUNT_NAME, ContactsContract.Settings.ACCOUNT_TYPE),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val type = cursor.getString(1) ?: continue
                    if (!isSimAccount(type)) result += WritableContactAccount(name, type)
                }
            }
        }
        return result.sortedWith(compareByDescending<WritableContactAccount> { it.isGoogle }.thenBy { it.label })
    }

    override suspend fun saveContact(
        name: String,
        number: String,
        destination: ContactDestination,
    ): ContactSaveResult {
        if (!hasWritePermission()) return ContactSaveResult.Failure(ContactSaveFailure.PermissionDenied)
        if (name.trim().isEmpty()) return ContactSaveResult.Failure(ContactSaveFailure.InvalidName)
        val normalized = PhoneNumberUtils.normalize(number)
            ?: return ContactSaveResult.Failure(ContactSaveFailure.InvalidNumber)
        return when (destination) {
            ContactDestination.Device -> insertRawContact(name.trim(), normalized, null)
            is ContactDestination.Cloud -> insertRawContact(
                name.trim(),
                normalized,
                WritableContactAccount(destination.accountName, destination.accountType),
            )
            is ContactDestination.Existing -> appendNumber(destination, normalized)
            is ContactDestination.Sim -> insertSim(destination.subscriptionId, name.trim(), normalized)
        }
    }

    private fun insertRawContact(
        name: String,
        number: String,
        account: WritableContactAccount?,
    ): ContactSaveResult {
        val operations = arrayListOf<ContentProviderOperation>()
        val raw = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account?.accountName)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account?.accountType)
        operations += raw.build()
        operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            .build()
        operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            .build()
        return runCatching {
            val results = resolver.applyBatch(ContactsContract.AUTHORITY, operations)
            ContactSaveResult.Success(results.firstOrNull()?.uri)
        }.getOrElse { ContactSaveResult.Failure(ContactSaveFailure.ProviderFailure) }
    }

    private fun appendNumber(destination: ContactDestination.Existing, number: String): ContactSaveResult {
        if (destination.accountType?.let(::isSimAccount) == true) {
            return ContactSaveResult.Failure(ContactSaveFailure.ProviderFailure)
        }
        val rawContactId = findWritableRawContactId(destination) ?:
            return ContactSaveResult.Failure(ContactSaveFailure.ProviderFailure)
        return runCatching {
            val values = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }
            val uri = resolver.insert(ContactsContract.Data.CONTENT_URI, values)
            if (uri == null) ContactSaveResult.Failure(ContactSaveFailure.ProviderFailure)
            else ContactSaveResult.Success(uri)
        }.getOrElse { ContactSaveResult.Failure(ContactSaveFailure.ProviderFailure) }
    }

    private fun findWritableRawContactId(destination: ContactDestination.Existing): Long? {
        val projection = arrayOf(
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.ACCOUNT_NAME,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
        )
        return runCatching {
            resolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                projection,
                "${ContactsContract.RawContacts.CONTACT_ID}=?",
                arrayOf(destination.contactId.toString()),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val accountType = cursor.getString(2)
                    val accountName = cursor.getString(1)
                    if (!isSimAccount(accountType.orEmpty()) &&
                        (destination.accountType == null || destination.accountType == accountType) &&
                        (destination.accountName == null || destination.accountName == accountName)
                    ) return@use cursor.getLong(0)
                }
                null
            }
        }.getOrNull()
    }

    private fun insertSim(subscriptionId: Int, name: String, number: String): ContactSaveResult {
        if (subscriptionId < 0) return ContactSaveResult.Failure(ContactSaveFailure.SimUnavailable)
        when (val validation = PhoneNumberUtils.validateSimName(name)) {
            is com.dualsimdialer.app.model.SimValidation.Invalid -> return ContactSaveResult.Failure(ContactSaveFailure.UnsupportedEncoding)
            com.dualsimdialer.app.model.SimValidation.Valid -> Unit
        }
        when (val validation = PhoneNumberUtils.validateSimNumber(number)) {
            is com.dualsimdialer.app.model.SimValidation.Invalid -> return ContactSaveResult.Failure(ContactSaveFailure.InvalidNumber)
            com.dualsimdialer.app.model.SimValidation.Valid -> Unit
        }
        val uri = runCatching {
            SimPhonebookContract.SimRecords.getContentUri(subscriptionId, SimPhonebookContract.ElementaryFiles.EF_ADN)
        }.getOrElse { return ContactSaveResult.Failure(ContactSaveFailure.SimUnavailable) }
        val encodedLength = runCatching {
            SimPhonebookContract.SimRecords.getEncodedNameLength(resolver, name)
        }.getOrDefault(SimPhonebookContract.SimRecords.ERROR_NAME_UNSUPPORTED)
        if (encodedLength == SimPhonebookContract.SimRecords.ERROR_NAME_UNSUPPORTED) {
            return ContactSaveResult.Failure(ContactSaveFailure.UnsupportedEncoding)
        }
        val capacity = readSimCapacity(subscriptionId)
        if (capacity != null && capacity.maxRecords > 0 && capacity.recordCount >= capacity.maxRecords) {
            return ContactSaveResult.Failure(ContactSaveFailure.SimFull)
        }
        if (capacity?.nameMaxLength != null && capacity.nameMaxLength > 0 && encodedLength > capacity.nameMaxLength) {
            return ContactSaveResult.Failure(ContactSaveFailure.UnsupportedEncoding)
        }
        if (capacity?.phoneNumberMaxLength != null && capacity.phoneNumberMaxLength > 0 && number.length > capacity.phoneNumberMaxLength) {
            return ContactSaveResult.Failure(ContactSaveFailure.InvalidNumber)
        }
        val values = ContentValues().apply {
            put(SimPhonebookContract.SimRecords.NAME, name)
            put(SimPhonebookContract.SimRecords.PHONE_NUMBER, number)
        }
        return runCatching {
            val inserted = resolver.insert(uri, values)
            if (inserted != null) ContactSaveResult.Success(inserted)
            else ContactSaveResult.Failure(ContactSaveFailure.ProviderFailure)
        }.getOrElse { error ->
            val message = error.message.orEmpty().lowercase()
            when {
                "full" in message || "capacity" in message -> ContactSaveResult.Failure(ContactSaveFailure.SimFull)
                "read" in message || "write" in message -> ContactSaveResult.Failure(ContactSaveFailure.SimReadOnly)
                else -> ContactSaveResult.Failure(ContactSaveFailure.ProviderFailure)
            }
        }
    }

    private fun readSimCapacity(subscriptionId: Int): SimCapacity? {
        return runCatching {
            resolver.query(
                SimPhonebookContract.ElementaryFiles.CONTENT_URI,
                arrayOf(
                    SimPhonebookContract.ElementaryFiles.RECORD_COUNT,
                    SimPhonebookContract.ElementaryFiles.MAX_RECORDS,
                    SimPhonebookContract.ElementaryFiles.NAME_MAX_LENGTH,
                    SimPhonebookContract.ElementaryFiles.PHONE_NUMBER_MAX_LENGTH,
                ),
                "${SimPhonebookContract.ElementaryFiles.SUBSCRIPTION_ID}=? AND ${SimPhonebookContract.ElementaryFiles.EF_TYPE}=?",
                arrayOf(subscriptionId.toString(), SimPhonebookContract.ElementaryFiles.EF_ADN.toString()),
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                SimCapacity(
                    recordCount = cursor.getInt(cursor.getColumnIndexOrThrow(SimPhonebookContract.ElementaryFiles.RECORD_COUNT)),
                    maxRecords = cursor.getInt(cursor.getColumnIndexOrThrow(SimPhonebookContract.ElementaryFiles.MAX_RECORDS)),
                    nameMaxLength = cursor.getInt(cursor.getColumnIndexOrThrow(SimPhonebookContract.ElementaryFiles.NAME_MAX_LENGTH)),
                    phoneNumberMaxLength = cursor.getInt(cursor.getColumnIndexOrThrow(SimPhonebookContract.ElementaryFiles.PHONE_NUMBER_MAX_LENGTH)),
                )
            }
        }.getOrNull() ?: runCatching {
            resolver.query(uriForSim(subscriptionId), arrayOf(SimPhonebookContract.SimRecords.RECORD_NUMBER), null, null, null)
                ?.use { SimCapacity(it.count, MAX_SIM_RECORDS, null, null) }
        }.getOrNull()
    }

    private fun uriForSim(subscriptionId: Int): Uri = SimPhonebookContract.SimRecords.getContentUri(
        subscriptionId,
        SimPhonebookContract.ElementaryFiles.EF_ADN,
    )

    private fun hasReadPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasWritePermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    private fun isSimAccount(accountType: String): Boolean = accountType.contains("sim", ignoreCase = true) ||
        accountType.contains("icc", ignoreCase = true)

    private fun phoneLabel(type: Int): String? = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
        else -> null
    }

    private data class SimCapacity(
        val recordCount: Int,
        val maxRecords: Int,
        val nameMaxLength: Int?,
        val phoneNumberMaxLength: Int?,
    )

    private data class MutableContact(
        val id: Long,
        val lookupKey: String?,
        val name: String,
        val photo: Uri?,
        val phones: MutableList<ContactPhone> = mutableListOf(),
    ) {
        fun toSummary() = ContactSummary(id, lookupKey, name, phones.toList(), photo)
    }

    private companion object {
        const val MAX_SIM_RECORDS = 250
    }
}
