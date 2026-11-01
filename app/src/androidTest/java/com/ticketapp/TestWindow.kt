package com.ticketapp

import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream

/** Emulator-only display overrides. Preserve an existing override instead of resetting user settings. */
object TestWindow {
    data class Overrides(val size:String?,val density:String?)
    private fun shell(command:String):String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.bufferedReader().readText() }
        }
    fun capture()=Overrides(
        Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1),
        Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
    )
    fun resize(width:Int,height:Int) {
        shell("wm density 160")
        shell("wm size ${width}x$height")
        val applied=shell("wm size")
        check(applied.contains("Override size: ${width}x$height") || applied.contains("Physical size: ${width}x$height")) {
            "Android clamped the test display size: $applied. Start the emulator with -skin 800x1200; see README.md."
        }
    }
    fun restore(previous:Overrides) {
        shell("wm size ${previous.size?:"reset"}")
        shell("wm density ${previous.density?:"reset"}")
    }
}
