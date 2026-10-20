package com.einkmemo.app

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    var pageKeyHandler: ((Int) -> Boolean)? = null

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
