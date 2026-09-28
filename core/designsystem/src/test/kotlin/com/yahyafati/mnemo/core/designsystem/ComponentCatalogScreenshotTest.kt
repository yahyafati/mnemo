package com.yahyafati.mnemo.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.yahyafati.mnemo.core.designsystem.component.EmptyState
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.component.MnemoChip
import com.yahyafati.mnemo.core.designsystem.component.MnemoNavigationBar
import com.yahyafati.mnemo.core.designsystem.component.MnemoNavigationBarItem
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.component.StatTile
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Baseline screenshots of every design-system component, in light and dark.
 *
 * Record:  ./gradlew :core:designsystem:recordRoborazziDebug
 * Verify:  ./gradlew :core:designsystem:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1100dp-xhdpi")
class ComponentCatalogScreenshotTest {
    @Test
    fun catalogLight() = captureRoboImage("src/test/screenshots/component_catalog_light.png") {
        MnemoTheme(darkTheme = false) { ComponentCatalog() }
    }

    @Test
    fun catalogDark() = captureRoboImage("src/test/screenshots/component_catalog_dark.png") {
        MnemoTheme(darkTheme = true) { ComponentCatalog() }
    }
}

@Composable
private fun ComponentCatalog() {
    val spacing = MnemoTheme.spacing
    Surface(color = MaterialTheme.colorScheme.background) { CatalogContent() }
}

@Composable
private fun CatalogContent() {
    val spacing = MnemoTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        MnemoTopBar(
            title = "Mnemo",
            windowInsets = WindowInsets(0),
            actions = {
                IconButton(onClick = {}) { Icon(MnemoIcons.Account, contentDescription = null) }
            },
        )
        Column(
            modifier = Modifier.padding(horizontal = spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Section("Type") {
                Text("Ready for your daily 28 cards?", style = MaterialTheme.typography.displayMedium)
                Text("Daily Retention Mix", style = MaterialTheme.typography.headlineMedium)
                Text("What is long-term potentiation?", style = MnemoTheme.typography.studyPromptCompact)
                Text("Curated queue for peak memory consolidation.", style = MaterialTheme.typography.bodySmall)
                Text("14 / 8 / 6", style = MnemoTheme.typography.metricLg)
            }
            Section("Buttons") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    MnemoButton("New Deck", onClick = {}, leadingIcon = MnemoIcons.Add)
                    MnemoButton("Start Session", onClick = {}, style = MnemoButtonStyle.Secondary, trailingIcon = MnemoIcons.ArrowForward)
                    MnemoButton("Skip", onClick = {}, style = MnemoButtonStyle.Text)
                }
            }
            Section("Chips") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MnemoChip("All Decks (6)", selected = true, onClick = {})
                    MnemoChip("Due Today (3)", selected = false, onClick = {})
                    MnemoChip("Starred ★", selected = false, onClick = {})
                }
            }
            Section("Stat tiles") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    StatTile("Streak", "14", unit = "days", icon = MnemoIcons.Streak, iconTint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
                    StatTile("Retained", "94.2%", icon = MnemoIcons.TrendingUp, iconTint = MaterialTheme.colorScheme.secondary, valueColor = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                    StatTile("Mastered", "1,420", icon = MnemoIcons.CheckCircle, modifier = Modifier.weight(1f))
                }
            }
            Section("Empty state") {
                EmptyState(
                    icon = MnemoIcons.Decks,
                    title = "No decks yet",
                    message = "Decks you create or import will appear here.",
                    action = { MnemoButton("New Deck", onClick = {}, leadingIcon = MnemoIcons.Add) },
                )
            }
            Section("Palette") {
                val c = MaterialTheme.colorScheme
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(c.primary, c.primaryContainer, c.secondary, c.secondaryContainer, c.tertiary, c.tertiaryContainer, c.error, c.surfaceContainer, c.surfaceContainerHigh, c.onSurface)
                        .forEach { Swatch(it) }
                }
            }
        }
        Box(Modifier.weight(1f))
        MnemoNavigationBar(windowInsets = WindowInsets(0)) {
            MnemoNavigationBarItem(selected = true, onClick = {}, icon = MnemoIcons.Decks, selectedIcon = MnemoIcons.DecksSelected, label = "Decks")
            MnemoNavigationBarItem(selected = false, onClick = {}, icon = MnemoIcons.Study, label = "Study")
            MnemoNavigationBarItem(selected = false, onClick = {}, icon = MnemoIcons.Create, label = "Create")
            MnemoNavigationBarItem(selected = false, onClick = {}, icon = MnemoIcons.Analytics, label = "Analytics")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        Text(
            text = title.uppercase(),
            style = MnemoTheme.typography.metricSm,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        Modifier
            .size(28.dp)
            .background(color, MaterialTheme.shapes.small),
    )
}
