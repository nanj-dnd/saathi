package app.saathi

import app.saathi.playbooks.PlaybookStore
import org.junit.Assert.*
import org.junit.Test

class PlaybookStoreTest {
    @Test fun `unfamiliar apps receive general instructions and known apps keep their own hints`() {
        val files = mapOf("_general" to "Universal guidance", "com.whatsapp" to "WhatsApp guidance", "_browser" to "Web guidance")
        val store = PlaybookStore(files.keys) { files.getValue(it) }
        assertEquals("Universal guidance", store.forApp("example.unknown.app"))
        assertEquals("Universal guidance\n\nWhatsApp guidance", store.forApp("com.whatsapp"))
        assertEquals("Universal guidance\n\nWeb guidance", store.forApp("org.mozilla.firefox"))
    }

    @Test fun `a missing specific playbook leaves the general guide available`() {
        val store = PlaybookStore(setOf("_general", "example.broken.app")) {
            if (it == "_general") "Universal guidance" else error("missing file")
        }
        assertEquals("Universal guidance", store.forApp("example.broken.app"))
    }
}
