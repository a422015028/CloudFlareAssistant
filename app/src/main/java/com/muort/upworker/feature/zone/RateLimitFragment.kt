package com.muort.upworker.feature.zone

import dagger.hilt.android.AndroidEntryPoint

/**
 * 速率限制规则页（phase = http_ratelimit）。
 * 列表 / 启停 / 删除 / 添加均由 [BaseZoneRulesetFragment] 统一实现。
 */
@AndroidEntryPoint
class RateLimitFragment : BaseZoneRulesetFragment() {
    override val phase: String = "http_ratelimit"
    override val addDialogTitle: String = "添加速率限制规则"
}
