package com.tanush.bouncer.utils

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object FileExportHelper {
    // Writes the given content to a cached file and returns a content:// URI via FileProvider
    fun writeCsvToCache(context: Context, filename: String, content: String): android.net.Uri? {
        return try {
            val cacheDir = context.cacheDir
            val file = File(cacheDir, filename)
            FileOutputStream(file).use { it.write(content.toByteArray()) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
