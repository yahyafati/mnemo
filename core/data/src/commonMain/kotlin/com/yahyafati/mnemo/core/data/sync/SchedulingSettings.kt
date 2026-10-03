package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.FsrsWeights
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant

/**
 * The scheduling settings as one record (ADR 0013): retention, daily limits, the steps and the fitted FSRS weights.
 * Appearance, the reminder, backups and onboarding are per device and never leave it. The record is written the same
 * way every time (fixed order of keys), so two equal settings are equal text, which is how a local edit is noticed.
 */
internal class SchedulingSettings(private val repository: UserSettingsRepository) {
    suspend fun current(): JsonObject = encode(repository.settings.first())

    /** All the settings, for the scheduler that replays reviews. */
    suspend fun userSettings(): UserSettings = repository.settings.first()

    /** Makes this device's settings the ones in [record]; a field it doesn't understand is ignored. */
    suspend fun write(record: JsonObject) {
        val now = repository.settings.first()
        val wanted = decode(record, now)
        if (wanted.desiredRetention != now.desiredRetention) repository.setDesiredRetention(wanted.desiredRetention)
        if (wanted.newCardsPerDay != now.newCardsPerDay) repository.setNewCardsPerDay(wanted.newCardsPerDay)
        if (wanted.reviewsPerDay != now.reviewsPerDay) repository.setReviewsPerDay(wanted.reviewsPerDay)
        if (wanted.learningSteps != now.learningSteps) repository.setLearningSteps(wanted.learningSteps)
        if (wanted.relearningSteps != now.relearningSteps) repository.setRelearningSteps(wanted.relearningSteps)
        if (wanted.fsrsWeights != now.fsrsWeights) repository.setFsrsWeights(wanted.fsrsWeights)
    }

    private fun encode(settings: UserSettings): JsonObject = buildJsonObject {
        put("desiredRetention", settings.desiredRetention)
        put("newCardsPerDay", settings.newCardsPerDay)
        put("reviewsPerDay", settings.reviewsPerDay)
        put("learningSteps", JsonArray(settings.learningSteps.map { JsonPrimitive(it.toMillis()) }))
        put("relearningSteps", JsonArray(settings.relearningSteps.map { JsonPrimitive(it.toMillis()) }))
        val weights = settings.fsrsWeights
        put(
            "fsrsWeights",
            if (weights == null) {
                JsonNull
            } else {
                buildJsonObject {
                    put("values", JsonArray(weights.values.map { JsonPrimitive(it) }))
                    put("optimizedAt", weights.optimizedAt.toEpochMilli())
                    put("trainingReviews", weights.trainingReviews)
                    put("previousLoss", weights.previousLoss)
                    put("loss", weights.loss)
                }
            },
        )
    }

    /** [record] on top of [base]: what the record doesn't say stays as it is. */
    private fun decode(record: JsonObject, base: UserSettings): UserSettings {
        fun steps(key: String, fallback: List<Duration>) =
            record[key]?.takeIf { it is JsonArray }?.jsonArray?.map { Duration.ofMillis(it.jsonPrimitive.long) } ?: fallback
        return base.copy(
            desiredRetention = record["desiredRetention"]?.jsonPrimitive?.double ?: base.desiredRetention,
            newCardsPerDay = record["newCardsPerDay"]?.jsonPrimitive?.int ?: base.newCardsPerDay,
            reviewsPerDay = record["reviewsPerDay"]?.jsonPrimitive?.int ?: base.reviewsPerDay,
            learningSteps = steps("learningSteps", base.learningSteps),
            relearningSteps = steps("relearningSteps", base.relearningSteps),
            fsrsWeights = if ("fsrsWeights" in record) decodeWeights(record.getValue("fsrsWeights")) else base.fsrsWeights,
        )
    }

    private fun decodeWeights(element: JsonElement): FsrsWeights? {
        if (element !is JsonObject) return null
        return FsrsWeights(
            values = element.getValue("values").jsonArray.map { it.jsonPrimitive.double },
            optimizedAt = Instant.ofEpochMilli(element.getValue("optimizedAt").jsonPrimitive.long),
            trainingReviews = element.getValue("trainingReviews").jsonPrimitive.int,
            previousLoss = element.getValue("previousLoss").jsonPrimitive.double,
            loss = element.getValue("loss").jsonPrimitive.double,
        )
    }

    companion object {
        /** The record as text, which is how it is kept and compared. */
        fun text(record: JsonObject): String = Json.encodeToString(JsonObject.serializer(), record)

        fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
    }
}
