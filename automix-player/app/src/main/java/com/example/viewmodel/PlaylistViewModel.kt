package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.DualAudioEngine
import com.example.data.KnnDatasetLoader
import com.example.data.MusicScanner
import com.example.knn.KnnPredictor
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel utama untuk mengelola daftar lagu (Playlist), logika pemutaran,
 * dan sinkronisasi prediksi pencarian tetangga terdekat (KNN) untuk Automix.
 */
class PlaylistViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlaylistViewModel"
    }

    private val context = application.applicationContext

    // Engine Audio Ganda
    val audioEngine = DualAudioEngine(context)

    // Dataset Referensi Spotify dari CSV
    private val _referenceTracks = MutableStateFlow<List<Track>>(emptyList())
    val referenceTracks: StateFlow<List<Track>> = _referenceTracks.asStateFlow()

    // Daftar lagu yang terdeteksi di memori internal HP (di-load lewat scan)
    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks: StateFlow<List<Track>> = _localTracks.asStateFlow()

    // Status loading saat memindai
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Daftar 5 tetangga terdekat (K=5) beserta jarak Euclideannya untuk lagu aktif saat ini
    private val _knnRecommendations = MutableStateFlow<List<Pair<Track, Double>>>(emptyList())
    val knnRecommendations: StateFlow<List<Pair<Track, Double>>> = _knnRecommendations.asStateFlow()

    init {
        // Daftarkan listener transisi audio agar ketika lagu berganti, lagu aktif baru memicu pencarian KNN baru
        audioEngine.onTransitionCompleted = { newActiveTrack ->
            Log.d(TAG, "Transisi lagu terdeteksi via callback. Menjalankan KNN baru untuk: ${newActiveTrack.title}")
            triggerKnnPrediction(newActiveTrack)
        }

        // Jalankan pekerjaan inisialisasi awal pada background thread
        initializeDataset()
    }

    /**
     * Membaca dataset CSV referensi dari assets secara asinkron.
     */
    private fun initializeDataset() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val parsedDataset = KnnDatasetLoader.loadDataset(context)
            _referenceTracks.value = parsedDataset
            
            // Setelah memuat dataset referensi, lakukan pemindaian awal musik lokal
            scanMusicInternal(parsedDataset)
            _isLoading.value = false
        }
    }

    /**
     * Memindai file musik lokal.
     */
    fun scanLocalMusic() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            scanMusicInternal(_referenceTracks.value)
            _isLoading.value = false
        }
    }

    private fun scanMusicInternal(refDataset: List<Track>) {
        val scanned = MusicScanner.scanLocalMusic(context, refDataset)
        _localTracks.value = scanned
        Log.d(TAG, "Pemindaian musik lokal selesai. Menemukan ${scanned.size} lagu aktif.")

        // Jika ada lagu yang aktif sedang diputar, lakukan prediksi ulang.
        // Jika belum ada lagu yang diputar, otomatis pilih lagu pertama sebagai pembuka (belum memutar)
        val active = audioEngine.currentTrack.value
        if (active != null) {
            triggerKnnPrediction(active)
        }
    }

    /**
     * Memutar lagu pilihan pengguna secara langsung.
     */
    fun selectAndPlayTrack(track: Track) {
        audioEngine.playTrack(track)
        triggerKnnPrediction(track)
    }

    /**
     * Menjalankan algoritma K-Nearest Neighbor (k=5) secara asinkron untuk mencari kemiripan kognitif.
     * Setelah pre-sentasi Top-5 ditemukan, sistem mengambil satu pemenang secara acak dari 5 besar tersebut,
     * dan memasukkannya ke Deck B (Secondary Player) sebagai lagu berikutnya yang dijadwalkan.
     */
    private fun triggerKnnPrediction(activeTrack: Track) {
        viewModelScope.launch(Dispatchers.Default) {
            // Evaluasi tetangga terdekat di lingkup koleksi lokal (misalnya dari 18 lagu lokal yang terdeteksi)
            val corpus = _localTracks.value
            if (corpus.size > 1) {
                // Cari 5 tetangga terdekat (K=5)
                val top5 = KnnPredictor.findNearestNeighbors(activeTrack, corpus, k = 5)
                _knnRecommendations.value = top5

                Log.d(TAG, "Hasil Prediksi KNN (K=5) untuk [${activeTrack.title}]:")
                top5.forEachIndexed { i, pair ->
                    val sim = KnnPredictor.calculateSimilarityPercentage(pair.second)
                    Log.d(TAG, " -> #${i+1}: ${pair.first.title} (${pair.first.artists}) - Jarak: ${pair.second} (Kemiripan: ${String.format("%.2f", sim)}%)")
                }

                // Ambil satu secara acak dari 5 teratas untuk di-prep di player sekunder
                if (top5.isNotEmpty()) {
                    val winner = top5.random().first
                    Log.d(TAG, "AUTOMIX TARGET terpilih secara acak dari Top-5: ${winner.title} - Memuat ke Player Kedua.")
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        audioEngine.prepareNextTrack(winner)
                    }
                }
            } else {
                Log.w(TAG, "Koleksi lokal terlalu sedikit (${corpus.size} lagu), tidak dapat menjalankan prediksi KNN.")
                _knnRecommendations.value = emptyList()
            }
        }
    }

    /**
     * Menjalankan pembuat lagu demo tiruan untuk mensimulasikan penambahan lagu baru
     * sesuai Batasan Masalah No. 3.
     */
    fun insertMockMp3s() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            // Ambil 3 lagu acak dari dataset referensi untuk dipasang sebagai MP3 fisik di direktori hp
            val availableRef = _referenceTracks.value
            if (availableRef.isNotEmpty()) {
                val sampleGenerations = availableRef.shuffled().take(3)
                val created = MusicScanner.createMockMp3Files(context, sampleGenerations)
                Log.d(TAG, "Berhasil memproduksi $created musik tiruan baru.")
                
                // Pindai ulang secara instan
                scanMusicInternal(availableRef)
            }
            _isLoading.value = false
        }
    }

    /**
     * Toggle Play/Pause.
     */
    fun togglePlayPause() {
        if (audioEngine.isPlaying.value) {
            audioEngine.pause()
        } else {
            if (audioEngine.currentTrack.value == null && _localTracks.value.isNotEmpty()) {
                // Putar lagu pertama jika belum mulai
                selectAndPlayTrack(_localTracks.value.first())
            } else {
                audioEngine.resume()
            }
        }
    }

    /**
     * Skip lagu aktif dan ganti dengan lagu rekomendasi (Deck Swap langsung).
     */
    fun skipToNextTrack() {
        audioEngine.skipToNext()
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}
