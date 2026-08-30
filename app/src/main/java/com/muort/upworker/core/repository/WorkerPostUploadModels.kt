package com.muort.upworker.core.repository

import androidx.annotation.StringRes
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.WorkerScript

/**
 * Identifies each post-upload stage for UI rendering / error reporting order.
 * Order matches the execution sequence in [WorkerRepository.afterUpload].
 */
enum class WorkerPostStageKind {
    Observability,
    Subdomain,
    Deployment
}

/**
 * Result of a single post-upload stage. UI layer only reads [kind] + [messageResId] +
 * [formatArgs], and uses Context.getString to render. Using a sealed class with explicit
 * Success/Failure keeps ViewModel/DiffUtil logic simple (no extra boolean flag).
 */
sealed class WorkerPostActionStage {
    abstract val kind: WorkerPostStageKind

    /**
     * Stage completed successfully.
     * @param messageResId R.string.* id (with format placeholders if needed)
     * @param formatArgs  arguments to pass to Context.getString(resId, *args)
     */
    data class Success(
        override val kind: WorkerPostStageKind,
        @StringRes val messageResId: Int,
        val formatArgs: Array<out Any?> = emptyArray()
    ) : WorkerPostActionStage()

    /**
     * Stage failed (but later stages may still run — see afterUpload policy).
     * @param messageResId R.string.* id (must accept %1$s-style error text)
     * @param formatArgs  arguments to pass to Context.getString(resId, *args); first arg is
     *                    usually the original error message from Cloudflare API / Throwable.
     */
    data class Failure(
        override val kind: WorkerPostStageKind,
        @StringRes val messageResId: Int,
        val formatArgs: Array<out Any?> = emptyArray()
    ) : WorkerPostActionStage()
}

/**
 * Aggregated return value of [WorkerRepository.afterUpload]:
 * @param overallUpload  the original upload result (NEVER downgraded even if later stages fail)
 * @param stages         ordered results of Observability → Subdomain → Deployment (always 3)
 */
data class WorkerAfterUploadResult(
    val overallUpload: Resource<WorkerScript>,
    val stages: List<WorkerPostActionStage>
)

// ============================================================================
// P1-3: detectAndAppendNodejsCompat result model
// ============================================================================

/**
 * Structured result of [WorkerRepository.detectAndAppendNodejsCompat].
 *
 * Design matches P1-3 5 RED tests. All string references route through R.string.worker_nodejs_*
 * via [logResId] + [logFormatArgs] — UI/Repo caller can emit without knowing the Chinese/English
 * text itself.
 *
 * @property finalFlags    Original [existingFlags] with possible de-duplicated
 *                         `"nodejs_compat"` append on pattern hit. Never shrinks the original
 *                         list; keeps original order of existing entries; append at end.
 * @property hitPatterns   User-friendly names of detection patterns that matched (e.g.
 *                         `"require(\" (CJS)"`, `"Buffer.*"`, `"globalThis.process"`).
 *                         Distinct values only; order preserved = order in P1-3 DETECT spec.
 * @property logResId      R.string id referencing one of worker_nodejs_detect_no_hit /
 *                         worker_nodejs_detect_hit_hint_format /
 *                         worker_nodejs_flag_dup_skip_format / worker_nodejs_flag_append_fail_format.
 * @property logFormatArgs Matching format placeholders for [logResId]. 0-arg for no_hit,
 *                         1-arg (pattern-names joined) for hit_hint_format, 1-arg (flag name)
 *                         for dup_skip_format.
 */
data class WorkerNodejsDetectResult(
    val finalFlags: List<String>,
    val hitPatterns: List<String>,
    @StringRes val logResId: Int,
    val logFormatArgs: Array<out Any?>
)
