package com.yahyafati.mnemo.core.scheduler.optimizer

import com.yahyafati.mnemo.core.scheduler.FsrsParameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reference data from py-fsrs 6.3.2's optimizer, written by `core/scheduler/fixtures/make_optimizer_fixtures.py`:
 * a simulated learner's review log (`revlog.csv`) and what py-fsrs computes from it (`reference.json`).
 */
class FsrsOptimizerTest {
    private val optimizer = FsrsOptimizer()

    private val reference: JsonObject =
        Json.parseToJsonElement(resource("optimizer/reference.json")).jsonObject

    /** Card id → its history, in card id order (as py-fsrs sorts them). */
    private val histories: Map<Int, FsrsReviewHistory> = resource("optimizer/revlog.csv").lineSequence()
        .drop(1)
        .filter { it.isNotBlank() }
        .map { line -> line.split(',').let { Triple(it[0].toInt(), it[1].toLong(), it[2].toInt()) } }
        .groupBy({ it.first }, { it.second to it.third })
        .toSortedMap()
        .mapValues { (_, reviews) ->
            val sorted = reviews.sortedBy { it.first }
            FsrsReviewHistory(sorted.map { it.first }.toLongArray(), sorted.map { it.second }.toIntArray())
        }

    private val all = histories.values.toList()

    private fun doubles(key: String) = reference.getValue(key).jsonArray.map { it.jsonPrimitive.double }

    private fun double(key: String) = reference.getValue(key).jsonPrimitive.double

    @Test
    fun readsTheWholeReferenceLog() {
        assertEquals(reference.getValue("cards").jsonPrimitive.int, histories.size)
        assertEquals(reference.getValue("reviews").jsonPrimitive.int, all.sumOf { it.size })
        assertTrue(optimizer.trainingReviewCount(all) >= FsrsOptimizer.MIN_TRAINING_REVIEWS)
    }

    @Test
    fun lossMatchesReference() {
        assertEquals(double("loss_default"), optimizer.loss(FsrsParameters.DEFAULT_WEIGHTS, all), 1e-12)
        assertEquals(double("loss_true"), optimizer.loss(doubles("true_parameters"), all), 1e-12)
        assertEquals(double("loss_optimized"), optimizer.loss(doubles("optimized"), all), 1e-12)
    }

    @Test
    fun gradientMatchesAutograd() {
        val (loss, gradient) = optimizer.lossAndGradient(FsrsParameters.DEFAULT_WEIGHTS, all)
        assertEquals(double("loss_default"), loss, 1e-12)
        doubles("gradient_default").forEachIndexed { i, expected ->
            assertEquals(expected, gradient[i], 1e-12, "d loss / d w$i")
        }
    }

    /**
     * Central differences on the loss, away from the defaults: checks every weight's derivative,
     * including the Hard/Easy and short-term terms, independently of the reference.
     */
    @Test
    fun gradientMatchesFiniteDifferences() {
        val weights = doubles("true_parameters")
        val (_, gradient) = optimizer.lossAndGradient(weights, all)
        for (i in weights.indices) {
            val h = 1e-6 * maxOf(1.0, abs(weights[i]))
            val up = optimizer.loss(weights.toMutableList().also { it[i] += h }, all)
            val down = optimizer.loss(weights.toMutableList().also { it[i] -= h }, all)
            val numeric = (up - down) / (2 * h)
            assertEquals(numeric, gradient[i], 1e-6 + 1e-4 * abs(numeric), "d loss / d w$i")
        }
    }

    /** Same data, same card order each epoch: the same weights as py-fsrs. */
    @Test
    fun optimizeMatchesReference() {
        val orders = reference.getValue("epoch_orders").jsonArray.map { epoch ->
            (epoch as JsonArray).map { histories.getValue(it.jsonPrimitive.int) }
        }
        val progress = mutableListOf<Float>()
        val result = optimizer.optimize(
            histories = all,
            shuffle = { epoch, cards ->
                cards.clear()
                cards.addAll(orders[epoch])
            },
            onProgress = { progress += it },
        )

        assertNotNull(result)
        doubles("optimized").forEachIndexed { i, expected ->
            assertEquals(expected, result.weights[i], 1e-9, "w$i")
        }
        assertEquals(double("loss_optimized"), result.loss, 1e-12)
        assertEquals(optimizer.trainingReviewCount(all), result.trainingReviews)
        assertEquals(1f, progress.last())
        assertEquals(progress.sorted(), progress)
    }

    @Test
    fun optimizeImprovesOnTheDefaults() {
        val result = assertNotNull(optimizer.optimize(all, Random(1)))
        val defaultLoss = optimizer.loss(FsrsParameters.DEFAULT_WEIGHTS, all)
        assertTrue(result.loss < defaultLoss, "optimized ${result.loss} vs default $defaultLoss")
        // Valid weights: the scheduler accepts them.
        FsrsParameters(weights = result.weights)
    }

    @Test
    fun tooFewReviewsKeepsTheDefaults() {
        var reviews = 0
        val few = all.takeWhile { history ->
            reviews += optimizer.trainingReviewCount(listOf(history))
            reviews < FsrsOptimizer.MIN_TRAINING_REVIEWS
        }
        assertNull(optimizer.optimize(few))
    }

    /** Analytics must stay responsive with 100k+ reviews (ROADMAP Phase 5); fitting runs in the background. */
    @Test
    fun fitsAHundredThousandReviewsQuickly() {
        val copies = 100_000 / all.sumOf { it.size } + 1
        val large = (0 until copies).flatMap { all }
        assertTrue(large.sumOf { it.size } > 100_000)
        var result: FsrsOptimizerResult?
        val millis = measureTimeMillis { result = optimizer.optimize(large) }
        assertNotNull(result)
        assertTrue(millis < 30_000, "took $millis ms")
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader.getResource(name)) { "Missing test resource $name" }.readText()
}
