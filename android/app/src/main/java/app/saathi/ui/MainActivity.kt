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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.saathi.Languages
import app.saathi.Prefs
import app.saathi.R
import app.saathi.design.Glyph
import app.saathi.design.Glyphs
import app.saathi.design.Metro
import app.saathi.design.SaathiType
import app.saathi.service.BuddyAccessibilityService
import app.saathi.voice.Speaker
import app.saathi.voice.VoiceSetup

/**
 * Setup, laid out as a metro line: five stops from language to ready, done stops ticked, the
 * current one large with its sign. Written so either the senior or a family member can follow it,
 * in the senior's chosen language.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val metro = Metro.of(this)
        val ground = if (metro === Metro.DARK) GROUND_DARK else GROUND_LIGHT
        enableEdgeToEdge(
            statusBarStyle = if (metro === Metro.DARK) SystemBarStyle.dark(ground) else SystemBarStyle.light(ground, metro.ink),
            navigationBarStyle = if (metro === Metro.DARK) SystemBarStyle.dark(ground) else SystemBarStyle.light(ground, metro.ink),
        )
        val prefs = Prefs(this)
        setContent { SaathiTheme { Setup(prefs) } }
    }
}

private const val GROUND_LIGHT = 0xFFFFFFFF.toInt()
private const val GROUND_DARK = 0xFF111215.toInt()

/** Compose-side colours, read from the same [Metro] tokens the overlay uses. */
private class Tones(val m: Metro, dark: Boolean) {
    val ground = Color(if (dark) GROUND_DARK else GROUND_LIGHT)
    val panel = Color(m.panel)
    val ink = Color(m.ink)
    val soft = Color(m.inkSoft)
    val accent = Color(m.accent)
    val accentFill = Color(m.accentFill)
    val onAccent = Color(m.onAccent)
    val done = Color(m.done)
    val caution = Color(m.caution)
    val track = if (dark) Color(0xFF3A3E45) else Color(0xFFC9CDD3)
    /** Unselected chips and the sign's hairline on the white ground. */
    val chip = if (dark) Color(0xFF2A2D33) else Color(0xFFECEEF1)
    val hairline = if (dark) Color(0xFF2A2D33) else Color(0xFFDDE1E6)
}

private val LocalTones = staticCompositionLocalOf<Tones> { error("no tones") }
private val LocalFace = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

@Composable
private fun SaathiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val tones = remember(dark) { Tones(if (dark) Metro.DARK else Metro.LIGHT, dark) }
    val scheme = if (dark) darkColorScheme(primary = tones.accentFill, onPrimary = tones.onAccent, background = tones.ground, surface = tones.panel, onSurface = tones.ink)
    else lightColorScheme(primary = tones.accentFill, onPrimary = tones.onAccent, background = tones.ground, surface = tones.panel, onSurface = tones.ink)
    CompositionLocalProvider(LocalTones provides tones) {
        MaterialTheme(colorScheme = scheme) {
            Surface(Modifier.fillMaxSize(), color = tones.ground) { content() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Setup(prefs: Prefs) {
    val base = LocalContext.current
    val t = LocalTones.current
    var language by remember { mutableStateOf(prefs.language) }
    val res = remember(language) { Languages.context(base, language) }
    val face = remember(language) { SaathiType.family(base, language) }
    fun s(id: Int) = res.getString(id)

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
    val voice = remember { VoiceSetup(base) }
    DisposableEffect(voice) { onDispose { voice.shutdown() } }
    var rate by remember { mutableStateOf(prefs.speechRate) }
    var voiceStatus by remember(language) { mutableStateOf<Speaker.VoiceStatus?>(null) }
    var offlineStarted by remember(language) { mutableStateOf(false) }

    // The last stop (Ready) is never "complete" on its own: it becomes current once the rest are done.
    val complete = listOf(true, micGranted, serviceOn, voiceStatus == Speaker.VoiceStatus.OK, false)
    val current = complete.indexOfFirst { !it }
    // A finished stop can be reopened (to change the voice speed, say); null means "follow the line".
    var opened by remember { mutableStateOf<Int?>(null) }
    val focus = opened ?: current

    // While "Turn on Saathi" is the current stop, its action is pinned to the bottom of the screen
    // so it is always in reach without scrolling past the disclosure.
    val pinAccess = focus == 2 && !serviceOn
    CompositionLocalProvider(LocalFace provides face) {
      Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = if (pinAccess) 112.dp else 28.dp),
        ) {
            // Comp 3: wordmark, tagline and language chips as one centred stack.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(s(R.string.app_name), 40, FontWeight.ExtraBold, t.ink, Modifier.semantics { heading() })
                Spacer(Modifier.height(8.dp))
                Txt(s(R.string.onb_subtitle), 19, FontWeight.Normal, t.soft, lineHeight = 27, center = true)
            }
            Spacer(Modifier.height(20.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Languages.ALL.forEach { (tag, name) ->
                    LanguageChip(name, tag, selected = tag == language) {
                        language = tag
                        prefs.language = tag
                    }
                }
            }
            Spacer(Modifier.height(28.dp))

            val stops = listOf(
                R.string.step_language, R.string.step_mic, R.string.step_access, R.string.step_voice, R.string.step_ready,
            )
            stops.forEachIndexed { i, title ->
                Stop(
                    index = i,
                    title = s(title).substringAfter(". ", s(title)),
                    done = complete[i] || (i == 4 && current == 4),
                    focused = i == focus,
                    last = i == stops.lastIndex,
                    doneLabel = s(R.string.done),
                    onOpen = { opened = if (opened == i) null else i },
                ) {
                    when (i) {
                        0 -> Txt(Languages.ALL[language].orEmpty(), 20, FontWeight.SemiBold, t.ink)
                        1 -> if (micGranted) DoneLine(s(R.string.done)) else Sign {
                            PrimaryButton(s(R.string.btn_allow)) {
                                val perms = buildList {
                                    add(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                askPermissions.launch(perms.toTypedArray())
                            }
                        }
                        2 -> if (serviceOn) DoneLine(s(R.string.access_on)) else Sign {
                            // Lead with the two-line summary; the full prominent disclosure follows at
                            // reading size, above the action (which is pinned to the screen bottom).
                            Txt(s(R.string.access_summary), 21, FontWeight.SemiBold, t.ink, lineHeight = 29)
                            Txt(s(R.string.disclosure), 16, FontWeight.Normal, t.soft, lineHeight = 23)
                            Txt(s(R.string.access_hint), 16, FontWeight.Normal, t.soft, lineHeight = 23)
                            Txt(s(R.string.access_restricted), 16, FontWeight.Normal, t.soft, lineHeight = 23)
                            SecondaryButton(s(R.string.btn_app_info)) {
                                base.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${base.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        3 -> Sign {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(Speaker.SLOW_RATE to R.string.speed_slow, Speaker.DEFAULT_RATE to R.string.speed_normal).forEach { (r, label) ->
                                    Choice(s(label), selected = rate == r, Modifier.weight(1f)) {
                                        rate = r
                                        prefs.speechRate = r
                                    }
                                }
                            }
                            PrimaryButton(s(R.string.voice_test), Glyph.SPEAKER) {
                                voice.test(language, rate, s(R.string.voice_sample)) { voiceStatus = it }
                            }
                            when (voiceStatus) {
                                Speaker.VoiceStatus.OK -> DoneLine(s(R.string.voice_ok))
                                Speaker.VoiceStatus.MISSING, Speaker.VoiceStatus.NO_ENGINE -> {
                                    Notice(s(R.string.voice_missing))
                                    PrimaryButton(s(R.string.voice_install)) { base.startActivity(voice.installVoiceIntent()) }
                                }
                                null -> {}
                            }
                            if (voice.canDownloadOfflineSpeech) {
                                if (offlineStarted) Txt(s(R.string.offline_started), 16, FontWeight.Normal, t.soft, lineHeight = 23)
                                else SecondaryButton(s(R.string.offline_download)) { offlineStarted = voice.downloadOfflineSpeech(language) }
                            }
                        }
                        4 -> Sign {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                HelpDisc(s(R.string.bubble_help), 72.dp)
                                Txt(s(R.string.ready_body), 19, FontWeight.Normal, t.ink, Modifier.weight(1f), lineHeight = 27)
                            }
                        }
                    }
                }
            }
        }
        if (pinAccess) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(t.ground)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                PrimaryButton(s(R.string.btn_open_settings)) {
                    base.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
      }
    }
}

private val LINE_X = 22.dp
private val RAIL = 6.dp

/**
 * One stop on the line. The rail runs through every stop; it is blue up to the current stop and
 * grey after it. Done stops show a filled disc with a drawn tick (shape, not just colour).
 */
@Composable
private fun Stop(
    index: Int,
    title: String,
    done: Boolean,
    focused: Boolean,
    last: Boolean,
    doneLabel: String,
    onOpen: () -> Unit,
    body: @Composable () -> Unit,
) {
    val t = LocalTones.current
    val railColor = if (done) t.accent else t.track
    val marker = if (focused) 34.dp else if (done) 30.dp else 22.dp
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(LINE_X * 2).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val x = LINE_X.toPx()
                val markerY = 20.dp.toPx()
                if (index > 0) drawLine(t.track, Offset(x, 0f), Offset(x, markerY), RAIL.toPx())
                if (index > 0 && (done || focused)) drawLine(t.accent, Offset(x, 0f), Offset(x, markerY), RAIL.toPx())
                if (!last) drawLine(railColor, Offset(x, markerY), Offset(x, size.height), RAIL.toPx())
                val r = marker.toPx() / 2
                when {
                    done && !focused -> {
                        // accentFill, not accent: the white tick needs ≥3:1 on it in dark mode too.
                        drawCircle(t.accentFill, r, Offset(x, markerY))
                        drawIntoCanvas {
                            val g = r * 1.2f
                            Glyphs.draw(it.nativeCanvas, Glyph.CHECK, RectF(x - g / 2, markerY - g / 2, x + g / 2, markerY + g / 2), t.onAccent.toArgb())
                        }
                    }
                    focused -> {
                        drawCircle(t.ground, r, Offset(x, markerY))
                        drawCircle(t.accent, r - 3.5.dp.toPx(), Offset(x, markerY), style = Stroke(7.dp.toPx()))
                    }
                    else -> {
                        drawCircle(t.ground, r, Offset(x, markerY))
                        drawCircle(t.track, r - 2.dp.toPx(), Offset(x, markerY), style = Stroke(4.dp.toPx()))
                    }
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp, bottom = if (last) 0.dp else 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier
                    .heightIn(min = 40.dp)
                    .clickable(role = Role.Button, onClick = onOpen)
                    .semantics {
                        heading()
                        stateDescription = if (done) doneLabel else ""
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt("${index + 1}", if (focused) 26 else 20, FontWeight.Bold, if (done || focused) t.accent else t.soft)
                Spacer(Modifier.width(12.dp))
                // weight(1f) bounds the title to the column so long labels wrap instead of clipping.
                Txt(title, if (focused) 26 else 20, if (focused) FontWeight.Bold else FontWeight.SemiBold, if (focused || done) t.ink else t.soft, Modifier.weight(1f))
            }
            if (focused) body()
        }
    }
}

/** The enamel sign beside the current stop: a panel with a metro-blue band on its leading edge. */
@Composable
private fun Sign(content: @Composable () -> Unit) {
    val t = LocalTones.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(t.panel, RoundedCornerShape(8.dp))
            .border(1.dp, t.hairline, RoundedCornerShape(8.dp)),
    ) {
        Box(Modifier.width(10.dp).fillMaxHeight().background(t.accent, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)))
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

@Composable
private fun PrimaryButton(label: String, glyph: Glyph? = null, onClick: () -> Unit) {
    val t = LocalTones.current
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = t.accentFill, contentColor = t.onAccent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, t.onAccent, 24.dp)
            Spacer(Modifier.width(10.dp))
        }
        Txt(label, 21, FontWeight.Bold, t.onAccent)
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    val t = LocalTones.current
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(2.5.dp, t.accent),
        // Vertical padding so a two-line label (Hindi "…डाउनलोड करें") is never cut by the border.
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Txt(label, 18, FontWeight.SemiBold, t.accent, center = true) }
}

/** A two-way choice; the selected one is filled and ticked, so it reads without colour. */
@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalTones.current
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(2.5.dp, t.accent),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) t.accentFill else Color.Transparent),
        // Tight padding: half-width pills hold "Normal" or "சாதாரண" plus the tick on one line.
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier.heightIn(min = 56.dp).semantics { this.selected = selected },
    ) {
        if (selected) {
            GlyphIcon(Glyph.CHECK, t.onAccent, 20.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(fontFamily = LocalFace.current, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = if (selected) t.onAccent else t.accent),
        )
    }
}

/** A language chip set in that language's own script face. */
@Composable
private fun LanguageChip(name: String, tag: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalTones.current
    val context = LocalContext.current
    val face = remember(tag) { SaathiType.family(context, tag) }
    Box(
        Modifier
            .heightIn(min = 52.dp)
            // Comp 3: unselected chips are plain grey fills; the chosen one is tinted and outlined in blue.
            .background(if (selected) t.accent.copy(alpha = 0.14f) else t.chip, RoundedCornerShape(26.dp))
            .border(2.5.dp, if (selected) t.accent else Color.Transparent, RoundedCornerShape(26.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, style = TextStyle(fontFamily = face, fontSize = 21.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = if (selected) t.accent else t.ink))
    }
}

@Composable
private fun DoneLine(label: String) {
    val t = LocalTones.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(t.done, CircleShape), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.CHECK, Color.White, 18.dp)
        }
        Spacer(Modifier.width(10.dp))
        Txt(label, 19, FontWeight.SemiBold, t.ink)
    }
}

/** Something that needs doing: a caution note marked with a warning glyph, not colour alone. */
@Composable
private fun Notice(text: String) {
    val t = LocalTones.current
    Row(
        Modifier
            .fillMaxWidth()
            .border(2.dp, t.caution, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        GlyphIcon(Glyph.WARNING, t.caution, 24.dp)
        Spacer(Modifier.width(10.dp))
        Txt(text, 17, FontWeight.Normal, t.ink, lineHeight = 25)
    }
}

/** The same Help disc the senior will see over every app, so setup can point it out. */
@Composable
private fun HelpDisc(label: String, size: Dp) {
    val t = LocalTones.current
    Box(
        Modifier
            .size(size)
            .background(t.accentFill, CircleShape)
            .border(2.5.dp, if (t.m === Metro.DARK) Color(0xFF0B0C0E) else Color.White, CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphIcon(Glyph.GUIDE, t.onAccent, size * 0.34f)
            if (size >= 64.dp) Txt(label, 13, FontWeight.Bold, t.onAccent)
        }
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
            textAlign = if (center) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Unspecified,
            fontFamily = LocalFace.current,
            fontSize = size.sp,
            fontWeight = weight,
            color = color,
            lineHeight = (lineHeight ?: (size * 1.2f).toInt()).sp,
        ),
    )
}

private fun hasMic(context: Context) =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
