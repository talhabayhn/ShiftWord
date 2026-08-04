package com.example.shiftword.game

/**
 * Real audio files (SOUND_SOURCING.md), bundled as Compose Multiplatform resources under
 * `commonMain/composeResources/files/` so both platforms load them through the same
 * `Res.readBytes(...)` API — mechanical shift click and cascade-step chime replaced the earlier
 * synthesized `ToneGenerator` tones with these; [playLevelComplete]/[playGameOver] are new hooks
 * (previously nothing played at all on a win/loss transition).
 */
interface SoundEffects {
    fun playShift()
    fun playCascadeStep(step: Int)
    fun playLevelComplete()
    fun playGameOver()
}

/**
 * Default for GameViewModel — deliberately inert. Real implementations touch platform audio
 * APIs, which are only backed by a real device/emulator/simulator; on a plain JVM unit test (this
 * project's androidHostTest has no Robolectric shadow wired in for audio), constructing or
 * calling one throws. Tests should never need to know that — they just get silence unless a
 * caller opts into real sound explicitly, which only the app shell does.
 */
object NoSoundEffects : SoundEffects {
    override fun playShift() {}
    override fun playCascadeStep(step: Int) {}
    override fun playLevelComplete() {}
    override fun playGameOver() {}
}

/**
 * Constructs the real, platform-backed [SoundEffects] implementation. An `expect class` (not a
 * plain `expect fun`) for the same reason as [com.example.shiftword.data.DatabaseDriverFactory]:
 * Android's implementation needs a `Context` (to write the bundled mp3 bytes to a cache file
 * SoundPool can load from) and iOS's doesn't -- each platform's `actual` constructor is free to
 * diverge because only platform-specific code (`MainActivity.kt`, `MainViewController.kt`) ever
 * constructs this; common code only ever receives an already-built instance as a parameter.
 */
expect class SoundEffectsFactory {
    fun create(): SoundEffects
}
