package app.saathi

import app.saathi.safety.ScamGuard
import app.saathi.voice.EndpointDetector
import app.saathi.voice.EndpointDetector.Verdict
import app.saathi.voice.Wav
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceAndSafetyTest {
    @Test fun `wav header describes 16 kHz mono 16-bit pcm`() {
        val pcm = ByteArray(3200) { it.toByte() }
        val wav = Wav.fromPcm16(pcm, 16_000)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals(36 + pcm.size, b.getInt(4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(1.toShort(), b.getShort(22)) // mono
        assertEquals(16_000, b.getInt(24))
        assertEquals(16.toShort(), b.getShort(34))
        assertEquals(pcm.size, b.getInt(40))
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }

    private fun run(frames: List<Double>): Pair<Verdict, Int> {
        val d = EndpointDetector()
        frames.forEachIndexed { i, rms ->
            val v = d.onFrame(rms)
            if (v != Verdict.CONTINUE) return v to i
        }
        return Verdict.CONTINUE to frames.size
    }

    @Test fun `ends after speech followed by trailing silence`() {
        val frames = List(3) { 100.0 } + List(20) { 3000.0 } + List(30) { 100.0 }
        val (v, at) = run(frames)
        assertEquals(Verdict.END, v)
        assertEquals(3 + 20 + 16 - 1, at) // 1.6 s of silence
    }

    @Test fun `short pauses mid-sentence do not end the turn`() {
        val frames = List(3) { 100.0 } + List(10) { 3000.0 } + List(10) { 100.0 } + List(10) { 3000.0 }
        assertEquals(Verdict.CONTINUE, run(frames).first)
    }

    @Test fun `gives up when nobody speaks`() {
        val (v, at) = run(List(100) { 120.0 })
        assertEquals(Verdict.NO_SPEECH, v)
        assertEquals(69, at)
    }

    @Test fun `scam guard warns about remote-control apps once per cooldown`() {
        var t = 0L
        val g = ScamGuard { t }
        assertFalse(g.shouldWarn("com.grofers.customerapp"))
        assertTrue(g.shouldWarn("com.anydesk.anydeskandroid"))
        assertFalse(g.shouldWarn("com.anydesk.anydeskandroid"))
        t += ScamGuard.COOLDOWN_MS
        assertTrue(g.shouldWarn("com.anydesk.anydeskandroid"))
    }
}
