package app.saathi.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import app.saathi.BuildConfig
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
        const val WEB_TREE_RETRIES = 3
        const val WEB_TREE_WAIT_MS = 250L
    }

    override suspend fun look(): Snapshot {
        val bounds = service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        val w = bounds.width()
        val h = bounds.height()

        // A secure or redrawing foreground window can have no root. Do not substitute a
        // readable background app: its tree and picture would describe the wrong screen.
        val candidates = appWindows()
        val window = candidates.firstOrNull()
        val root = window?.root ?: service.rootInActiveWindow
        val app = root?.packageName?.toString().orEmpty()
        val t0 = SystemClock.elapsedRealtime()
        var tree = withContext(Dispatchers.Default) {
            root?.let { TreeSerializer.serialize(toRaw(it, 0), w, h) } ?: SerializedTree("", emptyMap())
        }
        // WebView turns on its accessibility document lazily. Wait briefly for labels instead
        // of sending a picture containing values that have not reached the tree yet.
        repeat(WEB_TREE_RETRIES) {
            if (tree.opaqueWebView) {
                delay(WEB_TREE_WAIT_MS)
                val updated = window?.root ?: service.rootInActiveWindow
                tree = if (updated?.packageName?.toString() == app) withContext(Dispatchers.Default) {
                    TreeSerializer.serialize(toRaw(updated, 0), w, h)
                } else SerializedTree("", emptyMap(), omitScreenshot = true, opaqueWebView = true)
            }
        }

        // A screenshot must not bypass text redaction. Send only the tree when visible fields
        // or sensitive context were found, including nodes beyond the serialized output limit.
        val t1 = SystemClock.elapsedRealtime()
        val shot = if (tree.omitScreenshot) Shot(null, false, Box(0, 0, w, h)) else capture(window, Box(0, 0, w, h))
        if (BuildConfig.DEBUG) Log.d("Saathi", "look $app: tree ${t1 - t0}ms, picture ${SystemClock.elapsedRealtime() - t1}ms")
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

    /** App windows, most likely first: the active one, then the focused one, then the rest. */
    private fun appWindows(): List<AccessibilityWindowInfo> {
        val apps = service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        return apps.sortedBy { if (it.isActive) 0 else if (it.isFocused) 1 else 2 }
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
            val box = Box(r.left, r.top, r.right, r.bottom)
            var res = screenshot { cb -> service.takeScreenshotOfWindow(window.id, service.mainExecutor, cb) }
            if (res is Result.Err && res.code == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                delay(400)
                res = screenshot { cb -> service.takeScreenshotOfWindow(window.id, service.mainExecutor, cb) }
            }
            // Never fall back to a display capture after a window failure. It can include other
            // windows and may not preserve the secure-window result, especially when throttled.
            return when (res) {
                is Result.Ok -> Shot(encode(res.shot), false, box)
                is Result.Err -> {
                    if (BuildConfig.DEBUG) Log.d("Saathi", "window screenshot error ${res.code}")
                    Shot(null, res.code == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW, box)
                }
            }
        }
        var res = screenshot { cb -> service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, cb) }
        if (res is Result.Err && res.code == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
            delay(400)
            res = screenshot { cb -> service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, cb) }
        }
        if (res !is Result.Ok) {
            val code = (res as Result.Err).code
            if (BuildConfig.DEBUG) Log.d("Saathi", "display screenshot error $code")
            return Shot(null, Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                code == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW, screen)
        }
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
