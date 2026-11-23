package com.faradaymicrosystems.nobodygenerator.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.faradaymicrosystems.nobodygenerator.MainActivity
import com.faradaymicrosystems.nobodygenerator.R

object Notifications {
    const val PROGRESS_ID = 1
    private const val RESULT_ID = 2
    private const val CH_PROGRESS = "progress"
    private const val CH_RESULT = "results"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Generating audio", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress while a document is being read aloud to MP3"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "Finished audio", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when your MP3 is ready"
            },
        )
    }

    private fun openApp(context: Context, library: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_LIBRARY, library)
        return PendingIntent.getActivity(
            context, if (library) 2 else 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun progress(context: Context, title: String, stage: String, done: Int, total: Int): Notification {
        val cancel = PendingIntent.getService(
            context, 3,
            Intent(context, GenerateService::class.java).setAction(GenerateService.ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_voice)
            .setContentTitle(title)
            .setContentText(stage)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(total, done, total == 0)
            .setContentIntent(openApp(context, library = false))
            .addAction(0, "Cancel", cancel)
            .build()
    }

    fun finished(context: Context, title: String) = post(
        context,
        NotificationCompat.Builder(context, CH_RESULT)
            .setSmallIcon(R.drawable.ic_stat_voice)
            .setContentTitle("Your MP3 is ready")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, library = true))
            .build(),
    )

    fun failed(context: Context, message: String) = post(
        context,
        NotificationCompat.Builder(context, CH_RESULT)
            .setSmallIcon(R.drawable.ic_stat_voice)
            .setContentTitle("Couldn't make the MP3")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, library = false))
            .build(),
    )

    private fun post(context: Context, n: Notification) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(RESULT_ID, n)
    }

    fun update(context: Context, n: Notification) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(PROGRESS_ID, n)
    }
}
