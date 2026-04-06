package com.example.redcassette

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat

object AudioController {
    var onPlayPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null
    var onSeekTo: ((Long) -> Unit)? = null
}

class CassetteService : Service() {
    private lateinit var mediaSession: MediaSessionCompat
    private val CHANNEL_ID = "RedCassetteChannel"

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Red Cassette Playback", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        mediaSession = MediaSessionCompat(this, "CassetteSession")
        mediaSession.isActive = true

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { AudioController.onPlayPause?.invoke() }
            override fun onPause() { AudioController.onPlayPause?.invoke() }
            override fun onSkipToNext() { AudioController.onNext?.invoke() }
            override fun onSkipToPrevious() { AudioController.onPrev?.invoke() }
            override fun onSeekTo(pos: Long) { AudioController.onSeekTo?.invoke(pos) }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_PLAY_PAUSE" -> { AudioController.onPlayPause?.invoke(); return START_STICKY }
            "ACTION_PREV" -> { AudioController.onPrev?.invoke(); return START_STICKY }
            "ACTION_NEXT" -> { AudioController.onNext?.invoke(); return START_STICKY }
            "STOP_SERVICE" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
                else stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val title = intent?.getStringExtra("TITLE") ?: "Unknown"
        val isPlaying = intent?.getBooleanExtra("IS_PLAYING", false) ?: false
        val duration = intent?.getLongExtra("DURATION", 0L) ?: 0L
        val position = intent?.getLongExtra("POSITION", 0L) ?: 0L
        val labelUri = intent?.getStringExtra("LABEL_URI")

        // Chế ảnh nền mờ
        val albumArt = getThemedAlbumArt(labelUri)

        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Red Cassette")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .build()
        )

        // Chỉ khai báo 3 thao tác cơ bản
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO)
            .setState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, position, 1.0f)

        mediaSession.setPlaybackState(stateBuilder.build())

        val playIntent = PendingIntent.getService(this, 1, Intent(this, CassetteService::class.java).apply { action = "ACTION_PLAY_PAUSE" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val prevIntent = PendingIntent.getService(this, 2, Intent(this, CassetteService::class.java).apply { action = "ACTION_PREV" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val nextIntent = PendingIntent.getService(this, 3, Intent(this, CassetteService::class.java).apply { action = "ACTION_NEXT" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val appIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("Red Cassette")
            .setContentIntent(appIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevIntent)
            .addAction(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "PlayPause", playIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2) // Khai báo lại vị trí 3 nút
                .setMediaSession(mediaSession.sessionToken))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()

        startForeground(1, notification)
        return START_STICKY
    }

    private fun getThemedAlbumArt(uriString: String?): Bitmap {
        val width = 800; val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.parseColor("#1A1A1A"))

        if (uriString != null) {
            try {
                contentResolver.openInputStream(Uri.parse(uriString))?.use {
                    val original = BitmapFactory.decodeStream(it)
                    val scaled = Bitmap.createScaledBitmap(original, width, height, true)
                    val paint = Paint().apply { alpha = 100 }
                    canvas.drawBitmap(scaled, 0f, 0f, paint)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        val overlayPaint = Paint().apply { color = Color.parseColor("#4DB71C1C") }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        return bitmap
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy(); mediaSession.release() }
}