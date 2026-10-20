package com.einkmemo.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.util.Locale

class MainActivity : ComponentActivity() {
    var pageKeyHandler: ((Int) -> Boolean)? = null

    override fun attachBaseContext(newBase: Context) {
        val mode = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(K_LANGUAGE, 0)
        if (mode == 0) {
            super.attachBaseContext(newBase)
            return
        }
        val locale = if (mode == 1) Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot(this) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val delta = when (event.keyCode) {
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_UP -> -1
                KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> 1
                else -> 0
            }
            if (delta != 0 && pageKeyHandler?.invoke(delta) == true) {
                return true
            }
            Log.d("EinkMemoKey", "keyCode=" + event.keyCode + " action=" + event.action)
        }
        return super.dispatchKeyEvent(event)
    }
}