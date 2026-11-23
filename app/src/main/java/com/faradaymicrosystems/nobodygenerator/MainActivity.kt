package com.faradaymicrosystems.nobodygenerator

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faradaymicrosystems.nobodygenerator.data.Recording
import com.faradaymicrosystems.nobodygenerator.service.Notifications
import com.faradaymicrosystems.nobodygenerator.ui.CreateScreen
import com.faradaymicrosystems.nobodygenerator.ui.LibraryScreen
import com.faradaymicrosystems.nobodygenerator.ui.NobodyTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var tab by mutableIntStateOf(0)

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::pickFile)
    }
    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri ->
        if (uri != null) vm.exportTo(uri) else vm.pendingExport = null
    }
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.createChannels(this)
        if (savedInstanceState == null) handle(intent)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            NobodyTheme {
                val ui by vm.ui.collectAsStateWithLifecycle()
                val job by vm.job.collectAsStateWithLifecycle()
                val recordings by vm.recordings.collectAsStateWithLifecycle()
                val player by vm.player.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(ui.message) {
                    ui.message?.let {
                        snackbar.showSnackbar(it)
                        vm.clearMessage()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = { Icon(painterResource(R.drawable.ic_wave), contentDescription = null) },
                                label = { Text("Create") },
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = { Icon(painterResource(R.drawable.ic_library), contentDescription = null) },
                                label = { Text("Library") },
                            )
                        }
                    },
                ) { padding ->
                    if (tab == 0) {
                        CreateScreen(
                            ui = ui,
                            job = job,
                            onPickFile = { openDocument.launch(arrayOf("*/*")) },
                            onClearFile = vm::clearPicked,
                            onInput = vm::setInput,
                            onVoice = vm::setVoice,
                            onRate = vm::setRate,
                            onPitch = vm::setPitch,
                            onPreview = vm::preview,
                            onGo = vm::go,
                            onCancel = vm::cancel,
                            onDismiss = vm::dismissJob,
                            onOpenLibrary = {
                                vm.dismissJob()
                                tab = 1
                            },
                            onGetVoices = ::openSpeechSettings,
                            modifier = Modifier.padding(padding),
                        )
                    } else {
                        LibraryScreen(
                            recordings = recordings,
                            player = player,
                            onPlay = { rec -> runCatching { vm.player.toggle(rec.file, rec.title) } },
                            onSeek = vm.player::seekTo,
                            onSkip = vm.player::skip,
                            onSave = { rec ->
                                vm.pendingExport = rec
                                saveDocument.launch("${rec.title}.mp3")
                            },
                            onShare = ::share,
                            onDelete = vm::delete,
                            modifier = Modifier.padding(padding),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshVoices() // picks up voices installed while the user was in settings
        vm.refreshRecordings()
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_OPEN_LIBRARY, false)) {
            vm.dismissJob()
            tab = 1
            return
        }
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW) {
            vm.handleIncoming(intent)
            tab = 0
        }
    }

    private fun share(rec: Recording) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", rec.file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("audio/mpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, rec.title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Share MP3"))
    }

    private fun openSpeechSettings() {
        val attempts = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent(android.provider.Settings.ACTION_SETTINGS),
        )
        for (i in attempts) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        Toast.makeText(this, "Open Settings → Accessibility → Text-to-speech", Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_OPEN_LIBRARY = "open_library"
    }
}
