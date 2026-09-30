package com.yahyafati.mnemo.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.designsystem.component.EmptyState
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.CurvePoint
import com.yahyafati.mnemo.core.model.DailyReviews
import com.yahyafati.mnemo.core.model.DeckMaturity
import com.yahyafati.mnemo.core.model.DeckRetention
import com.yahyafati.mnemo.core.model.DueForecast
import com.yahyafati.mnemo.core.model.ForgettingCurve
import com.yahyafati.mnemo.core.model.RetentionStats
import com.yahyafati.mnemo.core.model.ReviewPassCounts
import com.yahyafati.mnemo.core.ui.chart.ForecastBars
import com.yahyafati.mnemo.core.ui.chart.ForgettingCurveChart
import com.yahyafati.mnemo.core.ui.chart.HeatmapDay
import com.yahyafati.mnemo.core.ui.chart.ReviewHeatmap
import com.yahyafati.mnemo.core.ui.chart.StackedBar
import com.yahyafati.mnemo.core.ui.scroll.ScrollbarFor
import com.yahyafati.mnemo.feature.analytics.resources.Res
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_active_days
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_active_days_label
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_activity_subtitle
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_activity_title
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_curve_description
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_curve_passive
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_curve_passive_end
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_curve_scheduled
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_curve_subtitle
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_curve_title
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_day_future
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_day_reviews
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_deck_mature_share
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_deck_not_studied
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_deck_recall
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_deck_studied
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_decks_count
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_decks_title
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_edit_card
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_empty_message
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_empty_title
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_forecast_bars
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_forecast_count
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_forecast_kicker
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_forecast_minutes
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_forecast_range
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_forecast_subtitle
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_forecast_title
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_forecast_tomorrow
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_forecast_total
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_hardest_subtitle
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_hardest_title
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_intensity
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_intensity_low
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_intensity_peak
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kicker
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_delta_down
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_delta_up
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_efficiency
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_efficiency_note
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_hours
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_minutes
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_retention
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_retention_none
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_retention_target
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_stability
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_stability_note
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_stability_value
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_volume
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_kpi_volume_note
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_lapses
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_learning
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_leech
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_mature
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_model_default
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_model_fitted
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_new
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_none
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_streak
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_title
import com.yahyafati.mnemo.feature.analytics.resources.feature_analytics_young
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.math.abs

@Composable
internal fun AnalyticsScreen(
    onEditNote: (noteId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AnalyticsScreen(uiState = uiState, onEditNote = onEditNote, modifier = modifier)
}

@Composable
internal fun AnalyticsScreen(
    uiState: AnalyticsUiState,
    onEditNote: (noteId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        AnalyticsUiState.Loading -> Box(modifier.fillMaxSize())
        AnalyticsUiState.Empty -> Box(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = MnemoIcons.Analytics,
                title = stringResource(Res.string.feature_analytics_empty_title),
                message = stringResource(Res.string.feature_analytics_empty_message),
            )
        }
        is AnalyticsUiState.Ready -> Ready(uiState, onEditNote, modifier)
    }
}

@Composable
private fun Ready(state: AnalyticsUiState.Ready, onEditNote: (String) -> Unit, modifier: Modifier) {
    val spacing = MnemoTheme.spacing
    val stats = state.stats
    val listState = rememberLazyListState()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = spacing.screenMargin, end = spacing.screenMargin, top = spacing.md, bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(key = "header") { Header(stats) }
            item(key = "kpis") { Kpis(stats) }
            item(key = "curve") { CurveCard(stats.forgettingCurve, stats.desiredRetention) }
            item(key = "activity") { ActivityCard(state) }
            if (stats.decks.isNotEmpty()) {
                item(key = "decks-title") {
                    SectionTitle(
                        title = stringResource(Res.string.feature_analytics_decks_title),
                        trailing = pluralStringResource(Res.plurals.feature_analytics_decks_count, stats.decks.size, stats.decks.size),
                    )
                }
                items(stats.decks, key = { "deck-${it.deckId}" }) { DeckCard(it) }
            }
            item(key = "forecast") { ForecastCard(stats.forecast, stats.averageAnswer, stats.desiredRetention) }
            if (state.hardestCards.isNotEmpty()) {
                item(key = "hardest") { HardestCards(state.hardestCards, onEditNote) }
            }
        }
        ScrollbarFor(listState)
    }
}

@Composable
private fun Header(stats: RetentionStats) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
            Icon(MnemoIcons.Analytics, null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(Res.string.feature_analytics_kicker).uppercase(),
                style = MnemoTheme.typography.metricSm,
                color = colors.primary,
            )
        }
        Text(
            text = stringResource(Res.string.feature_analytics_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        val weights = stats.fsrsWeights
        Text(
            text = if (weights == null) {
                stringResource(Res.string.feature_analytics_model_default)
            } else {
                pluralStringResource(
                    Res.plurals.feature_analytics_model_fitted,
                    weights.trainingReviews,
                    count(weights.trainingReviews),
                    weights.optimizedAt.atZone(ZoneOffset.systemDefault()).toLocalDate()
                        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun Kpis(stats: RetentionStats) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            val rate = stats.retention.rate
            KpiTile(
                label = stringResource(Res.string.feature_analytics_kpi_retention),
                value = rate?.let(::percent) ?: stringResource(Res.string.feature_analytics_none),
                note = if (rate == null) {
                    stringResource(Res.string.feature_analytics_kpi_retention_none)
                } else {
                    stringResource(Res.string.feature_analytics_kpi_retention_target, percent(stats.desiredRetention))
                },
                valueColor = when {
                    rate == null -> colors.onSurface
                    rate >= stats.desiredRetention - 0.02 -> colors.secondary
                    else -> colors.tertiary
                },
                badge = { RetentionDelta(stats.retention, stats.previousRetention) },
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = stringResource(Res.string.feature_analytics_kpi_volume),
                value = count(stats.reviewsLast30Days),
                note = stringResource(Res.string.feature_analytics_kpi_volume_note),
                icon = MnemoIcons.FactCheck,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            KpiTile(
                label = stringResource(Res.string.feature_analytics_kpi_stability),
                value = stats.averageStability?.let { stringResource(Res.string.feature_analytics_kpi_stability_value, decimal(it)) }
                    ?: stringResource(Res.string.feature_analytics_none),
                note = stringResource(Res.string.feature_analytics_kpi_stability_note),
                icon = MnemoIcons.Timelapse,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = stringResource(Res.string.feature_analytics_kpi_efficiency),
                value = duration(stats.timeSaved),
                note = stringResource(Res.string.feature_analytics_kpi_efficiency_note),
                icon = MnemoIcons.Schedule,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KpiTile(
    label: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    badge: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(MnemoTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label.uppercase(),
                    style = MnemoTheme.typography.metricSm.copy(fontSize = 11.sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (icon != null) Icon(icon, null, tint = colors.outline, modifier = Modifier.size(16.dp))
                badge()
            }
            Column {
                Text(
                    text = value,
                    style = MnemoTheme.typography.metricLg.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
                    color = valueColor,
                    maxLines = 1,
                )
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

/** How retention moved against the previous 30 days, as in the mockup's "↑ 2.4%" pill. */
@Composable
private fun RetentionDelta(current: ReviewPassCounts, previous: ReviewPassCounts) {
    val now = current.rate ?: return
    val before = previous.rate ?: return
    val delta = now - before
    if (abs(delta) < 0.001) return
    val up = delta > 0
    val colors = MaterialTheme.colorScheme
    val text = percent(abs(delta))
    val description = stringResource(if (up) Res.string.feature_analytics_kpi_delta_up else Res.string.feature_analytics_kpi_delta_down, text)
    Row(
        modifier = Modifier
            .background(if (up) colors.secondaryContainer else colors.tertiaryContainer, MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (up) colors.onSecondaryContainer else colors.onTertiaryContainer
        Icon(if (up) MnemoIcons.TrendingUp else MnemoIcons.TrendingDown, null, tint = content, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(2.dp))
        Text(text, style = MnemoTheme.typography.metricSm.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = content)
    }
}

@Composable
private fun CurveCard(curve: ForgettingCurve, desiredRetention: Double) {
    val colors = MaterialTheme.colorScheme
    val passiveEnd = curve.passive.last().recall
    Card {
        CardTitle(
            title = stringResource(Res.string.feature_analytics_curve_title),
            subtitle = stringResource(Res.string.feature_analytics_curve_subtitle, percent(desiredRetention)),
            icon = MnemoIcons.ShowChart,
        )
        ForgettingCurveChart(
            curve = curve,
            desiredRetention = desiredRetention,
            description = stringResource(
                Res.string.feature_analytics_curve_description,
                curve.horizonDays,
                curve.reviewDays.joinToString(),
                percent(curve.scheduledAverage),
                percent(passiveEnd),
            ),
            modifier = Modifier.padding(vertical = MnemoTheme.spacing.sm),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.md)) {
            Legend(colors.primaryContainer, stringResource(Res.string.feature_analytics_curve_scheduled))
            Legend(colors.outlineVariant, stringResource(Res.string.feature_analytics_curve_passive))
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.feature_analytics_curve_passive_end, percent(passiveEnd), curve.horizonDays),
                style = MnemoTheme.typography.metricSm.copy(fontSize = 11.sp),
                color = colors.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.weight(1.4f, fill = false),
            )
        }
    }
}

@Composable
private fun ActivityCard(state: AnalyticsUiState.Ready) {
    val colors = MaterialTheme.colorScheme
    val streak = state.stats.streakDays
    val locale = LocalLocale.current.platformLocale
    val weekdays = (0 until 7).map { state.firstDayOfWeek.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale) }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val weeks = state.calendar.map { week ->
        week.map { day ->
            val date = day.date.format(dateFormat)
            HeatmapDay(
                reviews = day.reviews,
                isToday = day.date == state.today,
                description = day.reviews?.let { pluralStringResource(Res.plurals.feature_analytics_day_reviews, it, it, date) }
                    ?: stringResource(Res.string.feature_analytics_day_future, date),
            )
        }
    }
    Card {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                    Text(
                        text = stringResource(Res.string.feature_analytics_activity_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MaterialTheme.typography.headlineSmall.fontFamily),
                        modifier = Modifier.semantics { heading() },
                    )
                    if (streak > 0) {
                        Row(
                            modifier = Modifier
                                .background(colors.tertiaryContainer, MaterialTheme.shapes.extraLarge)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(MnemoIcons.Streak, null, tint = colors.onTertiaryContainer, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = pluralStringResource(Res.plurals.feature_analytics_streak, streak, streak),
                                style = MnemoTheme.typography.metricSm.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = colors.onTertiaryContainer,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(Res.string.feature_analytics_activity_subtitle),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(
                    text = stringResource(Res.string.feature_analytics_active_days, state.activeDays, state.pastDays),
                    style = MnemoTheme.typography.metricLg.copy(fontWeight = FontWeight.Bold),
                    color = colors.primary,
                )
                Text(
                    text = stringResource(Res.string.feature_analytics_active_days_label).uppercase(),
                    style = MnemoTheme.typography.metricSm.copy(fontSize = 10.sp),
                    color = colors.onSurfaceVariant,
                )
            }
        }
        ReviewHeatmap(weeks = weeks, weekdayLabels = weekdays, modifier = Modifier.padding(top = MnemoTheme.spacing.md))
        Row(
            modifier = Modifier.padding(top = MnemoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val small = MnemoTheme.typography.metricSm.copy(fontSize = 10.sp)
            Text(stringResource(Res.string.feature_analytics_intensity), style = small, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(stringResource(Res.string.feature_analytics_intensity_low), style = small, color = colors.onSurfaceVariant)
            listOf(colors.surfaceContainerHigh, colors.primaryContainer.copy(alpha = 0.5f), colors.primary).forEach {
                Box(Modifier.size(10.dp).background(it, MaterialTheme.shapes.small))
            }
            Text(stringResource(Res.string.feature_analytics_intensity_peak), style = small, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeckCard(deck: DeckRetention) {
    val colors = MaterialTheme.colorScheme
    val maturity = deck.maturity
    val studied = maturity.studiedCards
    Card(verticalSpacing = MnemoTheme.spacing.sm) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(deck.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (studied == 0) {
                        stringResource(Res.string.feature_analytics_deck_not_studied)
                    } else {
                        pluralStringResource(Res.plurals.feature_analytics_deck_studied, studied, studied) +
                            (deck.recallNow?.let { " · " + stringResource(Res.string.feature_analytics_deck_recall, percent(it)) } ?: "")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (studied > 0) {
                Text(
                    text = stringResource(Res.string.feature_analytics_deck_mature_share, percent(maturity.mature.toDouble() / studied)),
                    style = MnemoTheme.typography.metricSm.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.onSecondaryContainer,
                    modifier = Modifier
                        .background(colors.secondaryContainer, MaterialTheme.shapes.extraLarge)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        StackedBar(
            listOf(
                maturity.mature to colors.secondary,
                maturity.young to colors.primaryContainer,
                maturity.learning to colors.tertiary.copy(alpha = 0.55f),
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.md)) {
            Legend(colors.secondary, stringResource(Res.string.feature_analytics_mature, maturity.mature))
            Legend(colors.primaryContainer, stringResource(Res.string.feature_analytics_young, maturity.young))
            Legend(colors.tertiary.copy(alpha = 0.55f), stringResource(Res.string.feature_analytics_learning, maturity.learning))
            if (maturity.newCards > 0) Legend(colors.surfaceContainerHigh, stringResource(Res.string.feature_analytics_new, maturity.newCards))
        }
    }
}

@Composable
private fun ForecastCard(forecast: List<DueForecast>, averageAnswer: Duration, desiredRetention: Double) {
    val colors = MaterialTheme.colorScheme
    val onPrimary = colors.onPrimary
    val coming = forecast.drop(1).take(3)
    val busiest = coming.maxOfOrNull { it.cards } ?: 0
    Surface(shape = MaterialTheme.shapes.large, color = colors.primary, contentColor = onPrimary, shadowElevation = 2.dp) {
        Column(Modifier.padding(MnemoTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            Row(
                modifier = Modifier
                    .background(onPrimary.copy(alpha = 0.12f), MaterialTheme.shapes.extraLarge)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MnemoIcons.Upcoming, null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.feature_analytics_forecast_kicker), style = MnemoTheme.typography.metricSm.copy(fontSize = 10.sp))
            }
            Text(
                text = stringResource(Res.string.feature_analytics_forecast_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.feature_analytics_forecast_subtitle, percent(desiredRetention)),
                style = MaterialTheme.typography.bodySmall,
                color = onPrimary.copy(alpha = 0.8f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                coming.forEachIndexed { i, day ->
                    ForecastTile(
                        label = if (i == 0) {
                            stringResource(Res.string.feature_analytics_forecast_tomorrow)
                        } else {
                            day.date.dayOfWeek.getDisplayName(TextStyle.FULL, LocalLocale.current.platformLocale)
                        },
                        day = day,
                        averageAnswer = averageAnswer,
                        busiest = day.cards > 0 && day.cards == busiest,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val bars = forecast.map { it.cards }
            ForecastBars(
                values = bars,
                color = onPrimary.copy(alpha = 0.35f),
                accent = colors.secondaryContainer,
                highlight = { it in 1..3 },
                description = stringResource(Res.string.feature_analytics_forecast_bars, bars.joinToString()),
                modifier = Modifier.padding(top = MnemoTheme.spacing.xs),
            )
            HorizontalDivider(color = onPrimary.copy(alpha = 0.12f))
            Row {
                Text(
                    text = coming.sumOf { it.cards }.let { pluralStringResource(Res.plurals.feature_analytics_forecast_total, it, it) },
                    style = MnemoTheme.typography.metricSm,
                    color = onPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(Res.string.feature_analytics_forecast_range),
                    style = MnemoTheme.typography.metricSm,
                    color = onPrimary.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun ForecastTile(label: String, day: DueForecast, averageAnswer: Duration, busiest: Boolean, modifier: Modifier) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val minutes = (day.cards * averageAnswer.toMillis() / 60_000.0).roundToInt()
    Column(
        modifier = modifier
            .background(onPrimary.copy(alpha = 0.1f), MaterialTheme.shapes.medium)
            .padding(10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = onPrimary.copy(alpha = 0.7f), maxLines = 1)
        Text(
            text = stringResource(Res.string.feature_analytics_forecast_count, day.cards),
            style = MnemoTheme.typography.metricLg.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            color = if (busiest) MaterialTheme.colorScheme.secondaryContainer else onPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(Res.string.feature_analytics_forecast_minutes, minutes),
            style = MnemoTheme.typography.metricSm.copy(fontSize = 10.sp),
            color = onPrimary.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun HardestCards(cards: List<HardCard>, onEditNote: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(verticalSpacing = 0.dp) {
        CardTitle(
            title = stringResource(Res.string.feature_analytics_hardest_title),
            subtitle = pluralStringResource(Res.plurals.feature_analytics_hardest_subtitle, RetentionStats.LEECH_LAPSES, RetentionStats.LEECH_LAPSES),
            icon = MnemoIcons.Leech,
        )
        Spacer(Modifier.size(MnemoTheme.spacing.sm))
        val editLabel = stringResource(Res.string.feature_analytics_edit_card)
        cards.forEachIndexed { i, card ->
            if (i > 0) HorizontalDivider(color = colors.surfaceContainer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = editLabel) { onEditNote(card.noteId) }
                    .padding(vertical = MnemoTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(card.front, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = card.deckName + " · " + pluralStringResource(Res.plurals.feature_analytics_lapses, card.lapses, card.lapses),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (card.isLeech) {
                    Text(
                        text = stringResource(Res.string.feature_analytics_leech),
                        style = MnemoTheme.typography.metricSm.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.onErrorContainer,
                        modifier = Modifier
                            .padding(start = MnemoTheme.spacing.sm)
                            .background(colors.errorContainer, MaterialTheme.shapes.small)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Icon(MnemoIcons.Edit, null, tint = colors.outline, modifier = Modifier.padding(start = MnemoTheme.spacing.sm).size(18.dp))
            }
        }
    }
}

@Composable
private fun Card(
    verticalSpacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(MnemoTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(verticalSpacing), content = content)
    }
}

@Composable
private fun CardTitle(title: String, subtitle: String, icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MaterialTheme.typography.headlineSmall.fontFamily),
                modifier = Modifier.semantics { heading() },
            )
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Icon(icon, null, tint = colors.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String) {
    Row(Modifier.padding(top = MnemoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Text(trailing, style = MnemoTheme.typography.metricSm, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, MaterialTheme.shapes.extraLarge))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MnemoTheme.typography.metricSm.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

private fun percent(value: Double): String = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }.format(value)

private fun decimal(value: Double): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)

private fun count(value: Int): String = NumberFormat.getIntegerInstance().format(value)

@Composable
private fun duration(value: Duration): String {
    val hours = value.toMinutes() / 60.0
    return if (hours >= 1) {
        stringResource(Res.string.feature_analytics_kpi_hours, decimal(hours))
    } else {
        stringResource(Res.string.feature_analytics_kpi_minutes, value.toMinutes().toInt())
    }
}

/** Sample data for the preview and the screenshot tests. */
internal fun sampleAnalyticsState(): AnalyticsUiState.Ready {
    val today = LocalDate.of(2026, 10, 22)
    val curve = ForgettingCurve(
        horizonDays = 60,
        scheduled = (0..240).map { CurvePoint(it / 4.0, 1 - 0.1 * ((it / 4.0) % 12) / 12) },
        reviewDays = listOf(2, 13, 59),
        passive = (0..240).map { CurvePoint(it / 4.0, 1 - 0.4 * it / 240.0) },
    )
    val stats = RetentionStats(
        hasReviews = true,
        desiredRetention = 0.9,
        fsrsWeights = null,
        retention = ReviewPassCounts(1_280, 1_213),
        previousRetention = ReviewPassCounts(1_000, 924),
        reviewsLast30Days = 1_280,
        averageStability = 14.2,
        timeSaved = Duration.ofMinutes(288),
        averageAnswer = Duration.ofSeconds(9),
        forgettingCurve = curve,
        activity = (41 downTo 0).map { DailyReviews(today.minusDays(it.toLong()), (it * 37) % 90) },
        streakDays = 14,
        decks = listOf(
            DeckRetention("a", "Cognitive Neuroscience", DeckMaturity("a", 12, 15, 47, 458, 9_000.0), 0.962),
            DeckRetention("b", "Japanese Kanji N3", DeckMaturity("b", 0, 42, 105, 273, 4_000.0), 0.914),
        ),
        forecast = (0 until 14).map { DueForecast(today.plusDays(it.toLong()), listOf(40, 22, 18, 31, 9, 12, 25)[it % 7]) },
        hardestCards = emptyList(),
    )
    val start = today.minusDays(31)
    return AnalyticsUiState.Ready(
        stats = stats,
        today = today,
        calendar = (0 until 5).map { w ->
            (0 until 7).map { d ->
                val date = start.plusDays(w * 7L + d)
                CalendarDay(date, if (date.isAfter(today)) null else (w * 7 + d) * 13 % 90)
            }
        },
        firstDayOfWeek = DayOfWeek.MONDAY,
        hardestCards = listOf(
            HardCard("c1", "n1", "What does the hippocampus consolidate?", "Neuroscience", 9),
            HardCard("c2", "n2", "漢字: 議", "Kanji N3", 4),
        ),
    )
}

@Preview(showBackground = true, heightDp = 2200)
@Composable
private fun AnalyticsScreenPreview() {
    MnemoTheme {
        AnalyticsScreen(uiState = sampleAnalyticsState(), onEditNote = {})
    }
}
