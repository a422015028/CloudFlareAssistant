package com.muort.upworker.feature.zone

import dagger.hilt.android.AndroidEntryPoint

/**
 * Transform Rules 页（phase = http_request_transform，URL/请求头改写）。
 * 列表 / 启停 / 删除 / 添加均由 [BaseZoneRulesetFragment] 统一实现。
 */
@AndroidEntryPoint
class TransformRulesFragment : BaseZoneRulesetFragment() {
    override val phase: String = "http_request_transform"
    override val addDialogTitle: String = "添加 Transform 规则"
}
