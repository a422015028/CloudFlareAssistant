package com.muort.upworker.feature.zone

import dagger.hilt.android.AndroidEntryPoint

/**
 * 缓存规则页（phase = http_request_cache_settings）。
 * 列表 / 启停 / 删除 / 添加均由 [BaseZoneRulesetFragment] 统一实现。
 */
@AndroidEntryPoint
class CacheRulesFragment : BaseZoneRulesetFragment() {
    override val phase: String = "http_request_cache_settings"
    override val addDialogTitle: String = "添加缓存规则"
}
