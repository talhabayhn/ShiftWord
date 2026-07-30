package com.example.shiftword.game

import android.media.AudioManager
import android.media.ToneGenerator

// ToneGenerator synthesizes tones on-device — no bundled audio assets needed/available in this
// project. DTMF tone constants 1-9 are used purely as a set of distinct, increasingly "busy"
// clicks per step; they are not a music-theory pitch ramp, just a cheap escalating feel.
private class AndroidToneSoundEffects : SoundEffects {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)

    override fun playShift() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
    }

    override fun playCascadeStep(step: Int) {
        val dtmfTones = intArrayOf(
            ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_2, ToneGenerator.TONE_DTMF_3,
            ToneGenerator.TONE_DTMF_4, ToneGenerator.TONE_DTMF_5, ToneGenerator.TONE_DTMF_6,
            ToneGenerator.TONE_DTMF_7, ToneGenerator.TONE_DTMF_8, ToneGenerator.TONE_DTMF_9,
        )
        val index = (step - 1).coerceIn(0, dtmfTones.lastIndex)
        toneGenerator.startTone(dtmfTones[index], 90)
    }
}

actual fun platformSoundEffects(): SoundEffects = AndroidToneSoundEffects()
