package com.billate.app.data.remote

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageDataSource @Inject constructor(
    private val contentResolver: ContentResolver,
) {
    fun readBitmap(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }
}
