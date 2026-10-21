package com.notee.pinyin.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import com.notee.pinyin.BuildConfig
import com.notee.pinyin.R
import com.notee.pinyin.ime.PinyinInputMethodService

/** The only screen outside the keyboard: get it turned on, get it picked, and say where the source is. */
class SetupActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        status = findViewById(R.id.setup_status)
        findViewById<View>(R.id.setup_enable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<View>(R.id.setup_switch).setOnClickListener {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
        findViewById<View>(R.id.setup_privacy_link).setOnClickListener {
            open(getString(R.string.setup_privacy_url))
        }
        val source = findViewById<TextView>(R.id.setup_source)
        if (BuildConfig.SOURCE_URL.isEmpty()) {
            source.visibility = View.GONE
        } else {
            source.text = getString(R.string.setup_source) + "\n" + BuildConfig.SOURCE_URL
            source.setOnClickListener { open(BuildConfig.SOURCE_URL) }
        }
    }

    override fun onResume() {
        super.onResume()
        showStatus()
    }

    /** The input method picker is a dialog; closing it gives focus back without a resume. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) showStatus()
    }

    /** No browser on the device: leave the page as it is. */
    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun showStatus() {
        val id = ComponentName(this, PinyinInputMethodService::class.java).flattenToShortString()
        val enabled = getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.any { it.id == id } == true
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) == id
        status.setText(
            when {
                current -> R.string.setup_status_ready
                enabled -> R.string.setup_status_enabled
                else -> R.string.setup_status_off
            },
        )
    }
}
