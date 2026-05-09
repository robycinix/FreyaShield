package com.callguardian.screening

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.callguardian.data.AppPreferences

class CallChoiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rawNumber = intent.getStringExtra(EXTRA_RAW_NUMBER).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val normalized = normalizePhonePattern(rawNumber)
        if (normalized.isBlank()) {
            return
        }

        when (intent.action) {
            ACTION_TRUST -> {
                val trusted = (AppPreferences.loadWhitelist(context) + normalized).distinct()
                AppPreferences.saveWhitelist(context, trusted)
                AppPreferences.applyToCore(context)
                Toast.makeText(context, "Numero aggiunto ai fidati", Toast.LENGTH_SHORT).show()
            }

            ACTION_BLOCK_SIMILAR -> {
                val prefix = normalized.take(minOf(6, normalized.length))
                if (prefix.length >= 5) {
                    val blocked = (AppPreferences.loadBlocklist(context) + "$prefix*").distinct()
                    AppPreferences.saveBlocklist(context, blocked)
                    AppPreferences.applyToCore(context)
                    Toast.makeText(context, "Prefisso aggiunto ai bloccati", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.cancel(notificationId)
    }

    private fun normalizePhonePattern(rawPhoneNumber: String): String {
        var cleaned = rawPhoneNumber.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.drop(2)
        } else if (!cleaned.startsWith("+") && cleaned.isNotBlank()) {
            cleaned = "+39$cleaned"
        }
        return cleaned
    }

    companion object {
        const val ACTION_TRUST = "com.callguardian.action.TRUST_CALLER"
        const val ACTION_BLOCK_SIMILAR = "com.callguardian.action.BLOCK_SIMILAR_CALLERS"
        const val EXTRA_RAW_NUMBER = "extra_raw_number"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
