package com.example.shiftword.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import shiftword.shared.generated.resources.Res

actual class SoundEffectsFactory(private val context: Context) {
    // Cached, not constructed fresh per call: AppNavHost's GAMEPLAY route calls create() on
    // every level transition/replay (a new GameViewModel each time, same as the old
    // platformSoundEffects() pattern this replaced) -- with a real SoundPool + native audio
    // buffers behind each instance now (unlike the old ToneGenerator, cheap to throw away),
    // constructing fresh every time would leak a SoundPool per level and redundantly re-decode
    // the same four files repeatedly. One factory instance already lives for the whole app
    // session (constructed once in MainActivity, threaded down through App.kt/AppNavHost), so
    // caching here ties the underlying SoundPool's lifetime to that instead.
    private val instance by lazy { AndroidFileSoundEffects(context) }

    actual fun create(): SoundEffects = instance
}

// The four action-to-sound mappings this project ships with -- also used as both the
// composeResources file name AND the SoundPool cache-file name, so there's exactly one place
// (this list) that has to change if a sound file is swapped or a new one added.
private val SOUND_FILES = listOf("slide.mp3", "succesfull_slide.mp3", "level_complete.mp3", "gameover_pastel.mp3")

/**
 * Real-file playback via `SoundPool` (Android's purpose-built API for short, low-latency,
 * possibly-overlapping SFX -- a better fit here than `MediaPlayer` per-play, which pays real
 * decoder-setup latency on every call and would be felt on `playShift`'s every-move cadence).
 * Replaces the previous `AndroidToneSoundEffects` (`ToneGenerator`-synthesized tones).
 *
 * Loading is asynchronous by construction, not an afterthought: `Res.readBytes` is a suspend
 * call (Compose Multiplatform resources), and `SoundPool.load` is itself async even after that
 * (it notifies completion via a listener, not a return value) -- so [soundIds] only gets an
 * entry once a load genuinely finishes. Calling `play*()` before then is safe, not a race to
 * guard against: `SoundPool.play` on a not-yet-loaded id is a documented no-op (returns 0, never
 * throws), so the only user-visible effect of tapping before load completes is that specific
 * call being silent -- bounded to the first fraction of a second after a fresh
 * [AndroidFileSoundEffects] is constructed (once per level, per GameViewModel), not an ongoing
 * risk.
 *
 * Real-device bug found and fixed here (see the PR that introduced this comment): the original
 * version called `soundPool.setOnLoadCompleteListener { ... }` INSIDE the per-file loop below,
 * once per file. `SoundPool` only ever holds ONE listener -- each call overwrites the previous
 * one, it does not add a second one. Since all four loads are kicked off concurrently
 * (`Dispatchers.IO`), whichever file's coroutine happened to call `setOnLoadCompleteListener`
 * LAST silently became the only one that would ever populate [soundIds]; the other three
 * files' native loads completed successfully (confirmed on-device: `status == 0` for all four),
 * but their completion callbacks were routed to a listener closure checking a DIFFERENT
 * captured `loadId`, so they never matched and [soundIds] never got an entry for them --
 * `play()` then found `null` and silently no-op'd forever for those three, every time,
 * consistent with `SoundPool.play` on an unloaded id being a documented no-op, not an error.
 * This explains why exactly one of the four sounds worked, and why it wasn't always the same
 * one across app launches: the "last listener installed" is a coroutine-scheduling race, not a
 * fixed outcome. Fixed by installing exactly one listener, once, that resolves the file via a
 * [loadIdToFileName] reverse lookup instead of relying on which listener happens to still be
 * installed.
 */
private class AndroidFileSoundEffects(context: Context) : SoundEffects {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    // ConcurrentHashMap, not a plain mutableMapOf: both maps are written from whichever
    // Dispatchers.IO thread each file's load coroutine happens to run on (up to 4 concurrently),
    // and read from wherever play() is called (soundDispatcher) plus the single
    // OnLoadCompleteListener callback (SoundPool's own callback thread) -- genuine
    // multi-thread access, not just multi-coroutine-on-one-thread.
    private val soundIds = ConcurrentHashMap<String, Int>()
    private val loadIdToFileName = ConcurrentHashMap<Int, String>()

    // SupervisorJob: one sound file failing to load (corrupt asset, disk write failure) must not
    // cancel loading the other three. Dispatchers.IO: Res.readBytes + File.writeBytes are both
    // blocking I/O, must never run on the caller's thread (this constructor runs on
    // GameViewModel's soundDispatcher already -- see its own doc comment -- but that's a single
    // dedicated thread shared by every GameViewModel instance; blocking it here would delay
    // every OTHER sound-effect call queued behind it too).
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Installed exactly ONCE, before any load() call below -- see the class doc comment for
        // why installing this per-file (the original bug) silently dropped three of every four
        // sounds. A single listener resolving via loadIdToFileName is correct regardless of how
        // many loads are in flight or what order they complete in.
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadIdToFileName[sampleId]?.let { fileName -> soundIds[fileName] = sampleId }
            }
        }
        val cacheDir = context.cacheDir
        SOUND_FILES.forEach { fileName ->
            loadScope.launch {
                val bytes = Res.readBytes("files/$fileName")
                val file = File(cacheDir, fileName)
                file.writeBytes(bytes)
                val loadId = soundPool.load(file.absolutePath, 1)
                loadIdToFileName[loadId] = fileName
            }
        }
    }

    private fun play(fileName: String) {
        soundIds[fileName]?.let { id -> soundPool.play(id, 1f, 1f, 1, 0, 1f) }
    }

    override fun playShift() = play("slide.mp3")
    override fun playCascadeStep(step: Int) = play("succesfull_slide.mp3")
    override fun playLevelComplete() = play("level_complete.mp3")
    override fun playGameOver() = play("gameover_pastel.mp3")
}
