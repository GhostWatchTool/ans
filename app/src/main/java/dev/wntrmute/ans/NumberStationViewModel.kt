package dev.wntrmute.ans

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

/**
 * Holds the transport state (play/stop, repeat settings) and drives the
 * [NumberStationPlayer]. The message text itself lives in the composable so the
 * field keeps its caret across recomposition; a snapshot is passed to [playOrStop].
 */
class NumberStationViewModel(app: Application) : AndroidViewModel(app) {

    private val player = NumberStationPlayer(app)

    init {
        // Assigned here rather than via apply {} so the lambdas resolve against
        // this ViewModel's properties, not the player's read-only `isPlaying`.
        player.onPlayingChanged = { value -> isPlaying = value }
        player.onError = { message -> errorMessage = message }
    }

    var isPlaying by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var repeatEnabled by mutableStateOf(false)
    var loopUntilStopped by mutableStateOf(true)

    var repeatCount by mutableStateOf(1)
        private set

    fun changeRepeatCount(value: Int) {
        repeatCount = value.coerceIn(MIN_REPEATS, MAX_REPEATS)
    }

    fun consumeError() {
        errorMessage = null
    }

    /** Toggle playback. Reads the supplied message when starting. */
    fun playOrStop(message: String) {
        if (isPlaying) {
            player.stop()
            return
        }
        val groups = NumberFormatter.groups(message)
        if (groups.isEmpty()) return
        // repeatCount is "times to repeat after the first read", so total
        // passes is repeatCount + 1.
        player.start(groups, repeatEnabled, loopUntilStopped, repeatCount + 1)
    }

    override fun onCleared() {
        player.shutdown()
    }

    private companion object {
        const val MIN_REPEATS = 1
        const val MAX_REPEATS = 99
    }
}
