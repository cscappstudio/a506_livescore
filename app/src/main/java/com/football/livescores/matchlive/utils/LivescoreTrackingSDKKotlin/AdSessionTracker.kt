package com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bộ quản lý đếm số lượt hiển thị (impression index) của từng định dạng quảng cáo trong cùng một phiên (session).
 * Giúp thống kê đường cong suy giảm eCPM (eCPM Decay) trên CMS.
 */
object AdSessionTracker {
    private val adImpressionCounters = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * Tăng số lượt hiển thị của định dạng quảng cáo cụ thể trong session và trả về chỉ số hiện tại.
     *
     * @param adType Định dạng quảng cáo ("banner", "interstitial", "native", "rewarded")
     * @return Thứ tự hiển thị của quảng cáo đó trong phiên này (bắt đầu từ 1)
     */
    fun incrementAndGet(adType: String): Int {
        val counter = adImpressionCounters.getOrPut(adType) { AtomicInteger(0) }
        return counter.incrementAndGet()
    }

    /**
     * Xóa toàn bộ bộ đếm khi session hết hạn hoặc khi ứng dụng khởi tạo phiên mới.
     */
    fun resetSession() {
        adImpressionCounters.clear()
    }
}
