package dev.wntrmute.ans

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Holds the transport state (repeat settings) and bridges the UI to
 * [NumberStationService]. Playback itself lives in the service, so it survives
 * configuration changes and backgrounding; this ViewModel only observes
 * [PlaybackState] and issues play/stop commands.
 *
 * The message text lives in the composable (so the field keeps its caret); a
 * snapshot is passed to [playOrStop] when a broadcast starts.
 */
class NumberStationViewModel(app: Application) : AndroidViewModel(app) {

    var isPlaying by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var repeatEnabled by mutableStateOf(false)
    var loopUntilStopped by mutableStateOf(true)

    var repeatCount by mutableIntStateOf(1)
        private set

    init {
        viewModelScope.launch {
            PlaybackState.isPlaying.collect { isPlaying = it }
        }
        viewModelScope.launch {
            PlaybackState.errors.collect { errorMessage = it }
        }
    }

    fun changeRepeatCount(value: Int) {
        repeatCount = value.coerceIn(MIN_REPEATS, MAX_REPEATS)
    }

    fun consumeError() {
        errorMessage = null
    }

    /** Toggle playback. Reads the supplied message when starting. */
    fun playOrStop(message: String) {
        val context = getApplication<Application>()
        if (isPlaying) {
            NumberStationService.stop(context)
            return
        }
        val groups = NumberFormatter.groups(message)
        if (groups.isEmpty()) return
        // repeatCount is "times to repeat after the first read", so total
        // passes is repeatCount + 1.
        NumberStationService.play(
            context = context,
            groups = groups,
            repeat = repeatEnabled,
            loopForever = loopUntilStopped,
            plays = repeatCount + 1,
        )
    }

    private companion object {
        const val MIN_REPEATS = 1
        const val MAX_REPEATS = 99
    }
}
