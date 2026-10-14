package app.saathi.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Base64
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import app.saathi.safety.ScreenSafety
import app.saathi.session.Eyes
import app.saathi.session.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** Reads the foreground app's element tree and takes a downscaled screenshot. */
class ScreenReader(private val service: AccessibilityService) : Eyes {

    private companion object {
        const val LONG_SIDE = 768
        const val JPEG_QUALITY = 70
        const val MAX_DEPTH = 40
    }

    override suspend fun look(): Snapshot {
        val bounds = service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        val w = bounds.width()
        val h = bounds.height()

        val window = appWindow()
        val root = window?.root ?: service.rootInActiveWindow
        val app = root?.packageName?.toString().orEmpty()
        val tree = withContext(Dispatchers.Default) {
            root?.let { TreeSerializer.serialize(toRaw(it, 0), w, h) } ?: SerializedTree("", emptyMap())
        }

        // The tree already masks one-time codes, but a picture of an SMS with an OTP would show it.
        // When codes or passwords are mentioned, the masked tree goes alone.
        val shot = if (ScreenSafety.sensitive(tree.text)) Shot(null, false, Box(0, 0, w, h)) else capture(window, Box(0, 0, w, h))
        return Snapshot(
            app = app,
            screenshotB64 = shot.jpegB64,
            screenW = w,
            screenH = h,
            tree = tree.text,
            boxes = tree.boxes,
            secure = shot.secure,
            shotBox = shot.box,
        )
    }

    private fun appWindow(): AccessibilityWindowInfo? {
        val apps = service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        return apps.firstOrNull { it.isActive } ?: apps.firstOrNull { it.isFocused } ?: apps.firstOrNull()
    }

    private fun toRaw(node: AccessibilityNodeInfo, depth: Int): RawNode {
        val r = Rect().also(node::getBoundsInScreen)
        val children = if (depth >= MAX_DEPTH) emptyList() else
            (0 until node.childCount).mapNotNull { node.getChild(it) }.map { toRaw(it, depth + 1) }
        return RawNode(
            className = node.className?.toString(),
            text = node.text?.toString(),
            desc = node.contentDescription?.toString(),
            hint = node.hintText?.toString(),
            bounds = Box(r.left, r.top, r.right, r.bottom),
            visible = node.isVisibleToUser,
            clickable = node.isClickable,
            editable = node.isEditable,
            password = node.isPassword,
            scrollable = node.isScrollable,
            checked = if (node.isCheckable) node.isChecked else null,
            children = children,
        )
    }

    private class Shot(val jpegB64: String?, val secure: Boolean, val box: Box)

    private suspend fun capture(window: AccessibilityWindowInfo?, screen: Box): Shot {
        // Android 14+: capture only the app's window. Secure (FLAG_SECURE) windows refuse outright,
        // and our own overlay is not in the picture.
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val r = Rect().also(window::getBoundsInScreen)
            when (val res = screenshot { cb -> service.takeScreenshotOfWindow(window.id, service.mainExecutor, cb) }) {
                is Result.Ok -> return Shot(encode(res.shot), false, Box(r.left, r.top, r.right, r.bottom))
                is Result.Err -> if (res.code == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
                    return Shot(null, true, screen)
                }
            }
        }
        var res = screenshot { cb -> service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, cb) }
        if (res is Result.Err && res.code == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
            delay(400)
            res = screenshot { cb -> service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, cb) }
        }
        if (res !is Result.Ok) return Shot(null, false, screen)
        return withContext(Dispatchers.Default) {
            val bmp = toBitmap(res.shot)
            // Before Android 14 secure windows come back as solid black instead of an error.
            if (bmp == null || isBlank(bmp)) Shot(null, bmp != null, screen) else Shot(jpeg(bmp), false, screen)
        }
    }

    private sealed interface Result {
        class Ok(val shot: ScreenshotResult) : Result
        class Err(val code: Int) : Result
    }

    private suspend fun screenshot(take: (TakeScreenshotCallback) -> Unit): Result =
        suspendCancellableCoroutine { cont ->
            take(object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    if (cont.isActive) cont.resume(Result.Ok(result)) else result.hardwareBuffer.close()
                }
                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) cont.resume(Result.Err(errorCode))
                }
            })
        }

    private suspend fun encode(shot: ScreenshotResult): String? = withContext(Dispatchers.Default) {
        toBitmap(shot)?.let(::jpeg)
    }

    private fun toBitmap(shot: ScreenshotResult): Bitmap? = shot.hardwareBuffer.use { hb ->
        Bitmap.wrapHardwareBuffer(hb, shot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun jpeg(bmp: Bitmap): String {
        val scale = LONG_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt(), (bmp.height * scale).roundToInt(), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun isBlank(bmp: Bitmap): Boolean {
        val stepX = maxOf(1, bmp.width / 20)
        val stepY = maxOf(1, bmp.height / 20)
        for (y in 0 until bmp.height step stepY) for (x in 0 until bmp.width step stepX) {
            val c = bmp.getPixel(x, y)
            if ((c shr 16 and 0xff) > 8 || (c shr 8 and 0xff) > 8 || (c and 0xff) > 8) return false
        }
        return true
    }
}
