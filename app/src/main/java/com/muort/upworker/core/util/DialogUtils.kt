package com.muort.upworker.core.util

import android.content.Context
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.R
import com.muort.upworker.databinding.DialogAccountSelectionBinding

/**
 * 统一的Material Design对话框工具类
 * 提供一致的对话框样式和交互体验
 */
object DialogUtils {
    
    /**
     * 显示确认对话框
     */
    fun showConfirmDialog(
        context: Context,
        title: String,
        message: String,
        positiveText: String? = null,
        negativeText: String? = null,
        onConfirm: () -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        val pos = positiveText ?: context.getString(R.string.dialog_utils_positive_confirm)
        val neg = negativeText ?: context.getString(R.string.dialog_utils_negative_cancel)
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(pos) { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton(neg) { dialog, _ ->
                onCancel()
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * 显示单选对话框
     */
    fun showSingleChoiceDialog(
        context: Context,
        title: String,
        items: Array<String>,
        checkedItem: Int = -1,
        onItemSelected: (Int) -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                onItemSelected(which)
                dialog.dismiss()
            }
            .setNegativeButton(context.getString(R.string.dialog_utils_negative_cancel), null)
            .show()
    }
    
    /**
     * 显示输入对话框
     */
    fun showInputDialog(
        context: Context,
        title: String,
        hint: String = "",
        defaultValue: String = "",
        positiveText: String? = null,
        negativeText: String? = null,
        onConfirm: (String) -> Unit
    ) {
        val pos = positiveText ?: context.getString(R.string.dialog_utils_positive_confirm)
        val neg = negativeText ?: context.getString(R.string.dialog_utils_negative_cancel)
        val editText = android.widget.EditText(context).apply {
            this.hint = hint
            setText(defaultValue)
            setPadding(50, 30, 50, 30)
        }
        
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton(pos) { dialog, _ ->
                onConfirm(editText.text.toString())
                dialog.dismiss()
            }
            .setNegativeButton(neg, null)
            .show()
    }
    
    /**
     * 显示加载对话框
     */
    fun showLoadingDialog(
        context: Context,
        message: String? = null
    ) = MaterialAlertDialogBuilder(context)
        .setMessage(message ?: context.getString(R.string.dialog_utils_loading_message))
        .setCancelable(false)
        .create()
}
