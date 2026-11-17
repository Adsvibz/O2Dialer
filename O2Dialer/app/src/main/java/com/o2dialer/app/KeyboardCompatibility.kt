package com.o2dialer.app

import android.graphics.Rect
import android.os.Build
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.delay

/** Use native resize on older platforms, including API 30–32 OEM builds. */
internal val legacyKeyboardWindow: Boolean
    get() = Build.VERSION.SDK_INT < 33

@Composable
internal fun LegacyDialogResize() {
    val view = LocalView.current
    SideEffect {
        if (legacyKeyboardWindow) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                val resize = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                if (window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST != resize) {
                    window.setSoftInputMode(resize)
                }
            }
        }
    }
}

/** The residual obstruction is based on the measured view, not the full display.
 * A window already resized by Android therefore receives no extra keyboard space.
 */
internal fun residualKeyboardOverlap(
    viewBottom: Int, visibleBottom: Int, imeReported: Boolean, thresholdPx: Int
): Int {
    val overlap = (viewBottom - visibleBottom).coerceAtLeast(0)
    return if (imeReported || overlap >= thresholdPx) overlap else 0
}

internal data class KeyboardViewport(
    val obstructionPx: Int = 0,
    val visible: Boolean = false,
    val visibleBottom: Int = 0,
    val viewHeight: Int = 0
)

/** Some OEM keyboards report zero IME insets. Read the actual visible window frame
 * on layout and pre-draw, without consuming or replacing the platform inset listener.
 */
@Composable
internal fun rememberKeyboardViewport(): KeyboardViewport {
    val view = LocalView.current
    val threshold = with(LocalDensity.current) { 80 * density }.toInt()
    var frame by remember(view) { mutableStateOf(KeyboardViewport()) }
    DisposableEffect(view, threshold) {
        val rect = Rect()
        val position = IntArray(2)
        var largestVisibleBottom = 0
        var lastWidth = 0
        fun measure() {
            if (!view.isAttachedToWindow || view.width == 0 || view.height == 0) return
            view.getWindowVisibleDisplayFrame(rect)
            view.getLocationOnScreen(position)
            if (rect.bottom <= rect.top) return
            if (lastWidth != view.width) {
                lastWidth = view.width
                largestVisibleBottom = rect.bottom
            }
            largestVisibleBottom = maxOf(largestVisibleBottom, rect.bottom)
            val insets = ViewCompat.getRootWindowInsets(view)
            val imeReported = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
            val overlap = residualKeyboardOverlap(position[1] + view.height, rect.bottom, imeReported, threshold)
            val visible = imeReported || overlap > 0 || largestVisibleBottom - rect.bottom >= threshold
            val next = KeyboardViewport(overlap, visible, rect.bottom, view.height)
            if (frame != next) frame = next
        }
        val layout = ViewTreeObserver.OnGlobalLayoutListener { measure() }
        val preDraw = ViewTreeObserver.OnPreDrawListener { measure(); true }
        val observer = view.viewTreeObserver
        observer.addOnGlobalLayoutListener(layout)
        observer.addOnPreDrawListener(preDraw)
        measure()
        onDispose {
            val current = if (observer.isAlive) observer else view.viewTreeObserver
            current.removeOnGlobalLayoutListener(layout)
            current.removeOnPreDrawListener(preDraw)
        }
    }
    return frame
}

/** For native resize, only compensate the actual remaining overlap. For modern
 * edge-to-edge windows, supplement IME insets rather than adding them twice.
 */
@Composable
internal fun Modifier.keyboardViewportPadding(
    viewport: KeyboardViewport = rememberKeyboardViewport(),
    nativeResize: Boolean = legacyKeyboardWindow
): Modifier {
    val density = LocalDensity.current
    val ime = WindowInsets.ime.getBottom(density)
    return if (nativeResize) {
        this.padding(bottom = with(density) { viewport.obstructionPx.toDp() })
            .consumeWindowInsets(WindowInsets.ime)
    } else {
        this.imePadding()
            .padding(bottom = with(density) { (viewport.obstructionPx - ime).coerceAtLeast(0).toDp() })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.keepFieldVisible(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val frame = if (focused) rememberKeyboardViewport() else KeyboardViewport()
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, ime, frame.visibleBottom, frame.viewHeight) {
        if (focused) {
            // Retry after actual viewport changes, not only the initial focus event.
            delay(100)
            requester.bringIntoView()
            delay(300)
            requester.bringIntoView()
        }
    }
    return this.bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused }
}
