package com.duobudget.app

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.duobudget.app.ui.theme.ThemePreferences
import com.duobudget.app.ui.theme.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

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

    @Test
    fun allThemesAndAnimationKeepPrimaryLayoutStable() {
        rule.waitForIdle()
        val labels = listOf("Наш бюджет", "Главная", "Операции", "Цели", "Ещё")

        ThemeStyle.entries.forEach { style ->
            rule.runOnUiThread {
                ThemePreferences.setStyle(rule.activity, style)
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
            "Ботаника",
            "Минимализм",
            "Неон",
            "Семейная",
            "Аврора",
            "Шалфей",
            "Material",
            "Золотой песок",
            "Коралл",
            "Океан",
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
