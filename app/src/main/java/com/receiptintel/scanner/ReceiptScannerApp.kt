package com.receiptintel.scanner

import android.app.Application
import com.receiptintel.scanner.data.local.AppDatabase

/**
 * Application entry point.
 *
 * We deliberately avoid a full DI framework (Hilt/Koin) to keep the build
 * lightweight and easy to reason about for a student/solo-maintainer project.
 * A single lazily-created [AppDatabase] instance is exposed here and consumed
 * by [com.receiptintel.scanner.di.ServiceLocator]. If the project grows a
 * larger team, swapping this for Hilt is a mechanical, low-risk change since
 * every dependency is already constructor-injected.
 */
class ReceiptScannerApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
    }
}
