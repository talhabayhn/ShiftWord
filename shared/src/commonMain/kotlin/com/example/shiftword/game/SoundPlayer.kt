package com.example.shiftword.game

/**
 * Mechanical shift click on every committed move, plus an escalating cue per cascade chain
 * step — reuses resolveCascade's existing CascadeStep.step (see GameViewModel), no new state.
 */
interface SoundEffects {
    fun playShift()
    fun playCascadeStep(step: Int)
}

/**
 * Default for GameViewModel — deliberately inert. Android's real implementation touches
 * android.media.ToneGenerator, which is only backed by a real device/emulator audio stack; on
 * a plain JVM unit test (this project's androidHostTest has no Robolectric shadow wired in),
 * constructing or calling it throws. Tests should never need to know that — they just get
 * silence unless a caller opts into real sound explicitly, which only the app shell does.
 */
object NoSoundEffects : SoundEffects {
    override fun playShift() {}
    override fun playCascadeStep(step: Int) {}
}

expect fun platformSoundEffects(): SoundEffects
