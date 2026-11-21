package com.ssaka.softplayer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

data class Song(val id: Long, val title: String, val artist: String, val uri: Uri, val duration: Long)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var songs by mutableStateOf<List<Song>>(emptyList())
    private var granted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { grantedNow ->
        granted = grantedNow
        if (grantedNow) scanSongs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        granted = hasAudioPermission()
        if (granted) scanSongs()
        setContent {
            SoftPlayerTheme {
                SoftPlayerScreen(
                    songs = songs,
                    permissionGranted = granted,
                    player = player,
                    requestPermission = { permissionLauncher.launch(audioPermission()) }
                )
            }
        }
    }

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

    private fun scanSongs() {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val cursor = contentResolver.query(
            collection, columns, "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null, MediaStore.Audio.Media.TITLE + " ASC"
        )
        val result = mutableListOf<Song>()
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                result.add(
                    Song(
                        id,
                        c.getString(titleCol) ?: "Unknown track",
                        c.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                        ContentUris.withAppendedId(collection, id),
                        c.getLong(durationCol)
                    )
                )
            }
        }
        songs = result
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@Composable
private fun SoftPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB5F36B),
            background = Color(0xFF10120F),
            surface = Color(0xFF1A1D18),
            onPrimary = Color(0xFF17200D)
        ),
        content = content
    )
}

@Composable
private fun SoftPlayerScreen(
    songs: List<Song>,
    permissionGranted: Boolean,
    player: ExoPlayer,
    requestPermission: () -> Unit
) {
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                selectedSong = songs.getOrNull(player.currentMediaItemIndex)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    fun playSong(song: Song) {
        selectedSong = song
        val index = songs.indexOf(song).coerceAtLeast(0)
        player.setMediaItems(songs.map { MediaItem.fromUri(it.uri) }, index, 0L)
        player.prepare()
        player.play()
    }

    fun playNext() {
        if (player.hasNextMediaItem()) player.seekToNext()
        else if (songs.isNotEmpty()) playSong(songs.first())
    }

    fun playPrevious() {
        if (player.currentPosition > 3000L) player.seekTo(0L)
        else if (player.hasPreviousMediaItem()) player.seekToPrevious()
        else if (songs.isNotEmpty()) playSong(songs.last())
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("SOFT PLAYER", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("Your music. Your mood.", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(24.dp))
        Text("YOUR LIBRARY", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("${songs.size} songs", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        if (!permissionGranted) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Find music on your phone", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Allow access to audio files.", color = Color.Gray)
                    Button(onClick = requestPermission) { Text("Allow music access") }
                }
            }
        } else if (songs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicOff, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.Gray)
                    Text("No music found", fontWeight = FontWeight.Bold)
                    Text("Add audio files to your phone.", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 10.dp)) {
                items(songs, key = { it.id }) { song ->
                    Row(
                        Modifier.fillMaxWidth().clickable { playSong(song) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(46.dp).background(Color(0xFF272B24), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Color.Gray)
                        }
                        Text(formatTime(song.duration), fontSize = 12.sp, color = Color.Gray)
                    }
                    HorizontalDivider(color = Color(0xFF292D26))
                }
            }
        }

        if (selectedSong != null) {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Color(0xFF30372A), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Album, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selectedSong!!.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text(selectedSong!!.artist, color = Color.Gray, fontSize = 12.sp)
                    }
                    IconButton(onClick = { shuffle = !shuffle; player.shuffleModeEnabled = shuffle }) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (shuffle) MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                    IconButton(onClick = {
                        repeatMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                        player.repeatMode = repeatMode
                    }) {
                        Icon(Icons.Default.Repeat, contentDescription = "Repeat", tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                }
                Slider(
                    value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                    onValueChange = { position = it.toLong() },
                    onValueChangeFinished = { player.seekTo(position) },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat()
                )
                Row {
                    Text(formatTime(position), fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(duration), fontSize = 11.sp, color = Color.Gray)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { playPrevious() }) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(34.dp)) }
                    Spacer(Modifier.width(18.dp))
                    IconButton(
                        onClick = { if (player.isPlaying) player.pause() else player.play() },
                        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play or pause", tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(18.dp))
                    IconButton(onClick = { playNext() }) { Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(34.dp)) }
                }
            }
        } else {
            Text("Choose a song to start listening", color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp))
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
