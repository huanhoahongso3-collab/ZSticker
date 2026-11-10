package com.gowtham.library.utils

import android.content.Context
import android.net.Uri
import java.io.File

object FileUtilKt {

    @JvmStatic
    fun getValidatedFileUri(context: Context, uri: Uri): String? {
        val actual = getActualFileUri(context, uri)
        if (actual != null && File(actual).canRead()) {
            return actual
        }
        return try {
            FileCacheHandler.putFileInCache(context, context.contentResolver.openInputStream(uri))
        } catch (e: Exception) {
            actual
        }
    }

    @JvmStatic
    fun getActualFileUri(context: Context, uri: Uri): String? {
        try {
            if (uri.scheme == "file" || uri.scheme == null) {
                val path = uri.path ?: uri.toString()
                if (File(path).exists()) return path
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
