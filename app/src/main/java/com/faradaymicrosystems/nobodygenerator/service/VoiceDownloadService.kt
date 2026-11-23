package com.faradaymicrosystems.nobodygenerator.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.faradaymicrosystems.nobodygenerator.tts.KokoroModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Foreground service that downloads and installs the natural (Kokoro) voices. */
class VoiceDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel()
            if (job == null) stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY

        Notifications.createChannels(this)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, Notifications.DOWNLOAD_ID, Notifications.voiceProgress(this, KokoroModel.state.value), type)

        val updates = scope.launch {
            var last = 0L
            KokoroModel.state.collectLatest { s ->
                val now = System.currentTimeMillis()
                if (now - last > 800) {
                    last = now
                    Notifications.updateDownload(this@VoiceDownloadService, Notifications.voiceProgress(this@VoiceDownloadService, s))
                }
            }
        }
        job = scope.launch {
            try {
                KokoroModel.install(this@VoiceDownloadService)
                Notifications.voicesReady(this@VoiceDownloadService)
            } catch (e: CancellationException) {
                KokoroModel.setNotInstalled(this@VoiceDownloadService)
            } catch (e: Throwable) {
                Log.e(TAG, "Voice install failed", e)
                val msg = e.message ?: "Couldn't install the natural voices."
                KokoroModel.setFailed(msg)
                Notifications.voicesFailed(this@VoiceDownloadService, msg)
            } finally {
                updates.cancel()
                ServiceCompat.stopForeground(this@VoiceDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NobodyGenerator"
        const val ACTION_CANCEL = "com.faradaymicrosystems.nobodygenerator.CANCEL_VOICE_DOWNLOAD"

        fun start(context: Context) {
            KokoroModel.markStarting()
            ContextCompat.startForegroundService(context, Intent(context, VoiceDownloadService::class.java))
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, VoiceDownloadService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
