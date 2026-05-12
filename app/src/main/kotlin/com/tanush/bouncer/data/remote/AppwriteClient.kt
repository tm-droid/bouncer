package com.tanush.bouncer.data.remote

import android.content.Context
import android.util.Log
import com.tanush.bouncer.ApiKeys
import io.appwrite.Client

object AppwriteClient {
    private const val TAG = "AppwriteClient"

    @Volatile
    var client: Client? = null
        private set
    @Volatile
    var isReady = false
        private set

    fun initialize(context: Context) {
        val endpoint = ApiKeys.appwriteEndpoint.trimEnd('/')
        val projectId = ApiKeys.appwriteProjectId
        isReady = endpoint.isNotBlank() && projectId.isNotBlank()
        if (isReady) {
            client = Client(context)
                .setEndpoint(endpoint)
                .setProject(projectId)
        } else {
            Log.w(TAG, "Appwrite not configured — SDK client disabled")
        }
    }
}
