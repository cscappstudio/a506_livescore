package com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin

import android.content.Context
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Bộ bắt Exception toàn cục (Global Uncaught Exception Handler)
 * Tự động gửi log crash lên server để thống kê tỷ lệ Stability Index trước khi ứng dụng sập hoàn toàn.
 */
class GlobalCrashHandler(
    private val context: Context,
    private val apiService: LiveScoreApiService,
    private val deviceId: String
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTraceString = sw.toString()

            val errorType = throwable.javaClass.simpleName
            val errorMessage = throwable.message ?: "Unknown error"

            val metadata = mapOf(
                "error_type" to errorType,
                "message" to errorMessage,
                "stack_trace" to stackTraceString.take(2000) // Giới hạn kích thước tránh payload quá lớn
            )

            // Gửi bất đồng bộ trên trackingScope không block luồng chính
            trackingScope.launch {
                try {
                    apiService.logUserAction(
                        actionType = "app_error",
                        deviceId = deviceId,
                        metadata = metadata
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Chuyển tiếp exception cho crash handler mặc định của Android
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
