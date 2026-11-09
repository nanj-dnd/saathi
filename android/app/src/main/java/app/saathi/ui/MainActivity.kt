package app.saathi.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.saathi.BuildConfig
import app.saathi.LanguageList
import app.saathi.Languages
import app.saathi.Prefs
import app.saathi.R
import app.saathi.design.BoardDrawable
import app.saathi.design.Buddy
import app.saathi.design.Glyph
import app.saathi.design.Glyphs
import app.saathi.design.SaathiType
import app.saathi.net.WorkerClient
import app.saathi.service.BuddyAccessibilityService
import app.saathi.voice.CloudVoice
import app.saathi.voice.Speaker
import app.saathi.voice.VoiceSetup
import java.util.Locale

/**
 * Setup, one step per screen: language, microphone, turning Bob on, the voice, ready. Each screen
 * is a white page under a yellow signboard strip with one black board to press at the bottom.
 * Written so either the senior or a family member can follow it, in the senior's chosen language.
 * One theme only: the page is the same whatever the phone's dark-mode setting.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Buddy.YELLOW, Buddy.INK),
            navigationBarStyle = SystemBarStyle.light(Buddy.WHITE, Buddy.INK),
        )
        val prefs = Prefs(this)
        setContent { BobTheme { Setup(prefs, onFinish = ::finish) } }
    }
}

private val Yellow = Color(Buddy.YELLOW)
private val Ink = Color(Buddy.INK)
private val Paper = Color(Buddy.WHITE)
private val Track = Color(0xFFD9D9D9)
private val Done = Color(Buddy.LIGHT.done)

/** Paytone One for Latin board lettering; every other script uses the phone's face at extra-bold. */
private val Sign = FontFamily(Font(R.font.paytone_one))

private fun signFamily(text: String) = if (SaathiType.isLatin(text)) Sign else FontFamily.Default

private fun signWeight(text: String) = if (SaathiType.isLatin(text)) FontWeight.Normal else FontWeight.ExtraBold

@Composable
private fun BobTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, onPrimary = Yellow, background = Paper, surface = Paper, onSurface = Ink)) {
        Surface(Modifier.fillMaxSize(), color = Paper) { content() }
    }
}

private const val STEPS = 5

@Composable
private fun Setup(prefs: Prefs, onFinish: () -> Unit) {
    val base = LocalContext.current
    var language by remember { mutableStateOf(prefs.language) }
    val res = remember(language) { Languages.context(base, language) }
    fun s(id: Int) = res.getString(id)
    /** Step titles are stored numbered ("2. Allow the microphone"); the strip's step count carries the number now. */
    fun title(id: Int) = s(id).substringAfter(". ", s(id))

    var micGranted by remember { mutableStateOf(hasMic(base)) }
    var serviceOn by remember { mutableStateOf(BuddyAccessibilityService.isEnabled(base)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                micGranted = hasMic(base)
                serviceOn = BuddyAccessibilityService.isEnabled(base)
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        micGranted = hasMic(base)
    }
    val voice = remember {
        val worker = WorkerClient(BuildConfig.WORKER_URL, prefs)
        VoiceSetup(base, CloudVoice(base, worker::speak))
    }
    DisposableEffect(voice) { onDispose { voice.shutdown() } }
    var rate by remember { mutableStateOf(prefs.speechRate) }
    var voiceStatus by remember(language) { mutableStateOf<Speaker.VoiceStatus?>(null) }
    var offlineStarted by remember(language) { mutableStateOf(false) }

    // A first run starts at the language; once everything is granted, setup reopens on Ready.
    var page by rememberSaveable { mutableIntStateOf(if (!micGranted) 0 else if (!serviceOn) 2 else STEPS - 1) }
    // Granting the mic or turning Bob on moves straight to the next step.
    LaunchedEffect(micGranted) { if (micGranted && page == 1) page = 2 }
    LaunchedEffect(serviceOn) { if (serviceOn && page == 2) page = 3 }
    BackHandler(enabled = page > 0) { page-- }

    val direction = if (Languages.isRtl(language)) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column(Modifier.fillMaxSize().background(Paper).safeDrawingPadding()) {
            Strip(res.getString(R.string.step_count, page + 1, STEPS))
            Progress(page, Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp))

            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320)) { it / 6 * dir } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(220)) { -it / 6 * dir } + fadeOut(tween(220)))
                },
                label = "step",
            ) { step ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (step) {
                        0 -> {
                            Title(title(R.string.step_language))
                            Body(s(R.string.onb_subtitle))
                            LanguagePicker(language, ::s) {
                                language = it
                                prefs.language = it
                            }
                        }
                        1 -> {
                            Title(title(R.string.step_mic))
                            Body(s(R.string.mic_body))
                            // What the senior will see while Bob listens: the Help board, listening.
                            Frame { HelpBoard(s(R.string.bubble_listening), listening = true) }
                            if (micGranted) DoneRow(s(R.string.done))
                        }
                        2 -> {
                            Title(title(R.string.step_access))
                            if (serviceOn) {
                                Body(s(R.string.access_summary))
                                DoneRow(s(R.string.access_on))
                            } else {
                                // The prominent disclosure, in full, above the button that agrees to it.
                                Txt(s(R.string.access_summary), 20, FontWeight.SemiBold, Ink, lineHeight = 28)
                                Column(
                                    Modifier.fillMaxWidth().board(Paper, Ink, rivets = false, outer = true).padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Txt(s(R.string.disclosure), 16, FontWeight.Normal, Ink, lineHeight = 24)
                                    Txt(s(R.string.access_hint), 16, FontWeight.Medium, Ink, lineHeight = 24)
                                    Txt(s(R.string.access_restricted), 16, FontWeight.Normal, Ink, lineHeight = 24)
                                }
                                TextAction(s(R.string.btn_app_info)) {
                                    base.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${base.packageName}"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                        }
                        3 -> {
                            Title(title(R.string.step_voice))
                            Segmented(
                                listOf(s(R.string.speed_slow) to Speaker.SLOW_RATE, s(R.string.speed_normal) to Speaker.DEFAULT_RATE),
                                selected = rate,
                            ) {
                                rate = it
                                prefs.speechRate = it
                            }
                            QuietBoard(s(R.string.voice_test), Glyph.SPEAKER) {
                                voice.test(language, rate, s(R.string.voice_sample)) { voiceStatus = it }
                            }
                            when (voiceStatus) {
                                Speaker.VoiceStatus.OK -> DoneRow(s(R.string.voice_ok))
                                Speaker.VoiceStatus.MISSING, Speaker.VoiceStatus.NO_ENGINE -> {
                                    Notice(s(R.string.voice_missing))
                                    QuietBoard(s(R.string.voice_install), null) { base.startActivity(voice.installVoiceIntent()) }
                                }
                                null -> {}
                            }
                            if (voice.canDownloadOfflineSpeech) {
                                if (offlineStarted) Body(s(R.string.offline_started), size = 16)
                                else TextAction(s(R.string.offline_download)) { offlineStarted = voice.downloadOfflineSpeech(language) }
                            }
                        }
                        else -> {
                            Title(title(R.string.step_ready))
                            // The same Help board the senior will see over every app, so setup can point it out.
                            Frame { HelpBoard(s(R.string.bubble_help), listening = false) }
                            Body(s(R.string.ready_body), size = 19)
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (page > 0) TextAction(s(R.string.btn_back)) { page-- }
                when (page) {
                    1 -> if (micGranted) PrimaryBoard(s(R.string.btn_continue)) { page = 2 }
                    else PrimaryBoard(s(R.string.btn_allow)) {
                        val perms = buildList {
                            add(Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        askPermissions.launch(perms.toTypedArray())
                    }
                    2 -> if (serviceOn) PrimaryBoard(s(R.string.btn_continue)) { page = 3 }
                    else PrimaryBoard(s(R.string.btn_open_settings)) {
                        base.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    STEPS - 1 -> PrimaryBoard(s(R.string.done), onClick = onFinish)
                    else -> PrimaryBoard(s(R.string.btn_continue)) { page++ }
                }
            }
        }
    }
}

/** A signboard behind any composable: the same drawing as the overlay's boards. */
private fun Modifier.board(fill: Color, line: Color, rivets: Boolean = true, outer: Boolean = false): Modifier = composed {
    val context = LocalContext.current
    val drawable = remember(fill, line, rivets, outer) { BoardDrawable(context, fill.toArgb(), line.toArgb(), rivets, outer) }
    drawBehind {
        drawIntoCanvas {
            drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
            drawable.draw(it.nativeCanvas)
        }
    }
}

/** The yellow signboard strip across the top: Bob's face, the name, the step count. */
@Composable
private fun Strip(stepCount: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            .heightIn(min = 68.dp)
            .board(Yellow, Ink)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.bob_mark), contentDescription = null,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text("bob", style = TextStyle(fontFamily = Sign, fontSize = 26.sp, color = Ink))
        Spacer(Modifier.weight(1f))
        Text(
            stepCount,
            style = TextStyle(fontFamily = signFamily(stepCount), fontWeight = signWeight(stepCount), fontSize = 16.sp, color = Ink, letterSpacing = 0.5.sp),
        )
    }
}

/** Five short bars: black up to and including the current step. */
@Composable
private fun Progress(page: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(STEPS) { i ->
            Box(Modifier.weight(1f).height(6.dp).background(if (i <= page) Ink else Track, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        modifier = Modifier.semantics { heading() },
        style = TextStyle(
            fontFamily = signFamily(text), fontWeight = signWeight(text), fontSize = 31.sp, lineHeight = 40.sp, color = Ink,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
    )
}

@Composable
private fun Body(text: String, size: Int = 18) {
    Txt(text, size, FontWeight.Normal, Ink, lineHeight = (size * 1.45f).toInt())
}

/**
 * Every language Bob speaks: a search box, then the phone's own language, then India, then the
 * world, each name in its own script with its English name beneath when that differs.
 */
@Composable
private fun LanguagePicker(selected: String, s: (Int) -> String, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val phone = remember { Languages.matching(Locale.getDefault()) }
    val q = query.trim().lowercase()
    fun hit(l: app.saathi.Language) = q.isEmpty() || l.native.lowercase().contains(q) || l.english.lowercase().contains(q) || l.tag.lowercase().startsWith(q)
    val all = LanguageList.ALL.filter(::hit)
    val sections = buildList {
        if (q.isEmpty() && phone != null) add(s(R.string.lang_phone) to all.filter { it.tag == phone })
        add(s(R.string.lang_india) to all.filter { it.india && (q.isNotEmpty() || it.tag != phone) })
        add(s(R.string.lang_world) to all.filter { !it.india && (q.isNotEmpty() || it.tag != phone) })
    }.filter { it.second.isNotEmpty() }

    SearchField(query, s(R.string.lang_search)) { query = it }
    if (sections.isEmpty()) Body(s(R.string.lang_none))
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        sections.forEach { (label, langs) ->
            // The keyline sits outside the rows (2dp inset), so it reads down both sides of the list.
            Column(Modifier.fillMaxWidth().board(Paper, Ink, rivets = false, outer = true).padding(2.dp)) {
                SectionLabel(label)
                langs.forEachIndexed { i, l ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Ink))
                    LanguageRow(l, l.tag == selected) { onPick(l.tag) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        Modifier.fillMaxWidth().background(Ink, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(Yellow, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            text.uppercase(),
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontFamily = signFamily(text), fontWeight = signWeight(text), fontSize = 16.sp, color = Yellow, letterSpacing = 0.6.sp),
        )
    }
}

@Composable
private fun LanguageRow(l: app.saathi.Language, selected: Boolean, onClick: () -> Unit) {
    val fg = if (selected) Yellow else Ink
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(if (selected) Ink else Paper)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(l.native, 23, FontWeight.SemiBold, fg)
            if (l.english != l.native) Txt(l.english, 15, FontWeight.Normal, fg)
        }
        // The tick marks the choice by shape as well as by the black fill.
        if (selected) GlyphIcon(Glyph.CHECK, Yellow, 28.dp)
    }
}

@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .board(Paper, Ink, rivets = false, outer = true)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A drawn magnifier in the board's one stroke.
        Canvas(Modifier.size(24.dp)) {
            val w = 2.6.dp.toPx()
            drawCircle(Ink, radius = size.width * 0.33f, center = Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(w))
            drawLine(Ink, Offset(size.width * 0.66f, size.height * 0.66f), Offset(size.width * 0.92f, size.height * 0.92f), w, cap = StrokeCap.Round)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            val style = TextStyle(fontSize = 19.sp, color = Ink, fontWeight = FontWeight.SemiBold)
            if (value.isEmpty()) Txt(hint, 19, FontWeight.Normal, Ink)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true, textStyle = style,
                cursorBrush = SolidColor(Ink),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
            )
        }
    }
}

/** A two-way choice as two boards; the chosen one floods black and carries a tick. */
@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onPick: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { (label, value) ->
            val on = value == selected
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .then(if (on) Modifier.board(Ink, Ink, rivets = false) else Modifier.board(Paper, Ink, rivets = false, outer = true))
                    .selectable(selected = on, role = Role.RadioButton) { onPick(value) }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) {
                    GlyphIcon(Glyph.CHECK, Yellow, 20.dp)
                    Spacer(Modifier.width(8.dp))
                }
                SignText(label, 19, if (on) Yellow else Ink)
            }
        }
    }
}

/** The one thing to press: a black board with yellow lettering and rivets. */
@Composable
private fun PrimaryBoard(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .shadow(3.dp, RoundedCornerShape(8.dp))
            .board(Ink, Ink)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { SignText(label, 21, Yellow, center = true) }
}

@Composable
private fun QuietBoard(label: String, glyph: Glyph?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .board(Paper, Ink, rivets = false, outer = true)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, Ink, 24.dp)
            Spacer(Modifier.width(10.dp))
        }
        SignText(label, 19, Ink, center = true)
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            // Room for the ि matra that overhangs a word's start.
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, textDecoration = TextDecoration.Underline,
                lineHeight = 26.sp, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            ),
        )
    }
}

@Composable
private fun DoneRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).background(Done, CircleShape), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.CHECK, Color.White, 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Txt(label, 19, FontWeight.SemiBold, Ink, Modifier.weight(1f, fill = false))
    }
}

/** Something that needs doing: a yellow board with a warning glyph, not colour alone. */
@Composable
private fun Notice(text: String) {
    Row(
        Modifier.fillMaxWidth().board(Yellow, Ink, rivets = false).padding(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        GlyphIcon(Glyph.WARNING, Ink, 24.dp)
        Spacer(Modifier.width(12.dp))
        Txt(text, 17, FontWeight.Medium, Ink, lineHeight = 25)
    }
}

/** A keylined white panel that stages the Help board as it will appear over other apps. */
@Composable
private fun Frame(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(176.dp).board(Color(0xFFF4F4F4), Ink, rivets = false, outer = true),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Bob as he floats over other apps: a square yellow board with the face, or voice bars while listening. */
@Composable
private fun HelpBoard(label: String, listening: Boolean) {
    Box(
        Modifier
            .size(68.dp)
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .board(Yellow, Ink)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (listening) {
            Canvas(Modifier.size(30.dp)) {
                val step = size.width / 4
                listOf(0.5f, 0.9f, 0.65f, 0.8f).forEachIndexed { k, h ->
                    val x = step * (k + 0.5f)
                    drawLine(Ink, Offset(x, size.height * (1 - h) / 2), Offset(x, size.height * (1 + h) / 2), 4.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        } else {
            Image(painterResource(R.drawable.bob_mark), contentDescription = null, modifier = Modifier.size(44.dp).offset(y = 1.5.dp))
        }
        // Keyline and rivets over the face, as on the overlay.
        Box(Modifier.matchParentSize().board(Color.Transparent, Ink))
    }
}

@Composable
private fun GlyphIcon(glyph: Glyph, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        drawIntoCanvas { Glyphs.draw(it.nativeCanvas, glyph, RectF(0f, 0f, this.size.width, this.size.height), color.toArgb()) }
    }
}

/** Board lettering: Paytone One, with the phone's faces for scripts it lacks. */
@Composable
private fun SignText(text: String, size: Int, color: Color, center: Boolean = false) {
    Text(
        text,
        style = TextStyle(
            fontFamily = signFamily(text), fontWeight = signWeight(text), fontSize = size.sp, color = color,
            textAlign = if (center) TextAlign.Center else TextAlign.Unspecified,
            lineHeight = (size * 1.4f).sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
    )
}

@Composable
private fun Txt(
    text: String,
    size: Int,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    lineHeight: Int? = null,
    center: Boolean = false,
) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(
            textAlign = if (center) TextAlign.Center else TextAlign.Unspecified,
            fontSize = size.sp,
            fontWeight = weight,
            color = color,
            // Room above the first line for Devanagari's ि and ी hooks and the Dravidian vowel signs,
            // which a tight line height clips.
            lineHeight = (lineHeight ?: (size * 1.4f).toInt()).sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
    )
}

private fun hasMic(context: Context) =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
