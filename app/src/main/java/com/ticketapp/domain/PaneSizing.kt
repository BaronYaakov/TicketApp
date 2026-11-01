package com.ticketapp.domain

/** Logical pixels (dp), based on the actual app window, never a device model or orientation. */
object PaneSizing {
    const val BREAKPOINT = 700f
    const val MIN_LIST = 260f
    const val MIN_DETAIL = 360f
    const val DIVIDER = 48f
    const val DEFAULT_FRACTION = 0.40f

    fun isExpanded(width: Float) = width >= BREAKPOINT
    fun listWidth(width: Float, fraction: Float): Float {
        require(isExpanded(width))
        val available = width - DIVIDER
        val safe = fraction.takeIf { it.isFinite() } ?: DEFAULT_FRACTION
        return (available * safe).coerceIn(MIN_LIST, available - MIN_DETAIL)
    }
    fun fraction(width: Float, requestedWidth: Float): Float =
        listWidth(width, requestedWidth / (width - DIVIDER)) / (width - DIVIDER)
}
