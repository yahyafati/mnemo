package com.yahyafati.mnemo.widget

import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.R
import com.yahyafati.mnemo.core.model.TodaySummary
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The home-screen widget shows today's queue, or the streak once everything is done. */
@RunWith(RobolectricTestRunner::class)
class TodayWidgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun render(summary: TodaySummary) = TodayWidget.views(context, summary).apply(context, FrameLayout(context))

    private fun android.view.View.text(id: Int) = findViewById<TextView>(id).text.toString()

    @Test
    fun showsTodaysQueue() {
        val view = render(TodaySummary(dueCount = 10, newCount = 5, learningCount = 3, estimatedMinutes = 12, streakDays = 4, reviewedToday = 0, totalCards = 200))
        assertEquals("18", view.text(R.id.widget_count))
        assertEquals("cards to study", view.text(R.id.widget_label))
        assertEquals("13 due · 5 new · ~12 min", view.text(R.id.widget_detail))
        assertEquals("18 cards to study today. Opens Mnemo to study.", view.findViewById<android.view.View>(R.id.widget_root).contentDescription)
    }

    @Test
    fun showsTheStreakWhenDone() {
        val view = render(TodaySummary(0, 0, 0, 0, streakDays = 7, reviewedToday = 40, totalCards = 200))
        assertEquals("All caught up", view.text(R.id.widget_label))
        assertEquals("7-day streak", view.text(R.id.widget_detail))
    }
}
