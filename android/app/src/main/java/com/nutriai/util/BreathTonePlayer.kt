package com.nutriai.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates soft, calming breathing tones on the fly (pure sine waves) - no audio files, no
 * network, works fully offline. Used by the meditation session so inhale/hold/exhale each get a
 * gentle chime that glides in pitch. Cheap: a short PCM buffer per phase, played once.
 */
class BreathTonePlayer {
    private val sampleRate = 44100
    @Volatile private var track: AudioTrack? = null

    /**
     * Plays a gentle tone that glides from [startHz] to [endHz] over [durationMs] with a soft
     * fade in/out (so there are no clicks). Any tone already playing is stopped first.
     */
    fun playGlide(startHz: Double, endHz: Double, durationMs: Int, volume: Float = 0.30f) {
        stop()
        val numSamples = (durationMs.toLong() * sampleRate / 1000).toInt().coerceAtLeast(1)
        val buffer = ShortArray(numSamples)
        val fade = (numSamples * 0.10).toInt().coerceAtLeast(1)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val freq = startHz + (endHz - startHz) * t
            phase += 2.0 * PI * freq / sampleRate
            var amp = volume
            if (i < fade) amp *= i.toFloat() / fade
            if (i > numSamples - fade) amp *= (numSamples - i).toFloat() / fade
            buffer[i] = (sin(phase) * amp * Short.MAX_VALUE).toInt().toShort()
        }

        val at = runCatching {
            AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                buffer.size * 2,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
        }.getOrNull() ?: return

        runCatching {
            at.write(buffer, 0, buffer.size)
            at.play()
        }
        track = at
    }

    fun stop() {
        val t = track
        track = null
        runCatching {
            t?.pause()
            t?.flush()
            t?.release()
        }
    }
}
