package com.yahyafati.mnemo.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme

enum class MnemoButtonStyle {
    /** Filled primary: "New Deck". */
    Primary,

    /** Light surface with primary text, for use on colored cards: "Start Session". */
    Secondary,

    /** No container: low-emphasis actions. */
    Text,
}

@Composable
fun MnemoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MnemoButtonStyle = MnemoButtonStyle.Primary,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val buttonColors = when (style) {
        MnemoButtonStyle.Primary -> ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
        )

        MnemoButtonStyle.Secondary -> ButtonDefaults.buttonColors(
            containerColor = colors.surfaceContainerLowest,
            contentColor = colors.primary,
        )

        MnemoButtonStyle.Text -> ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.primary,
        )
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = buttonColors,
        elevation = if (style == MnemoButtonStyle.Text) null else ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
        if (trailingIcon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Preview
@Composable
private fun MnemoButtonPreview() {
    MnemoTheme {
        MnemoButton(text = "New Deck", onClick = {}, leadingIcon = MnemoIcons.Add)
    }
}
