package app.ios.messages.data

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract

/** Thin wrapper over the system block list (works while we are the default SMS app). Never throws. */
object Blocking {
    fun canBlock(ctx: Context): Boolean =
        runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(ctx) }.getOrDefault(false)

    fun isBlocked(ctx: Context, number: String): Boolean =
        runCatching { BlockedNumberContract.isBlocked(ctx, number) }.getOrDefault(false)

    fun block(ctx: Context, number: String): Boolean = runCatching {
        val v = ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
        ctx.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, v) != null
    }.getOrDefault(false)

    fun unblock(ctx: Context, number: String): Boolean =
        runCatching { BlockedNumberContract.unblock(ctx, number) > 0 }.getOrDefault(false)
}
