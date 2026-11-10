package com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Quản lý theo dõi di chuyển màn hình và thời gian lưu lại (Duration) ở mỗi màn hình.
 * Tự động tính toán session_id độc lập (Reset nếu không hoạt động > 30 phút).
 */
object ScreenTracker {
    private var currentScreen: String? = null
    private var screenStartTime: Long = 0
    private var sessionId: String = ""
    private var lastActiveTime: Long = 0
    
    private const val SESSION_TIMEOUT_MS = 30 * 60 * 1000 // 30 phút

    /**
     * Lấy ID phiên hiện tại. Nếu chưa có hoặc đã quá thời gian chờ (30 phút inactive),
     * tự động làm mới và reset bộ đếm quảng cáo của phiên cũ.
     */
    @Synchronized
    fun getSessionId(): String {
        val now = System.currentTimeMillis()
        if (sessionId.isEmpty() || (now - lastActiveTime > SESSION_TIMEOUT_MS)) {
            sessionId = UUID.randomUUID().toString()
            AdSessionTracker.resetSession()
        }
        lastActiveTime = now
        return sessionId
    }

    /**
     * Cập nhật thời điểm hoạt động cuối cùng của người dùng để duy trì session.
     */
    fun updateLastActiveTime() {
        lastActiveTime = System.currentTimeMillis()
    }

    /**
     * Ghi nhận mở màn hình mới.
     * Tự động tính toán thời gian ở lại màn hình trước đó và gửi log ngầm lên server.
     *
     * @param apiService Retrofit API Service
     * @param deviceId ID thiết bị duy nhất (Android ID hoặc UUID ngẫu nhiên được lưu trữ)
     * @param newScreen Tên màn hình mới vừa mở (ví dụ: "MatchDetail", "Home")
     */
    fun trackScreenView(apiService: LiveScoreApiService, deviceId: String, newScreen: String, packageName: String? = null) {
        val sessId = getSessionId()
        val prevScreen = currentScreen
        var durationSeconds = 0

        if (prevScreen != null && screenStartTime > 0) {
            val elapsedMs = SystemClock.elapsedRealtime() - screenStartTime
            durationSeconds = (elapsedMs / 1000).toInt()
        }

        currentScreen = newScreen
        screenStartTime = SystemClock.elapsedRealtime()

        // Gửi API log di chuyển màn hình ngầm
        trackingScope.launch {
            try {
                apiService.logScreenView(
                    ScreenViewRequest(
                        package_name = packageName,
                        device_id = deviceId,
                        session_id = sessId,
                        screen_name = newScreen,
                        previous_screen = prevScreen ?: "none",
                        duration_seconds = durationSeconds
                    )
                )
            } catch (e: Exception) {
                // Có thể log ra console hoặc bỏ qua để tránh crash
                e.printStackTrace()
            }
        }
    }
}
