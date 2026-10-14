package app.saathi.voice

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object Wav {
    /** Wraps 16-bit little-endian mono PCM in a RIFF/WAVE header. */
    fun fromPcm16(pcm: ByteArray, sampleRate: Int): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }
        return ByteArrayOutputStream(44 + pcm.size).apply { write(header.array()); write(pcm) }.toByteArray()
    }

    fun rms(samples: ShortArray, count: Int): Double {
        if (count <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until count) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / count)
    }
}

/**
 * Decides when an utterance has started and ended from per-frame loudness.
 * Seniors pause mid-sentence, so the end-of-speech silence is generous.
 */
class EndpointDetector(
    private val frameMs: Int = 100,
    private val calibrationMs: Int = 300,
    private val trailingSilenceMs: Int = 1600,
    private val noSpeechTimeoutMs: Int = 7000,
    private val maxMs: Int = 15000,
    private val minThreshold: Double = 450.0,
) {
    enum class Verdict { CONTINUE, END, NO_SPEECH }

    private var elapsed = 0
    private var noiseSum = 0.0
    private var noiseFrames = 0
    private var threshold = minThreshold
    private var silenceRun = 0
    var heardSpeech = false
        private set

    fun onFrame(rms: Double): Verdict {
        elapsed += frameMs
        if (elapsed <= calibrationMs) {
            noiseSum += rms; noiseFrames++
            threshold = maxOf(minThreshold, noiseSum / noiseFrames * 3)
            return Verdict.CONTINUE
        }
        if (rms >= threshold) {
            heardSpeech = true
            silenceRun = 0
        } else if (heardSpeech) {
            silenceRun += frameMs
        }
        return when {
            heardSpeech && silenceRun >= trailingSilenceMs -> Verdict.END
            elapsed >= maxMs -> if (heardSpeech) Verdict.END else Verdict.NO_SPEECH
            !heardSpeech && elapsed >= noSpeechTimeoutMs -> Verdict.NO_SPEECH
            else -> Verdict.CONTINUE
        }
    }
}
