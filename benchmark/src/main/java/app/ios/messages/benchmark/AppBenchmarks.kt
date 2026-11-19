package app.ios.messages.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run on a device with the "benchmark" build type installed:
 *   gradle :app:assembleBenchmark :benchmark:connectedBenchmarkAndroidTest
 * The app seeds a synthetic list (bench_items) and can open its sibling app (bench_action) only in that build type.
 */
@RunWith(AndroidJUnit4::class)
class AppBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private val target = "app.ios.messages"
    private val peer = "com.iosphone.contacts"

    private fun grant() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        listOf("android.permission.READ_SMS", "android.permission.READ_CONTACTS", "android.permission.POST_NOTIFICATIONS", "android.permission.READ_PHONE_STATE").forEach { device.executeShellCommand("pm grant " + target + " " + it) }
    }

    /** 1) Cold start to the first frame. */
    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = target,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = { grant(); pressHome() },
    ) {
        startActivityAndWait()
    }

    /** 2) Fling through a seeded list of 5000 items. */
    @Test
    fun scrollList5000() = rule.measureRepeated(
        packageName = target,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = { grant(); pressHome() },
    ) {
        startActivityAndWait { it.putExtra("bench_items", 5000) }
        val list = device.wait(Until.findObject(By.scrollable(true)), 5_000) ?: return@measureRepeated
        list.setGestureMargin(device.displayWidth / 5)
        repeat(6) { list.fling(Direction.DOWN); device.waitForIdle() }
        repeat(3) { list.fling(Direction.UP); device.waitForIdle() }
    }

    /** 3) Hop to the sibling app (shared-element transition when it is installed) and back. */
    @Test
    fun switchBetweenApps() = rule.measureRepeated(
        packageName = target,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = { grant(); pressHome() },
    ) {
        startActivityAndWait {
            it.putExtra("bench_items", 200)
            it.putExtra("bench_action", "open_peer")
        }
        device.wait(Until.hasObject(By.pkg(peer).depth(0)), 5_000)
        device.waitForIdle()
        device.pressBack()
        device.wait(Until.hasObject(By.pkg(target).depth(0)), 5_000)
    }
}
