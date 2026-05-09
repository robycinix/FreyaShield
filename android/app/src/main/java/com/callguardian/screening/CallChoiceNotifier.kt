package com.callguardian.screening

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.callguardian.MainActivity
import com.callguardian.R
import com.callguardian.engine.PlatformRiskAssessment

object CallChoiceNotifier {
    private const val CHANNEL_ID = "freya_call_choices"
    private const val CHANNEL_NAME = "Scelte chiamata Freya"

    fun show(context: Context, rawNumber: String, assessment: PlatformRiskAssessment) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        val notificationId = rawNumber.hashCode().xor(System.currentTimeMillis().toInt())
        val maskedNumber = maskPhoneNumber(rawNumber)
        val title = if (assessment.action == ACTION_WARN) {
            "Freya vede un rischio"
        } else {
            "Freya ha controllato"
        }
        val text = if (assessment.action == ACTION_WARN) {
            "$maskedNumber puo passare, ma merita attenzione."
        } else {
            "$maskedNumber ha superato i controlli."
        }

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(Notification.PRIORITY_HIGH)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, notificationId))
            .addAction(
                R.drawable.ic_launcher_monochrome,
                "Fidati",
                choiceIntent(context, notificationId, rawNumber, CallChoiceReceiver.ACTION_TRUST)
            )
            .addAction(
                R.drawable.ic_launcher_monochrome,
                "Blocca simili",
                choiceIntent(context, notificationId, rawNumber, CallChoiceReceiver.ACTION_BLOCK_SIMILAR)
            )
            .build()

        manager.notify(notificationId, notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            return
        }
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Mostra le scelte rapide quando una chiamata supera i controlli."
        manager.createNotificationChannel(channel)
    }

    private fun openAppIntent(context: Context, notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun choiceIntent(
        context: Context,
        notificationId: Int,
        rawNumber: String,
        action: String
    ): PendingIntent {
        val intent = Intent(context, CallChoiceReceiver::class.java)
            .setAction(action)
            .putExtra(CallChoiceReceiver.EXTRA_RAW_NUMBER, rawNumber)
            .putExtra(CallChoiceReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        return PendingIntent.getBroadcast(
            context,
            notificationId xor action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun maskPhoneNumber(number: String): String {
        val cleaned = number.trim()
        if (cleaned.length <= 6) {
            return "******"
        }
        return cleaned.take(6) + "******"
    }

    private const val ACTION_WARN = 1
}
