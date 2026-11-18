package com.o2dialer.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow

/** Fit small header labels before allowing a line break or ellipsis.
 * Keep the system's larger accessibility font settings intact.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
internal fun O2FitTitle(text: String, style: TextStyle, color: Color = LocalContentColor.current) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        val largeText = density.fontScale > 1.15f
        val measured = measurer.measure(AnnotatedString(text), style = style, softWrap = false, maxLines = 1)
        val factor = if (largeText || measured.size.width <= 0 || !constraints.hasBoundedWidth) 1f else
            (constraints.maxWidth.toFloat() / measured.size.width).coerceIn(.85f, 1f)
        Text(
            text = text,
            style = style.copy(fontSize = style.fontSize * factor),
            color = color,
            softWrap = largeText,
            maxLines = if (largeText) 2 else 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
