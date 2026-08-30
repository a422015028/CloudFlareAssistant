package com.muort.upworker.core.repository

import okhttp3.MultipartBody
import okhttp3.RequestBody

/**
 * Progress callback for [PagesRepository.pollDeployment].
 * Parameters: (stageText: String, poll: Int, maxPolls: Int, backoffMs: Long?)
 *  - stageText: Human-readable mapped stage via R.string.pages_poll_stage_*
 *  - poll: Current poll counter (1-based)
 *  - maxPolls: Upper bound supplied by caller
 *  - backoffMs: Delay before *next* poll will be, or null when this is the last
 *    event (success / failure / timeout / aborted — no next poll follows).
 */
typealias PagesPollProgressListener = (
    stageText: String,
    poll: Int,
    maxPolls: Int,
    backoffMs: Long?
) -> Unit

/**
 * Terminal result for Pages deployment Ko-style polling.
 * Strictly aligned with P1-1B PagesPollingTest 6 RED tests behaviours.
 */
sealed class PagesPollResult {
    /** latestStage.name == "success"; aliases copied from [PagesDeployment.aliases]. */
    data class Success(
        val deploymentId: String,
        val projectName: String,
        val aliases: List<String>
    ) : PagesPollResult()

    /** latestStage.name == "failed"; errorMessage extracted from stages/errors if any. */
    data class Failure(
        val latestStageName: String?,
        val errorMessage: String?
    ) : PagesPollResult()

    /** Exhausted [maxPolls] without reaching success / failed terminal. */
    data class Timeout(
        val lastStageName: String?,
        val maxPolls: Int
    ) : PagesPollResult()

    /** HTTP 5xx / parse error / unexpected [Throwable] — bubble up without crashing VM. */
    data class Aborted(val cause: Throwable) : PagesPollResult()
}

// ============================================================================
// P1-2: buildSpecialFormData result model
// ============================================================================

/**
 * Structured result of [PagesRepository.buildSpecialFormData].
 *
 * Contains the pre-processed parts + i18n log events (R.string.* id → formatArgs)
 * so the createDeployment caller can replay them through its onLog callback without
 * re-implementing the form-data-specific Chinese/English string logic.
 *
 * @property workerBody         Ready-to-upload worker payload body; null if the ZIP
 *                              contains neither _worker.js nor _worker.bundle.
 * @property workerName         Form-data filename of the worker ("_worker.js" for
 *                              nested multipart, "_worker.bundle" for binary bundle,
 *                              "" when [workerBody] is null).
 * @property specialParts       Multipart parts for _headers / _redirects / _routes.json.
 * @property specialFileNames   File names 1:1 aligned to [specialParts] ("_headers", etc.)
 *                              so tests can assert part identity by name without reflection.
 * @property logEvents          I18n log events: Pair<R.string resId, formatArgs>. Each
 *                              event references a pages_formdata_* key.
 * @property appliedCompatDate  Auto-injected compatibility_date (e.g. when _worker.js
 *                              is present and caller passed no custom date). null when
 *                              caller already provided a custom date or nothing triggered.
 */
data class PagesSpecialFormDataResult(
    val workerBody: RequestBody?,
    val workerName: String,
    val specialParts: List<MultipartBody.Part>,
    val specialFileNames: List<String>,
    val logEvents: List<Pair<Int, Array<out Any?>>>,
    val appliedCompatDate: String?
)
