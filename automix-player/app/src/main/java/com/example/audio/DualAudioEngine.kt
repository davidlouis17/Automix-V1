package com.example.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Audio Engine ganda (Dual Audio Engine) menggunakan dua instansi ExoPlayer.
 * Dilengkapi fitur crossfade otomatis saat sisa durasi lagu aktif <= 20 detik.
 */
class DualAudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "DualAudioEngine"
        const val CROSSFADE_THRESHOLD_MS = 20000L // 20 detik sesuai batasan Tugas Akhir
    }

    private val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Dua instansi ExoPlayer untuk memproses audio paralel (Deck A & Deck B)
    private var playerA = ExoPlayer.Builder(context).build()
    private var playerB = ExoPlayer.Builder(context).build()

    // Referensi dinamis ke player Utama (Primary) dan Sekunder (Secondary)
    var primaryPlayer: ExoPlayer = playerA
        private set
    var secondaryPlayer: ExoPlayer = playerB
        private set

    // State pemutaran yang diekspos ke UI secara deklaratif (Compose-friendly)
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _nextTrack = MutableStateFlow<Track?>(null)
    val nextTrack: StateFlow<Track?> = _nextTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _primaryVolume = MutableStateFlow(1.0f)
    val primaryVolume: StateFlow<Float> = _primaryVolume.asStateFlow()

    private val _secondaryVolume = MutableStateFlow(0.0f)
    val secondaryVolume: StateFlow<Float> = _secondaryVolume.asStateFlow()

    private val _isCrossfading = MutableStateFlow(false)
    val isCrossfading: StateFlow<Boolean> = _isCrossfading.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0L) // Posisi mili detik lagu utama
    val playbackProgress: StateFlow<Long> = _playbackProgress.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L) // Total durasi lagu utama
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    // Status penyelesaian transisi untuk pemberitahuan ViewModel agar melakukan KNN baru
    var onTransitionCompleted: ((Track) -> Unit)? = null

    private var monitorJob: Job? = null
    private var crossfadeProgressJob: Job? = null

    init {
        setupPlayerListeners()
        startPlaybackMonitor()
    }

    private fun setupPlayerListeners() {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (primaryPlayer.isPlaying) {
                    _isPlaying.value = true
                } else if (!primaryPlayer.isPlaying && !secondaryPlayer.isPlaying) {
                    _isPlaying.value = false
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // Jika lagu utama berakhir dan kita belum menyelesaikan crossfade, paksa penyelesaian
                    if (_isCrossfading.value) {
                        completeCrossfade()
                    } else if (_nextTrack.value != null) {
                        triggerManualTransition()
                    } else {
                        _isPlaying.value = false
                    }
                }
            }
        }

        playerA.addListener(listener)
        playerB.addListener(listener)

        // Set volume awal
        playerA.volume = 1.0f
        playerB.volume = 0.0f
    }

    /**
     * Memutar lagu sebagai lagu utama (Primary Player).
     */
    fun playTrack(track: Track) {
        stopAll()
        _currentTrack.value = track
        _nextTrack.value = null
        _isCrossfading.value = false
        _primaryVolume.value = 1.0f
        _secondaryVolume.value = 0.0f

        primaryPlayer.volume = 1.0f
        secondaryPlayer.volume = 0.0f

        val mediaItem = createMediaItem(track)
        primaryPlayer.setMediaItem(mediaItem)
        primaryPlayer.prepare()
        primaryPlayer.play()
        _isPlaying.value = true

        Log.d(TAG, "Playing primary track: ${track.title} [BPM: ${track.tempo}]")
    }

    /**
     * Mempersiapkan lagu berikutnya pada player kedua (Secondary Player).
     * Lagu ini akan bermarkas di Deck B (Secondary) dan bersiap untuk di-crossfade.
     */
    fun prepareNextTrack(track: Track) {
        _nextTrack.value = track
        secondaryPlayer.volume = 0.0f
        
        val mediaItem = createMediaItem(track)
        secondaryPlayer.setMediaItem(mediaItem)
        secondaryPlayer.prepare()
        
        Log.d(TAG, "Secondary player pre-fetched and prepared track: ${track.title} [BPM: ${track.tempo}]")
    }

    /**
     * Pause lagu aktif.
     */
    fun pause() {
        if (_isCrossfading.value) {
            primaryPlayer.pause()
            secondaryPlayer.pause()
        } else {
            primaryPlayer.pause()
        }
        _isPlaying.value = false
    }

    /**
     * Resume lagu aktif.
     */
    fun resume() {
        if (_isCrossfading.value) {
            primaryPlayer.play()
            secondaryPlayer.play()
        } else {
            primaryPlayer.play()
        }
        _isPlaying.value = true
    }

    /**
     * Lompat (Skip) langsung ke lagu berikutnya secara instan (tanpa menunggu crossfade alami).
     */
    fun skipToNext() {
        val next = _nextTrack.value
        if (next != null) {
            triggerManualTransition()
        }
    }

    private fun triggerManualTransition() {
        Log.d(TAG, "Manual transition/skip triggered")
        val next = _nextTrack.value ?: return
        
        // Memulai crossfade cepat (misal durasi 1 detik)
        executeCrossfadeFast(next)
    }

    private fun createMediaItem(track: Track): MediaItem {
        // Cari Uri berkas lagu
        val defaultDemoUri = Uri.parse("file:///android_asset/demo_loop.mp3")
        val uri = if (!track.filePath.isNullOrEmpty()) {
            val file = File(track.filePath)
            if (file.exists() && file.length() > 2000L) { // Prevent tiny fake dummy files from crashing ExoPlayer
                Uri.fromFile(file)
            } else {
                // Silakan fallback ke demo loop aset jika file fisik tidak ada atau tidak valid
                defaultDemoUri
            }
        } else {
            // Default demo asset untuk keperluan pre-sales / visual debugging
            defaultDemoUri
        }
        return MediaItem.Builder().setUri(uri).build()
    }

    /**
     * Monitor posisi pemutaran secara terus menerus (tiap 200ms) untuk mendeteksi threshold sisa durasi.
     */
    private fun startPlaybackMonitor() {
        monitorJob?.cancel()
        monitorJob = applicationScope.launch {
            while (isActive) {
                try {
                    if (primaryPlayer.playbackState == Player.STATE_READY && primaryPlayer.isPlaying) {
                        val currentPos = primaryPlayer.currentPosition
                        val duration = primaryPlayer.duration
                        
                        _playbackProgress.value = currentPos
                        if (duration > 0) {
                            _playbackDuration.value = duration
                            
                            val remainingTime = duration - currentPos
                            
                            // Deteksi threshold sisa durasi <= 20 detik (20000ms) untuk memicu crossfade
                            if (remainingTime <= CROSSFADE_THRESHOLD_MS && 
                                !_isCrossfading.value && 
                                _nextTrack.value != null
                            ) {
                                triggerCrossfade(remainingTime)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in playback monitor", e)
                }
                delay(200)
            }
        }
    }

    /**
     * Memicu dimulainya fase crossfade secara gradual (Automix).
     */
    private fun triggerCrossfade(durationRemainingMs: Long) {
        _isCrossfading.value = true
        Log.d(TAG, "Memicu AUTOMIX CROSSFADE! Sisa durasi lagu aktif: ${durationRemainingMs / 1000.0} detik.")

        // Mulai mainkan Secondary Player dengan volume awal 0.0f
        secondaryPlayer.volume = 0.0f
        secondaryPlayer.play()

        crossfadeProgressJob?.cancel()
        crossfadeProgressJob = applicationScope.launch {
            val crossfadeDuration = durationRemainingMs.coerceAtLeast(1000L) // Durasi transisi
            var elapsed = 0L
            val interval = 100L

            while (elapsed < crossfadeDuration && _isCrossfading.value) {
                val progress = elapsed.toDouble() / crossfadeDuration.toDouble()
                
                // Manipulasi Volume Paralel (Fade-Out Utama & Fade-In Sekunder) secara Linear
                val outgoingVol = (1.0 - progress).toFloat().coerceIn(0.0f, 1.0f)
                val incomingVol = progress.toFloat().coerceIn(0.0f, 1.0f)

                _primaryVolume.value = outgoingVol
                _secondaryVolume.value = incomingVol

                primaryPlayer.volume = outgoingVol
                secondaryPlayer.volume = incomingVol

                delay(interval)
                elapsed += interval
            }
            // Selesaikan crossfade
            completeCrossfade()
        }
    }

    /**
     * Eksekusi transisi cepat saat manual skip dilakukan.
     */
    private fun executeCrossfadeFast(nextTrack: Track) {
        _isCrossfading.value = true
        secondaryPlayer.volume = 0.0f
        
        val mediaItem = createMediaItem(nextTrack)
        secondaryPlayer.setMediaItem(mediaItem)
        secondaryPlayer.prepare()
        secondaryPlayer.play()

        crossfadeProgressJob?.cancel()
        crossfadeProgressJob = applicationScope.launch {
            val duration = 1000L // 1 detik transisi cepat
            var elapsed = 0L
            val interval = 50L

            while (elapsed < duration && _isCrossfading.value) {
                val progress = elapsed.toDouble() / duration.toDouble()
                val outgoingVol = (1.0 - progress).toFloat().coerceIn(0.0f, 1.0f)
                val incomingVol = progress.toFloat().coerceIn(0.0f, 1.0f)

                _primaryVolume.value = outgoingVol
                _secondaryVolume.value = incomingVol

                primaryPlayer.volume = outgoingVol
                secondaryPlayer.volume = incomingVol

                delay(interval)
                elapsed += interval
            }
            completeCrossfade()
        }
    }

    /**
     * Selesaikan proses crossfade secara mutlak, hentikan lagu lama, dan swap peran player.
     */
    private fun completeCrossfade() {
        crossfadeProgressJob?.cancel()
        if (!_isCrossfading.value) return

        Log.d(TAG, "Crossfade selesai. Melakukan SWAP PLAYER.")

        // Hentikan pemutaran lagu lama (Primary) secara bersih
        primaryPlayer.stop()
        primaryPlayer.clearMediaItems()
        primaryPlayer.volume = 0.0f

        // Pastikan lagu baru (Sekunder) sudah di volume penuh
        secondaryPlayer.volume = 1.0f
        _secondaryVolume.value = 1.0f
        _primaryVolume.value = 0.0f

        // Tukar referensi player secara sirkular (Deck Swap)
        val temp = primaryPlayer
        primaryPlayer = secondaryPlayer
        secondaryPlayer = temp

        val solvedNext = _nextTrack.value
        _currentTrack.value = solvedNext
        _nextTrack.value = null
        _isCrossfading.value = false

        _playbackProgress.value = 0L
        _playbackDuration.value = primaryPlayer.duration.coerceAtLeast(0L)

        // Beritahukan ViewModel bahwa transisi lagu bermigrasi
        solvedNext?.let {
            onTransitionCompleted?.invoke(it)
        }
    }

    /**
     * Paksa menghentikan semua pemutaran dan me-reset engine.
     */
    fun stopAll() {
        monitorJob?.cancel()
        crossfadeProgressJob?.cancel()
        
        playerA.stop()
        playerA.clearMediaItems()
        playerA.volume = 1.0f
        
        playerB.stop()
        playerB.clearMediaItems()
        playerB.volume = 0.0f

        primaryPlayer = playerA
        secondaryPlayer = playerB

        _currentTrack.value = null
        _nextTrack.value = null
        _isPlaying.value = false
        _isCrossfading.value = false
        _primaryVolume.value = 1.0f
        _secondaryVolume.value = 0.0f
        _playbackProgress.value = 0L
        _playbackDuration.value = 0L

        startPlaybackMonitor()
    }

    /**
     * Lakukan rilis sumber daya secara bersih untuk menghindari kebocoran memori.
     */
    fun release() {
        applicationScope.cancel()
        playerA.release()
        playerB.release()
    }
}
