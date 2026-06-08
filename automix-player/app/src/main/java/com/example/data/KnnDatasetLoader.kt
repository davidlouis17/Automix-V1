package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.Track
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Helper untuk memuat dan mem-parsing dataset Spotify Tracks (.csv) dari folder assets.
 */
object KnnDatasetLoader {
    private const val TAG = "KnnDatasetLoader"
    private const val FILE_NAME = "spotify_tracks.csv"

    /**
     * Membaca file CSV dari assets dan mengembalikannya sebagai daftar obyek Track.
     */
    fun loadDataset(context: Context): List<Track> {
        val tracksList = mutableListOf<Track>()
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open(FILE_NAME)
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Baca header kolom
            val headerLine = reader.readLine()
            Log.d(TAG, "Header dataset: $headerLine")

            var line: String? = reader.readLine()
            var index = 1
            while (line != null) {
                if (line.isNotBlank()) {
                    val parsedLine = parseCsvLine(line)
                    // Format kolom: track_name, artists, tempo, energy, danceability, track_genre
                    if (parsedLine.size >= 5) {
                        try {
                            val trackName = parsedLine[0]
                            val artists = parsedLine[1]
                            val tempo = parsedLine[2].toDoubleOrNull() ?: 120.0
                            val energy = parsedLine[3].toDoubleOrNull() ?: 0.5
                            val danceability = parsedLine[4].toDoubleOrNull() ?: 0.5
                            val genre = if (parsedLine.size > 5) parsedLine[5] else "unknown"

                            val track = Track(
                                id = "track_$index",
                                title = trackName,
                                artists = artists,
                                tempo = tempo,
                                energy = energy,
                                danceability = danceability,
                                genre = genre
                            )
                            tracksList.add(track)
                            index++
                        } catch (e: Exception) {
                            Log.e(TAG, "Gagal parsing baris $index: $line", e)
                        }
                    }
                }
                line = reader.readLine()
            }
            reader.close()
            Log.d(TAG, "Berhasil memuat ${tracksList.size} lagu dari dataset referensi.")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat file dataset dari assets", e)
        }
        return tracksList
    }

    /**
     * Parsing baris CSV secara manual bertipe Finite State Machine sederhana
     * untuk menangani tanda kutip ganda ("") apabila judul lagu berisi tanda koma.
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false

        for (i in line.indices) {
            val char = line[i]
            if (char == '"') {
                inQuotes = !inQuotes // Toggle mode tanda kutip
            } else if (char == ',' && !inQuotes) {
                fields.add(currentField.toString().trim())
                currentField.setLength(0) // Reset builder
            } else {
                currentField.append(char)
            }
        }
        fields.add(currentField.toString().trim())
        return fields
    }
}
