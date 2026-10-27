package com.example.ytmusic2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MusicBackgroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var mediaSession: MediaSessionCompat

    private var currentTitle: String = "YouTube Music"
    private var currentArtist: String = "Memutar di latar belakang"
    private var isPlaying: Boolean = false
    private var currentDurationMs: Long = 0L
    private var currentPositionMs: Long = 0L
    private var currentArtworkUrl: String = ""
    private var currentArtworkBitmap: Bitmap? = null
    private var defaultArtworkBitmap: Bitmap? = null

    private var isNoisyReceiverRegistered: Boolean = false
    private val artworkExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                controlCallback?.invoke(ACTION_PAUSE)
                if (isPlaying) {
                    isPlaying = false
                    updateMediaSessionState()
                    refreshNotification()
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "ytm_playback_channel"
        const val NOTIFICATION_ID = 101

        const val ACTION_PREV = "com.example.ytmusic2.ACTION_PREV"
        const val ACTION_PLAY = "com.example.ytmusic2.ACTION_PLAY"
        const val ACTION_PLAY_PAUSE = "com.example.ytmusic2.ACTION_PLAY_PAUSE"
        const val ACTION_PAUSE = "com.example.ytmusic2.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.ytmusic2.ACTION_NEXT"
        const val ACTION_UPDATE_SONG = "com.example.ytmusic2.ACTION_UPDATE_SONG"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_ARTWORK_SOURCE = "extra_artwork_source"

        var controlCallback: ((String) -> Unit)? = null
        var seekCallback: ((Long) -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        defaultArtworkBitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        createNotificationChannel()
        initMediaSession()
        acquireWakeLock()
        registerNoisyReceiver()
    }

    private fun initMediaSession() {
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setClass(this@MusicBackgroundService, MediaButtonReceiver::class.java)
        }
        val pendingMediaButton = PendingIntent.getBroadcast(
            this,
            0,
            mediaButtonIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSessionCompat(this, "YTMusicMediaSession").apply {
            setMediaButtonReceiver(pendingMediaButton)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    isPlaying = true
                    updateMediaSessionState()
                    refreshNotification()
                    controlCallback?.invoke(ACTION_PLAY)
                }

                override fun onPause() {
                    isPlaying = false
                    updateMediaSessionState()
                    refreshNotification()
                    controlCallback?.invoke(ACTION_PAUSE)
                }

                override fun onSkipToNext() {
                    controlCallback?.invoke(ACTION_NEXT)
                }

                override fun onSkipToPrevious() {
                    controlCallback?.invoke(ACTION_PREV)
                }

                override fun onStop() {
                    isPlaying = false
                    updateMediaSessionState()
                    refreshNotification()
                    controlCallback?.invoke(ACTION_PAUSE)
                }

                override fun onSeekTo(pos: Long) {
                    currentPositionMs = pos.coerceAtLeast(0L)
                    updateMediaSessionState()
                    refreshNotification()
                    seekCallback?.invoke(currentPositionMs)
                }
            })
            isActive = true
        }
        updateMediaSessionState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            MediaButtonReceiver.handleIntent(mediaSession, intent)
        }

        val action = intent?.action
        when (action) {
            ACTION_PREV -> controlCallback?.invoke(ACTION_PREV)
            ACTION_NEXT -> controlCallback?.invoke(ACTION_NEXT)
            ACTION_PLAY -> {
                isPlaying = true
                updateMediaSessionState()
                controlCallback?.invoke(ACTION_PLAY)
            }
            ACTION_PAUSE -> {
                isPlaying = false
                updateMediaSessionState()
                controlCallback?.invoke(ACTION_PAUSE)
            }
            ACTION_PLAY_PAUSE -> {
                isPlaying = !isPlaying
                updateMediaSessionState()
                controlCallback?.invoke(ACTION_PLAY_PAUSE)
            }
            ACTION_UPDATE_SONG -> {
                val title = intent.getStringExtra(EXTRA_TITLE)
                val artist = intent.getStringExtra(EXTRA_ARTIST)
                val playing = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
                val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, currentDurationMs)
                val positionMs = intent.getLongExtra(EXTRA_POSITION_MS, currentPositionMs)
                val artworkSource = intent.getStringExtra(EXTRA_ARTWORK_SOURCE).orEmpty()

                if (!title.isNullOrEmpty()) currentTitle = title
                if (!artist.isNullOrEmpty()) currentArtist = artist
                isPlaying = playing
                currentDurationMs = durationMs.coerceAtLeast(0L)
                currentPositionMs = positionMs.coerceAtLeast(0L)

                if (artworkSource != currentArtworkUrl) {
                    currentArtworkUrl = artworkSource
                    loadArtworkAsync(artworkSource)
                }
                updateMediaSessionState()
            }
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun updateMediaSessionState() {
        if (!::mediaSession.isInitialized) return

        val artBitmap = currentArtworkBitmap ?: defaultArtworkBitmap
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "YouTube Music")
            .putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                if (currentDurationMs > 0L) currentDurationMs else -1L
            )

        if (artBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artBitmap)
        }
        mediaSession.setMetadata(metadataBuilder.build())

        val availableActions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP

        val playbackState = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(availableActions)
            .setState(
                playbackState,
                currentPositionMs,
                if (isPlaying) 1.0f else 0.0f,
                SystemClock.elapsedRealtime()
            )
        mediaSession.setPlaybackState(stateBuilder.build())
    }

    private fun loadArtworkAsync(source: String) {
        if (source.isBlank()) {
            currentArtworkBitmap = null
            updateMediaSessionState()
            refreshNotification()
            return
        }

        artworkExecutor.execute {
            val bitmap: Bitmap? = try {
                if (source.startsWith("http://") || source.startsWith("https://")) {
                    val conn = (URL(source).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                        doInput = true
                        connect()
                    }
                    conn.inputStream.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(source)
                        val embedded = retriever.embeddedPicture
                        if (embedded != null) {
                            BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
                        } else {
                            null
                        }
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
                null
            }

            mainHandler.post {
                if (source == currentArtworkUrl) {
                    currentArtworkBitmap = bitmap
                    updateMediaSessionState()
                    refreshNotification()
                }
            }
        }
    }

    private fun refreshNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "YTMusic2::CpuWakeLock"
        ).apply {
            acquire(24 * 60 * 60 * 1000L)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicBackgroundService::class.java).setAction(ACTION_PREV),
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MusicBackgroundService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MusicBackgroundService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseText = if (isPlaying) "Pause" else "Play"
        val largeIcon = currentArtworkBitmap ?: defaultArtworkBitmap

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSubText("YouTube Music")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(largeIcon)
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, playPauseText, playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi pemutar musik YouTube Music"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(noisyAudioReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(noisyAudioReceiver, filter)
            }
            isNoisyReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                unregisterReceiver(noisyAudioReceiver)
            } catch (_: Exception) {}
            isNoisyReceiverRegistered = false
        }
    }

    override fun onDestroy() {
        unregisterNoisyReceiver()
        artworkExecutor.shutdownNow()
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
