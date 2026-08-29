package com.muort.upworker.feature

import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.RadioGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.muort.upworker.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Worker / Pages 运行时设置对话框共用的 UI 行为 */

/** Cloudflare 官方常用兼容性标志（对标网页版快速添加） */
val COMMON_COMPATIBILITY_FLAGS = arrayOf(
    "nodejs_compat",
    "nodejs_compat_v2",
    "global_fetch_strictly_public",
    "browser_rendering",
    "durable_object_fetch_allows_ambiguous_bindings",
    "transformstream_enable_standard_webidl",
    "experimental_service_binding_extra_handlers",
    "fetch_refuses_unknown_protocols",
)

/** 从当前 View 向上查找包裹它的 TextInputLayout */
private fun findParentTextInputLayout(v: View): TextInputLayout? {
    var parent: View? = v.parent as? View
    while (parent != null) {
        if (parent is TextInputLayout) return parent
        parent = parent.parent as? View
    }
    return null
}

/** 点输入框尾部日历图标弹 MaterialDatePicker，结果写入 dateInput */
fun TextInputLayout.attachDatePicker(fragment: Fragment, dateInput: TextInputEditText) {
    setEndIconOnClickListener {
        val initial = try {
            LocalDate.parse(dateInput.text?.toString()?.trim() ?: "")
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(context.getString(R.string.runtime_compat_date_picker_title))
            .setSelection(initial ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            dateInput.setText(
                Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC"))
                    .toLocalDate().toString()
            )
        }
        picker.show(fragment.childFragmentManager, "compatibility_date_picker")
    }
}

/**
 * 在兼容性标志多行输入框所在的 TIL 右端手动设置下拉箭头图标（与项目名称下拉框视觉一致），
 * 点击箭头弹出"常用标志快速添加"下拉。
 * - 不使用 Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu 样式，
 *   因为该样式会强制要求内部控件是 AutoCompleteTextView，会破坏我们的多行/手动输入能力。
 * - 行为：选中任意标志自动追加（换行分隔、自动去重），光标移到末尾，不替换现有文本。
 */
fun TextInputEditText.attachInlineFlagSuggestions() {
    val til = findParentTextInputLayout(this) ?: return
    val ctx = this.context
    // 使用项目内置的下拉箭头 drawable（与 ExposedDropdownMenu 视觉一致），
    // 不引用 Material 库 private 资源以避免 PrivateResource lint 报错。
    til.endIconMode = TextInputLayout.END_ICON_CUSTOM
    til.setEndIconDrawable(R.drawable.ic_dropdown_arrow)
    til.setEndIconOnClickListener {
        val popup = ListPopupWindow(ctx)
        popup.setAdapter(
            ArrayAdapter(ctx, android.R.layout.simple_list_item_1, COMMON_COMPATIBILITY_FLAGS)
        )
        popup.anchorView = til
        popup.isModal = true
        popup.width = til.width
        popup.setOnItemClickListener { _, _, position, _ ->
            val flag = COMMON_COMPATIBILITY_FLAGS[position]
            val existing = this.text?.toString()?.trim() ?: ""
            val current = existing.split(Regex("[,\n]")).map { it.trim() }
            if (flag !in current) {
                val next = if (existing.isEmpty()) flag else "$existing\n$flag"
                this.setText(next)
                this.setSelection(next.length)
            }
            popup.dismiss()
        }
        popup.show()
    }
}

// ==================== 放置模式（默认 / 智能 / 区域 / 服务） ====================

private fun placementRadioId(mode: String?): Int = when (mode) {
    "smart" -> R.id.placementSmart
    "region" -> R.id.placementRegion
    "service" -> R.id.placementService
    else -> R.id.placementOff
}

/**
 * 放置单选组回显 + region/host 附加输入框联动显示。
 * Pages 端不支持区域/服务，不传这两个输入框即可。
 */
fun RadioGroup.bindPlacement(regionLayout: View?, hostLayout: View?, mode: String?) {
    check(placementRadioId(mode))
    setOnCheckedChangeListener { _, _ -> syncPlacementExtras(regionLayout, hostLayout) }
    syncPlacementExtras(regionLayout, hostLayout)
}

private fun RadioGroup.syncPlacementExtras(regionLayout: View?, hostLayout: View?) {
    regionLayout?.visibility =
        if (checkedRadioButtonId == R.id.placementRegion) View.VISIBLE else View.GONE
    hostLayout?.visibility =
        if (checkedRadioButtonId == R.id.placementService) View.VISIBLE else View.GONE
}

/** 放置单选组读取 @return Triple(模式, region 值, host 值) */
fun RadioGroup.readPlacement(
    regionInput: TextInputEditText?,
    hostInput: TextInputEditText?,
): Triple<String, String?, String?> = when (checkedRadioButtonId) {
    R.id.placementSmart -> Triple("smart", null, null)
    R.id.placementRegion -> Triple(
        "region",
        regionInput?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
        null,
    )
    R.id.placementService -> Triple(
        "service",
        null,
        hostInput?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
    )
    else -> Triple("standard", null, null)
}
