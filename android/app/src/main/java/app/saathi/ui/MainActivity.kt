package app.saathi.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.saathi.Languages
import app.saathi.Prefs
import app.saathi.R
import app.saathi.service.BuddyAccessibilityService

private val Saffron = Color(0xFFE8590C)
private val Ink = Color(0xFF1B1B1F)
private val Paper = Color(0xFFFFF8F1)
private val Done = Color(0xFF2B8A3E)

/**
 * One-time setup, written for the son, daughter or neighbour who installs Saathi for the senior.
 * Shown in the senior's chosen language so it can be read together.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Paper.toArgb(), Ink.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Paper.toArgb(), Ink.toArgb()),
        )
        val prefs = Prefs(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Saffron, background = Paper, surface = Paper)) {
                Surface(Modifier.fillMaxSize(), color = Paper) { Setup(prefs) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Setup(prefs: Prefs) {
    val base = LocalContext.current
    var language by remember { mutableStateOf(prefs.language) }
    val res = remember(language) { Languages.context(base, language) }
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

    Column(
        Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(s(R.string.app_name), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Saffron)
        Text(s(R.string.onb_subtitle), fontSize = 20.sp, color = Ink, lineHeight = 28.sp)

        Step(s(R.string.step_language)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Languages.ALL.forEach { (tag, name) ->
                    val selected = tag == language
                    OutlinedButton(
                        onClick = { language = tag; prefs.language = tag },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Saffron else Color.Gray),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) Saffron.copy(alpha = 0.12f) else Color.Transparent,
                        ),
                        modifier = Modifier.heightIn(min = 60.dp),
                    ) { Text(name, fontSize = 24.sp, color = Ink) }
                }
            }
        }

        Step(s(R.string.step_mic)) {
            if (micGranted) DoneChip(s(R.string.done)) else BigButton(s(R.string.btn_allow)) {
                val perms = buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                askPermissions.launch(perms.toTypedArray())
            }
        }

        Step(s(R.string.step_access)) {
            if (serviceOn) {
                DoneChip(s(R.string.access_on))
            } else {
                Text(s(R.string.disclosure), fontSize = 17.sp, color = Ink, lineHeight = 25.sp,
                    modifier = Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(14.dp))
                Spacer(Modifier.height(12.dp))
                BigButton(s(R.string.btn_open_settings)) {
                    base.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                Spacer(Modifier.height(10.dp))
                Text(s(R.string.access_hint), fontSize = 16.sp, color = Color.DarkGray, lineHeight = 23.sp)
                Spacer(Modifier.height(6.dp))
                Text(s(R.string.access_restricted), fontSize = 16.sp, color = Color.DarkGray, lineHeight = 23.sp)
                OutlinedButton(onClick = {
                    base.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${base.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(s(R.string.btn_app_info), fontSize = 16.sp) }
            }
        }

        Step(s(R.string.step_ready)) {
            Text(s(R.string.ready_body), fontSize = 20.sp, color = Ink, lineHeight = 28.sp)
        }
    }
}

@Composable
private fun Step(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        content()
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) { Text(label, fontSize = 22.sp) }
}

@Composable
private fun DoneChip(label: String) {
    Text("✓  $label", fontSize = 22.sp, color = Done, fontWeight = FontWeight.SemiBold)
}

private fun hasMic(context: Context) =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
