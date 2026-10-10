package com.duobudget.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duobudget.app.ui.theme.ThemeMode
import com.duobudget.app.ui.theme.ThemePreferences
import com.duobudget.app.ui.theme.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class ThemeLayoutInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun visibleBounds(label: String): Rect {
        val matcher = hasText(label) or hasContentDescription(label)
        val nodes = rule.onAllNodes(matcher).fetchSemanticsNodes()
        assertTrue("Missing UI node: $label", nodes.isNotEmpty())
        val node = nodes.firstOrNull { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
            ?: error("No measurable node for $label")
        return node.boundsInRoot
    }

    private fun assertInsideRoot(label: String): Rect {
        val root = rule.onNodeWithTag("app").fetchSemanticsNode().boundsInRoot
        val b = visibleBounds(label)
        assertTrue("$label left is outside root: $b / $root", b.left >= root.left - 1f)
        assertTrue("$label top is outside root: $b / $root", b.top >= root.top - 1f)
        assertTrue("$label right is outside root: $b / $root", b.right <= root.right + 1f)
        assertTrue("$label bottom is outside root: $b / $root", b.bottom <= root.bottom + 1f)
        return b
    }

    private fun assertSameRect(expected: Rect, actual: Rect, label: String) {
        assertEquals("$label left moved", expected.left, actual.left, 1f)
        assertEquals("$label top moved", expected.top, actual.top, 1f)
        assertEquals("$label right moved", expected.right, actual.right, 1f)
        assertEquals("$label bottom moved", expected.bottom, actual.bottom, 1f)
    }

    private fun recommendedMode(style: ThemeStyle) = when (style) {
        ThemeStyle.BOTANICAL, ThemeStyle.NEON, ThemeStyle.LUXURY, ThemeStyle.CYBER -> ThemeMode.LIGHT
        ThemeStyle.MINIMAL, ThemeStyle.FAMILY, ThemeStyle.AURORA, ThemeStyle.SAGE, ThemeStyle.MATERIAL, ThemeStyle.CORAL -> ThemeMode.DARK
    }

    private fun previewName(style: ThemeStyle) = when (style) {
        ThemeStyle.BOTANICAL -> "01-classic-light"
        ThemeStyle.MINIMAL -> "02-classic-dark"
        ThemeStyle.NEON -> "03-mountains"
        ThemeStyle.FAMILY -> "04-forest"
        ThemeStyle.AURORA -> "05-city"
        ThemeStyle.SAGE -> "06-sunset"
        ThemeStyle.MATERIAL -> "07-ocean"
        ThemeStyle.LUXURY -> "08-minimal"
        ThemeStyle.CORAL -> "09-purple-night"
        ThemeStyle.CYBER -> "10-golden-sand"
    }

    private fun shot(name: String) {
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val bytes = ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
                output.toByteArray()
            }
            bitmap.recycle()
            val target = File(rule.activity.filesDir, "theme-preview-$name.jpg")
            target.writeBytes(bytes)
            check(target.length() > 1024L) { "Theme preview is unexpectedly small: $name" }
        }
    }

    @Test
    fun allThemesAndAnimationKeepPrimaryLayoutStable() {
        rule.waitForIdle()
        val labels = listOf("Наш бюджет", "Главная", "Операции", "Цели", "Ещё")

        ThemeStyle.entries.forEach { style ->
            rule.runOnUiThread {
                ThemePreferences.setStyle(rule.activity, style)
                ThemePreferences.set(rule.activity, recommendedMode(style))
                ThemePreferences.setAnimatedBackground(rule.activity, true)
            }
            rule.waitForIdle()

            val animatedBounds = labels.associateWith { assertInsideRoot(it) }
            val nav = listOf("Главная", "Операции", "Цели", "Ещё").map { animatedBounds.getValue(it) }
            nav.zipWithNext().forEachIndexed { index, (left, right) ->
                assertTrue(
                    "Bottom navigation labels overlap for $style at $index: $left / $right",
                    left.right <= right.left + 1f
                )
            }
            shot(previewName(style))

            rule.runOnUiThread {
                ThemePreferences.setAnimatedBackground(rule.activity, false)
            }
            rule.waitForIdle()
            labels.forEach { label ->
                assertSameRect(animatedBounds.getValue(label), assertInsideRoot(label), "$style / $label")
            }
        }
    }

    @Test
    fun appearanceControlsFitAndRemainReachable() {
        rule.waitForIdle()
        rule.onAllNodes(hasText("Ещё"))[0].performClick()
        rule.waitForIdle()

        val required = listOf(
            "Классическая светлая",
            "Классическая тёмная",
            "Горные пейзажи",
            "Лесная природа",
            "Городской шик",
            "Закат",
            "Океан",
            "Минимализм",
            "Фиолетовая ночь",
            "Золотой песок",
            "Анимированный фон",
            "Система",
            "Светлая",
            "Тёмная"
        )

        required.forEach { label ->
            val node = rule.onAllNodes(hasText(label))[0]
            runCatching { node.performScrollTo() }
            rule.waitForIdle()
            node.assertIsDisplayed()
        }
    }
}
