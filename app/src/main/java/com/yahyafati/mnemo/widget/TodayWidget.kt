package com.yahyafati.mnemo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.yahyafati.mnemo.MainActivity
import com.yahyafati.mnemo.R
import com.yahyafati.mnemo.core.common.intent.AppIntents
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.domain.GetTodaySummaryUseCase
import com.yahyafati.mnemo.core.model.TodaySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.time.Duration

/**
 * The home-screen widget: today's queue (to review, new, minutes) and a Study button that opens
 * the Daily Mix. Plain RemoteViews: no extra library, and it renders on every launcher.
 *
 * The system refreshes it every 30 minutes (and so across the 4 a.m. day rollover);
 * [TodayWidgetUpdater] refreshes it whenever the counts change while the app is running.
 */
class TodayWidgetProvider : AppWidgetProvider(), KoinComponent {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        // Koin is started in Application.onCreate, before the system delivers any broadcast.
        val getTodaySummary = get<GetTodaySummaryUseCase>()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val summary = getTodaySummary().first()
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, TodayWidget.views(context, summary)) }
            } finally {
                pending.finish()
            }
        }
    }
}

internal object TodayWidget {
    fun views(context: Context, summary: TodaySummary): RemoteViews {
        val resources = context.resources
        val toStudy = summary.totalToStudy
        return RemoteViews(context.packageName, R.layout.widget_today).apply {
            setTextViewText(R.id.widget_count, toStudy.toString())
            setTextViewText(
                R.id.widget_label,
                if (toStudy == 0) resources.getString(R.string.widget_done) else resources.getQuantityString(R.plurals.widget_cards, toStudy),
            )
            setTextViewText(
                R.id.widget_detail,
                if (toStudy == 0) {
                    resources.getQuantityString(R.plurals.widget_streak, summary.streakDays, summary.streakDays)
                } else {
                    resources.getString(R.string.widget_detail, summary.dueCount + summary.learningCount, summary.newCount, summary.estimatedMinutes)
                },
            )
            setContentDescription(
                R.id.widget_root,
                if (toStudy == 0) resources.getString(R.string.widget_done) else resources.getQuantityString(R.plurals.widget_description, toStudy, toStudy),
            )
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AppIntents.EXTRA_OPEN, AppIntents.OPEN_STUDY)
            }
            val pendingIntent = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            setOnClickPendingIntent(R.id.widget_study, pendingIntent)
        }
    }

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
}

/**
 * Pushes new counts to the widgets while the process lives: after every answer, import or edit.
 * Does nothing when no widget is placed. The counts' "today" is fixed when their query starts, so
 * the collection restarts at each study-day boundary.
 */
class TodayWidgetUpdater(
    private val context: Context,
    private val getTodaySummary: GetTodaySummaryUseCase,
    private val clock: Clock,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                val untilRollover = Duration.between(clock.now(), StudyDay.end(clock.now(), clock.zone())).toMillis().coerceAtLeast(1_000)
                withTimeoutOrNull(untilRollover) {
                    getTodaySummary().collect { summary ->
                        val ids = TodayWidget.ids(context)
                        if (ids.isNotEmpty()) {
                            val manager = AppWidgetManager.getInstance(context)
                            ids.forEach { manager.updateAppWidget(it, TodayWidget.views(context, summary)) }
                        }
                    }
                }
            }
        }
    }
}
