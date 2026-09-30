package com.yahyafati.mnemo.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities

/**
 * An icon button that says what it does when the pointer rests on it (desktop ROADMAP D7). Where the
 * platform has no mouse ([com.yahyafati.mnemo.core.designsystem.platform.PlatformCapabilities.keyboardAndMouse]
 * off) it is a plain [IconButton].
 *
 * [tooltip] is the text of the tip; put the shortcut in it ("Undo (Ctrl+Z)"). The icon inside
 * [content] still carries the content description for screen readers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemoIconButton(
    onClick: () -> Unit,
    tooltip: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit,
) {
    if (!LocalPlatformCapabilities.current.keyboardAndMouse) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors, content = content)
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = modifier.clickCursor(), enabled = enabled, colors = colors, content = content)
    }
}

/**
 * The common case: one [icon] in a button. [contentDescription] is what a screen reader says and the
 * tooltip shows; [shortcut] is the label of the key that does the same ("Ctrl+Z"), shown after it.
 */
@Composable
fun MnemoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    iconModifier: Modifier = Modifier,
    shortcut: String? = null,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
) {
    MnemoIconButton(
        onClick = onClick,
        tooltip = if (shortcut == null) contentDescription else "$contentDescription ($shortcut)",
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    ) {
        // The default is the button's own content color, which only exists inside the button.
        Icon(icon, contentDescription, iconModifier, tint = if (tint.isSpecified) tint else LocalContentColor.current)
    }
}
