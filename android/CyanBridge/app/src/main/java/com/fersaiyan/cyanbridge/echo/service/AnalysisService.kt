package com.fersaiyan.cyanbridge.echo.service

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Mock AI analysis.
 *
 * THIS FILE IS THE HARDWARE SEAM. To go from demo to real ECHO glasses,
 * replace the body of [analyzePerson] with:
 *
 *   glasses camera (BLE control -> Wi-Fi Direct transfer) -> JPEG frame
 *     -> expression model (relay API or on-device) -> AnalysisOutcome
 *
 * Keep the contract below and nothing else in the app has to change:
 *   returns [AnalysisOutcome.Success] with expression + confidence 0..1
 *   returns [AnalysisOutcome.Failure] with NO_PERSON or ANALYSIS_FAILED
 *   throws  CancellationException when the caller's scope is cancelled
 *
 * Cancellation is cooperative here exactly as it was in the JS build: the UI
 * lets the user cancel an in-flight capture by double-tapping again, and a
 * capture that ignores cancellation will hold the glasses session lease open.
 * Any real implementation MUST stay cancellable — see
 * `glasses/GlassesSessionCoordinator.kt` for the lease rules.
 */
sealed interface AnalysisOutcome {
    data class Success(val expression: String, val confidence: Float) : AnalysisOutcome
    data class Failure(val error: AnalysisError) : AnalysisOutcome
}

enum class AnalysisError { NO_PERSON, ANALYSIS_FAILED }

/** Demo tuning. The real pipeline has no equivalent, so these live here only. */
private object Mock {
    const val MIN_LATENCY_MS = 1800L
    const val JITTER_MS = 800L
    const val SUCCESS_RATE = 0.72f
    const val NO_PERSON_RATE = 0.16f // remainder is ANALYSIS_FAILED
}

private val SUCCESS_POOL = listOf(
    AnalysisOutcome.Success("Smiling", 0.87f),
    AnalysisOutcome.Success("Neutral", 0.74f),
    AnalysisOutcome.Success("Surprised", 0.81f),
    AnalysisOutcome.Success("Focused", 0.69f),
)

suspend fun analyzePerson(): AnalysisOutcome {
    delay(Mock.MIN_LATENCY_MS + Random.nextLong(Mock.JITTER_MS))
    val r = Random.nextFloat()
    return when {
        r < Mock.SUCCESS_RATE -> SUCCESS_POOL.random()
        r < Mock.SUCCESS_RATE + Mock.NO_PERSON_RATE ->
            AnalysisOutcome.Failure(AnalysisError.NO_PERSON)
        else -> AnalysisOutcome.Failure(AnalysisError.ANALYSIS_FAILED)
    }
}

/**
 * Spoken form of a result. Kept next to the result shape so the wording and
 * the data can never drift apart.
 */
fun speakResult(expression: String, pct: Int): String =
    "The person appears to be ${expression.lowercase()}. Confidence $pct percent."

fun speakFailure(error: AnalysisError): String = when (error) {
    AnalysisError.NO_PERSON -> "No person detected in front of you."
    AnalysisError.ANALYSIS_FAILED -> "I couldn't identify an expression. Please try again."
}
