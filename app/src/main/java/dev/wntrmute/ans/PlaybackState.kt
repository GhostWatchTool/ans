package dev.wntrmute.ans

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Three-state lifecycle of a broadcast.
 *
 * - [Idle] — no broadcast in progress.
 * - [Playing] — actively reading the message. The Stop button shows "Stop"
 *   and signals a *graceful* stop (finish the current pass, play the signoff,
 *   then end).
 * - [Stopping] — a graceful stop has been requested; the player will inject
 *   a signoff after the current pass ends and then move to [Idle]. The Stop
 *   button shows "Stop Now" and a tap hard-kills playback immediately.
 */
enum class Phase { Idle, Playing, Stopping }

/**
 * Shared, process-wide playback state. The [NumberStationService] owns playback
 * and publishes here; the UI ([NumberStationViewModel]) observes. This decouples
 * the UI lifecycle from playback, so a broadcast keeps running while the app is
 * backgrounded, rotated, or its Activity is destroyed and recreated.
 */
object PlaybackState {

    private val _phase = MutableStateFlow(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    // Buffered so an error emitted before the UI is collecting is not dropped.
    private val _errors = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    fun setPhase(value: Phase) {
        _phase.value = value
    }

    fun emitError(message: String) {
        _errors.tryEmit(message)
    }
}
