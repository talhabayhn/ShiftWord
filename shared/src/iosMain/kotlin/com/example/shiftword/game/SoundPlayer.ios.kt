package com.example.shiftword.game

import platform.AudioToolbox.AudioServicesPlaySystemSound

// AudioServicesPlaySystemSound plays fixed canned system-sound clips by id — it has no pitch
// parameter, unlike Android's synthesized ToneGenerator. True pitch escalation on iOS without
// this project's own bundled audio assets isn't available through this lightweight API, so
// escalation here means "step through a small set of distinct system sound ids," a real but
// weaker approximation of the same effect than the Android side gets. Documented limitation,
// not an oversight — bundling real audio assets is future work, not part of this pass.
private class IosSystemSoundEffects : SoundEffects {
    override fun playShift() {
        AudioServicesPlaySystemSound(1104u) // standard system "tock" click
    }

    override fun playCascadeStep(step: Int) {
        val systemSoundIds = uintArrayOf(1103u, 1104u, 1105u, 1057u, 1156u)
        val index = (step - 1).coerceIn(0, systemSoundIds.lastIndex)
        AudioServicesPlaySystemSound(systemSoundIds[index])
    }
}

actual fun platformSoundEffects(): SoundEffects = IosSystemSoundEffects()
