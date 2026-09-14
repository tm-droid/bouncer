package com.tanush.bouncer.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal const val TARGET_PACKAGE = "com.tanush.bouncer"
private const val SETTLE_MS = 5_000L
private const val HOME_LOAD_MS = 15_000L
private const val TAP_SETTLE_MS = 1_500L
private const val SKIP_ATTEMPTS = 3

/**
 * Records the classes and methods the app runs on its hottest paths, so they ship AOT-compiled
 * instead of being interpreted and JIT-compiled on the first launches after install.
 *
 * The journey is cold start, scrolling Home, opening a match and swiping its tabs. Each step after
 * startup is best-effort: live data decides whether a match is on screen, and a profile missing
 * one journey is still far better than none.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        grantNotificationPermission()
        startActivityAndWait()
        device.waitForIdle(SETTLE_MS)
        dismissFirstRun()
        scrollHome()
        openFirstMatchAndSwipeTabs()
    }
}

/**
 * A fresh install opens on onboarding, and the first profile this generator produced covered only
 * that screen — no Home, no match detail. Skip persists the onboarding flag, so later iterations
 * start on Home. The notification-permission dialog is dismissed the same way when it appears.
 */
/**
 * The runtime notification-permission dialog opens over onboarding on a fresh install and swallows
 * the Skip tap, which left whole profiles covering onboarding alone. Granting it up front removes
 * the dialog. A failure (pre-33 device, already granted) is irrelevant to the journey.
 */
internal fun MacrobenchmarkScope.grantNotificationPermission() {
    runCatching { device.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.POST_NOTIFICATIONS") }
}

internal fun MacrobenchmarkScope.dismissFirstRun() {
    // A tap in the first second after launch is dropped — measured in this generator's own logcat,
    // where Skip was "clicked" 1.1 s after the activity displayed and onboarding never left. So wait
    // for the screen to settle and retry until Skip is actually gone.
    if (device.wait(Until.hasObject(By.text("Skip")), SETTLE_MS) == true) {
        for (attempt in 1..SKIP_ATTEMPTS) {
            Thread.sleep(TAP_SETTLE_MS)
            val skip = device.findObject(By.text("Skip")) ?: break
            skip.click()
            if (device.wait(Until.gone(By.text("Skip")), SETTLE_MS) == true) break
        }
    }
    listOf("Don’t allow", "Don't allow", "Not Now").forEach { label ->
        device.findObject(By.text(label))?.let {
            it.click()
            device.waitForIdle()
        }
    }
}

internal fun MacrobenchmarkScope.scrollHome() {
    // Home's list fills from the network, so give it time rather than looking once.
    val scrollable = device.wait(Until.findObject(By.scrollable(true)), HOME_LOAD_MS) ?: return
    scrollable.setGestureMargin(device.displayWidth / 5)
    scrollable.fling(Direction.DOWN)
    device.waitForIdle()
    scrollable.fling(Direction.UP)
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.openFirstMatchAndSwipeTabs() {
    // Every match card carries a "format · series" line ("1st T20I · UAE tour of Malaysia"), and
    // nothing else on Home uses the separator. A tap on that text lands inside the card's click
    // area; Compose does not expose the card itself as a distinct clickable node to UiAutomator.
    val row = device.wait(Until.findObject(By.textContains(" · ")), SETTLE_MS) ?: return
    row.click()
    device.waitForIdle(SETTLE_MS)
    repeat(3) {
        device.swipe(
            device.displayWidth * 4 / 5, device.displayHeight / 2,
            device.displayWidth / 5, device.displayHeight / 2,
            20,
        )
        device.waitForIdle()
    }
    device.pressBack()
    device.waitForIdle()
}
