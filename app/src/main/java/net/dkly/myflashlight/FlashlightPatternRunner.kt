package net.dkly.myflashlight

import android.os.Handler
import android.os.Looper

class FlashlightPatternRunner(private val onToggle: (Boolean) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false

    fun start(mode: FlashlightMode) {
        stop()
        active = true
        when (mode) {
            FlashlightMode.STROBE -> schedule(STROBE_PATTERN)
            FlashlightMode.SOS -> schedule(SOS_PATTERN)
            FlashlightMode.STEADY -> active = false
        }
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun schedule(pattern: LongArray) {
        var index = 0
        val step = object : Runnable {
            override fun run() {
                if (!active) return
                val isOn = index % 2 == 0
                runCatching { onToggle(isOn) }
                val delay = pattern[index % pattern.size]
                index++
                handler.postDelayed(this, delay)
            }
        }
        handler.post(step)
    }

    companion object {
        private val STROBE_PATTERN = longArrayOf(100L, 100L)

        // Morse "SOS" at 200 ms/unit: dot=1u on, dash=3u on,
        // intra-letter gap=1u off, letter gap=3u off, word gap=7u off.
        private val SOS_PATTERN = longArrayOf(
            200, 200, 200, 200, 200, 600,
            600, 200, 600, 200, 600, 600,
            200, 200, 200, 200, 200, 1400
        )
    }
}
