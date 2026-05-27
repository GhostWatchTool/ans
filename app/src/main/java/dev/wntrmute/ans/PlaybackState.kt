package dev.wntrmute.ans

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared, process-wide playback state. The [NumberStationService] owns playback
 * and publishes here; the UI ([NumberStationViewModel]) observes. This decouples
 * the UI lifecycle from playback, so a broadcast keeps running while the app is
 * backgrounded, rotated, or its Activity is destroyed and recreated.
 */
object PlaybackState {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Buffered so an error emitted before the UI is collecting is not dropped.
    private val _errors = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    fun setPlaying(value: Boolean) {
        _isPlaying.value = value
    }

    fun emitError(message: String) {
        _errors.tryEmit(message)
    }
}
