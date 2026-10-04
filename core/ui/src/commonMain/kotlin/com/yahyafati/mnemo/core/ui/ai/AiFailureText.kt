package com.yahyafati.mnemo.core.ui.ai

import androidx.compose.runtime.Composable
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.ui.resources.Res
import com.yahyafati.mnemo.core.ui.resources.core_ui_ai_problem_bad_request
import com.yahyafati.mnemo.core.ui.resources.core_ui_ai_problem_images_not_accepted
import com.yahyafati.mnemo.core.ui.resources.core_ui_ai_problem_insecure
import com.yahyafati.mnemo.core.ui.resources.core_ui_ai_problem_invalid
import com.yahyafati.mnemo.core.ui.resources.core_ui_ai_problem_key_unavailable
import com.yahyafati.mnemo.core.ui.resources.core_ui_ai_problem_not_found
import com.yahyafati.mnemo.core.ui.resources.core_ui_ai_problem_rate_limited
import com.yahyafati.mnemo.core.ui.resources.core_ui_ai_problem_server
import com.yahyafati.mnemo.core.ui.resources.core_ui_ai_problem_unauthorized
import com.yahyafati.mnemo.core.ui.resources.core_ui_ai_problem_unknown
import com.yahyafati.mnemo.core.ui.resources.core_ui_ai_problem_unreachable
import org.jetbrains.compose.resources.stringResource

/** A failed AI request in words: "the key was rejected (HTTP 401: Incorrect API key …)". */
@Composable
fun aiFailureText(failure: AiFailure): String {
    val problem = stringResource(
        when (failure.problem) {
            AiProblem.Unauthorized -> Res.string.core_ui_ai_problem_unauthorized
            AiProblem.NotFound -> Res.string.core_ui_ai_problem_not_found
            AiProblem.RateLimited -> Res.string.core_ui_ai_problem_rate_limited
            AiProblem.BadRequest -> Res.string.core_ui_ai_problem_bad_request
            AiProblem.ServerError -> Res.string.core_ui_ai_problem_server
            AiProblem.Unreachable -> Res.string.core_ui_ai_problem_unreachable
            AiProblem.InsecureUrl -> Res.string.core_ui_ai_problem_insecure
            AiProblem.KeyUnavailable -> Res.string.core_ui_ai_problem_key_unavailable
            AiProblem.InvalidResponse -> Res.string.core_ui_ai_problem_invalid
            AiProblem.ImagesNotAccepted -> Res.string.core_ui_ai_problem_images_not_accepted
            AiProblem.Unknown -> Res.string.core_ui_ai_problem_unknown
        },
    )
    return if (failure.detail.isNullOrBlank()) problem else "$problem (${failure.detail})"
}
