package dev.wntrmute.ans

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Foreground service that runs a number-station broadcast independently of the
 * UI. Holds audio focus (so it pauses other media and is the legitimate owner
 * of a mediaPlayback foreground service) and a partial wake lock (so a long
 * loop keeps reading with the screen off). Publishes state via [PlaybackState].
 */
class NumberStationService : Service() {

    private lateinit var player: NumberStationPlayer
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tornDown = false

    private val audioAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    // Permanent focus loss (a call, another app taking over) hard-stops the
    // broadcast — no graceful signoff because the audio channel just got taken.
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS) hardStopPlayback()
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService()!!
        player = NumberStationPlayer(this).apply {
            setAudioAttributes(audioAttributes)
            onPhaseChanged = { phase ->
                PlaybackState.setPhase(phase)
                if (phase == Phase.Idle) teardownService()
            }
            onError = { message ->
                PlaybackState.emitError(message)
                hardStopPlayback()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> startPlayback(intent)
            ACTION_STOP -> player.requestStop()
            ACTION_STOP_NOW -> hardStopPlayback()
            else -> hardStopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun startPlayback(intent: Intent) {
        tornDown = false
        // Started via startForegroundService(): we must call startForeground()
        // promptly, before any early return, or the system kills us.
        startForeground(NOTIFICATION_ID, buildNotification())
        val groups = intent.getStringArrayListExtra(EXTRA_GROUPS) ?: arrayListOf()
        if (groups.isEmpty()) {
            hardStopPlayback()
            return
        }
        requestAudioFocus()
        acquireWakeLock()
        // player.start() emits Phase.Playing through onPhaseChanged, which
        // updates PlaybackState; no need to set it here.
        player.start(
            groups = groups,
            repeat = intent.getBooleanExtra(EXTRA_REPEAT, false),
            loopForever = intent.getBooleanExtra(EXTRA_LOOP, false),
            plays = intent.getIntExtra(EXTRA_PLAYS, 1),
        )
    }

    /** Stop the broadcast immediately (no signoff) and tear down the service. */
    private fun hardStopPlayback() {
        player.stopNow() // emits Phase.Idle, which routes back to teardownService()
        // If the player was already idle, the callback won't fire; ensure
        // teardown still happens so we don't leak the FGS / focus / wakelock.
        teardownService()
    }

    /** Called when the player transitions to Phase.Idle (graceful or hard). */
    private fun teardownService() {
        if (tornDown) return
        tornDown = true
        abandonAudioFocus()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player.shutdown()
        abandonAudioFocus()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Audio focus ---

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    // --- Wake lock ---

    private fun acquireWakeLock() {
        val pm = getSystemService<PowerManager>() ?: return
        val lock = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .also { wakeLock = it }
        if (!lock.isHeld) lock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    // --- Notification ---

    private fun buildNotification(): Notification {
        createChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, NumberStationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_broadcast)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_broadcasting))
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_playback),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setSound(null, null) }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_PLAY = "dev.wntrmute.ans.action.PLAY"
        private const val ACTION_STOP = "dev.wntrmute.ans.action.STOP"
        private const val ACTION_STOP_NOW = "dev.wntrmute.ans.action.STOP_NOW"
        private const val EXTRA_GROUPS = "groups"
        private const val EXTRA_REPEAT = "repeat"
        private const val EXTRA_LOOP = "loop"
        private const val EXTRA_PLAYS = "plays"

        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "NumberStation::playback"
        private const val WAKE_LOCK_TIMEOUT_MS = 60L * 60L * 1000L // 1h safety cap

        /** Start (or restart) a broadcast. Safe to call while the app is foreground. */
        fun play(
            context: Context,
            groups: List<String>,
            repeat: Boolean,
            loopForever: Boolean,
            plays: Int,
        ) {
            val intent = Intent(context, NumberStationService::class.java).apply {
                action = ACTION_PLAY
                putStringArrayListExtra(EXTRA_GROUPS, ArrayList(groups))
                putExtra(EXTRA_REPEAT, repeat)
                putExtra(EXTRA_LOOP, loopForever)
                putExtra(EXTRA_PLAYS, plays)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Request a graceful stop: finish the current pass, play the signoff,
         * then end. If the player isn't running this is a no-op.
         */
        fun stop(context: Context) {
            val intent = Intent(context, NumberStationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /** Hard-stop the broadcast immediately, skipping any signoff. */
        fun stopNow(context: Context) {
            val intent = Intent(context, NumberStationService::class.java).apply {
                action = ACTION_STOP_NOW
            }
            context.startService(intent)
        }
    }
}
