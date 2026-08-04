package com.example.shiftword.game

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create
import shiftword.shared.generated.resources.Res

actual class SoundEffectsFactory {
    // Cached, not constructed fresh per call -- see the Android actual's identical doc comment
    // (SoundPlayer.android.kt) for why: AppNavHost calls create() on every level
    // transition/replay, and re-triggering the async Res.readBytes load each time would be
    // wasteful even though AVAudioPlayer itself has no native-buffer-leak concern the way
    // SoundPool does.
    private val instance by lazy { IosFileSoundEffects() }

    actual fun create(): SoundEffects = instance
}

private val SOUND_FILES = listOf("slide.mp3", "succesfull_slide.mp3", "level_complete.mp3", "gameover_pastel.mp3")

/**
 * Real-file playback via `AVAudioPlayer`, replacing the previous `IosSystemSoundEffects`
 * (`AudioServicesPlaySystemSound`-based canned system clicks — a documented lesser-fidelity
 * approximation used only because this project had no bundled audio assets of its own yet; see
 * SOUND_SOURCING.md for where these now come from).
 *
 * Mirrors the Android side (`SoundPlayer.android.kt`) in structure and reasoning: `Res.readBytes`
 * (Compose Multiplatform resources) is suspend, so loading happens on a background-dispatched
 * coroutine kicked off at construction, not blocking whatever thread constructs this. Unlike
 * Android's `SoundPool`, `AVAudioPlayer` can be prepared directly from in-memory `NSData` -- no
 * cache-file write needed, since iOS has no `SoundPool`-style "must load from a file path"
 * constraint.
 *
 * One `AVAudioPlayer` per call, not one shared/reused instance per sound: calling `play()` again
 * on an instance already mid-playback restarts it from the beginning rather than layering a
 * second concurrent play, which would be wrong for `playCascadeStep` (can legitimately fire
 * multiple times in quick succession for a multi-step chain -- GameViewModel's chainLog loop).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosFileSoundEffects : SoundEffects {
    private val soundBytes = mutableMapOf<String, ByteArray>()
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        SOUND_FILES.forEach { fileName ->
            loadScope.launch {
                soundBytes[fileName] = Res.readBytes("files/$fileName")
            }
        }
    }

    // A fresh AVAudioPlayer per call (see class doc comment) -- there's no fire-and-forget
    // "just play this data" free function on iOS, so an instance must be kept alive at least
    // until playback finishes. AVAudioPlayer keeps its own internal playback session running
    // independent of the Kotlin/Native reference once play() has started, so it's safe to let
    // this local go out of scope rather than retaining it.
    private fun play(fileName: String) {
        val bytes = soundBytes[fileName] ?: return
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        AVAudioPlayer(data = data, error = null).play()
    }

    override fun playShift() = play("slide.mp3")
    override fun playCascadeStep(step: Int) = play("succesfull_slide.mp3")
    override fun playLevelComplete() = play("level_complete.mp3")
    override fun playGameOver() = play("gameover_pastel.mp3")
}
