package com.o2dialer.app

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay

/** Native resize avoids the unreliable edge-to-edge IME insets of older dialog windows. */
internal val legacyKeyboardWindow: Boolean
    get() = Build.VERSION.SDK_INT < 30

@Composable
internal fun LegacyDialogResize() {
    val view = LocalView.current
    SideEffect {
        if (legacyKeyboardWindow) {
            (view.parent as? DialogWindowProvider)?.window
                ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
}

/** Relocate the edited field after focus and after the keyboard inset changes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.keepFieldVisible(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused) {
            delay(100)
            requester.bringIntoView()
            if (legacyKeyboardWindow) {
                delay(250)
                requester.bringIntoView()
            }
        }
    }
    return this.bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused }
}
