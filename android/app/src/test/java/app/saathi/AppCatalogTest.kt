package app.saathi

import app.saathi.apps.AppCatalog
import app.saathi.net.AppInfo
import org.junit.Assert.*
import org.junit.Test

class AppCatalogTest {
    private val signal = AppInfo("org.thoughtcrime.securesms", "Signal")
    private val outlook = AppInfo("com.microsoft.office.outlook", "Outlook")
    private val privateApp = AppInfo("example.private.app", "Unrelated private app")
    private fun request(text: String) = RecipeEngineTest.req("", app = signal.packageName, utterance = text)

    @Test fun `unknown apps are matched by their installed labels without a recipe`() {
        val catalog = AppCatalog(listOf(signal, outlook, privateApp))
        val result = catalog.contextualize(request("send an email using Outlook"))
        assertEquals("Signal", result.appName)
        assertEquals(listOf(signal, outlook), result.installedApps)
        assertFalse(result.installedApps.contains(privateApp))
        assertTrue(catalog.mentioned("the signalman is here").isEmpty())
    }

    @Test fun `app matches preserve native script names and case tolerance`() {
        val app = AppInfo("example.native.app", "नमस्ते")
        val catalog = AppCatalog(listOf(app, outlook))
        assertEquals(listOf(app), catalog.mentioned("नमस्ते खोलो"))
        assertEquals(listOf(outlook), catalog.mentioned("open OUTLOOK"))
    }

    @Test fun `ambiguous labels are all retained and never resolved to an arbitrary package`() {
        val other = signal.copy(packageName = "example.signal.clone")
        val catalog = AppCatalog(listOf(signal, other, signal))
        assertEquals(2, catalog.mentioned("open Signal").size)
        assertNull(catalog.resolve("Signal"))
        assertEquals(signal.packageName, AppCatalog(listOf(signal)).resolve(" SIGNAL "))
    }

    @Test fun `context is bounded and the foreground app is retained`() {
        val apps = (1..30).map { AppInfo("example.app$it", "Reader") } + signal
        val result = AppCatalog(apps).contextualize(request("open Reader"))
        assertEquals(AppCatalog.MAX_RELEVANT, result.installedApps.size)
        assertEquals(signal, result.installedApps.first())
    }

    @Test fun `unknown foreground and absent matches expose no unrelated inventory`() {
        val result = AppCatalog(listOf(privateApp)).contextualize(request("help me"))
        assertEquals("", result.appName)
        assertTrue(result.installedApps.isEmpty())
    }
}
