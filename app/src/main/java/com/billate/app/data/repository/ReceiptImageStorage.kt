package com.billate.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages receipt images in app-internal storage.
 *
 * Images are stored under `files/receipts/` in internal storage,
 * which is scoped to the app and not accessible by other apps.
 */
@Singleton
class ReceiptImageStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val receiptsDir: File
        get() = File(context.filesDir, "receipts").also { it.mkdirs() }

    /**
     * Save an image from an [InputStream] and return the filename.
     */
    fun saveImage(inputStream: InputStream, filename: String): String {
        val file = File(receiptsDir, filename)
        file.outputStream().use { out ->
            inputStream.copyTo(out)
        }
        return filename
    }

    /**
     * Get the absolute [File] for a stored receipt image.
     */
    fun getImageFile(filename: String): File = File(receiptsDir, filename)

    /**
     * Delete a stored receipt image.
     */
    fun deleteImage(filename: String) {
        val file = File(receiptsDir, filename)
        if (file.exists()) file.delete()
    }
}
