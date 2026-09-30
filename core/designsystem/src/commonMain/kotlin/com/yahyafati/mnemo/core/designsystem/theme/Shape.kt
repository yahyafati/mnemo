package com.yahyafati.mnemo.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// The mockups use tight corners: DEFAULT 2dp, lg 4dp, xl 8dp, full 12dp.
internal val MnemoShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp), // DEFAULT
    small = RoundedCornerShape(4.dp), // rounded-lg: buttons, stat tiles, inputs
    medium = RoundedCornerShape(8.dp), // rounded-xl: deck cards, hero card
    large = RoundedCornerShape(12.dp), // rounded-full: pills, badges
    extraLarge = RoundedCornerShape(16.dp),
)
