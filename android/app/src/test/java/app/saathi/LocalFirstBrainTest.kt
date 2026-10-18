package app.saathi

import app.saathi.RecipeEngineTest.Companion.book
import app.saathi.RecipeEngineTest.Companion.req
import app.saathi.brain.CacheStore
import app.saathi.brain.FileCacheStore
import app.saathi.brain.LocalFirstBrain
import app.saathi.brain.ScreenKey
import app.saathi.brain.TurnCache
import app.saathi.net.Action
import app.saathi.net.Danger
import app.saathi.net.Guide
import app.saathi.net.PastStep
import app.saathi.net.Source
import app.saathi.net.Status
import app.saathi.net.TurnRequest
import app.saathi.net.TurnResponse
import app.saathi.recipes.RecipeBook
import app.saathi.recipes.RecipeEngine
import app.saathi.session.Brain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class LocalFirstBrainTest {
    private class MemoryStore(var text: String? = null) : CacheStore {
        var writes = 0
        override fun read() = text
        override fun write(text: String) { this.text = text; writes++ }
    }

    private class FakeRemote(var reply: Guide) : Brain {
        val calls = mutableListOf<TurnRequest>()
        var fail = false
        override suspend fun turn(request: TurnRequest): TurnResponse {
            calls += request
            if (fail) throw IOException("offline")
            return TurnResponse(reply, source = Source.MODEL)
        }
    }

    private val blinkitHome = """
        n1 EditText "Search 'milk'" [40,300,1040,400] click edit
        n2 TextView "Delivery in 9 minutes" [40,150,700,220]
        n3 TextView "Amul Taaza Milk 500 ml" [40,600,500,700]
        n4 Button "ADD" [800,620,1000,700] click
        n5 TextView "Mother Dairy Curd 400 g" [40,800,500,900]
        n6 Button "ADD" [800,820,1000,900] click
    """.trimIndent()

    private val addMilk = Guide(say = "Amul के पास 'ADD' दबाइए", goal = "order milk", targetKind = "node", nodeId = "n4", label = "ADD")

    private fun brain(
        remote: Brain,
        store: CacheStore = MemoryStore(),
        clock: () -> Long = { 0L },
        books: RecipeBook = RecipeBook.EMPTY,
        canOpen: ((String) -> Boolean)? = null,
    ) =
        LocalFirstBrain(
            recipes = { books },
            cache = TurnCache(store, now = clock, io = Dispatchers.Unconfined),
            remote = remote,
            sensitiveLine = { "yourself:$it" },
            canOpen = canOpen,
        )

    private fun auto(tree: String = blinkitHome, history: List<PastStep> = emptyList()) =
        req(tree, app = "com.grofers.customerapp", goal = "order milk", history = history)

    private val launcher = "n1 TextView \"Phone\" [40,2000,200,2100] click\nn2 TextView \"Chrome\" [240,2000,400,2100] click"

    /** A turn no recipe answers: a task with no script, mid-way. */
    private fun modelTurn() = req(
        launcher, app = "com.android.launcher3", goal = "send a voice note to my grandson",
        history = listOf(PastStep("x", "", Status.NEXT_STEP, "com.android.launcher3")),
    )

    private fun openWhatsApp() = Guide(
        say = "WhatsApp खोल रही हूँ।", goal = "call on WhatsApp", label = "WhatsApp",
        action = Action.OPEN_APP, actionArg = "com.whatsapp",
    )

    @Test fun `an app the model opens is opened when it is here`() = runTest {
        val r = brain(FakeRemote(openWhatsApp()), books = book, canOpen = { it == "com.whatsapp" })
            .turn(modelTurn())
        assertEquals(Action.OPEN_APP, r.guide.action)
        assertEquals("com.whatsapp", r.guide.actionArg)
    }

    @Test fun `an app the model opens but the phone lacks becomes its Play Store page`() = runTest {
        val r = brain(FakeRemote(openWhatsApp()), books = book, canOpen = { it == "com.android.vending" })
            .turn(modelTurn())
        assertEquals(Action.OPEN_STORE_PAGE, r.guide.action)
        assertEquals("'WhatsApp' अभी इस फ़ोन में नहीं है। Play Store में उसका पेज खोल रही हूँ।", r.guide.say)
        assertEquals(RecipeEngine.STORE_GOAL, r.guide.goal)
    }

    @Test fun `without a Play Store the senior hears where to look instead`() = runTest {
        val r = brain(FakeRemote(openWhatsApp()), books = book, canOpen = { false })
            .turn(modelTurn())
        assertEquals("", r.guide.action)
        assertTrue(r.guide.say.contains("WhatsApp"))
        assertTrue(!r.guide.say.contains("खोल रही"))
    }

    @Test fun `secure screens are answered on the phone`() = runTest {
        val remote = FakeRemote(addMilk)
        val r = brain(remote).turn(auto().copy(secureScreen = true, language = "ta-IN"))
        assertEquals("yourself:ta-IN", r.guide.say)
        assertEquals(Status.USER_MUST_DO, r.guide.status)
        assertEquals(Danger.SENSITIVE, r.guide.danger)
        assertEquals("order milk", r.guide.goal)
        assertTrue(remote.calls.isEmpty())
    }

    @Test fun `recipes answer without the network`() = runTest {
        val remote = FakeRemote(addMilk).apply { fail = true }
        val r = brain(remote, books = book).turn(req(RecipeEngineTest.settingsHome, utterance = "अक्षर बड़े करने हैं"))
        assertEquals(Source.RECIPE, r.source)
        assertTrue(remote.calls.isEmpty())
    }

    @Test fun `the same screen and progress is answered from the phone the second time`() = runTest {
        val remote = FakeRemote(addMilk)
        val store = MemoryStore()
        val b = brain(remote, store)
        assertEquals(Source.MODEL, b.turn(auto()).source)
        val again = b.turn(auto())
        assertEquals(Source.CACHE, again.source)
        assertEquals(addMilk, again.guide)
        assertEquals(1, remote.calls.size)
        assertEquals(1, store.writes)

        // Survives a restart of the service.
        val fresh = brain(remote, MemoryStore(store.text))
        assertEquals(Source.CACHE, fresh.turn(auto()).source)
        assertEquals(1, remote.calls.size)
    }

    @Test fun `different progress, words or language mean a different key`() {
        val base = auto()
        val keys = listOf(
            base,
            base.copy(history = listOf(PastStep("x", "ADD", Status.NEXT_STEP, base.app))),
            base.copy(utterance = "दही भी चाहिए"),
            base.copy(language = "ta-IN"),
            base.copy(goal = "order curd"),
            base.copy(tree = base.tree.replace("Curd", "Paneer")),
        ).map(ScreenKey::turnKey)
        assertEquals(keys.size, keys.toSet().size)
        // A pixel of layout jitter is the same screen.
        assertEquals(ScreenKey.turnKey(base), ScreenKey.turnKey(base.copy(tree = base.tree.replace("[800,620,1000,700]", "[801,621,1000,700]"))))
    }

    @Test fun `warnings, questions, point targets and sensitive screens are never reused`() = runTest {
        for (reply in listOf(
            addMilk.copy(danger = Danger.SCAM),
            addMilk.copy(status = Status.NEED_INFO),
            addMilk.copy(status = Status.USER_MUST_DO, danger = Danger.SENSITIVE, targetKind = "none"),
            addMilk.copy(targetKind = "point", x = 0.5, y = 0.5),
        )) {
            val remote = FakeRemote(reply)
            val b = brain(remote)
            b.turn(auto())
            b.turn(auto())
            assertEquals(reply.toString(), 2, remote.calls.size)
        }
        val remote = FakeRemote(addMilk)
        val b = brain(remote)
        val otp = blinkitHome + "\nn7 TextView \"Enter OTP sent to your phone\" [0,1000,1080,1100]"
        b.turn(auto(otp))
        b.turn(auto(otp))
        assertEquals(2, remote.calls.size)
    }

    @Test fun `screens with scam signs always reach the model`() = runTest {
        val remote = FakeRemote(addMilk)
        val b = brain(remote, books = book)
        val scam = RecipeEngineTest.settingsHome + "\nn99 TextView \"Your KYC expires today, call 98xxxx\" [0,100,1080,200]"
        b.turn(req(scam, utterance = "अक्षर बड़े करने हैं"))
        b.turn(req(scam, utterance = "अक्षर बड़े करने हैं"))
        assertEquals(2, remote.calls.size)
        b.turn(req(RecipeEngineTest.settingsHome, utterance = "AnyDesk डाउनलोड करना है"))
        assertEquals(3, remote.calls.size)
    }

    @Test fun `offline, a scam-looking question still gets the scripted warning`() = runTest {
        val remote = FakeRemote(addMilk)
        val b = brain(remote, books = book)
        val kyc = req(RecipeEngineTest.settingsHome, app = "com.whatsapp", utterance = "KYC अपडेट करने का मैसेज आया है")
        assertEquals(Source.MODEL, b.turn(kyc).source) // online, the model sees the screen and warns

        remote.fail = true
        val r = b.turn(kyc)
        assertEquals(Source.RECIPE, r.source)
        assertEquals(Danger.SCAM, r.guide.danger)

        // An ordinary script is not given on a scam-looking screen, even offline.
        val scam = RecipeEngineTest.settingsHome + "\nn99 TextView \"Your KYC expires today\" [0,100,1080,200]"
        assertTrue(runCatching { b.turn(req(scam, utterance = "अक्षर बड़े करने हैं")) }.exceptionOrNull() is IOException)
    }

    @Test fun `screens that are mostly a picture are not cached`() = runTest {
        val remote = FakeRemote(addMilk)
        val b = brain(remote)
        val sparse = "n1 View \"\" [0,0,1080,2400] click\nn4 Button \"ADD\" [800,620,1000,700] click"
        b.turn(auto(sparse))
        b.turn(auto(sparse))
        assertEquals(2, remote.calls.size)
    }

    @Test fun `offline, an expired answer for this exact screen is still used`() = runTest {
        var now = 0L
        val remote = FakeRemote(addMilk)
        val b = brain(remote, clock = { now })
        b.turn(auto())
        now = 30L * 24 * 60 * 60 * 1000 // a month later: too old to trust while online
        assertEquals(Source.MODEL, b.turn(auto()).source)
        now *= 2
        remote.fail = true
        val r = b.turn(auto())
        assertEquals(Source.CACHE, r.source)
    }

    @Test fun `offline with nothing saved still reports the failure`() = runTest {
        val remote = FakeRemote(addMilk).apply { fail = true }
        val e = runCatching { brain(remote).turn(auto()) }.exceptionOrNull()
        assertTrue(e is IOException)
    }

    @Test fun `a saved target that is no longer on screen is not reused`() = runTest {
        val remote = FakeRemote(addMilk)
        val b = brain(remote)
        b.turn(auto())
        assertTrue(LocalFirstBrain.fits(addMilk, auto()))
        assertTrue(!LocalFirstBrain.fits(addMilk, auto(blinkitHome.replace("n4 Button \"ADD\"", "n4 Button \"- 1 +\""))))
    }

    @Test fun `cache keeps the most recently used entries`() = runTest {
        val store = MemoryStore()
        val cache = TurnCache(store, capacity = 2, io = Dispatchers.Unconfined)
        cache.put("a", addMilk)
        cache.put("b", addMilk)
        cache.get("a")
        cache.put("c", addMilk)
        assertEquals(addMilk, cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals(2, cache.size())
    }

    @Test fun `a corrupt cache file starts empty and is replaced`() = runTest {
        val dir = Files.createTempDirectory("saathi").toFile()
        val file = File(dir, "turn-cache.json").apply { writeText("{not json") }
        val cache = TurnCache(FileCacheStore(file), io = Dispatchers.Unconfined)
        assertNull(cache.get("a"))
        cache.put("a", addMilk)
        assertEquals(addMilk, TurnCache(FileCacheStore(file), io = Dispatchers.Unconfined).get("a"))
        assertNotEquals("{not json", file.readText())
        dir.deleteRecursively()
    }
}
