package com.shilapi.xcertplay.network

import android.os.Build

/**
 * Identifies the Pateo head unit in the BAIC X7 (`c3005h`, model `C3005H_MX8Q`, Freescale i.MX8,
 * Android 9).
 *
 * Confirmed on the car with a diagnostic report: CarPlay's speech-recognition microphone opens the
 * right device (`routedDeviceType=15`, built-in mic) but every `AudioRecord.read` returns zero bytes
 * (`zeroReads` = every read, `peakDbfs=silent`), so WeChat voice messages and iOS dictation hear
 * nothing. The same firmware delivers normal audio through `VOICE_COMMUNICATION` during a call, and
 * [GeelyHeadUnit] documents the same split on Geely/Lynk units — this firmware simply does not open
 * the microphone for `VOICE_RECOGNITION`.
 *
 * [com.shilapi.xcertplay.media.MicrophoneUplink] therefore records speech recognition with
 * `VOICE_COMMUNICATION` here. Every use is gated by [detected], so other head units keep the stock
 * behaviour, and unit tests (where `Build` reports Robolectric) never take this path.
 */
object PateoHeadUnit {

    private val identifiers: List<String>
        get() = runCatching {
            listOf(Build.PRODUCT, Build.DEVICE, Build.MODEL, Build.MANUFACTURER, Build.BRAND)
                .map { it.lowercase() }
        }.getOrDefault(emptyList())

    /** The head unit's device code (`ro.product.device`) and board name. */
    private const val DEVICE = "c3005h"

    /** The model string the head unit reports. */
    private const val MODEL = "c3005h_mx8q"

    val detected: Boolean = identifiers.any { id ->
        id == DEVICE || id == MODEL || id.contains(DEVICE)
    }
}
