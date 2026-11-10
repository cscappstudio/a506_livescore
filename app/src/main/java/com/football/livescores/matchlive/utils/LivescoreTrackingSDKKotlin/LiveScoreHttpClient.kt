package com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Helper xây dựng OkHttpClient kèm Header Interceptor tự động thêm các trường nhận diện phiên bản và giải mã hóa.
 */
object LiveScoreHttpClient {

    /**
     * Tạo OkHttpClient với các Headers cần thiết cho API Live Score
     *
     * @param packageName Tên Package của ứng dụng (x-param1)
     * @param versionCode Phiên bản của ứng dụng (x-param2)
     */
    fun createClient(packageName: String = "1", versionCode: String = "1"): OkHttpClient {
        val param1 = "1"
        val param2 = "1"
        val headerInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val newUrl = originalRequest.url.newBuilder()
                .addQueryParameter("param1", param1)
                .addQueryParameter("param2", param2)
                .build()
            val requestWithHeaders = originalRequest.newBuilder()
                .url(newUrl)
                .header("x-param1", param1)
                .header("x-param2", param2)
                .build()
            chain.proceed(requestWithHeaders)
        }

        return OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
