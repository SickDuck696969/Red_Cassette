package com.example.redcassette

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RepeatMode { OFF, ALL, ONE }
data class Song(val uri: Uri, val title: String)

class RedCassetteViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPrefs = application.getSharedPreferences("RedCassettePrefs", Context.MODE_PRIVATE)
    private val dao = AppDatabase.getDatabase(application).playlistDao()

    // --- TRẠNG THÁI UI & TÍNH NĂNG ---
    val isPlaying = MutableStateFlow(false)
    val currentSongTitle = MutableStateFlow("Chưa có bài hát nào")
    val progress = MutableStateFlow(0f)
    val isUserSeeking = MutableStateFlow(false)
    val isShuffle = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(RepeatMode.OFF)

    // --- CÀI ĐẶT ỨNG DỤNG ---
    val rootFolderUri = MutableStateFlow(sharedPrefs.getString("rootFolderUri", null))
    val appBackgroundUri = MutableStateFlow(sharedPrefs.getString("appBgUri", null))
    val cassetteLabelUri = MutableStateFlow(sharedPrefs.getString("cassetteLabelUri", null))

    // --- PLAYLIST & DANH SÁCH CHỜ (QUEUE) ---
    val currentPlaylistName = MutableStateFlow<String?>("Thư mục gốc")
    val allPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val selectedPlaylist = MutableStateFlow<Playlist?>(null)

    val rootSongs = MutableStateFlow<List<Song>>(emptyList())
    val currentPlaybackList = MutableStateFlow<List<Song>>(emptyList())
    val currentSongIndexFlow = MutableStateFlow(-1)

    private var playbackSongs = listOf<Song>()

    // --- AUDIO ---
    private var mediaPlayer: MediaPlayer? = null
    private var currentSongIndex = -1
    private var progressJob: Job? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (isPlaying.value) togglePlayPause()
            }
        }
    }

    init {
        AudioController.onPlayPause = { togglePlayPause() }
        AudioController.onNext = { nextSong() }
        AudioController.onPrev = { prevSong() }
        AudioController.onSeekTo = { pos ->
            mediaPlayer?.seekTo(pos.toInt())
            progress.value = pos.toFloat() / (mediaPlayer?.duration ?: 1).toFloat()
            updateNotification(isPlaying.value)
        }

        application.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllPlaylists().collectLatest { playlists ->
                allPlaylists.value = playlists
                val lastPid = sharedPrefs.getInt("lastPlaylistId", -1)
                if (lastPid != -1 && selectedPlaylist.value == null) {
                    playlists.find { it.id == lastPid }?.let {
                        selectedPlaylist.value = it
                        currentPlaylistName.value = it.name
                    }
                }
            }
        }

        restorePlaybackState()
    }

    private fun savePlaybackState() {
        val currentUri = if (currentSongIndex in playbackSongs.indices) playbackSongs[currentSongIndex].uri.toString() else null
        sharedPrefs.edit()
            .putInt("lastPlaylistId", selectedPlaylist.value?.id ?: -1)
            .putString("lastSongUri", currentUri)
            .apply()
    }

    private fun restorePlaybackState() {
        val lastPid = sharedPrefs.getInt("lastPlaylistId", -1)
        val lastUri = sharedPrefs.getString("lastSongUri", null)

        if (lastPid != -1) {
            viewModelScope.launch(Dispatchers.IO) {
                val pSongs = dao.getSongsForPlaylist(lastPid)
                playbackSongs = pSongs.map { Song(Uri.parse(it.uri), it.title) }
                currentPlaybackList.value = playbackSongs

                val idx = playbackSongs.indexOfFirst { it.uri.toString() == lastUri }.takeIf { it != -1 } ?: 0
                withContext(Dispatchers.Main) {
                    if (playbackSongs.isNotEmpty()) playSong(idx, autoPlay = false)
                }
            }
        } else {
            rootFolderUri.value?.let { scanRootFolder(it, autoPlay = false, targetUri = lastUri) }
        }
    }

    private fun updateNotification(playing: Boolean) {
        val duration = mediaPlayer?.duration?.toLong() ?: 0L
        val position = mediaPlayer?.currentPosition?.toLong() ?: 0L

        val intent = Intent(getApplication(), CassetteService::class.java).apply {
            action = "UPDATE_NOTIFICATION"
            putExtra("TITLE", currentSongTitle.value)
            putExtra("IS_PLAYING", playing)
            putExtra("DURATION", duration)
            putExtra("POSITION", position)
            putExtra("LABEL_URI", cassetteLabelUri.value)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getApplication<Application>().startForegroundService(intent)
            else getApplication<Application>().startService(intent)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun scanRootFolder(uriString: String, autoPlay: Boolean = true, targetUri: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val folder = DocumentFile.fromTreeUri(context, Uri.parse(uriString))
                val mp3Files = folder?.listFiles()?.filter { it.name?.lowercase()?.endsWith(".mp3") == true || it.type == "audio/mpeg" } ?: emptyList()
                val songs = mp3Files.map { Song(uri = it.uri, title = it.name?.removeSuffix(".mp3") ?: "Unknown Song") }
                rootSongs.value = songs

                if (selectedPlaylist.value == null) {
                    playbackSongs = songs
                    currentPlaybackList.value = songs
                    val idx = if (targetUri != null) songs.indexOfFirst { it.uri.toString() == targetUri }.takeIf { it != -1 } ?: 0 else 0

                    withContext(Dispatchers.Main) {
                        if (playbackSongs.isNotEmpty()) playSong(idx, autoPlay)
                        else currentSongTitle.value = "Thư mục trống!"
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun playSong(index: Int, autoPlay: Boolean = true) {
        if (playbackSongs.isEmpty() || index !in playbackSongs.indices) return
        currentSongIndex = index
        currentSongIndexFlow.value = index
        val song = playbackSongs[index]
        currentSongTitle.value = song.title

        savePlaybackState()

        isPlaying.value = false
        updateNotification(false)

        mediaPlayer?.release()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                try { setWakeMode(getApplication(), PowerManager.PARTIAL_WAKE_LOCK) }
                catch (e: Exception) { e.printStackTrace() }

                val contentResolver = getApplication<Application>().contentResolver
                try {
                    val parcelFileDescriptor = contentResolver.openFileDescriptor(song.uri, "r")
                    if (parcelFileDescriptor != null) {
                        setDataSource(parcelFileDescriptor.fileDescriptor)
                        parcelFileDescriptor.close()
                    } else setDataSource(getApplication(), song.uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                    setDataSource(getApplication(), song.uri)
                }

                setOnPreparedListener { mp ->
                    if (autoPlay) {
                        mp.start()
                        this@RedCassetteViewModel.isPlaying.value = true
                        updateNotification(true)
                        startProgressTracker()
                    } else {
                        progress.value = 0f
                    }
                }

                setOnCompletionListener { handleSongEnd() }
                setOnErrorListener { _, _, _ -> viewModelScope.launch { delay(1000); nextSong() }; true }

                prepareAsync()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun handleSongEnd() {
        when (repeatMode.value) {
            RepeatMode.ONE -> playSong(currentSongIndex)
            RepeatMode.ALL -> nextSong()
            RepeatMode.OFF -> {
                if (isShuffle.value) nextSong()
                else if (currentSongIndex < playbackSongs.size - 1) nextSong()
                else { isPlaying.value = false; progress.value = 0f; updateNotification(false) }
            }
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) { it.pause(); isPlaying.value = false; updateNotification(false) }
            else { it.start(); isPlaying.value = true; updateNotification(true); startProgressTracker() }
        } ?: run { if (playbackSongs.isNotEmpty()) playSong(currentSongIndex.takeIf { it != -1 } ?: 0) }
    }

    fun nextSong() {
        if (playbackSongs.isEmpty()) return
        val nextIdx = if (isShuffle.value && playbackSongs.size > 1) {
            var randomIdx: Int
            do { randomIdx = playbackSongs.indices.random() } while (randomIdx == currentSongIndex)
            randomIdx
        } else {
            (currentSongIndex + 1) % playbackSongs.size
        }
        playSong(nextIdx)
    }

    fun prevSong() {
        if (playbackSongs.isEmpty()) return
        if (mediaPlayer != null && mediaPlayer!!.currentPosition > 3000) { mediaPlayer!!.seekTo(0); return }
        val prevIdx = if (isShuffle.value && playbackSongs.size > 1) {
            var randomIdx: Int
            do { randomIdx = playbackSongs.indices.random() } while (randomIdx == currentSongIndex)
            randomIdx
        } else if (currentSongIndex - 1 < 0) playbackSongs.size - 1 else currentSongIndex - 1
        playSong(prevIdx)
    }

    fun seekTo(fraction: Float) {
        mediaPlayer?.let {
            it.seekTo((it.duration * fraction).toInt())
            progress.value = fraction
            updateNotification(isPlaying.value)
        }
    }
    fun toggleShuffle() { isShuffle.value = !isShuffle.value }
    fun toggleRepeat() { repeatMode.value = when (repeatMode.value) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF } }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                mediaPlayer?.let { if (it.isPlaying && !isUserSeeking.value) progress.value = it.currentPosition.toFloat() / it.duration.toFloat() }
                delay(500)
            }
        }
    }

    fun setRootFolder(uri: String) {
        rootFolderUri.value = uri
        sharedPrefs.edit().putString("rootFolderUri", uri).apply()
        if (selectedPlaylist.value == null) {
            currentPlaylistName.value = "Thư mục gốc"
            scanRootFolder(uri, autoPlay = true)
        }
    }
    fun setAppBackground(uri: String?) { appBackgroundUri.value = uri; sharedPrefs.edit().putString("appBgUri", uri).apply() }
    fun setCassetteLabelUri(uri: String?) { cassetteLabelUri.value = uri; sharedPrefs.edit().putString("cassetteLabelUri", uri).apply(); updateNotification(isPlaying.value) }

    fun createPlaylist(name: String, selectedUris: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val pid = dao.insertPlaylist(Playlist(name = name)).toInt()
            val pSongs = selectedUris.mapNotNull { uri -> rootSongs.value.find { it.uri.toString() == uri } }.map { PlaylistSong(playlistId = pid, uri = it.uri.toString(), title = it.title) }
            dao.insertPlaylistSongs(pSongs)
        }
    }

    fun updatePlaylist(playlistId: Int, selectedUris: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteSongsForPlaylist(playlistId)
            val pSongs = selectedUris.mapNotNull { uri -> rootSongs.value.find { it.uri.toString() == uri } }.map { PlaylistSong(playlistId = playlistId, uri = it.uri.toString(), title = it.title) }
            dao.insertPlaylistSongs(pSongs)
            if (selectedPlaylist.value?.id == playlistId) loadPlaylistAndPlay(selectedPlaylist.value!!)
        }
    }

    fun deletePlaylist(playlist: Playlist) { viewModelScope.launch(Dispatchers.IO) { dao.deletePlaylist(playlist); if (selectedPlaylist.value?.id == playlist.id) selectPlaylist(null) } }

    fun selectPlaylist(playlist: Playlist?) {
        if (playlist == null || selectedPlaylist.value?.id == playlist?.id) {
            selectedPlaylist.value = null; currentPlaylistName.value = "Thư mục gốc"
            playbackSongs = rootSongs.value
            currentPlaybackList.value = rootSongs.value
            savePlaybackState()
            if (playbackSongs.isNotEmpty()) playSong(0) else { mediaPlayer?.stop(); isPlaying.value = false; updateNotification(false) }
        } else {
            selectedPlaylist.value = playlist; currentPlaylistName.value = playlist.name
            loadPlaylistAndPlay(playlist)
        }
    }

    private fun loadPlaylistAndPlay(playlist: Playlist) {
        viewModelScope.launch(Dispatchers.IO) {
            val pSongs = dao.getSongsForPlaylist(playlist.id)
            playbackSongs = pSongs.map { Song(Uri.parse(it.uri), it.title) }
            currentPlaybackList.value = playbackSongs
            savePlaybackState()
            withContext(Dispatchers.Main) { if (playbackSongs.isNotEmpty()) playSong(0) else { mediaPlayer?.stop(); isPlaying.value = false; currentSongTitle.value = "Playlist trống!"; updateNotification(false) } }
        }
    }

    suspend fun getUrisForPlaylist(playlistId: Int): List<String> { return dao.getSongsForPlaylist(playlistId).map { it.uri } }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        progressJob?.cancel()
        getApplication<Application>().unregisterReceiver(noisyReceiver)

        val intent = Intent(getApplication(), CassetteService::class.java).apply { action = "STOP_SERVICE" }
        getApplication<Application>().startService(intent)
    }
}