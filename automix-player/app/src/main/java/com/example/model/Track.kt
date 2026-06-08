package com.example.model

/**
 * Representasi obyek lagu dengan fitur audio untuk algoritma K-Nearest Neighbor (KNN).
 * Data dikelompokkan dan dicocokkan sesuai dengan dataset Spotify yang dipetakan pada Tugas Akhir.
 */
data class Track(
    val id: String,
    val title: String,
    val artists: String,
    val tempo: Double,          // BPM asli (misal: 131.643)
    val energy: Double,         // Range 0.0 - 1.0 (misal: 0.7)
    val danceability: Double,   // Range 0.0 - 1.0 (misal: 0.866)
    val genre: String,
    val filePath: String? = null // Path berkas mp3 lokal jika sudah terpasang
) {

    // Konstanta normalisasi Min-Max berdasarkan spesifikasi Bab III.1.4 Proposal Tugas Akhir
    companion object {
        const val TEMPO_MIN = 60.0
        const val TEMPO_MAX = 216.0

        const val ENERGY_MIN = 0.158
        const val ENERGY_MAX = 1.000

        const val DANCEABILITY_MIN = 0.244
        const val DANCEABILITY_MAX = 0.934
    }

    /**
     * Normalisasi nilai tempo ke rentang 0-1 menggunakan Min-Max Scaling.
     */
    fun getNormalizedTempo(): Double {
        val bounded = tempo.coerceIn(TEMPO_MIN, TEMPO_MAX)
        return (bounded - TEMPO_MIN) / (TEMPO_MAX - TEMPO_MIN)
    }

    /**
     * Normalisasi nilai energy ke rentang 0-1 menggunakan Min-Max Scaling.
     */
    fun getNormalizedEnergy(): Double {
        val bounded = energy.coerceIn(ENERGY_MIN, ENERGY_MAX)
        return (bounded - ENERGY_MIN) / (ENERGY_MAX - ENERGY_MIN)
    }

    /**
     * Normalisasi nilai danceability ke rentang 0-1 menggunakan Min-Max Scaling.
     */
    fun getNormalizedDanceability(): Double {
        val bounded = danceability.coerceIn(DANCEABILITY_MIN, DANCEABILITY_MAX)
        return (bounded - DANCEABILITY_MIN) / (DANCEABILITY_MAX - DANCEABILITY_MIN)
    }

    /**
     * Mendapatkan vektor representasi fitur audio yang ternormalisasi (Tempo, Energy, Danceability).
     */
    fun getFeatureVector(): DoubleArray {
        return doubleArrayOf(
            getNormalizedTempo(),
            getNormalizedEnergy(),
            getNormalizedDanceability()
        )
    }
}
