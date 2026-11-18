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
import java.io.File

/**
 * Real IME test on the compact CI emulator. Render the actual application forms,
 * tap their editable fields, type text, and compare their screen bounds with the
 * actual software keyboard window. No calls or personal contacts are used.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardFormsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val appPackage = "com.o2dialer.app"
    private fun editable() = By.pkg(appPackage).clazz("android.widget.EditText")

    private fun reportFailure(name: String) {
        val folder = instrumentation.targetContext.getExternalFilesDir(null) ?: return
        runCatching { device.takeScreenshot(File(folder, "$name.png")) }
        runCatching { device.dumpWindowHierarchy(File(folder, "$name.xml")) }
    }

    private fun hideKeyboardAndWait() {
        device.pressBack()
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            val showing = instrumentation.uiAutomation.windows.any {
                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }
            if (!showing) return
            SystemClock.sleep(150)
        }
        fail("Keyboard did not close")
    }

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
        var edit = device.wait(Until.findObject(editable()), 3_000)
        if (edit == null || edit.visibleBounds.height() <= 0) {
            runCatching {
                UiScrollable(UiSelector().packageName(appPackage).scrollable(true))
                    .scrollIntoView(UiSelector().className("android.widget.EditText"))
            }
            edit = device.wait(Until.findObject(editable()), 5_000)
        }
        return requireNotNull(edit) { "Actual form input was not reachable" }
    }

    private fun assertTypingAboveKeyboard(value: String) {
        findEditable().click()
        val keyboard = waitForKeyboard()
        // Never inject a hardware key into a window while its input target is absent.
        // This is a failure, rather than retrying the edit or bypassing the keyboard.
        assertNotNull("Input lost focus when IME opened",
            device.wait(Until.findObject(editable().focused(true)), 5_000))
        device.executeShellCommand("input text $value")
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            val passed = runCatching {
                val field = device.findObject(editable().focused(true))
                if (field == null) false else {
                    val bounds = field.visibleBounds
                    field.text.orEmpty().contains(value) &&
                        bounds.height() >= 28 && bounds.bottom <= keyboard.top + 2
                }
            }.getOrDefault(false)
            if (passed) return
            SystemClock.sleep(200)
        }
        val field = device.findObject(editable().focused(true))
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
            try {
                assertTypingAboveKeyboard("01000000000")
                hideKeyboardAndWait()
                assertTypingAboveKeyboard("12345")
                assertTrue(findEditable().text.orEmpty().contains("01000000000"))
            } catch (failure: Throwable) {
                reportFailure("paste-numbers")
                throw failure
            }
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
                hideKeyboardAndWait()
                assertTypingAboveKeyboard("Again")
                assertTrue(findEditable().text.orEmpty().contains("KeyboardCheck"))
            } catch (failure: Throwable) {
                reportFailure("feedback")
                throw failure
            } finally {
                scenario.onActivity { Store.lists.removeAll { it.id == listId } }
            }
        }
    }
}
