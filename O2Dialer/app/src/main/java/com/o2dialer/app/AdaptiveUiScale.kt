package com.o2dialer.app

/**
 * Width-adaptive design scale. The 390dp and wider layout is unchanged.
 * Narrow phones reduce spacing, icons and type proportionally; the lower
 * bound avoids illegible micro-text. System fontScale is kept separately.
 */
internal fun adaptiveUiScale(widthDp: Int): Float =
    if (widthDp <= 0) 1f else (widthDp / 390f).coerceIn(0.86f, 1f)
