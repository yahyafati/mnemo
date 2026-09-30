package com.yahyafati.mnemo.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.resources.Res
import com.yahyafati.mnemo.core.designsystem.resources.mnemo_logo
import org.jetbrains.compose.resources.painterResource

/**
 * The Mnemo logo tile (two cards and a check on the brand's dark square). It is decorative: the
 * app name is always next to it, so it has no content description.
 */
@Composable
fun MnemoLogo(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Image(
        painter = painterResource(Res.drawable.mnemo_logo),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}
