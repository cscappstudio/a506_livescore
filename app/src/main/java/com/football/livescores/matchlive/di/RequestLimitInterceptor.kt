package com.football.livescores.matchlive.di

import com.football.livescores.matchlive.data.local.RequestLimitManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestLimitInterceptor @Inject constructor(
    private val limitManager: RequestLimitManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        // Apply to the specific API Sports endpoints
        if (url.contains("fixtures") || url.contains("statistics") || url.contains("events") || url.contains("lineups")) {
            val response = chain.proceed(request)
            if (response.isSuccessful) {
                limitManager.incrementCount()
            }
            return response
        }

        return chain.proceed(request)
    }
}
