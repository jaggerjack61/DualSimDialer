package com.dualsimdialer.app

import android.app.Application
import com.dualsimdialer.app.data.AndroidCallLogRepository
import com.dualsimdialer.app.data.AndroidContactsRepository
import com.dualsimdialer.app.data.AndroidSimRepository
import com.dualsimdialer.app.data.CallLogRepository
import com.dualsimdialer.app.data.ContactsRepository
import com.dualsimdialer.app.data.DataStoreSimPreferencesRepository
import com.dualsimdialer.app.data.SimPreferencesRepository
import com.dualsimdialer.app.data.SimRepository
import com.dualsimdialer.app.telecom.AndroidCallRouter
import com.dualsimdialer.app.telecom.CallRouter
import com.dualsimdialer.app.telecom.CallSessionController

class DialerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val simPreferencesRepository: SimPreferencesRepository = DataStoreSimPreferencesRepository(application)
    val simRepository: SimRepository = AndroidSimRepository(application, simPreferencesRepository)
    val callLogRepository: CallLogRepository = AndroidCallLogRepository(application)
    val contactsRepository: ContactsRepository = AndroidContactsRepository(application)
    val callRouter: CallRouter = AndroidCallRouter(application)
    val callSessionController = CallSessionController()
}
