package com.shilapi.xcertplay

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayInsets
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay

/**
 * CarPlay on the BAIC X7 instrument panel (Pateo `c3005h`).
 *
 * <p>The head unit exposes the panel as `local:1`, a 1920x720 HDMI display with
 * `FLAG_PRESENTATION`, but the car only shows the centre 768x720 strip of it
 * ([PateoClusterGeometry.FULL_MAP]). No existing route applies: [ClusterMapPresentation.findDisplay]
 * looks for a BYD, Geely or DiLink 4 panel and returns null here.
 *
 * <p>A `Presentation` cannot be used either. The stock map
 * (`com.pateonavi.naviapp`, uid 1000) owns the strip with a full-screen
 * `TYPE_APPLICATION_OVERLAY` window at `mBaseLayer=121000`; an app window sits at 21000 and stays
 * behind it. Measured 2026-11-21: force-stopping the stock map made DiPlay's ordinary activity
 * window on display 1 visible at once, so the window layer is the only obstacle.
 *
 * <p>This route therefore adds its own `TYPE_APPLICATION_OVERLAY` window to the panel display. Among
 * overlay windows the most recently added one is on top, and DiPlay starts after the stock map has
 * created its overlay, so the picture shows. [raise] re-adds the window when the stock map is
 * suspected to have re-created its own (see [PateoClusterModeDetector]).
 *
 * <p>Requires `android.permission.SYSTEM_ALERT_WINDOW`. Without the grant the route reports
 * unavailable and DiPlay behaves exactly as before on this head unit.
 */
internal object PateoClusterRoute {

    private const val TAG = "DiPlay-Cluster"
    private const val WINDOW_TITLE = "DiPlay-cluster"

    /**
     * Safe area for the panel stream: the whole strip is the map, so the marker stays near the
     * centre. Measured boundaries can tighten this later; it only tells the iPhone where the car
     * position must remain visible.
     */
    private val SAFE_AREA = AirPlayInsets(top = 12, bottom = 12, left = 12, right = 12)

    private var display: Display? = null
    private var windowManager: WindowManager? = null
    private var view: SurfaceView? = null
    private var params: WindowManager.LayoutParams? = null

    /** The CarPlay sink currently receiving the panel surface. */
    private var pictureSink: ((Surface?) -> Unit)? = null

    /** The panel display, or null when this head unit does not expose one. */
    fun panel(context: Context): Display? {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return null
        return manager.displays.firstOrNull { candidate ->
            if (!candidate.isValid) return@firstOrNull false
            if (candidate.displayId == Display.DEFAULT_DISPLAY) return@firstOrNull false
            if ((candidate.flags and Display.FLAG_PRESENTATION) == 0) return@firstOrNull false
            val size = Point()
            @Suppress("DEPRECATION")
            candidate.getRealSize(size)
            size.x == PateoClusterGeometry.DISPLAY_WIDTH && size.y == PateoClusterGeometry.DISPLAY_HEIGHT
        }
    }

    /** Whether the panel exists and the driver granted "display over other apps". */
    fun available(context: Context): Boolean =
        panel(context) != null && Settings.canDrawOverlays(context)

    /**
     * The stream the panel asks for: exactly the visible strip, at 1:1 scale.
     *
     * <p>The generic 1920x720 request would be cropped to the centre by the car, so the stream is
     * sized to the strip instead. Scale 100 keeps the picture 1:1; the panel does not scale it.
     */
    fun streamConfig(context: Context): AirPlayDisplayConfig? {
        if (!available(context)) return null
        val rect = PateoClusterGeometry.rectFor(PateoClusterMode.FULL_MAP) ?: return null
        return CarPlayClusterDisplay.config(
            widthPixels = rect.width,
            heightPixels = rect.height,
            scalePercent = 100,
            content = AirPlayPersistence.loadClusterContent(context),
            markerXPercent = AirPlayPersistence.loadClusterMarkerXPercent(context),
            markerYPercent = AirPlayPersistence.loadClusterMarkerYPercent(context),
            baseSafeArea = SAFE_AREA,
        )
    }

    /**
     * Shows the panel window and hands its surface to [onPicture] (null when it goes away).
     * Must run on the main thread; [CarPlayHostActivity.ensureClusterPresentation] does.
     */
    fun ensure(activity: Activity, onPicture: (Surface?) -> Unit) {
        val target = panel(activity) ?: run {
            onPicture(null)
            return
        }
        if (!Settings.canDrawOverlays(activity)) {
            Log.i(TAG, "cluster: SYSTEM_ALERT_WINDOW not granted; panel route stays off")
            onPicture(null)
            return
        }
        if (view != null && display?.displayId == target.displayId) {
            pictureSink = onPicture
            return
        }
        dismiss()

        val rect = PateoClusterGeometry.rectFor(PateoClusterMode.FULL_MAP) ?: run {
            onPicture(null)
            return
        }
        val displayContext = activity.createDisplayContext(target)
        val surfaceView = SurfaceView(displayContext)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.i(TAG, "cluster: panel surface ${rect.width}x${rect.height}")
                pictureSink?.invoke(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                pictureSink?.invoke(holder.surface)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                pictureSink?.invoke(null)
            }
        })

        val layout = WindowManager.LayoutParams(
            rect.width,
            rect.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.x
            y = rect.y
            setTitle(WINDOW_TITLE)
        }
        val manager = displayContext.getSystemService(WindowManager::class.java) ?: run {
            onPicture(null)
            return
        }
        try {
            manager.addView(surfaceView, layout)
            pictureSink = onPicture
            view = surfaceView
            params = layout
            windowManager = manager
            display = target
            Log.i(TAG, "cluster: overlay ${rect.width}x${rect.height}+${rect.x}+${rect.y} on display ${target.displayId}")
        } catch (error: Throwable) {
            Log.w(TAG, "cluster: addView on display ${target.displayId} failed", error)
            onPicture(null)
        }
    }

    /**
     * Re-adds the window so it stacks above an overlay the stock map created after DiPlay's.
     * The surface is recreated, so the next frame is a fresh IDR on the same stream — the sink
     * handles that as it does for any surface swap.
     */
    fun raise(reason: String) {
        val surfaceView = view ?: return
        val manager = windowManager ?: return
        val layout = params ?: return
        Log.i(TAG, "cluster: raising panel window ($reason)")
        try {
            manager.removeViewImmediate(surfaceView)
            manager.addView(surfaceView, layout)
        } catch (error: Throwable) {
            Log.w(TAG, "cluster: raise failed", error)
        }
    }

    /** Removes the window; the sink is told the picture is gone. */
    fun dismiss() {
        val surfaceView = view ?: run {
            pictureSink?.invoke(null)
            pictureSink = null
            return
        }
        try {
            windowManager?.removeViewImmediate(surfaceView)
        } catch (error: Throwable) {
            Log.w(TAG, "cluster: removeView failed", error)
        }
        view = null
        params = null
        windowManager = null
        display = null
        pictureSink?.invoke(null)
        pictureSink = null
    }
}
