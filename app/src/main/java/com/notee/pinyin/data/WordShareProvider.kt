package com.notee.pinyin.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Hands one export file to 云端硬盘. Not exported: only a grant on the send intent can read it.
 * The keyboard itself never opens a network connection.
 */
class WordShareProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        val file = fileFor(uri) ?: return null
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf(file.name, file.length()))
        }
    }

    override fun getType(uri: Uri): String = "text/plain"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = fileFor(uri) ?: throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** Only a file this provider just wrote, sitting directly in the cache. */
    private fun fileFor(uri: Uri): File? {
        val name = uri.lastPathSegment ?: return null
        if (!name.matches(EXPORT)) return null
        val file = File(context!!.cacheDir, name)
        return file.takeIf { it.isFile }
    }

    companion object {
        private val EXPORT = Regex(".+-\\d{8}-\\d{4}\\.txt")

        fun publish(context: android.content.Context, text: String, name: String): Uri {
            require(name.matches(EXPORT))
            val dir = context.cacheDir
            dir.listFiles()?.forEach { if (it.name.matches(EXPORT)) it.delete() }
            File(dir, name).writeText(text, Charsets.UTF_8)
            return Uri.Builder().scheme("content").authority("${context.packageName}.words").appendPath(name).build()
        }
    }
}
