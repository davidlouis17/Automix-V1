package com.example.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Track
import com.example.viewmodel.PlaylistViewModel
import com.example.knn.KnnPredictor
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppDashboard(viewModel: PlaylistViewModel) {
    val context = LocalContext.current

    val localTracks by viewModel.localTracks.collectAsState()
    val referenceTracks by viewModel.referenceTracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val knnRecommendations by viewModel.knnRecommendations.collectAsState()

    val currentTrack by viewModel.audioEngine.currentTrack.collectAsState()
    val nextTrack by viewModel.audioEngine.nextTrack.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val isCrossfading by viewModel.audioEngine.isCrossfading.collectAsState()
    val primaryVolume by viewModel.audioEngine.primaryVolume.collectAsState()
    val secondaryVolume by viewModel.audioEngine.secondaryVolume.collectAsState()
    val playbackProgress by viewModel.audioEngine.playbackProgress.collectAsState()
    val playbackDuration by viewModel.audioEngine.playbackDuration.collectAsState()
    
    var activeTab by remember { mutableIntStateOf(1) } // 0=Library, 1=Player, 2=KNN Lab

    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Izin diberikan! Memindai lagu...", Toast.LENGTH_SHORT).show()
            viewModel.scanLocalMusic()
        } else {
            Toast.makeText(context, "Mode Demo Terintegrasi.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(storagePermission)
    }

    // Elegant Dark Theme Colors
    val bgSurface = Color(0xFF1A1C1E)
    val textPrimary = Color(0xFFE2E2E6)
    val textSecondary = Color(0xFFCCC2DC)
    val accentPrimary = Color(0xFFD0BCFF)
    val accentDark = Color(0xFF381E72)
    val alertAccent = Color(0xFFF2B8B5)
    val cardBg = Color(0xFF44474E)
    val bottomPanelBg = Color(0xFF211F26)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgSurface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // --- HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AUTOMIX ENGINE ACTIVE",
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentPrimary
                    )
                    Text(
                        text = "Now Playing",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(cardBg, RoundedCornerShape(16.dp))
                            .clickable { viewModel.scanLocalMusic() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("refresh_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(accentPrimary, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("k=5", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(cardBg, RoundedCornerShape(18.dp))
                            .clickable {
                                viewModel.insertMockMp3s()
                                Toast.makeText(context, "Meniru unduhan: 3 file baru ditulis!", Toast.LENGTH_SHORT).show()
                            }
                            .testTag("seed_songs_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = textPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // --- MAIN SCROLLABLE AREA ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp)
            ) {
                if (activeTab == 1) {
                    // TAB 1: PRIMARY DECK / PLAYER VIEW
                    item {
                        val safeCurrentTrack = currentTrack
                        if (safeCurrentTrack != null) {
                            val track = safeCurrentTrack
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("player_deck_primary"),
                                verticalArrangement = Arrangement.spacedBy(32.dp)
                            ) {
                                // The Big Square Card (Glassmorphism + Gradient)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(40.dp))
                                        .background(Brush.linearGradient(listOf(accentPrimary, accentDark)))
                                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(40.dp))
                                ) {
                                    // Inner glassmorphism features
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(24.dp)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.Black.copy(alpha = 0.2f))
                                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                            .padding(16.dp)
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("KNN FEATURE VECTOR", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.sp)
                                                Text("dist: active", fontSize = 10.sp, color = accentPrimary)
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text("Tempo", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                                    Text("${track.tempo.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                                Column {
                                                    Text("Energy", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                                    Text(String.format(Locale.US, "%.2f", track.energy), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                                Column {
                                                    Text("Dance", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                                    Text(String.format(Locale.US, "%.2f", track.danceability), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
    
                                // Title & Artist
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = track.title,
                                        fontSize = 28.sp, // text-3xl approx
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary,
                                        letterSpacing = (-0.5).sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artists,
                                        fontSize = 18.sp, // text-lg
                                        color = textSecondary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
    
                                // Progress Bar
                                val secondsPlayed = playbackProgress / 1000L
                                val totalSeconds = playbackDuration / 1000L
                                val remainingSeconds = (totalSeconds - secondsPlayed).coerceAtLeast(0L)
                                
                                val isCrossfadeWindow = remainingSeconds <= 20 && remainingSeconds > 0 && nextTrack != null
    
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("PRIMARY: ${formatDuration(secondsPlayed)}", fontSize = 11.sp, color = accentPrimary)
                                        Text("-${formatDuration(remainingSeconds)}", fontSize = 11.sp, color = accentPrimary)
                                    }
    
                                    // Custom styled progress bar with warning zone
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .background(cardBg, RoundedCornerShape(3.dp))
                                            .clip(RoundedCornerShape(3.dp))
                                    ) {
                                        val expectedFrac = if (totalSeconds > 0) (secondsPlayed.toFloat() / totalSeconds.toFloat()) else 0f
                                        val progressFraction = if (expectedFrac.isNaN()) 0f else expectedFrac.coerceIn(0f, 1f)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(fraction = progressFraction)
                                                .background(accentPrimary)
                                        )
                                        // Warning boundary tail for crossfade indication
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(0.2f)
                                                .align(Alignment.CenterEnd)
                                                .background(alertAccent.copy(alpha = 0.4f))
                                        )
                                    }
    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Crossfade window active (<20s)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                        if (isCrossfadeWindow) {
                                            Text("SECONDARY BUFFERING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = alertAccent)
                                        }
                                    }
                                }
    
                                // PLAYBACK CONTROLS
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            viewModel.audioEngine.stopAll()
                                            viewModel.selectAndPlayTrack(track)
                                        },
                                        modifier = Modifier.size(48.dp).testTag("skip_prev_button")
                                    ) {
                                        Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = textPrimary, modifier = Modifier.size(32.dp))
                                    }
    
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp) // w-20 h-20
                                            .background(accentPrimary, RoundedCornerShape(28.dp))
                                            .clickable { viewModel.togglePlayPause() }
                                            .testTag("play_pause_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Mainkan / Jeda",
                                            tint = accentDark,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
    
                                    IconButton(
                                        onClick = { viewModel.skipToNextTrack() },
                                        enabled = nextTrack != null,
                                        modifier = Modifier.size(48.dp).testTag("skip_next_button")
                                    ) {
                                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = if (nextTrack != null) textPrimary else textPrimary.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        } else {
                            // Empty State Outline
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(40.dp))
                                    .background(cardBg.copy(alpha = 0.5f))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(40.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Select a track below to start", color = textSecondary)
                            }
                            
                            Spacer(modifier = Modifier.height(180.dp)) // space for controls
                        }
                    }
    
                    // DYNAMIC CROSSFADE MONITOR (Only when active)
                    if (isCrossfading && nextTrack != null) {
                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, alertAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                                colors = CardDefaults.cardColors(containerColor = bgSurface),
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("AUTOMIX CROSSFADING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = alertAccent)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Fade Out", fontSize = 10.sp, color = textSecondary)
                                            Text("${(primaryVolume * 100).toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accentPrimary)
                                        }
                                        Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, tint = alertAccent)
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Fade In", fontSize = 10.sp, color = textSecondary)
                                            Text("${(secondaryVolume * 100).toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = alertAccent)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } // End ActiveTab == 1
                
                if (activeTab == 0) {
                    // TAB 0: LIBRARY
                    item {
                        Column {
                            Text("Local Library", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = textPrimary, modifier = Modifier.padding(bottom = 12.dp))
                            localTracks.forEachIndexed { idx, track ->
                                val isCurrent = currentTrack?.id == track.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurrent) cardBg.copy(alpha = 0.5f) else Color.Transparent)
                                        .clickable { 
                                            viewModel.selectAndPlayTrack(track)
                                            activeTab = 1 // Auto-switch to player on play
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isCurrent) accentPrimary else textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(track.title, fontSize = 14.sp, color = if (isCurrent) accentPrimary else textPrimary, fontWeight = if(isCurrent) FontWeight.Bold else FontWeight.Normal)
                                        Text("${track.artists} • ${track.tempo.toInt()} BPM", fontSize = 12.sp, color = textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (activeTab == 2) {
                    // TAB 2: KNN LAB
                    item {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("KNN Lab (k=5)", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                                Text("Similarity", fontSize = 12.sp, color = textSecondary)
                            }
                            if (knnRecommendations.isEmpty()) {
                                Text("Play a song to view its nearest neighbors based on audio features.", fontSize = 14.sp, color = textSecondary, modifier = Modifier.padding(top = 16.dp))
                            } else {
                                knnRecommendations.forEachIndexed { idx, pair ->
                                    val track = pair.first
                                    val sim = KnnPredictor.calculateSimilarityPercentage(pair.second)
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${idx+1}", fontSize = 14.sp, color = if(idx==0) alertAccent else textSecondary, modifier = Modifier.width(28.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(track.title, fontSize = 16.sp, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${track.artists} • dist: ${String.format(Locale.US, "%.3f", pair.second)}", fontSize = 12.sp, color = textSecondary, maxLines = 1)
                                        }
                                        Text("${String.format(Locale.US, "%.1f", sim)}%", fontSize = 14.sp, color = if(idx==0) alertAccent else accentPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- BOTTOM PANEL (bg-[#211F26] rounded-t-[32px]) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(bottomPanelBg)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    
                    // NEXT PREDICTION (Secondary Deck preview)
                    val safeNextTrack = nextTrack
                    if (safeNextTrack != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBg.copy(alpha = 0.3f))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(accentDark), contentAlignment = Alignment.Center) {
                                Text("NXT", color = accentPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("NEXT PREDICTION (k=1)", fontSize = 10.sp, color = accentPrimary, fontWeight = FontWeight.Bold)
                                Text(safeNextTrack.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${safeNextTrack.artists} • Euclidean Prep", fontSize = 12.sp, color = textSecondary, maxLines = 1)
                            }
                            // Small indicator
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(8.dp).background(accentPrimary, RoundedCornerShape(4.dp)))
                            }
                        }
                    }

                    // TABS
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Library Tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { activeTab = 0 }.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(24.dp).border(2.dp, if(activeTab==0) accentPrimary else textPrimary, RoundedCornerShape(6.dp)))
                            Text("Library", fontSize = 10.sp, fontWeight = if(activeTab==0) FontWeight.Bold else FontWeight.Medium, color = if(activeTab==0) accentPrimary else textSecondary)
                        }
                        
                        // Player Tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { activeTab = 1 }.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(24.dp).background(if(activeTab==1) accentPrimary else Color.Transparent, RoundedCornerShape(12.dp)).border(if(activeTab==1) 0.dp else 2.dp, textPrimary, RoundedCornerShape(12.dp)))
                            Text("Player", fontSize = 10.sp, fontWeight = if(activeTab==1) FontWeight.Bold else FontWeight.Medium, color = if(activeTab==1) accentPrimary else textSecondary)
                        }
                        
                        // KNN Lab Tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { activeTab = 2 }.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(24.dp).border(2.dp, if(activeTab==2) accentPrimary else textPrimary, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Box(modifier = Modifier.size(8.dp).background(if(activeTab==2) accentPrimary else textPrimary, RoundedCornerShape(4.dp)))
                            }
                            Text("KNN Lab", fontSize = 10.sp, fontWeight = if(activeTab==2) FontWeight.Bold else FontWeight.Medium, color = if(activeTab==2) accentPrimary else textSecondary)
                        }
                    }
                }
            }
        }

        // Loading Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accentPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Processing Data...", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
