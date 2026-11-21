package com.shilapi.xcertplay

/**
 * The BAIC X7 (Pateo `c3005h`) instrument panel's geometry.
 *
 * <p>Measured 2026-11-21 on the car. The head unit exposes three displays: the centre screen
 * (`local:0`, BUILT_IN, 1920x720), the panel (`local:1`, HDMI, 1920x720, `FLAG_PRESENTATION`,
 * 30 fps) and a third screen (`local:2`, 648x608). The panel only shows the **centre strip** of
 * display 1; the car discards the rest, so CarPlay is laid out inside [FULL_MAP] rather than on the
 * whole display.
 */
internal data class ClusterRect(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * The panel layouts this head unit offers. Themes (classic / sport / minimal) only repaint the
 * dials and never move the map window, so they need no entry here.
 */
internal enum class PateoClusterMode(val visible: Boolean) {
    /** Map fills the whole visible strip. */
    FULL_MAP(true),

    /** Smaller map window; the rectangle is still to be measured on the car. */
    SMALL_MAP(true),

    /** "Simplified navigation": arrows and distance only, no video window. */
    TBT_ONLY(false),

    /** Map switched off; the panel shows dials only. */
    OFF(false),
    ;

    companion object {
        fun ofName(name: String?): PateoClusterMode =
            values().firstOrNull { it.name == name } ?: OFF
    }
}

internal object PateoClusterGeometry {

    const val DISPLAY_WIDTH = 1920
    const val DISPLAY_HEIGHT = 720

    /**
     * Full-map layout. Measured twice on 2026-11-21:
     *
     * - the default/small layout shows the map only in the centre strip, x=576..1344 (768x720);
     * - the panel's "full map" layout paints the whole display, x=0..1920 (1920x720), measured with
     *   `screencap -d 1` while the stock map was in that mode (every column lit, box 0,0,1920,720).
     *
     * So the car switches the visible region with its own layout, and DiPlay must match the layout
     * the driver selected. Full map is the default here because it is the layout the driver asked
     * for; [SMALL_MAP] keeps the centre strip for the other case.
     */
    val FULL_MAP = ClusterRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT)

    /** Default layout: the panel shows only the centre strip. Measured 576..1344, 768x720. */
    val SMALL_MAP = ClusterRect(576, 0, 768, 720)

    fun rectFor(mode: PateoClusterMode): ClusterRect? = when (mode) {
        PateoClusterMode.FULL_MAP -> FULL_MAP
        PateoClusterMode.SMALL_MAP -> SMALL_MAP
        PateoClusterMode.TBT_ONLY, PateoClusterMode.OFF -> null
    }

    fun describe(mode: PateoClusterMode): String {
        val rect = rectFor(mode)
        return if (rect == null) {
            "cluster: mode=$mode no video area"
        } else {
            "cluster: mode=$mode rect=${rect.x},${rect.y} ${rect.width}x${rect.height}"
        }
    }
}
