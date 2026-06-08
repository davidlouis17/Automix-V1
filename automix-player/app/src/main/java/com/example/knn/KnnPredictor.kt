package com.example.knn

import com.example.model.Track
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Mesin kalkulasi K-Nearest Neighbor (KNN) murni (Native Kotlin Math).
 * Digunakan untuk memprediksi rekomendasi lagu berdasarkan kesamaan fitur audio:
 * - Tempo
 * - Energy
 * - Danceability
 */
object KnnPredictor {

    /**
     * Menghitung Jarak Euclidean (Euclidean Distance) antara dua buah lagu
     * berdasarkan vektor fitur ternormalisasi.
     *
     * Rumus: d(x, y) = sqrt( (xtempo - ytempo)^2 + (xenergy - yenergy)^2 + (xdanceability - ydanceability)^2 )
     */
    fun calculateDistance(vectorA: DoubleArray, vectorB: DoubleArray): Double {
        var sumSq = 0.0
        for (i in vectorA.indices) {
            val diff = vectorA[i] - vectorB[i]
            sumSq += diff.pow(2.0)
        }
        return sqrt(sumSq)
    }

    /**
     * Fitur MinMax Scaling Dinamis.
     * Normalisasi nilai tempo, energy, dan danceability ke rentang 0-1
     * berdasarkan nilai minimum dan maksimum dari seluruh kandidat data.
     * Ini memastikan semua fitur setara dan tidak ada yang mendominasi jarak Euclidean.
     */
    fun getScaledVector(
        track: Track,
        minTempo: Double, maxTempo: Double,
        minEnergy: Double, maxEnergy: Double,
        minDance: Double, maxDance: Double
    ): DoubleArray {
        val nTempo = if (maxTempo > minTempo) (track.tempo.coerceIn(minTempo, maxTempo) - minTempo) / (maxTempo - minTempo) else 0.5
        val nEnergy = if (maxEnergy > minEnergy) (track.energy.coerceIn(minEnergy, maxEnergy) - minEnergy) / (maxEnergy - minEnergy) else 0.5
        val nDance = if (maxDance > minDance) (track.danceability.coerceIn(minDance, maxDance) - minDance) / (maxDance - minDance) else 0.5
        
        return doubleArrayOf(nTempo, nEnergy, nDance)
    }

    /**
     * Mencari K tetangga terdekat (K-Nearest Neighbors) dari lagu aktif yang diberikan.
     * Mengembalikan daftar pasangan Track dan koordinat jarak numeriknya, diurutkan dari jarak terkecil.
     *
     * @param activeTrack Lagu yang sedang diputar (titik acuan/query).
     * @param candidates Sisa daftar lagu di dalam repositori yang akan diperbandingkan.
     * @param k Jumlah tetangga terdekat yang ingin diambil (default k = 5).
     */
    fun findNearestNeighbors(
        activeTrack: Track,
        candidates: List<Track>,
        k: Int = 5
    ): List<Pair<Track, Double>> {
        val allData = candidates + activeTrack
        val minTempo = allData.minOfOrNull { it.tempo } ?: 60.0
        val maxTempo = allData.maxOfOrNull { it.tempo } ?: 200.0
        val minEnergy = allData.minOfOrNull { it.energy } ?: 0.0
        val maxEnergy = allData.maxOfOrNull { it.energy } ?: 1.0
        val minDance = allData.minOfOrNull { it.danceability } ?: 0.0
        val maxDance = allData.maxOfOrNull { it.danceability } ?: 1.0

        val scaledActiveVector = getScaledVector(activeTrack, minTempo, maxTempo, minEnergy, maxEnergy, minDance, maxDance)

        // Jangan sertakan lagu yang sama dengan lagu aktif dalam perhitungan rekomendasi berikutnya
        val filteredCandidates = candidates.filter { it.id != activeTrack.id }

        if (filteredCandidates.isEmpty()) return emptyList()

        // Hitung jarak ke setiap lagu kandidat
        val distances = filteredCandidates.map { track ->
            val scaledCandidateVector = getScaledVector(track, minTempo, maxTempo, minEnergy, maxEnergy, minDance, maxDance)
            val dist = calculateDistance(scaledActiveVector, scaledCandidateVector)
            Pair(track, dist)
        }

        // Urutkan berdasarkan jarak terkecil (semakin kecil, semakin mirip fitur audionya)
        val sortedDistances = distances.sortedBy { it.second }

        // Ambil k tetangga teratas
        return sortedDistances.take(k)
    }

    /**
     * Memprediksi lagu berikutnya secara cerdas dengan mengambil 5 tetangga terdekat (k=5),
     * lalu memilih salah satu dari 5 lagu tersebut secara acak demi menjaga variabilitas putar (Automix variance).
     *
     * @param activeTrack Lagu aktif yang sedang diputar.
     * @param candidates Semua koleksi lagu lokal atau dataset referensi yang tersedia.
     */
    fun predictNextTrack(
        activeTrack: Track,
        candidates: List<Track>
    ): Track? {
        val nearest = findNearestNeighbors(activeTrack, candidates, k = 5)
        if (nearest.isEmpty()) return null

        // Ambil salah satu tetangga terdekat secara acak (Random Selection dari Top-5)
        val randomIndex = (nearest.indices).random()
        return nearest[randomIndex].first
    }

    /**
     * Menghitung tingkat persentase kemiripan (Similarity Score) berdasarkan Jarak Euclidean.
     * Jarak maksimal dalam ruang 3 dimensi ternormalisasi [0-1] adalah akar(3) = 1.732.
     * Rumus: Similarity = (1.0 - (Distance / MaxDistance)) * 100%
     */
    fun calculateSimilarityPercentage(distance: Double): Double {
        val maxDistance = sqrt(3.0) // sqrt(1.0^2 + 1.0^2 + 1.0^2)
        val similarity = (1.0 - (distance / maxDistance)).coerceIn(0.0, 1.0)
        return similarity * 100.0
    }
}
