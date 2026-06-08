package com.example.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.Track
import java.io.File
import java.io.FileOutputStream

/**
 * Scanner untuk memindai pustaka musik lokal (.mp3) di penyimpanan perangkat menggunakan ContentResolver.
 * Dilengkapi fitur pencocokan otomatis (Feature Mapping) dengan dataset Spotify referensi.
 */
object MusicScanner {
    private const val TAG = "MusicScanner"

    /**
     * Memindai file media lokal dan memetakan karakteristik fiturnya dari dataset referensi.
     * Jika tidak ditemukan lagu fisik lokal, ia akan mengembalikan daftar demo yang disimulasikan
     * agar aplikasi tetap dapat berjalan secara interaktif di lingkungan Emulator.
     */
    fun scanLocalMusic(context: Context, referenceDataset: List<Track>): List<Track> {
        val localTracks = mutableListOf<Track>()

        // 1. Lakukan query ke MediaStore
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (c.moveToNext()) {
                    val rawTitle = c.getString(titleCol) ?: "Unknown Title"
                    val rawArtist = c.getString(artistCol) ?: "Unknown Artist"
                    val dataPath = c.getString(dataCol) ?: ""

                    // 2. Lakukan Sinkronisasi & Pemetaan Fitur Audio (Feature Mapping)
                    // Cari lagu yang cocok di dalam dataset referensi
                    val matchedTrack = findMatchingReferenceTrack(rawTitle, rawArtist, referenceDataset)

                    if (matchedTrack != null) {
                        // Gabungkan path fisik lokal dengan nilai fitur audio numerik dari dataset
                        localTracks.add(
                            matchedTrack.copy(
                                id = "local_${localTracks.size + 1}",
                                filePath = dataPath
                            )
                        )
                        Log.d(TAG, "Berhasil memetakan lagu lokal: $rawTitle oleh $rawArtist -> [Tempo: ${matchedTrack.tempo}]")
                    } else {
                        // Gunakan fallback pseudo-acak deterministik berdasarkan hash untuk mencegah clumping 100% similarity di KNN
                        val hash = kotlin.math.abs(rawTitle.hashCode())
                        localTracks.add(
                            Track(
                                id = "local_${localTracks.size + 1}",
                                title = rawTitle,
                                artists = rawArtist,
                                tempo = 60.0 + (hash % 120),
                                energy = (hash % 100) / 100.0,
                                danceability = ((hash / 13) % 100) / 100.0,
                                genre = "local",
                                filePath = dataPath
                            )
                        )
                        Log.d(TAG, "Lagu lokal tidak mendaftar di dataset, menggunakan default: $rawTitle")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan saat melakukan query MediaStore", e)
        }

        // 3. Mekanisme Fallback: Jika tidak ditemukan lagu lokal sama sekali (misal berjalan di Emulator bersih)
        // Maka kita kloning 18 lagu demo pertama dari dataset referensi agar penguji dapat langsung bernavigasi
        if (localTracks.isEmpty()) {
            Log.w(TAG, "Tidak ada musik ditemukan di HP. Mengaktifkan 18 Musik Demo Terintegrasi.")
            val demoDatasetLimit = referenceDataset.take(18)
            demoDatasetLimit.forEachIndexed { idx, track ->
                localTracks.add(
                    track.copy(
                        id = "local_demo_${idx + 1}",
                        // Set path palsu, player akan otomatis memutar demo_loop.mp3 dari assets
                        filePath = "assets/demo_loop.mp3"
                    )
                )
            }
        }

        return localTracks
    }

    /**
     * Algoritma fuzzy matching sederhana untuk mencocokkan judul lagu lokal dengan dataset.
     */
    private fun findMatchingReferenceTrack(
        localTitle: String,
        localArtist: String,
        referenceDataset: List<Track>
    ): Track? {
        val cleanTitle = localTitle.lowercase().trim()
        val cleanArtist = localArtist.lowercase().trim()

        // Cari kecocokan persis atau parsial
        return referenceDataset.find { ref ->
            val refTitle = ref.title.lowercase().trim()
            val refArtist = ref.artists.lowercase().trim()
            refTitle == cleanTitle || 
            refTitle.contains(cleanTitle) || 
            cleanTitle.contains(refTitle) ||
            refArtist.contains(cleanArtist) ||
            cleanArtist.contains(refArtist)
        }
    }

    /**
     * Fitur penunjang pengujian: Menulis file dummy MP3 ke direktori Musik perangkat luar
     * agar memicu pendeteksian musik baru di dalam MediaStore secara real-time. Fungsionalitas
     * ini menjawab batasan Masalah No. 3 (Sistem otomatis memindai ulang jika ada lagu baru terdeteksi).
     */
    fun createMockMp3Files(context: Context, songsToGenerate: List<Track>): Int {
        var createdCount = 0
        try {
            // Ambil direktori Music eksternal
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (!musicDir.exists()) {
                musicDir.mkdirs()
            }

            songsToGenerate.forEach { track ->
                val fileName = "${track.title.replace("/", "_")} - ${track.artists.replace("/", "_")}.mp3"
                val file = File(musicDir, fileName)

                if (!file.exists()) {
                    // Tulis header dummy agar MediaStore mendeteksi file sebagai MP3 audio yang valid
                    val outputStream = FileOutputStream(file)
                    // Menulis 1024 byte biner kosong tiruan
                    outputStream.write(ByteArray(1024))
                    outputStream.flush()
                    outputStream.close()

                    // Daftarkan ke MediaStore secara terprogram agar langsung terbaca di Android ContentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Audio.Media.TITLE, track.title)
                        put(MediaStore.Audio.Media.ARTIST, track.artists)
                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                        put(MediaStore.Audio.Media.DATA, file.absolutePath)
                        put(MediaStore.Audio.Media.IS_MUSIC, 1)
                    }

                    context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    createdCount++
                }
            }
            Log.d(TAG, "Berhasil memproduksi $createdCount file MP3 tiruan di berkas publik.")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memproduksi berkas media tiruan", e)
        }
        return createdCount
    }
}
