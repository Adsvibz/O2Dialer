package com.o2dialer.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Real IME test on the compact CI emulator. Render the actual application forms,
 * tap their editable fields, type text, and compare their screen bounds with the
 * actual software keyboard window. No calls or personal contacts are used.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardFormsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private fun waitForKeyboard(): Rect {
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        val deadline = SystemClock.uptimeMillis() + 12_000
        while (SystemClock.uptimeMillis() < deadline) {
            val keyboard = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            if (keyboard != null) {
                val bounds = Rect()
                keyboard.getBoundsInScreen(bounds)
                if (bounds.height() > 0 && bounds.bottom <= device.displayHeight + 2) {
                    SystemClock.sleep(500)
                    return bounds
                }
            }
            SystemClock.sleep(200)
        }
        throw AssertionError("Software keyboard window never appeared")
    }

    private fun findEditable(): UiObject2 {
        var edit = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 3_000)
        if (edit == null || edit.visibleBounds.height() <= 0) {
            runCatching {
                UiScrollable(UiSelector().scrollable(true))
                    .scrollIntoView(UiSelector().className("android.widget.EditText"))
            }
            edit = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)
        }
        return requireNotNull(edit) { "Actual form input was not reachable" }
    }

    private fun assertTypingAboveKeyboard(value: String) {
        findEditable().click()
        val keyboard = waitForKeyboard()
        device.executeShellCommand("input text $value")
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            val passed = runCatching {
                val field = device.findObject(By.clazz("android.widget.EditText").focused(true))
                if (field == null) false else {
                    val bounds = field.visibleBounds
                    field.text.orEmpty().contains(value) &&
                        bounds.height() >= 28 && bounds.bottom <= keyboard.top + 2
                }
            }.getOrDefault(false)
            if (passed) return
            SystemClock.sleep(200)
        }
        val field = device.findObject(By.clazz("android.widget.EditText").focused(true))
        fail("Typed field not visible above IME: keyboardTop=${keyboard.top}, focusedBounds=${field?.visibleBounds}")
    }

    @Test fun pasteNumbersKeepsTypedTextAboveRealKeyboard() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                check(!Store.campActive()) { "Use an isolated test emulator" }
                activity.setContent {
                    AppTheme { BulkDialog(onDismiss = {}, onAdd = {}) }
                }
            }
            assertTypingAboveKeyboard("01000000000")
            device.pressBack()
            SystemClock.sleep(600)
            assertTypingAboveKeyboard("12345")
        }
    }

    @Test fun feedbackKeepsTypedTextAboveRealKeyboard() {
        val listId = UUID.randomUUID().toString()
        val entry = Entry(number = "01000000000", status = St.ANSWERED)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                check(!Store.campActive()) { "Use an isolated test emulator" }
                Store.lists.add(CallList(id = listId, name = "Keyboard CI fixture", entries = listOf(entry)))
                activity.setContent {
                    AppTheme { ReferenceStageSheet(listId, entry.id) }
                }
            }
            try {
                assertTypingAboveKeyboard("KeyboardCheck")
                device.pressBack()
                SystemClock.sleep(600)
                assertTypingAboveKeyboard("Again")
            } finally {
                scenario.onActivity { Store.lists.removeAll { it.id == listId } }
            }
        }
    }
}
