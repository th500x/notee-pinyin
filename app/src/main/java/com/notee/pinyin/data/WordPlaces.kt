package com.notee.pinyin.data

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Where a word file is written or read. The keyboard process does not use the network. */
object WordPlaces {
    const val DRIVE = "com.google.android.apps.docs"

    fun fileName(stem: String): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return "$stem-$stamp.txt"
    }

    fun driveInstalled(context: Context): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(DRIVE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Public Downloads on API 29+. No storage permission. False when the insert fails. */
    fun saveToDownloads(context: Context, name: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: IOException) {
            resolver.delete(uri, null, null)
            false
        }
    }

    /** API 26–28. The system save sheet, local files only. */
    fun localCreate(name: String): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, name)
        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    }

    /** The user picks the file. After a reinstall this is the only way back into Downloads. */
    fun localOpen(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    }

    /** Drive's own upload screen, or null when that screen will not open. [name] is the file's display name. */
    fun driveSend(context: Context, text: String, name: String): Intent? {
        val uri = WordShareProvider.publish(context, text, name)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, name)
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(DRIVE)
        }
        return send.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /**
     * Drive's own picker, if this install still exposes one.
     * The system document UI is not a substitute: a missing screen means we refuse.
     */
    fun driveOpen(context: Context): Intent? {
        val get = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            setPackage(DRIVE)
        }
        if (get.resolveActivity(context.packageManager) != null) return get
        val open = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            setPackage(DRIVE)
        }
        return open.takeIf { it.resolveActivity(context.packageManager) != null }
    }
}

/** Result path from [WordFileActivity] back to the input method, which outlives the picker. */
object WordTransfer {
    var onImported: ((String?) -> Unit)? = null
    var onNotice: ((Int) -> Unit)? = null

    fun imported(text: String?) {
        onImported?.invoke(text)
    }

    fun notice(message: Int) {
        onNotice?.invoke(message)
    }
}
