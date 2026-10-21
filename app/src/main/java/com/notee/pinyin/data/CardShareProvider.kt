package com.notee.pinyin.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hands one blessing picture to the editor that accepted it.
 * Not exported: only the grant from [android.view.inputmethod.InputConnection.commitContent] can read it.
 */
class CardShareProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        val file = fileFor(uri) ?: return null
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf(file.name, file.length()))
        }
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = fileFor(uri) ?: throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun fileFor(uri: Uri): File? {
        val name = uri.lastPathSegment ?: return null
        if (!name.matches(FILE)) return null
        val file = File(context!!.cacheDir, name)
        return file.takeIf { it.isFile }
    }

    companion object {
        private val FILE = Regex("blessing-\\d{8}-\\d{6}-\\d+\\.jpg")
        private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        private val sequence = AtomicInteger()

        fun publish(context: Context, jpeg: ByteArray): Uri {
            val dir = context.cacheDir
            val cutoff = System.currentTimeMillis() - 86_400_000L
            dir.listFiles()?.forEach { file ->
                if (file.name.matches(FILE) && file.lastModified() < cutoff) file.delete()
            }
            val name = "blessing-${STAMP.format(LocalDateTime.now())}-${sequence.incrementAndGet()}.jpg"
            File(dir, name).writeBytes(jpeg)
            return Uri.Builder().scheme("content").authority("${context.packageName}.cards").appendPath(name).build()
        }
    }
}
