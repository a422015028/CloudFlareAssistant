package com.muort.upworker.core.model

import android.content.Context
import androidx.annotation.StringRes

sealed class UiMessage {
    /** Static string resource, optionally with format args (Any? array — Context.getString will handle). */
    data class ResourceString(
        @StringRes val resId: Int,
        val args: Array<out Any?> = emptyArray(),
    ) : UiMessage() {
        override fun equals(other: Any?): Boolean =
            other is ResourceString && other.resId == resId && other.args.contentEquals(args)
        override fun hashCode(): Int = 31 * resId + args.contentHashCode()

        override fun asString(context: Context): String =
            if (args.isEmpty()) context.getString(resId)
            else @Suppress("SpreadOperator") context.getString(resId, *args)
    }

    /** Raw dynamic string (server message, exception text, etc.). Never hardcode Chinese here. */
    data class RawString(val value: String) : UiMessage() {
        override fun asString(context: Context): String = value
    }

    /** Empty / no message — useful for MutableStateFlow initial value. */
    data object Empty : UiMessage() {
        override fun asString(context: Context): String = ""
    }

    companion object {
        fun of(@StringRes resId: Int, vararg args: Any?): UiMessage = ResourceString(resId, args)
    }

    /** Resolve this UiMessage to a user-visible String using [context]. */
    abstract fun asString(context: Context): String
}
