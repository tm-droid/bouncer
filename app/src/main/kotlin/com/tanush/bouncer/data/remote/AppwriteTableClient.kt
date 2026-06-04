package com.tanush.bouncer.data.remote

import android.util.Log
import io.appwrite.services.TablesDB

abstract class AppwriteTableClient(private val tag: String) {

    protected var dbId = ""
        private set
    protected var tableId = ""
        private set

    open val isReady: Boolean
        get() = AppwriteClient.isReady && dbId.isNotBlank() && tableId.isNotBlank()

    protected val tablesDB: TablesDB?
        get() = AppwriteClient.client?.let { TablesDB(it) }

    protected fun initTable(dbIdValue: String, tableIdValue: String) {
        dbId = dbIdValue
        tableId = tableIdValue
        if (!isReady) {
            Log.w(tag, "$tag not configured — remote tier disabled")
        }
    }
}
