package com.example.carcompanion.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class ClipboardUtils(private val context: Context) {
    fun copyText(text: String, label: String = "Copied Text") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }
}
