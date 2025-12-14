package com.muort.upworker.core.util

import android.content.Context
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        positiveText: String = "确定",
        negativeText: String = "取消",
        onConfirm: () -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton(negativeText) { dialog, _ ->
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
            .setNegativeButton("取消", null)
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
        positiveText: String = "确定",
        negativeText: String = "取消",
        onConfirm: (String) -> Unit
    ) {
        val editText = android.widget.EditText(context).apply {
            this.hint = hint
            setText(defaultValue)
            setPadding(50, 30, 50, 30)
        }
        
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton(positiveText) { dialog, _ ->
                onConfirm(editText.text.toString())
                dialog.dismiss()
            }
            .setNegativeButton(negativeText, null)
            .show()
    }
    
    /**
     * 显示加载对话框
     */
    fun showLoadingDialog(
        context: Context,
        message: String = "加载中..."
    ) = MaterialAlertDialogBuilder(context)
        .setMessage(message)
        .setCancelable(false)
        .create()
}
