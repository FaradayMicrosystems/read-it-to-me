package com.faradaymicrosystems.nobodygenerator

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faradaymicrosystems.nobodygenerator.audio.PcmPlayer
import com.faradaymicrosystems.nobodygenerator.data.Library
import com.faradaymicrosystems.nobodygenerator.data.Recording
import com.faradaymicrosystems.nobodygenerator.service.GenerateService
import com.faradaymicrosystems.nobodygenerator.service.JobState
import com.faradaymicrosystems.nobodygenerator.service.Jobs
import com.faradaymicrosystems.nobodygenerator.service.VoiceDownloadService
import com.faradaymicrosystems.nobodygenerator.tts.KokoroModel
import com.faradaymicrosystems.nobodygenerator.tts.KokoroVoices
import com.faradaymicrosystems.nobodygenerator.tts.SpeechEngine
import com.faradaymicrosystems.nobodygenerator.text.DocumentLoader
import com.faradaymicrosystems.nobodygenerator.text.Source
import com.faradaymicrosystems.nobodygenerator.tts.Synthesizer
import com.faradaymicrosystems.nobodygenerator.tts.VoiceCatalog
import com.faradaymicrosystems.nobodygenerator.tts.VoiceOption
import com.faradaymicrosystems.nobodygenerator.ui.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PickedFile(val path: String, val name: String)

data class UiState(
    val picked: PickedFile? = null,
    val input: String = "",
    val voices: List<VoiceOption> = emptyList(),
    val voiceName: String? = null,
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val engineReady: Boolean = false,
    val engineError: String? = null,
    val message: String? = null,
    val natural: KokoroModel.State = KokoroModel.State.NotInstalled,
    val previewing: Boolean = false,
) {
    val selectedVoice: VoiceOption? get() = voices.firstOrNull { it.name == voiceName }
    val naturalSelected: Boolean get() = selectedVoice?.natural == true
    val hasAustralian: Boolean get() = VoiceCatalog.hasAustralian(voices)
    val canGo: Boolean get() = engineReady && (picked != null || input.isNotBlank())
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(
        UiState(
            voiceName = prefs.getString("voice", null),
            rate = prefs.getFloat("rate", 1.0f),
            pitch = prefs.getFloat("pitch", 1.0f),
        ),
    )
    val ui: StateFlow<UiState> = _ui
    val job: StateFlow<JobState> = Jobs.state

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings

    val player = Player(viewModelScope, prefs.getFloat("playbackSpeed", 1.0f))
    private var synth: Synthesizer? = null
    private var systemReady = false
    private var systemError: String? = null
    var pendingExport: Recording? = null

    init {
        KokoroModel.refresh(app)
        viewModelScope.launch {
            try {
                synth = Synthesizer.create(app)
            } catch (e: Exception) {
                systemError = e.message
            }
            systemReady = true
            refreshVoices()
        }
        viewModelScope.launch {
            var previous: KokoroModel.State? = null
            KokoroModel.state.collect { s ->
                _ui.update { it.copy(natural = s) }
                val justInstalled = s is KokoroModel.State.Ready &&
                    (previous is KokoroModel.State.Downloading || previous is KokoroModel.State.Installing)
                if (s is KokoroModel.State.Ready || previous is KokoroModel.State.Ready) refreshVoices()
                // First install: switch to the natural default voice (British Emma).
                if (justInstalled) setVoice(KokoroVoices.default.optionName)
                previous = s
            }
        }
        viewModelScope.launch { Library.version.collect { refreshRecordings() } }
    }

    fun refreshVoices() {
        if (!systemReady) return
        val natural = if (KokoroModel.isReady(getApplication())) VoiceCatalog.naturalOptions() else emptyList()
        val system = synth?.let { VoiceCatalog.options(it.voices()) } ?: emptyList()
        val options = natural + system
        _ui.update { state ->
            val keep = options.any { it.name == state.voiceName }
            state.copy(
                voices = options,
                voiceName = if (keep) state.voiceName else VoiceCatalog.defaultVoice(options)?.name,
                engineReady = true,
                engineError = when {
                    options.isNotEmpty() -> null
                    systemError != null -> systemError
                    else -> "No English voices are installed on this phone."
                },
            )
        }
    }

    fun downloadNaturalVoices() = VoiceDownloadService.start(getApplication())
    fun cancelNaturalVoices() = VoiceDownloadService.cancel(getApplication())

    fun removeNaturalVoices() {
        if (job.value is JobState.Running) return
        if (_ui.value.naturalSelected) _ui.update { it.copy(voiceName = null) }
        KokoroModel.remove(getApplication())
        refreshVoices()
        _ui.value.voiceName?.let { setVoice(it) }
    }

    fun refreshRecordings() {
        viewModelScope.launch {
            _recordings.value = withContext(Dispatchers.IO) { Library.list(getApplication()) }
        }
    }

    fun setInput(text: String) = _ui.update { it.copy(input = text) }
    fun clearPicked() = _ui.update { it.copy(picked = null) }
    fun clearMessage() = _ui.update { it.copy(message = null) }

    fun setVoice(name: String) {
        prefs.edit().putString("voice", name).apply()
        _ui.update { it.copy(voiceName = name) }
    }

    fun setRate(v: Float) {
        prefs.edit().putFloat("rate", v).apply()
        _ui.update { it.copy(rate = v) }
    }

    fun setPlaybackSpeed(v: Float) {
        prefs.edit().putFloat("playbackSpeed", v).apply()
        player.setSpeed(v)
    }

    fun setPitch(v: Float) {
        prefs.edit().putFloat("pitch", v).apply()
        _ui.update { it.copy(pitch = v) }
    }

    fun preview() {
        val st = _ui.value
        if (st.naturalSelected) {
            if (st.previewing) return
            _ui.update { it.copy(previewing = true) }
            viewModelScope.launch {
                try {
                    val pcm = withContext(Dispatchers.Default) {
                        SpeechEngine.open(getApplication(), st.voiceName, st.rate, st.pitch).use { it.synthesize(SAMPLE) }
                    }
                    if (pcm != null) PcmPlayer.play(pcm) else _ui.update { it.copy(message = "That voice didn't make any sound.") }
                } catch (e: Exception) {
                    _ui.update { it.copy(message = "Couldn't play the sample: ${e.message}") }
                } finally {
                    _ui.update { it.copy(previewing = false) }
                }
            }
            return
        }
        val s = synth ?: return
        PcmPlayer.stop()
        s.configure(st.voiceName, st.rate, st.pitch)
        s.speak(SAMPLE)
    }

    /** Copies a picked or shared document into the app, so it can be read even after the picker's permission ends. */
    fun pickFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val picked = withContext(Dispatchers.IO) { copyIn(uri) }
                _ui.update { it.copy(picked = picked, input = "") }
            } catch (e: Exception) {
                _ui.update { it.copy(message = "Couldn't open that file: ${e.message}") }
            }
        }
    }

    fun handleIncoming(intent: Intent) {
        val stream: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (stream != null) {
            pickFile(stream)
            return
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (intent.action == Intent.ACTION_SEND && !text.isNullOrBlank()) {
            // Browsers share "Title https://link" — keep just the link when there is one.
            val link = Regex("""https?://\S+""").find(text)?.value
            val keepLink = link != null && text.length < 400
            _ui.update { it.copy(input = if (keepLink) link!! else text, picked = null) }
        }
    }

    fun go() {
        val st = _ui.value
        if (!st.canGo || job.value is JobState.Running) return
        val app = getApplication<Application>()
        val source: Source = when {
            st.picked != null -> Source.LocalFile(st.picked.path, st.picked.name)
            st.input.length > 50_000 -> {
                // Big pastes don't fit in an Intent; hand them over as a file.
                val f = File(File(app.cacheDir, "input").apply { mkdirs() }, "Pasted text.paste")
                f.writeText(st.input)
                Source.LocalFile(f.absolutePath, f.name)
            }
            else -> DocumentLoader.fromInputText(st.input)
        }
        player.release()
        synth?.stop()
        PcmPlayer.stop()
        GenerateService.start(app, source, st.voiceName, st.rate, st.pitch)
    }

    fun cancel() = GenerateService.cancel(getApplication())
    fun dismissJob() = Jobs.dismiss()

    fun delete(rec: Recording) {
        player.stopIf(rec.file)
        rec.file.delete()
        Library.changed()
    }

    fun exportTo(uri: Uri) {
        val rec = pendingExport ?: return
        pendingExport = null
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { out ->
                        rec.file.inputStream().use { it.copyTo(out) }
                    }
                }.isSuccess
            }
            _ui.update { it.copy(message = if (ok) "Saved \"${rec.title}\"" else "Couldn't save the file") }
        }
    }

    private fun copyIn(uri: Uri): PickedFile {
        val resolver = getApplication<Application>().contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        if (!name.contains('.')) {
            when (resolver.getType(uri)) {
                "application/pdf" -> name += ".pdf"
                "text/markdown", "text/x-markdown" -> name += ".md"
                "text/html" -> name += ".html"
                "text/plain" -> name += ".txt"
            }
        }
        val dir = File(getApplication<Application>().cacheDir, "input").apply {
            deleteRecursively()
            mkdirs()
        }
        val dest = File(dir, name.replace('/', '_'))
        resolver.openInputStream(uri)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
        return PickedFile(dest.absolutePath, name)
    }

    override fun onCleared() {
        PcmPlayer.stop()
        player.release()
        synth?.shutdown()
    }

    private companion object {
        const val SAMPLE = "G'day. This is how your documents will sound when I read them to you."
    }
}
