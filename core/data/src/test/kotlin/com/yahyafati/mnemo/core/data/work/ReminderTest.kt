package com.yahyafati.mnemo.core.data.work

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.yahyafati.mnemo.core.data.repository.WorkManagerReminderRepository
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.entity.CardEntity
import com.yahyafati.mnemo.core.database.entity.DeckEntity
import com.yahyafati.mnemo.core.model.ReminderSettings
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32]) // Before notification permission: posting needs no grant.
class ReminderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = MnemoDatabase.build(context, name = null)
    private val clock = TestClock(Instant.parse("2026-05-04T06:00:00Z"), ZoneId.of("UTC"))
    private val settings = FakeUserSettingsRepository()
    private lateinit var workManager: WorkManager
    private lateinit var reminders: WorkManagerReminderRepository

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        reminders = WorkManagerReminderRepository(workManager, settings, clock)
    }

    @After
    fun tearDown() = db.close()

    private fun pending(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(ReminderWorker.UNIQUE_NAME).get()
        .filter { !it.state.isFinished }

    @Test
    fun turningTheReminderOnSchedulesItAndOffCancelsIt() = runTest {
        reminders.setReminder(ReminderSettings(enabled = true, time = LocalTime.of(19, 30)))
        assertEquals(ReminderSettings(true, LocalTime.of(19, 30)), settings.settings.first().reminder)
        val work = pending().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        // 06:00 → 19:30 the same day.
        assertEquals((13 * 3600 + 1800) * 1000L, work.initialDelayMillis)

        reminders.setReminder(ReminderSettings(enabled = false, time = LocalTime.of(19, 30)))
        assertTrue(pending().isEmpty())
    }

    @Test
    fun theWorkerNotifiesOnlyWhenCardsAreDueAndSchedulesTomorrow() = runTest {
        settings.setReminder(ReminderSettings(enabled = true, time = LocalTime.of(7, 0)))
        val worker = worker()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        assertTrue(notifications.isEmpty(), "Nothing to study: no notification")
        assertEquals(1, pending().size)

        db.deckDao().upsert(DeckEntity("d", null, "Deck", "", null, false, 0, 0))
        db.cardDao().insert(
            listOf(CardEntity("c", "n", "d", 0, 2, due = 0, 1.0, 5.0, null, 0, 1, 0, false, false, false, null, 0, 0)),
        )
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        val posted = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("1 card is waiting for you today.", shadowOf(posted).contentText)
    }

    private fun worker(): ReminderWorker = TestListenableWorkerBuilder<ReminderWorker>(context)
        .setWorkerFactory(
            object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    ReminderWorker(appContext, workerParameters, db.deckDao(), settings, reminders, clock)
            },
        )
        .build()
}
