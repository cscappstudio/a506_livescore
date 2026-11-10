package com.football.livescores.matchlive.data.remote.gsm

import okhttp3.Interceptor
import okhttp3.Response

class GsmAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Connection", "keep-alive")

        val token = Constants.gsmAccessToken
        if (token.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}
