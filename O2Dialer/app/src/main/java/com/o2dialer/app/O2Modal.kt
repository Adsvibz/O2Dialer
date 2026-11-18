package com.o2dialer.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager

internal val LocalO2Modal = staticCompositionLocalOf { false }

/**
 * One dialog window and one scroll owner for input forms, across API 26+.
 * Its geometry is constrained by the usable viewport, never the full display.
 * No conditional reparenting of editable nodes while the IME is showing.
 */
@Composable
internal fun O2Modal(
    onDismiss: () -> Unit,
    bottomAligned: Boolean = false,
    maxWidth: Dp = 448.dp,
    radius: Dp = 24.dp,
    outerPadding: Dp = 12.dp,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = legacyKeyboardWindow)
    ) {
        LegacyDialogResize()
        val view = LocalView.current
        // The scrim belongs to our layout. Do not mutate the window every recomposition.
        LaunchedEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        val viewport = rememberKeyboardViewport()
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.matchParentSize().background(Color.Black.copy(alpha = .40f))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            )
            Box(
                Modifier.fillMaxSize().then(
                    if (legacyKeyboardWindow) Modifier.keyboardViewportPadding(viewport, nativeResize = true)
                    else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .navigationBarsPadding().keyboardViewportPadding(viewport, nativeResize = false)
                ).padding(horizontal = outerPadding, vertical = if (bottomAligned) 0.dp else outerPadding),
                contentAlignment = if (bottomAligned) Alignment.BottomCenter else Alignment.Center
            ) {
                val shape = if (bottomAligned) RoundedCornerShape(topStart = radius, topEnd = radius)
                    else RoundedCornerShape(radius)
                Surface(
                    modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth()
                        // The card receives pointer hits without creating another focus target.
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                    shape = shape,
                    color = cs.surface,
                    contentColor = cs.onSurface,
                    border = BorderStroke(1.dp, cs.outlineVariant),
                    shadowElevation = if (bottomAligned) 0.dp else 12.dp
                ) {
                    CompositionLocalProvider(LocalO2Modal provides true) {
                        val scroll = rememberScrollState()
                        Column(
                            Modifier.fillMaxWidth().then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier),
                            content = content
                        )
                    }
                }
            }
        }
    }
}
