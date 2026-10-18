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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.saathi.Languages
import app.saathi.Prefs
import app.saathi.R
import app.saathi.design.Buddy
import app.saathi.design.Glyph
import app.saathi.design.Glyphs
import app.saathi.service.BuddyAccessibilityService
import app.saathi.BuildConfig
import app.saathi.net.WorkerClient
import app.saathi.voice.CloudVoice
import app.saathi.voice.Speaker
import app.saathi.voice.VoiceSetup

/**
 * Setup, one step per screen: language, microphone, turning Saathi on, the voice, ready. Each
 * screen has one obvious button at the bottom. Written so either the senior or a family member can
 * follow it, in the senior's chosen language.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = Buddy.of(this).dark
        val ground = if (dark) GROUND_DARK else GROUND_LIGHT
        enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(ground) else SystemBarStyle.light(ground, INK_LIGHT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(ground) else SystemBarStyle.light(ground, INK_LIGHT),
        )
        val prefs = Prefs(this)
        setContent { SaathiTheme { Setup(prefs, onFinish = ::finish) } }
    }
}

private const val GROUND_LIGHT = 0xFFFFFFFF.toInt()
private const val GROUND_DARK = 0xFF0F1114.toInt()
private const val INK_LIGHT = 0xFF101215.toInt()

/** Compose-side colours, sharing the overlay's [Buddy] blue and badge colours. */
private class Tones(val b: Buddy) {
    val ground = Color(if (b.dark) GROUND_DARK else GROUND_LIGHT)
    val ink = Color(if (b.dark) 0xFFF3F4F6.toInt() else INK_LIGHT)
    val soft = Color(if (b.dark) 0xFFA4AAB3.toInt() else 0xFF5B616B.toInt())
    /** Tiles, the segmented track and quiet buttons. */
    val tile = Color(if (b.dark) 0xFF1C1F24.toInt() else 0xFFF2F3F5.toInt())
    /** The raised segment inside the track. */
    val raised = Color(if (b.dark) 0xFF33373F.toInt() else 0xFFFFFFFF.toInt())
    val track = Color(if (b.dark) 0xFF2A2E35.toInt() else 0xFFE3E6EA.toInt())
    val accent = Color(b.accent)
    val accentLine = Color(b.accentLine)
    val onAccent = Color(b.onAccent)
    val done = Color(b.done)
    val caution = Color(b.caution)
}

private val LocalTones = staticCompositionLocalOf<Tones> { error("no tones") }

@Composable
private fun SaathiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val tones = remember(dark) { Tones(if (dark) Buddy.DARK else Buddy.LIGHT) }
    val scheme = if (dark) darkColorScheme(primary = tones.accent, onPrimary = tones.onAccent, background = tones.ground, surface = tones.ground, onSurface = tones.ink)
    else lightColorScheme(primary = tones.accent, onPrimary = tones.onAccent, background = tones.ground, surface = tones.ground, onSurface = tones.ink)
    CompositionLocalProvider(LocalTones provides tones) {
        MaterialTheme(colorScheme = scheme) {
            Surface(Modifier.fillMaxSize(), color = tones.ground) { content() }
        }
    }
}

private const val STEPS = 5

@Composable
private fun Setup(prefs: Prefs, onFinish: () -> Unit) {
    val base = LocalContext.current
    val t = LocalTones.current
    var language by remember { mutableStateOf(prefs.language) }
    val res = remember(language) { Languages.context(base, language) }
    fun s(id: Int) = res.getString(id)
    /** Step titles are stored numbered ("2. Allow the microphone"); the progress bar carries the number now. */
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
    // Granting the mic or turning Saathi on moves straight to the next step.
    LaunchedEffect(micGranted) { if (micGranted && page == 1) page = 2 }
    LaunchedEffect(serviceOn) { if (serviceOn && page == 2) page = 3 }
    BackHandler(enabled = page > 0) { page-- }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page > 0) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clickable(role = Role.Button, onClickLabel = s(R.string.btn_back)) { page-- }
                        .semantics { contentDescription = s(R.string.btn_back) },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.BACK, t.ink, 26.dp) }
            } else {
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(34.dp).background(t.accent, CircleShape), contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.HAND, t.onAccent, 20.dp)
                }
                Spacer(Modifier.width(10.dp))
                Txt(s(R.string.app_name), 21, FontWeight.Bold, t.ink)
            }
        }
        Progress(page, Modifier.padding(horizontal = 24.dp))

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
                    .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    0 -> {
                        Title(title(R.string.step_language))
                        Body(s(R.string.onb_subtitle))
                        Spacer(Modifier.height(8.dp))
                        LanguageGrid(language) {
                            language = it
                            prefs.language = it
                        }
                    }
                    1 -> {
                        Title(title(R.string.step_mic))
                        Body(s(R.string.mic_body))
                        // What the senior will see while Saathi listens: the Help pill, turned dark.
                        Box(
                            Modifier.fillMaxWidth().height(180.dp).background(t.tile, RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center,
                        ) { ListeningPill(s(R.string.bubble_listening)) }
                        if (micGranted) DoneRow(s(R.string.done))
                    }
                    2 -> {
                        Title(title(R.string.step_access))
                        if (serviceOn) {
                            DoneRow(s(R.string.access_on))
                        } else {
                            // The prominent disclosure, in full, above the button that agrees to it.
                            Txt(s(R.string.access_summary), 20, FontWeight.SemiBold, t.ink, lineHeight = 28)
                            Column(
                                Modifier.fillMaxWidth().background(t.tile, RoundedCornerShape(20.dp)).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Txt(s(R.string.disclosure), 16, FontWeight.Normal, t.soft, lineHeight = 24)
                                Txt(s(R.string.access_hint), 16, FontWeight.Normal, t.soft, lineHeight = 24)
                                Txt(s(R.string.access_restricted), 16, FontWeight.Normal, t.soft, lineHeight = 24)
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
                        QuietButton(s(R.string.voice_test), Glyph.SPEAKER) {
                            voice.test(language, rate, s(R.string.voice_sample)) { voiceStatus = it }
                        }
                        when (voiceStatus) {
                            Speaker.VoiceStatus.OK -> DoneRow(s(R.string.voice_ok))
                            Speaker.VoiceStatus.MISSING, Speaker.VoiceStatus.NO_ENGINE -> {
                                Notice(s(R.string.voice_missing))
                                QuietButton(s(R.string.voice_install), null) { base.startActivity(voice.installVoiceIntent()) }
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
                        // The same Help pill the senior will see over every app, so setup can point it out.
                        Box(
                            Modifier.fillMaxWidth().height(180.dp).background(t.tile, RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center,
                        ) { HelpPill(s(R.string.bubble_help)) }
                        Body(s(R.string.ready_body), size = 19)
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)) {
            when (page) {
                1 -> if (micGranted) PrimaryPill(s(R.string.btn_continue)) { page = 2 }
                else PrimaryPill(s(R.string.btn_allow)) {
                    val perms = buildList {
                        add(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    askPermissions.launch(perms.toTypedArray())
                }
                2 -> if (serviceOn) PrimaryPill(s(R.string.btn_continue)) { page = 3 }
                else PrimaryPill(s(R.string.btn_open_settings)) {
                    base.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                STEPS - 1 -> PrimaryPill(s(R.string.done), onClick = onFinish)
                else -> PrimaryPill(s(R.string.btn_continue)) { page++ }
            }
        }
    }
}

/** Five short segments: blue up to and including the current step. */
@Composable
private fun Progress(page: Int, modifier: Modifier = Modifier) {
    val t = LocalTones.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(STEPS) { i ->
            Box(Modifier.weight(1f).height(6.dp).background(if (i <= page) t.accent else t.track, CircleShape))
        }
    }
}

@Composable
private fun Title(text: String) {
    val t = LocalTones.current
    Txt(text, 32, FontWeight.Bold, t.ink, Modifier.semantics { heading() }, lineHeight = 38)
}

@Composable
private fun Body(text: String, size: Int = 18) {
    val t = LocalTones.current
    Txt(text, size, FontWeight.Normal, t.soft, lineHeight = (size * 1.45f).toInt())
}

/** Six languages as a two-column grid of tiles, each name set in its own script. */
@Composable
private fun LanguageGrid(selected: String, onPick: (String) -> Unit) {
    val all = Languages.ALL.entries.toList()
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        all.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (tag, name) ->
                    LanguageTile(name, selected = tag == selected, Modifier.weight(1f)) { onPick(tag) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LanguageTile(name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalTones.current
    Row(
        modifier
            .heightIn(min = 76.dp)
            .background(if (selected) t.accent else t.tile, RoundedCornerShape(20.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(name, 24, FontWeight.SemiBold, if (selected) t.onAccent else t.ink, Modifier.weight(1f))
        // The tick marks the choice by shape as well as by the blue fill.
        if (selected) {
            Box(Modifier.size(26.dp).background(t.onAccent, CircleShape), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.CHECK, t.accent, 18.dp)
            }
        }
    }
}

/** A two-way choice as a track with a raised segment; the chosen one also carries a tick. */
@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onPick: (T) -> Unit) {
    val t = LocalTones.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(t.tile, CircleShape)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        options.forEach { (label, value) ->
            val on = value == selected
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .then(if (on) Modifier.shadow(2.dp, CircleShape).background(t.raised, CircleShape) else Modifier)
                    .selectable(selected = on, role = Role.RadioButton) { onPick(value) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) {
                    GlyphIcon(Glyph.CHECK, t.accentLine, 18.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Txt(label, 19, if (on) FontWeight.Bold else FontWeight.Medium, if (on) t.ink else t.soft)
            }
        }
    }
}

@Composable
private fun PrimaryPill(label: String, onClick: () -> Unit) {
    val t = LocalTones.current
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = t.accent, contentColor = t.onAccent),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
    ) { Txt(label, 20, FontWeight.SemiBold, t.onAccent, center = true) }
}

@Composable
private fun QuietButton(label: String, glyph: Glyph?, onClick: () -> Unit) {
    val t = LocalTones.current
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = t.tile, contentColor = t.ink),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, t.ink, 22.dp)
            Spacer(Modifier.width(10.dp))
        }
        Txt(label, 19, FontWeight.SemiBold, t.ink, center = true)
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    val t = LocalTones.current
    // The button clips to its pill, so its padding must hold the ि matra that overhangs a word's start;
    // the offset puts the words back in line with the column.
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).offset(x = (-12).dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Txt(label, 18, FontWeight.SemiBold, t.accentLine)
    }
}

@Composable
private fun DoneRow(label: String) {
    val t = LocalTones.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).background(t.done, CircleShape), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.CHECK, Color.White, 19.dp)
        }
        Spacer(Modifier.width(12.dp))
        Txt(label, 19, FontWeight.SemiBold, t.ink, Modifier.weight(1f, fill = false))
    }
}

/** Something that needs doing, marked with a warning glyph, not colour alone. */
@Composable
private fun Notice(text: String) {
    val t = LocalTones.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(t.caution.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Ink, not amber: an amber glyph on the amber tint falls below 3:1.
        GlyphIcon(Glyph.WARNING, t.ink, 24.dp)
        Spacer(Modifier.width(12.dp))
        Txt(text, 17, FontWeight.Normal, t.ink, lineHeight = 25)
    }
}

/** The Help pill as it floats over other apps. */
@Composable
private fun HelpPill(label: String) {
    val t = LocalTones.current
    Row(
        Modifier
            .shadow(8.dp, CircleShape)
            .background(t.accent, CircleShape)
            .heightIn(min = 56.dp)
            .padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.HAND, t.onAccent, 24.dp)
        Spacer(Modifier.width(8.dp))
        Txt(label, 19, FontWeight.Bold, t.onAccent)
    }
}

/** The Help pill while the mic is open: near-black, with voice bars beside the word. */
@Composable
private fun ListeningPill(label: String) {
    val card = Color(LocalTones.current.b.card)
    Row(
        Modifier
            .shadow(8.dp, CircleShape)
            .background(card, CircleShape)
            .heightIn(min = 56.dp)
            .padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val step = size.width / 4
            listOf(0.5f, 0.9f, 0.65f, 0.8f).forEachIndexed { k, h ->
                val x = step * (k + 0.5f)
                drawLine(Color.White, Offset(x, size.height * (1 - h) / 2), Offset(x, size.height * (1 + h) / 2), 3.2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(8.dp))
        Txt(label, 19, FontWeight.Bold, Color.White)
    }
}

@Composable
private fun GlyphIcon(glyph: Glyph, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        drawIntoCanvas { Glyphs.draw(it.nativeCanvas, glyph, RectF(0f, 0f, this.size.width, this.size.height), color.toArgb()) }
    }
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
