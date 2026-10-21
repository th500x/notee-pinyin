package com.notee.pinyin.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle

/**
 * The keyboard cannot receive [onActivityResult], so file pickers come back here.
 * Translucent, not exported, and not a screen of its own.
 */
class WordFileActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val launch = launchIntent()
        if (launch == null) {
            finish()
            return
        }
        try {
            startActivityForResult(launch, REQUEST)
        } catch (_: ActivityNotFoundException) {
            WordTransfer.notice(com.notee.pinyin.R.string.words_drive_sign_in)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = if (requestCode == REQUEST && resultCode == RESULT_OK) data?.data else null
        if (uri != null) {
            val exporting = intent.getStringExtra(EXTRA_MODE) == MODE_EXPORT
            if (exporting) {
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                val wrote = try {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(text.toByteArray(Charsets.UTF_8))
                        true
                    } ?: false
                } catch (_: java.io.IOException) {
                    false
                }
                WordTransfer.notice(if (wrote) com.notee.pinyin.R.string.words_saved else com.notee.pinyin.R.string.words_save_failed)
            } else {
                val text = try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.size > MAX_BYTES) null else bytes.toString(Charsets.UTF_8)
                    }
                } catch (_: java.io.IOException) {
                    null
                }
                WordTransfer.imported(text)
            }
        }
        finish()
    }

    @Suppress("DEPRECATION")
    private fun launchIntent(): Intent? = intent.getParcelableExtra(EXTRA_LAUNCH)

    companion object {
        const val MODE_EXPORT = "export"
        const val MODE_IMPORT = "import"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_LAUNCH = "launch"
        private const val REQUEST = 1
        private const val MAX_BYTES = 1_048_576

        fun start(context: android.content.Context, mode: String, text: String?, launch: Intent) {
            context.startActivity(
                Intent(context, WordFileActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(EXTRA_MODE, mode)
                    putExtra(EXTRA_TEXT, text)
                    putExtra(EXTRA_LAUNCH, launch)
                },
            )
        }
    }
}
