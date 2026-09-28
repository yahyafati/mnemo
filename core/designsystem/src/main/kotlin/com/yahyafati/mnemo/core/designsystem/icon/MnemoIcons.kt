package com.yahyafati.mnemo.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Style
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The Material Symbols used by the mockups, in one place. Features use these names instead of
 * importing `Icons.*` directly, so icons can later be swapped for bundled Material Symbols assets.
 */
object MnemoIcons {
    // Bottom navigation
    val Decks: ImageVector = Icons.Outlined.LocalLibrary
    val DecksSelected: ImageVector = Icons.Filled.LocalLibrary
    val Study: ImageVector = Icons.Outlined.Style
    val StudySelected: ImageVector = Icons.Filled.Style
    val Create: ImageVector = Icons.Outlined.AutoAwesome
    val CreateSelected: ImageVector = Icons.Filled.AutoAwesome
    val Analytics: ImageVector = Icons.Outlined.Insights
    val AnalyticsSelected: ImageVector = Icons.Filled.Insights

    // Actions and indicators
    val Account: ImageVector = Icons.Outlined.AccountCircle
    val Add: ImageVector = Icons.Outlined.Add
    val ArrowBack: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack
    val ArrowForward: ImageVector = Icons.AutoMirrored.Outlined.ArrowForward
    val Bolt: ImageVector = Icons.Outlined.Bolt
    val CheckCircle: ImageVector = Icons.Outlined.CheckCircle
    val FileUpload: ImageVector = Icons.Outlined.FileUpload
    val LibraryAdd: ImageVector = Icons.Outlined.LibraryAdd
    val Play: ImageVector = Icons.Outlined.PlayArrow
    val Schedule: ImageVector = Icons.Outlined.Schedule
    val Search: ImageVector = Icons.Outlined.Search
    val Settings: ImageVector = Icons.Outlined.Settings
    val Star: ImageVector = Icons.Filled.Star
    val StarOutline: ImageVector = Icons.Outlined.StarOutline
    val Streak: ImageVector = Icons.Outlined.LocalFireDepartment
    val TrendingUp: ImageVector = Icons.AutoMirrored.Outlined.TrendingUp
}
