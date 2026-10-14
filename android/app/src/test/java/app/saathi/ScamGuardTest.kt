package app.saathi

import app.saathi.safety.ScamGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamGuardTest {
    @Test fun `warns about remote-control apps once per cooldown`() {
        var t = 0L
        val g = ScamGuard { t }
        assertFalse(g.shouldWarn("com.grofers.customerapp"))
        assertTrue(g.shouldWarn("com.anydesk.anydeskandroid"))
        assertFalse(g.shouldWarn("com.anydesk.anydeskandroid"))
        t += ScamGuard.COOLDOWN_MS
        assertTrue(g.shouldWarn("com.anydesk.anydeskandroid"))
    }
}
