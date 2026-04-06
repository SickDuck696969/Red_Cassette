package com.example.redcassette

import android.app.Application
import android.content.Context
import android.content.Intent
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

    val isPlaying = MutableStateFlow(false)
    val currentSongTitle = MutableStateFlow("Chưa có bài hát nào")
    val progress = MutableStateFlow(0f)
    val isUserSeeking = MutableStateFlow(false)
    val isShuffle = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(RepeatMode.OFF)

    val rootFolderUri = MutableStateFlow(sharedPrefs.getString("rootFolderUri", null))
    val appBackgroundUri = MutableStateFlow(sharedPrefs.getString("appBgUri", null))
    val cassetteLabelUri = MutableStateFlow(sharedPrefs.getString("cassetteLabelUri", null))

    val currentPlaylistName = MutableStateFlow<String?>("Thư mục gốc")
    val allPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val selectedPlaylist = MutableStateFlow<Playlist?>(null)

    val rootSongs = MutableStateFlow<List<Song>>(emptyList())
    private var playbackSongs = listOf<Song>()

    private var mediaPlayer: MediaPlayer? = null
    private var currentSongIndex = -1
    private var progressJob: Job? = null

    init {
        AudioController.onPlayPause = { togglePlayPause() }
        AudioController.onNext = { nextSong() }
        AudioController.onPrev = { prevSong() }

        // Nhận lệnh tua từ thanh thông báo
        AudioController.onSeekTo = { pos ->
            mediaPlayer?.seekTo(pos.toInt())
            progress.value = pos.toFloat() / (mediaPlayer?.duration ?: 1).toFloat()
            updateNotification(isPlaying.value)
        }

        viewModelScope.launch(Dispatchers.IO) { dao.getAllPlaylists().collectLatest { allPlaylists.value = it } }
        rootFolderUri.value?.let { scanRootFolder(it, autoPlay = false) }
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
            putExtra("LABEL_URI", cassetteLabelUri.value) // Gửi hình ảnh qua
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getApplication<Application>().startForegroundService(intent)
            else getApplication<Application>().startService(intent)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun scanRootFolder(uriString: String, autoPlay: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val folder = DocumentFile.fromTreeUri(context, Uri.parse(uriString))
                val mp3Files = folder?.listFiles()?.filter { it.name?.lowercase()?.endsWith(".mp3") == true || it.type == "audio/mpeg" } ?: emptyList()
                val songs = mp3Files.map { Song(uri = it.uri, title = it.name?.removeSuffix(".mp3") ?: "Unknown Song") }
                rootSongs.value = songs

                if (selectedPlaylist.value == null) {
                    playbackSongs = songs
                    withContext(Dispatchers.Main) {
                        if (playbackSongs.isNotEmpty()) { if (autoPlay) playSong(0) else { currentSongTitle.value = playbackSongs[0].title; currentSongIndex = 0 } }
                        else { currentSongTitle.value = "Thư mục trống!" }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun playSong(index: Int) {
        if (playbackSongs.isEmpty() || index !in playbackSongs.indices) return
        currentSongIndex = index
        val song = playbackSongs[index]
        currentSongTitle.value = song.title

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

                // --- THUẬT TOÁN CHỐNG KHỰNG NHẠC KHI CHẠY NỀN ---
                val contentResolver = getApplication<Application>().contentResolver
                try {
                    // Cắm "ống hút" trực tiếp vào file nhạc (FileDescriptor)
                    val parcelFileDescriptor = contentResolver.openFileDescriptor(song.uri, "r")
                    if (parcelFileDescriptor != null) {
                        setDataSource(parcelFileDescriptor.fileDescriptor)
                        // Bắt buộc phải đóng ống hút lại ngay sau khi MediaPlayer đã kết nối thành công để tránh tràn RAM
                        parcelFileDescriptor.close()
                    } else {
                        setDataSource(getApplication(), song.uri) // Dự phòng nếu file bị lỗi
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    setDataSource(getApplication(), song.uri)
                }
                // ------------------------------------------------

                setOnPreparedListener { mp ->
                    mp.start()
                    this@RedCassetteViewModel.isPlaying.value = true
                    updateNotification(true)
                    startProgressTracker()
                }

                setOnCompletionListener { handleSongEnd() }

                setOnErrorListener { _, what, extra ->
                    viewModelScope.launch { delay(1000); nextSong() }
                    true
                }

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
        } ?: run { if (playbackSongs.isNotEmpty()) playSong(0) }
    }

    fun nextSong() {
        if (playbackSongs.isEmpty()) return
        val nextIdx = if (isShuffle.value) playbackSongs.indices.random() else (currentSongIndex + 1) % playbackSongs.size
        playSong(nextIdx)
    }

    fun prevSong() {
        if (playbackSongs.isEmpty()) return
        if (mediaPlayer != null && mediaPlayer!!.currentPosition > 3000) { mediaPlayer!!.seekTo(0); return }
        val prevIdx = if (isShuffle.value) playbackSongs.indices.random() else if (currentSongIndex - 1 < 0) playbackSongs.size - 1 else currentSongIndex - 1
        playSong(prevIdx)
    }

    fun seekTo(fraction: Float) {
        mediaPlayer?.let {
            it.seekTo((it.duration * fraction).toInt())
            progress.value = fraction
            updateNotification(isPlaying.value) // Đồng bộ thanh Slider trên thông báo
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

    fun setRootFolder(uri: String) { rootFolderUri.value = uri; sharedPrefs.edit().putString("rootFolderUri", uri).apply(); if (selectedPlaylist.value == null) currentPlaylistName.value = "Thư mục gốc"; scanRootFolder(uri, autoPlay = true) }
    fun setAppBackground(uri: String?) { appBackgroundUri.value = uri; sharedPrefs.edit().putString("appBgUri", uri).apply() }
    fun setCassetteLabelUri(uri: String?) { cassetteLabelUri.value = uri; sharedPrefs.edit().putString("cassetteLabelUri", uri).apply() }

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
            selectedPlaylist.value = null; currentPlaylistName.value = "Thư mục gốc"; playbackSongs = rootSongs.value
            if (playbackSongs.isNotEmpty()) playSong(0) else { mediaPlayer?.stop(); isPlaying.value = false; updateNotification(false) }
        } else {
            selectedPlaylist.value = playlist; currentPlaylistName.value = playlist.name; loadPlaylistAndPlay(playlist)
        }
    }

    private fun loadPlaylistAndPlay(playlist: Playlist) {
        viewModelScope.launch(Dispatchers.IO) {
            val pSongs = dao.getSongsForPlaylist(playlist.id)
            playbackSongs = pSongs.map { Song(Uri.parse(it.uri), it.title) }
            withContext(Dispatchers.Main) { if (playbackSongs.isNotEmpty()) playSong(0) else { mediaPlayer?.stop(); isPlaying.value = false; currentSongTitle.value = "Playlist trống!"; updateNotification(false) } }
        }
    }

    suspend fun getUrisForPlaylist(playlistId: Int): List<String> { return dao.getSongsForPlaylist(playlistId).map { it.uri } }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        progressJob?.cancel()
        // Tắt dịch vụ thông báo khi app bị huỷ hoàn toàn
        val intent = Intent(getApplication(), CassetteService::class.java).apply { action = "STOP_SERVICE" }
        getApplication<Application>().startService(intent)
    }
}