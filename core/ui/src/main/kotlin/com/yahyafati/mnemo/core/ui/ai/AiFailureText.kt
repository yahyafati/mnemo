package com.yahyafati.mnemo.core.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.ui.R

/** A failed AI request in words: "the key was rejected (HTTP 401: Incorrect API key …)". */
@Composable
fun aiFailureText(failure: AiFailure): String {
    val problem = stringResource(
        when (failure.problem) {
            AiProblem.Unauthorized -> R.string.core_ui_ai_problem_unauthorized
            AiProblem.NotFound -> R.string.core_ui_ai_problem_not_found
            AiProblem.RateLimited -> R.string.core_ui_ai_problem_rate_limited
            AiProblem.BadRequest -> R.string.core_ui_ai_problem_bad_request
            AiProblem.ServerError -> R.string.core_ui_ai_problem_server
            AiProblem.Unreachable -> R.string.core_ui_ai_problem_unreachable
            AiProblem.InsecureUrl -> R.string.core_ui_ai_problem_insecure
            AiProblem.KeyUnavailable -> R.string.core_ui_ai_problem_key_unavailable
            AiProblem.InvalidResponse -> R.string.core_ui_ai_problem_invalid
            AiProblem.Unknown -> R.string.core_ui_ai_problem_unknown
        },
    )
    return if (failure.detail.isNullOrBlank()) problem else "$problem (${failure.detail})"
}
