package com.yahyafati.mnemo.core.database

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.core.database.dao.DayCount
import com.yahyafati.mnemo.core.database.dao.PassCounts
import com.yahyafati.mnemo.core.database.entity.CardEntity
import com.yahyafati.mnemo.core.database.entity.ReviewLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class StatsDaoTest {
    private lateinit var db: MnemoDatabase
    private val day = 86_400_000L
    private val now = 1_000 * day + 12 * 3_600_000L

    @Before
    fun setUp() {
        db = MnemoDatabase.build(ApplicationProvider.getApplicationContext(), name = null)
    }

    @After
    fun tearDown() = db.close()

    private val stats get() = db.statsDao()

    private fun card(
        id: String,
        state: Int,
        deckId: String = "d1",
        due: Long = now,
        stability: Double? = null,
        lastReview: Long? = null,
        lapses: Int = 0,
        difficulty: Double? = null,
        suspended: Boolean = false,
        deletedAt: Long? = null,
    ) = CardEntity(
        id = id, noteId = "n-$id", deckId = deckId, templateOrd = 0, state = state, due = due,
        stability = stability, difficulty = difficulty, step = null, lastReview = lastReview, reps = 0,
        lapses = lapses, flagged = false, starred = false, suspended = suspended, buriedUntil = null,
        createdAt = 0, updatedAt = 0, deletedAt = deletedAt,
    )

    private fun log(id: String, cardId: String, reviewedAt: Long, rating: Int = 3, stateBefore: Int = 2, deletedAt: Long? = null) =
        ReviewLogEntity(
            id = id, cardId = cardId, rating = rating, stateBefore = stateBefore, reviewedAt = reviewedAt,
            elapsedDays = 0, scheduledDays = 0, durationMs = 5_000, stabilityAfter = 1.0, difficultyAfter = 5.0,
            createdAt = 0, updatedAt = 0, deletedAt = deletedAt,
        )

    @Test
    fun passCountsCoverReviewStateAnswersInTheWindow() = runTest {
        db.reviewLogDao().insertAll(
            listOf(
                log("pass", "c", now - day),
                log("hard", "c", now - day, rating = 2),
                log("fail", "c", now - day, rating = 1),
                log("learning", "c", now - day, rating = 1, stateBefore = 1),
                log("too-old", "c", now - 40 * day, rating = 1),
                log("deleted", "c", now - day, rating = 1, deletedAt = now),
            ),
        )
        assertEquals(PassCounts(reviews = 3, passed = 2), stats.observePassCounts(now - 30 * day, now).first())
        assertEquals(PassCounts(0, 0), stats.observePassCounts(now, now + day).first())
    }

    @Test
    fun dailyReviewsGroupByStudyDay() = runTest {
        // An offset of -4h: a review at 02:00 belongs to the previous study day.
        val offset = -4 * 3_600_000L
        db.reviewLogDao().insertAll(
            listOf(
                log("a", "c", 999 * day + 10 * 3_600_000L),
                log("b", "c", 1_000 * day + 2 * 3_600_000L),
                log("c", "c", 1_000 * day + 10 * 3_600_000L),
                log("old", "c", 900 * day),
            ),
        )
        assertEquals(
            listOf(DayCount(999, 2), DayCount(1_000, 1)),
            stats.observeDailyReviews(from = 990 * day, offsetMs = offset).first(),
        )
    }

    @Test
    fun deckMaturitySplitsByStateAndStability() = runTest {
        db.cardDao().insert(
            listOf(
                card("new", 0),
                card("learning", 1, stability = 1.0),
                card("relearning", 3, stability = 2.0),
                card("young", 2, stability = 5.0),
                card("mature", 2, stability = 30.0),
                card("suspended", 2, stability = 30.0, suspended = true),
                card("deleted", 2, stability = 30.0, deletedAt = now),
                card("other", 2, deckId = "d2", stability = 21.0),
            ),
        )
        val rows = stats.observeDeckMaturity(matureDays = 21.0).first().associateBy { it.deckId }
        val d1 = rows.getValue("d1")
        assertEquals(listOf(1, 2, 1, 1), listOf(d1.newCards, d1.learning, d1.young, d1.mature))
        assertEquals(35.0, d1.reviewStabilitySum, 1e-9)
        assertEquals(1, rows.getValue("d2").mature)
    }

    @Test
    fun retrievabilityBucketsKeepNearbyRatiosTogether() = runTest {
        db.cardDao().insert(
            listOf(
                // 10 days / stability 10 and 20 / 20: ratio 1.0, same bucket.
                card("a", 2, stability = 10.0, lastReview = now - 10 * day),
                card("b", 2, stability = 20.0, lastReview = now - 20 * day),
                // Half a day: 0 whole days, ratio 0.
                card("fresh", 1, stability = 2.0, lastReview = now - day / 2),
                // Far overdue: ratio 100, the last bucket.
                card("overdue", 2, stability = 1.0, lastReview = now - 100 * day),
                card("new", 0),
            ),
        )
        val buckets = stats.observeRetrievabilityBuckets(now).first()
            .map { it.cards to it.meanRatio }
            .sortedBy { it.second }
        assertEquals(listOf(1 to 0.0, 2 to 1.0, 1 to 100.0), buckets)
    }

    @Test
    fun dueForecastCountsOverdueCardsToday() = runTest {
        val todayStart = 1_000 * day
        db.cardDao().insert(
            listOf(
                card("overdue", 2, due = todayStart - 5 * day),
                card("today", 1, due = todayStart + 3_600_000L),
                card("tomorrow", 2, due = todayStart + day + 1),
                card("next-week", 2, due = todayStart + 8 * day),
                card("new", 0, due = todayStart),
                card("suspended", 2, due = todayStart, suspended = true),
            ),
        )
        assertEquals(
            listOf(DayCount(1_000, 2), DayCount(1_001, 1)),
            stats.observeDueForecast(todayStart, today = 1_000, until = todayStart + 7 * day, offsetMs = 0).first(),
        )
    }

    @Test
    fun mostLapsedCardsComeFirst() = runTest {
        db.cardDao().insert(
            listOf(
                card("easy", 2, lapses = 0),
                card("some", 2, lapses = 3, difficulty = 5.0),
                card("some-harder", 2, lapses = 3, difficulty = 8.0),
                card("leech", 2, lapses = 9),
                card("deleted", 2, lapses = 20, deletedAt = now),
            ),
        )
        assertEquals(listOf("leech", "some-harder", "some"), stats.observeMostLapsed(minLapses = 1, limit = 5).first())
        assertEquals(listOf("leech"), stats.observeMostLapsed(minLapses = 1, limit = 1).first())
    }

    @Test
    fun dailyBaselineCountsDaysSinceEachCardsFirstReview() = runTest {
        db.cardDao().insert(listOf(card("old", 2), card("recent", 2), card("deleted", 2, deletedAt = now)))
        db.reviewLogDao().insertAll(
            listOf(
                // First reviewed long before the window: counts from its start (30 days).
                log("o1", "old", now - 100 * day),
                log("o2", "old", now - 5 * day),
                // First reviewed 3.5 days ago: 3 whole days.
                log("r1", "recent", now - 3 * day - day / 2),
                log("d1", "deleted", now - 10 * day),
            ),
        )
        assertEquals(33L, stats.observeDailyReviewBaseline(from = now - 30 * day, now = now).first())
    }

    @Test
    fun reviewPointsComeGroupedByCardOldestFirst() = runTest {
        db.reviewLogDao().insertAll(
            listOf(log("b2", "b", 20), log("a1", "a", 30), log("b1", "b", 10), log("gone", "a", 5, deletedAt = 1)),
        )
        val logs = db.reviewLogDao()
        assertEquals(listOf("a", "b"), logs.getReviewedCardIds())
        assertEquals(listOf("a" to 30L, "b" to 10L, "b" to 20L), logs.getReviewPoints(listOf("a", "b")).map { it.cardId to it.reviewedAt })
    }

    /** ROADMAP Phase 5 exit check: analytics stay responsive with 100k+ reviews. */
    @Test
    fun staysFastWithAHundredThousandReviews() = runTest {
        val cards = 20_000
        val reviewsPerCard = 6
        db.withTransaction {
            db.cardDao().insert(
                List(cards) { i ->
                    card(
                        "c$i", 2, deckId = "d${i % 40}", due = now + (i % 60) * day, stability = 1.0 + i % 90,
                        lastReview = now - (i % 30) * day, lapses = i % 7, difficulty = 1.0 + i % 9,
                    )
                },
            )
            for (chunk in 0 until cards step 1_000) {
                db.reviewLogDao().insertAll(
                    (chunk until chunk + 1_000).flatMap { c ->
                        List(reviewsPerCard) { r -> log("l$c-$r", "c$c", now - (r * 17 + c % 13) * day, rating = 1 + (c + r) % 4) }
                    },
                )
            }
        }

        val millis = measureTimeMillis {
            stats.observePassCounts(now - 30 * day, now).first()
            stats.observeDailyReviews(now - 35 * day, 0).first()
            stats.observeDeckMaturity(21.0).first()
            stats.observeRetrievabilityBuckets(now).first()
            stats.observeDueForecast(now, now / day, now + 30 * day, 0).first()
            stats.observeMostLapsed(1, 10).first()
            stats.observeDailyReviewBaseline(now - 30 * day, now).first()
        }
        assertTrue(millis < 5_000, "Stats queries took $millis ms")

        // The review log is only ever read through an index, never scanned whole.
        val windowed = listOf(
            "SELECT COUNT(*) FROM review_logs WHERE deletedAt IS NULL AND stateBefore = 2 AND reviewedAt >= 0 AND reviewedAt < 1",
            "SELECT reviewedAt / 86400000 AS day, COUNT(*) FROM review_logs WHERE deletedAt IS NULL AND reviewedAt >= 0 GROUP BY day",
        )
        windowed.forEach { sql ->
            val plan = queryPlan(sql)
            assertTrue(plan.any { "review_logs USING INDEX" in it }, "No index for: $sql\n$plan")
        }
    }

    private suspend fun queryPlan(sql: String): List<String> = withContext(Dispatchers.IO) {
        db.query(SimpleSQLiteQuery("EXPLAIN QUERY PLAN $sql")).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("detail"))) }
        }
    }
}
