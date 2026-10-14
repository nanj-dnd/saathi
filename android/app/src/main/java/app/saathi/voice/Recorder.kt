package app.saathi.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.saathi.session.Ears
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** 16 kHz mono recorder that stops on its own after the senior stops talking. */
class Recorder(
    private val onStart: () -> Unit,
    private val onEnd: () -> Unit,
) : Ears {
    private companion object {
        const val RATE = 16_000
        const val FRAME = RATE / 10 // 100 ms
        const val MIN_TAPPED_SAMPLES = RATE * 6 / 10
    }

    @Volatile private var finishRequested = false

    override fun finish() {
        finishRequested = true
    }

    // Callers check RECORD_AUDIO before listening (GuideSession.hasMic).
    @SuppressLint("MissingPermission")
    override suspend fun listen(): ByteArray? = withContext(Dispatchers.IO) {
        finishRequested = false
        onStart()
        try {
            val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, FRAME * 4),
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return@withContext null
            }
            val frame = ShortArray(FRAME)
            val pcm = ByteArrayOutputStream()
            val detector = EndpointDetector()
            var samples = 0
            var noSpeech = false
            rec.startRecording()
            try {
                while (true) {
                    ensureActive()
                    val n = rec.read(frame, 0, frame.size)
                    if (n <= 0) break
                    for (i in 0 until n) {
                        pcm.write(frame[i].toInt() and 0xff)
                        pcm.write(frame[i].toInt() shr 8 and 0xff)
                    }
                    samples += n
                    // A second tap means "I'm done". Send what we have, even if it was quiet.
                    if (finishRequested) break
                    when (detector.onFrame(Wav.rms(frame, n))) {
                        EndpointDetector.Verdict.END -> break
                        EndpointDetector.Verdict.NO_SPEECH -> { noSpeech = true; break }
                        EndpointDetector.Verdict.CONTINUE -> {}
                    }
                }
            } finally {
                rec.stop()
                rec.release()
            }
            val tappedWithAudio = finishRequested && samples >= MIN_TAPPED_SAMPLES
            if (noSpeech || (!detector.heardSpeech && !tappedWithAudio)) null
            else Wav.fromPcm16(pcm.toByteArray(), RATE)
        } finally {
            onEnd()
        }
    }
}
