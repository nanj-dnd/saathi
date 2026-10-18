package app.saathi

import app.saathi.net.Action
import app.saathi.net.Danger
import app.saathi.net.Guide
import app.saathi.net.PastStep
import app.saathi.net.Status
import app.saathi.net.TurnRequest
import app.saathi.recipes.Common
import app.saathi.recipes.RecipeBook
import app.saathi.recipes.RecipeBundle
import app.saathi.recipes.RecipeEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs the shipped recipes in shared/recipes against realistic screens. */
class RecipeEngineTest {
    companion object {
        // AGP runs unit tests from the module directory (android/app).
        val dir = File("../../shared/recipes")

        fun shipped(): RecipeBundle {
            val files = dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
            return RecipeBundle(
                common = RecipeBook.parseCommon(File(dir, "_common.json").readText()),
                recipes = files.filter { it.name != "_common.json" }.map { RecipeBook.parseRecipe(it.readText()) },
            )
        }

        val book: RecipeBook by lazy { RecipeBook.merge(shipped()) }

        /** Settings home screen captured from a Pixel emulator (worker/fixtures/settings-home.json). */
        val settingsHome: String by lazy {
            Json.parseToJsonElement(File("../../worker/fixtures/settings-home.json").readText())
                .jsonObject["tree"]!!.jsonPrimitive.content
        }

        fun req(
            tree: String,
            app: String = "com.android.settings",
            utterance: String? = null,
            goal: String = "",
            language: String = "hi-IN",
            history: List<PastStep> = emptyList(),
        ) = TurnRequest(
            language = language, utterance = utterance, goal = goal, app = app, playbook = "",
            screenW = 1080, screenH = 2400, tree = tree, secureScreen = false, history = history,
        )

        fun past(g: Guide, app: String = "com.android.settings") = PastStep(g.say, g.label, g.status, app)
    }

    private val engine = RecipeEngine(book)

    private val displayScreen = """
        n1 TextView "Settings" [63,409,408,536]
        n2 LinearLayout "Network & internet Mobile, Wi‑Fi, hotspot" [0,778,1080,1009] click
        n3 LinearLayout "Battery 100%" [0,1009,1080,1240] click
        n4 LinearLayout "Display & touch Dark theme, font size, brightness" [0,1240,1080,1471] click
        n5 TextView "Display & touch" [189,1294,600,1365]
        n6 LinearLayout "Wallpaper & style Colours, themed icons" [0,1471,1080,1702] click
    """.trimIndent()

    private val fontScreen = """
        n1 TextView "Display size and text" [63,409,900,536]
        n2 TextView "Font size" [63,700,500,770]
        n3 ImageView "Make smaller" [40,800,140,900] click
        n4 SeekBar "Font size" [160,800,920,900] click
        n5 ImageView "Make larger" [940,800,1040,900] click
        n6 TextView "Display size" [63,1000,500,1070]
    """.trimIndent()

    private val launcher = """
        n1 TextView "Phone" [40,2000,200,2100] click
        n2 TextView "Messages" [240,2000,400,2100] click
        n3 TextView "WhatsApp" [440,2000,600,2100] click
        n4 TextView "Chrome" [640,2000,800,2100] click
        n5 TextView "Camera" [840,2000,1000,2100] click
    """.trimIndent()

    @Test fun `every shipped recipe is valid`() {
        val bundle = shipped()
        assertTrue(RecipeBook.validateCommon(bundle.common).joinToString(), RecipeBook.validateCommon(bundle.common).isEmpty())
        val problems = bundle.recipes.flatMap(RecipeBook::validate)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertEquals(bundle.recipes.size, book.recipes.size)
        assertEquals(bundle.recipes.size, bundle.recipes.map { it.goal }.toSet().size) // goals identify recipes
    }

    @Test fun `settings home without Display asks to scroll, like the demo walkthrough`() {
        val g = engine.guide(req(settingsHome, utterance = "पढ़ने के लिए अक्षर बड़े करने हैं"))!!
        assertEquals("स्क्रीन पर उंगली रखकर ऊपर की ओर सरकाइए।", g.say)
        assertEquals("none", g.targetKind)
        assertEquals("make the text bigger", g.goal)
    }

    @Test fun `Saathi scrolls a slide-up step itself, keeping the line for doing it by hand`() {
        val g = engine.guide(req(settingsHome, utterance = "पढ़ने के लिए अक्षर बड़े करने हैं"))!!
        assertEquals(Action.SCROLL_DOWN, g.action)
        assertEquals("स्क्रीन पर उंगली रखकर ऊपर की ओर सरकाइए।", g.say)
    }

    @Test fun `Saathi opens the app at the start of a task, never mid-task from another app`() {
        val opener = RecipeEngine(book, canOpen = { it == "com.android.settings" })
        val home = opener.guide(req(launcher, app = "com.miui.home", utterance = "अक्षर बड़े करने हैं"))!!
        assertEquals(Action.OPEN_APP, home.action)
        assertEquals("com.android.settings", home.actionArg)
        assertEquals("'Settings' खोल रही हूँ।", home.say)

        val fromAnotherApp = opener.guide(req(launcher, app = "com.google.android.youtube", utterance = "अक्षर बड़े करने हैं"))!!
        assertEquals(Action.OPEN_APP, fromAnotherApp.action)

        // Halfway through, in some other app: it says where to go rather than pulling the senior away.
        val midTask = opener.guide(
            req(launcher, app = "com.google.android.youtube", goal = "make the text bigger", history = listOf(past(home))),
        )!!
        assertEquals("", midTask.action)
        assertEquals("पहले Home बटन दबाइए, फिर 'Settings' ऐप खोलिए।", midTask.say)

        // An app this phone can't open is pointed at, as before.
        val notInstalled = opener.guide(req(launcher, app = "com.google.android.apps.nexuslauncher", utterance = "WhatsApp पर वीडियो कॉल करना है"))!!
        assertEquals("", notInstalled.action)
        assertEquals("n3", notInstalled.nodeId)
    }

    @Test fun `the whole text size task runs on the phone`() {
        val first = engine.guide(req(displayScreen, utterance = "अक्षर बड़े करने हैं"))!!
        assertEquals("n4", first.nodeId) // the clickable row, not its non-clickable title
        // The row's text includes its summary; the senior only hears the title they need to find.
        assertEquals("'Display & touch' पर दबाइए। अक्षरों का साइज़ इसी में है।", first.say)

        var history = listOf(past(first))
        val larger = engine.guide(req(fontScreen, goal = first.goal, history = history))!!
        assertEquals("n5", larger.nodeId)
        assertEquals(Status.NEXT_STEP, larger.status)

        history = history + past(larger)
        val again = engine.guide(req(fontScreen, goal = first.goal, history = history))!!
        assertEquals("n5", again.nodeId)
        assertTrue(again.say.contains("एक बार और"))

        history = history + past(again)
        val done = engine.guide(req(fontScreen, goal = first.goal, history = history))!!
        assertEquals(Status.DONE, done.status)
        assertEquals("none", done.targetKind)
    }

    @Test fun `each language gets its own line`() {
        for ((lang, said) in mapOf(
            "ta-IN" to "எழுத்துகளை பெரிதாக்க வேண்டும்",
            "te-IN" to "అక్షరాలు పెద్దగా కావాలి",
            "kn-IN" to "ಅಕ್ಷರಗಳು ದೊಡ್ಡದಾಗಿ ಬೇಕು",
            "ml-IN" to "അക്ഷരങ്ങൾ വലുതാക്കണം",
            "hi-IN" to "font bada karna hai",
        )) {
            val g = engine.guide(req(settingsHome, utterance = said, language = lang))
            assertNotNull("$lang: $said", g)
            assertEquals(book.byGoal("make the text bigger")!!.steps.first { it.id == "scroll" }.say[lang], g!!.say)
        }
    }

    @Test fun `negations, other wishes and unknown requests go to the model`() {
        assertNull(engine.guide(req(settingsHome, utterance = "अक्षर बड़े नहीं हो रहे")))
        assertNull(engine.guide(req(settingsHome, utterance = "अक्षर छोटे करने हैं")))
        assertNull(engine.guide(req(settingsHome, utterance = "बिजली का बिल भरना है")))
        assertNull(engine.guide(req(settingsHome, utterance = "   ")))
    }

    @Test fun `something said mid-task that no recipe knows hands over to the model`() {
        val g = engine.guide(req(displayScreen, utterance = "अक्षर बड़े करने हैं"))!!
        assertNull(engine.guide(req(displayScreen, goal = g.goal, utterance = "यह कौन सा बटन है", history = listOf(past(g)))))
    }

    @Test fun `a screen no step knows hands over to the model`() {
        val other = "n1 TextView \"Developer options\" [0,0,500,100]\nn2 Switch \"Use developer options\" [0,200,1080,300] click"
        assertNull(engine.guide(req(other, goal = "make the text bigger")))
    }

    @Test fun `a step repeated too often hands over to the model`() {
        val g = engine.guide(req(settingsHome, goal = "make the text bigger"))!!
        val stuck = List(4) { past(g) }
        assertNull(engine.guide(req(settingsHome, goal = "make the text bigger", history = stuck)))
        assertNotNull(engine.guide(req(settingsHome, goal = "make the text bigger", history = stuck.drop(1))))
    }

    @Test fun `outside the app it points at the home screen icon`() {
        val g = engine.guide(req(launcher, app = "com.google.android.apps.nexuslauncher", utterance = "WhatsApp पर वीडियो कॉल करना है"))!!
        assertEquals("n3", g.nodeId)
        assertEquals("'WhatsApp' ऐप पर दबाइए।", g.say)
        assertEquals("make a WhatsApp video call", g.goal)

        val noIcon = engine.guide(req(launcher, app = "com.miui.home", utterance = "अक्षर बड़े करने हैं"))!!
        assertEquals("none", noIcon.targetKind)
        assertTrue(noIcon.say.contains("Settings"))

        val elsewhere = engine.guide(req(launcher, app = "com.google.android.youtube", utterance = "अक्षर बड़े करने हैं"))!!
        assertEquals("पहले Home बटन दबाइए, फिर 'Settings' ऐप खोलिए।", elsewhere.say)
        assertEquals("none", elsewhere.targetKind) // "Settings" inside another app is not the Settings app
    }

    @Test fun `in-app triggers only count inside the app`() {
        val chat = "n1 TextView \"Ramesh\" [200,100,600,180]\nn2 ImageButton \"Video call\" [800,100,900,180] click\nn3 ImageButton \"Voice call\" [920,100,1020,180] click\nn4 ImageButton \"Attach\" [700,2200,780,2280] click\nn5 EditText \"Message\" [100,2200,680,2280] click edit"
        val inside = engine.guide(req(chat, app = "com.whatsapp", utterance = "वीडियो कॉल करना है"))!!
        assertEquals("n2", inside.nodeId)
        assertNull(engine.guide(req(launcher, app = "com.google.android.apps.nexuslauncher", utterance = "वीडियो कॉल करना है")))

        val voice = engine.guide(req(chat, app = "com.whatsapp", utterance = "बेटे को कॉल करना है"))!!
        assertEquals("n3", voice.nodeId)
    }

    @Test fun `whole-label steps ignore the same word inside a message`() {
        // "Calling" in a chat preview must not count as an ongoing call.
        val list = "n1 TextView \"Chats\" [0,100,300,180]\nn2 LinearLayout \"Ramesh Calling you in 5 min\" [0,300,1080,450] click\nn3 TextView \"Calling you in 5 min\" [200,380,900,440]"
        val g = engine.guide(req(list, app = "com.whatsapp", utterance = "whatsapp पर वीडियो कॉल"))!!
        assertEquals(Status.NEXT_STEP, g.status)
        assertTrue(g.say.contains("नाम"))
    }

    @Test fun `blinkit payment is left to the senior and never pointed at`() {
        val cart = "n1 TextView \"Bill details\" [0,800,1080,880]\nn2 TextView \"Delivering to Home\" [0,300,1080,380]\nn3 Button \"Proceed To Pay\" [0,2200,1080,2337] click"
        val g = engine.guide(req(cart, app = "com.grofers.customerapp", utterance = "ब्लिंकिट से दूध मंगाना है"))!!
        assertEquals(Status.USER_MUST_DO, g.status)
        assertEquals(Danger.SENSITIVE, g.danger)
        assertEquals("none", g.targetKind)
    }

    @Test fun `recipes that point at payment or PIN buttons are rejected`() {
        val bad = book.recipes.first().let { r ->
            r.copy(id = "bad", steps = listOf(r.steps.first().copy(id = "x", target = listOf("Pay now"), after = null)))
        }
        assertTrue(RecipeBook.validate(bad).any { "for the senior alone" in it })
        assertTrue(RecipeBook.merge(RecipeBundle(book.common, listOf(bad))).recipes.isEmpty())
    }

    @Test fun `a newer downloaded recipe replaces the bundled one, a broken one does not`() {
        val r = book.byGoal("make the text bigger")!!
        val newer = r.copy(version = r.version + 1, steps = r.steps.map { s -> s.copy(say = s.say.mapValues { "v2 " + it.value }) })
        val broken = r.copy(version = r.version + 5, steps = listOf(r.steps.first().copy(say = mapOf("hi-IN" to "only hindi"))))
        val merged = RecipeBook.merge(shipped(), RecipeBundle(Common(), listOf(newer, broken)))
        assertEquals(r.version + 1, merged.byGoal(r.goal)!!.version)
    }

    @Test fun `a negation must be a whole word`() {
        // "not " used to match inside "notifications", sending every such request to the model.
        assertEquals("tip-notifications", engine.match("show me my notifications", "com.google.android.apps.nexuslauncher")?.id)
        assertEquals("settings-dnd-off", engine.match("switch off do not disturb", "com.google.android.apps.nexuslauncher")?.id)
        assertEquals(null, engine.match("my notifications are not coming", "com.google.android.apps.nexuslauncher")?.id)
    }

    @Test fun `an unreadable screen is left to the model, not taken as outside the app`() {
        // Android 14+ hides "data sensitive" pages (Wi-Fi) from the tree, so the app comes back blank.
        val g = engine.guide(req("", app = "", utterance = "please turn on the wifi", language = "en-IN"))
        assertEquals(null, g)
    }

    @Test fun `typed debug requests start a recipe from the goal`() {
        val g = RecipeEngine(book).guide(req(settingsHome, goal = "how do I make the text bigger"))
        assertEquals("make the text bigger", g!!.goal)
    }

    @Test fun `every example phrase picks its own recipe and no other`() {
        val misses = mutableListOf<String>()
        for (r in book.recipes) {
            for (lang in RecipeBook.LANGUAGES) {
                val examples = r.examples[lang].orEmpty()
                if (examples.isEmpty()) misses += "${r.id}: no $lang example"
                for (ex in examples) {
                    val got = engine.match(ex, "com.google.android.apps.nexuslauncher")
                    if (got?.id != r.id) misses += "${r.id} [$lang] \"$ex\" -> ${got?.id ?: "model"}"
                }
            }
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    @Test fun `requests no script should answer reach the model`() {
        val forModel = listOf(
            // Money and bills: always the model, which knows the safety rules for payments.
            "बिजली का बिल भरना है", "बेटे को पैसे भेजने हैं", "UPI PIN बदलना है", "बिजली कटने का मैसेज आया है",
            "மின்சார கட்டணம் செலுத்த வேண்டும்", "కరెంట్ బిల్లు కట్టాలి", "ವಿದ್ಯುತ್ ಬಿಲ್ ಕಟ್ಟಬೇಕು", "കറന്റ് ബിൽ അടയ്ക്കണം",
            // Similar words, different wish.
            "WhatsApp पर वीडियो भेजना है", "Blinkit का मेरा ऑर्डर कहाँ है", "flight ticket book karna hai",
            "मेरी आवाज़ रिकॉर्ड करनी है", "अक्षर छोटे करने हैं",
            "वाईफाई का पासवर्ड बदलना है", "बैटरी जल्दी खत्म हो जाती है", "अस्पताल का रास्ता बताओ",
            // Problems and questions.
            "वीडियो कॉल में आवाज़ नहीं आ रही", "फ़ोन क्यों गरम हो रहा है", "இன்டர்நெட் வேலை செய்யவில்லை",
            "आज मौसम कैसा है", "फ़ोन अपने आप बंद हो जाता है",
            "फोटो जोड़नी है", "इसका मतलब क्या है", "पोते को एक ऐप इंस्टॉल करना है", "एक बात पूछनी है", "मेरी स्क्रीन पर काला धब्बा है",
            // Words that contain a trigger word inside them.
            "अलार्म के लिए गाना लगाना नहीं",
            // Close to a scam warning or a settings script, but a different wish.
            "पुलिस में शिकायत करनी है", "ग्रुप में बेटे को जोड़ना है", "केरल लॉटरी का रिज़ल्ट देखना है",
            "बेटे को स्क्रीन शेयर करके दिखाना है", "फ़ोन में नेटवर्क नहीं मिल रहा", "पति के फ़ोन में पैसे भेजने हैं",
            "क्रेडिट स्कोर देखना है", "ग्रुप से ऐडमिन हटाना है", "फ़ोन से पैसे चोरी हो गए",
            // English problems and negations.
            "do not disturb is not turning off", "why is my phone so slow", "WhatsApp is not working",
        )
        val wrong = forModel.mapNotNull { said ->
            engine.match(said, "com.google.android.apps.nexuslauncher")?.let { "\"$said\" -> ${it.id}" }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun `similar requests pick the more specific script`() {
        val expected = mapOf(
            "अक्षर बड़े करने हैं" to "settings-text-size",
            "WhatsApp के अक्षर बड़े करने हैं" to "whatsapp-font-size",
            "बैटरी कितनी बची है" to "tip-battery-level",
            "बैटरी का प्रतिशत ऊपर दिखाना है" to "settings-battery-percent",
            "फ़ोन बंद करना है" to "tip-power-off",
            "WhatsApp कॉल काटनी है" to "tip-end-call",
            "फ़ोन का सॉफ्टवेयर अपडेट करना है" to "settings-software-update",
            "WhatsApp अपडेट करना है" to "playstore-update",
            "यूट्यूब पर भजन सुनना है" to "youtube-watch",
            "यूट्यूब म्यूजिक पर भजन सुनने हैं" to "youtube-music-play",
            "अमेज़न प्राइम पर फ़िल्म देखनी है" to "primevideo-watch",
            "अमेज़न से चप्पल मंगानी है" to "amazon-order",
            "SMS पढ़कर सुनाओ" to "read-screen",
            "इमरजेंसी है, एम्बुलेंस चाहिए" to "tip-emergency",
            "इमरजेंसी कॉन्टैक्ट जोड़ना है" to "settings-emergency-contacts",
            "बुज़ुर्गों की हेल्पलाइन का नंबर क्या है" to "tip-senior-helpline",
            "कस्टमर केयर का नंबर चाहिए" to "safety-customer-care",
            "अलार्म की घंटी सेट करनी है" to "clock-alarm",
        )
        val wrong = expected.mapNotNull { (said, id) ->
            val got = engine.match(said, "com.google.android.apps.nexuslauncher")?.id ?: "model"
            if (got != id) "\"$said\" -> $got, want $id" else null
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
        assertEquals("read-screen", engine.match("नीचे लिखा हुआ पढ़कर सुनाओ", "com.google.android.youtube")?.id)
    }

    @Test fun `WhatsApp safety settings are walked through from the chat list`() {
        val chats = """
            n1 TextView "WhatsApp" [40,120,500,200]
            n2 ImageButton "More options" [980,120,1060,200] click
            n3 TextView "Chats" [40,2250,260,2330] click
            n4 LinearLayout "Ramesh Kal milte hain" [0,400,1080,560] click
        """.trimIndent()
        val said = "अनजान नंबर से WhatsApp कॉल आते हैं, उन्हें चुप करना है"
        val steps = mutableListOf<Guide>()
        fun next(tree: String, utterance: String? = null): Guide =
            engine.guide(req(tree, app = "com.whatsapp", utterance = utterance, goal = steps.firstOrNull()?.goal.orEmpty(),
                history = steps.map { past(it, "com.whatsapp") }))!!.also { steps += it }

        assertEquals("n2", next(chats, said).nodeId)
        val menu = "n1 TextView \"New group\" [500,150,1060,250] click\nn2 TextView \"Linked devices\" [500,250,1060,350] click\nn3 TextView \"Settings\" [500,350,1060,450] click"
        assertEquals("n3", next(menu).nodeId)
        // Real rows carry a summary under the title, so their label is "Title Summary".
        val settings = "n1 LinearLayout \"Account Security notifications, change number\" [0,400,1080,520] click\nn2 LinearLayout \"Privacy Block contacts, disappearing messages\" [0,520,1080,640] click\nn3 LinearLayout \"Chats Theme, wallpapers, chat history\" [0,640,1080,760] click\nn4 LinearLayout \"Storage and data Network usage, auto-download\" [0,760,1080,880] click"
        assertEquals("n2", next(settings).nodeId)
        val privacy = "n1 LinearLayout \"Last seen and online Nobody\" [0,400,1080,520] click\nn2 LinearLayout \"Read receipts\" [0,520,1080,640] click\nn3 LinearLayout \"Calls Silence unknown callers\" [0,640,1080,760] click"
        assertEquals("n3", next(privacy).nodeId)
        val calls = "n1 LinearLayout \"Silence unknown callers\" [0,400,900,560] click\nn2 Switch \"\" [920,440,1040,520] click unchecked"
        assertEquals("n2", next(calls).nodeId)
        val done = next(calls.replace("unchecked", "checked"))
        assertEquals(Status.DONE, done.status)

        // Asked from inside a chat: first back out to the list.
        val inChat = "n1 ImageButton \"Navigate up\" [0,120,100,200] click\nn2 ImageButton \"Attach\" [700,2250,800,2330] click"
        steps.clear()
        assertEquals("n1", next(inChat, "WhatsApp में टू स्टेप वेरिफिकेशन लगाना है").nodeId)
    }

    @Test fun `spoken tips work in any app and finish at once`() {
        val g = engine.guide(req(launcher, app = "com.google.android.youtube", utterance = "फ़ोन की आवाज़ बढ़ानी है"))!!
        assertEquals(Status.DONE, g.status)
        assertEquals("none", g.targetKind)
        assertEquals("make the phone louder", g.goal)
    }

    @Test fun `scam warnings speak even when the question has a negation`() {
        val g = engine.guide(req(launcher, app = "com.whatsapp", utterance = "बैंक वाला OTP मांग रहा है, बताऊं या नहीं?"))!!
        assertEquals(Danger.SCAM, g.danger)
        assertEquals("none", g.targetKind)
        assertTrue(g.say.startsWith("रुकिए"))
    }

    @Test fun `switches are read from the row or the control beside it`() {
        val off = """
            n1 TextView "Internet" [63,300,900,400]
            n2 LinearLayout "Wi‑Fi" [0,500,800,640] click
            n3 Switch "" [880,530,1040,610] click unchecked
            n4 TextView "Network preferences" [63,800,900,900]
        """.trimIndent()
        val g = engine.guide(req(off, utterance = "वाईफाई चालू करना है"))!!
        assertEquals("n3", g.nodeId) // the switch itself, not the row
        assertEquals("'Wi‑Fi' के सामने वाला बटन दबाइए, वह चालू हो जाएगा।", g.say)

        val on = off.replace("unchecked", "checked")
        assertEquals(Status.DONE, engine.guide(req(on, goal = g.goal, history = listOf(past(g))))!!.status)

        // Airplane mode is the other way round: the switch should end up off.
        val air = "n1 Switch \"Airplane mode\" [0,500,1080,640] click checked\nn2 TextView \"SIMs\" [0,700,1080,800]"
        val a = engine.guide(req(air, utterance = "फ्लाइट मोड हटाना है"))!!
        assertEquals("n1", a.nodeId)
        assertEquals(Status.DONE, engine.guide(req(air.replace("checked", "unchecked"), goal = a.goal, history = listOf(past(a))))!!.status)
    }

    @Test fun `a request inside an app prefers that app's recipe`() {
        assertEquals("whatsapp-voice-call", engine.match("बेटे को कॉल करना है", "com.whatsapp")?.id)
        assertEquals("phone-call", engine.match("बेटे को कॉल करना है", "com.google.android.dialer")?.id)
        assertEquals("phone-call", engine.match("बेटे को कॉल करना है", "com.miui.home")?.id)
        assertEquals("tip-answer-call", engine.match("कॉल कैसे उठाते हैं", "com.whatsapp")?.id)
    }

    @Test fun `zero-width joiners and dash variants do not break matching`() {
        assertEquals("tip-screenshot", engine.match("స్క్రీన్షాట్ ఎలా తీయాలి", "x")?.id) // no ZWNJ
        assertEquals("tip-screenshot", engine.match("స్క్రీన్\u200Cషాట్ ఎలా తీయాలి", "x")?.id)
        assertEquals("settings-wifi", engine.match("Wi‑Fi on karna hai", "x")?.id) // non-breaking hyphen
    }

    @Test fun `a recipe can follow the senior from the gallery through the share sheet into WhatsApp`() {
        val photo = "n1 ImageView \"Photo\" [0,0,1080,1800]\nn2 ImageButton \"Share\" [100,2000,200,2100] click\nn3 ImageButton \"Edit\" [300,2000,400,2100] click"
        val share = engine.guide(req(photo, app = "com.google.android.apps.photos", utterance = "गैलरी से फोटो भेजनी है"))!!
        assertEquals("n2", share.nodeId)

        val sheet = "n1 TextView \"Share with\" [0,1200,1080,1300]\nn2 LinearLayout \"WhatsApp\" [40,1400,260,1600] click\nn3 LinearLayout \"Gmail\" [300,1400,520,1600] click"
        val wa = engine.guide(req(sheet, app = "com.android.intentresolver", goal = share.goal, history = listOf(past(share))))!!
        assertEquals("n2", wa.nodeId)

        val contacts = "n1 TextView \"Send to…\" [0,100,1080,200]\nn2 LinearLayout \"Ramesh\" [0,300,1080,450] click"
        val to = engine.guide(req(contacts, app = "com.whatsapp", goal = share.goal, history = listOf(past(share), past(wa, "com.android.intentresolver"))))!!
        assertTrue(to.say.contains("नाम"))

        // Another app altogether still gets "open the Gallery".
        val lost = engine.guide(req(contacts, app = "com.google.android.youtube", goal = share.goal, history = listOf(past(share))))!!
        assertTrue(lost.say.contains("Gallery"))
    }

    @Test fun `trigger words must start a word`() {
        assertTrue(RecipeEngine.contains("गाना लगा दो", "गाना"))
        assertTrue(!RecipeEngine.contains("अलार्म लगाना है", "गाना"))
        assertTrue(RecipeEngine.contains("சத்தத்தை கூட்ட", "சத்த"))
        assertTrue(!RecipeEngine.contains("வெளிச்சத்தை கூட்ட", "சத்த"))
        // Negations are word endings in Tamil and Malayalam, so "unless" words match anywhere.
        assertTrue(RecipeEngine.contains("வேலை செய்யவில்லை", "வில்லை", wordStart = false))
    }

    @Test fun `the screen can be read aloud without leaving the phone`() {
        val chat = """
            n1 TextView "Ramesh" [200,100,600,180]
            n2 ImageButton "Video call" [800,100,900,180] click
            n3 TextView "Papa, I will reach by 7 pm." [40,400,900,480]
            n4 TextView "Papa, I will reach by 7 pm." [40,500,900,580]
            n5 EditText "Message" [100,2200,680,2280] click edit
        """.trimIndent()
        val g = engine.guide(req(chat, app = "com.whatsapp", utterance = "ये मैसेज पढ़ दो"))!!
        assertEquals("स्क्रीन पर लिखा है: Ramesh. Papa, I will reach by 7 pm.", g.say) // buttons and repeats skipped
        assertEquals(Status.DONE, g.status)
        val blank = engine.guide(req("n1 ImageView \"\" [0,0,1080,2400] click", app = "com.android.chrome", utterance = "पढ़कर सुनाओ"))!!
        assertTrue(blank.say.contains("कुछ लिखा नहीं"))
    }

    @Test fun `ambiguous requests go to the model`() {
        // Fits both WhatsApp call recipes equally once "video" is ignored by neither: should not guess.
        val tie = RecipeBook(book.common, book.recipes + book.byGoal("make a WhatsApp voice call")!!.copy(id = "dup", goal = "dup"))
        assertNull(RecipeEngine(tie).guide(req(launcher, app = "com.miui.home", utterance = "whatsapp पर कॉल करना है")))
    }
}
