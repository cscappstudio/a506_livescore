package com.football.livescores.matchlive.data.remote.gsm

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GsmApiService {

    @POST("/api/auth/login")
    suspend fun loginGSM(
        @Body request: GsmLoginRequest
    ): Response<GsmLoginResponse>

    @POST("/api/economy/google-play/verify")
    suspend fun verify(
        @Header("x-device-id") deviceId: String,
        @Body request: GsmVerifyRequest
    ): Response<GsmVerifyResponse>

    @POST("/api/iap/subcription/check")
    suspend fun verifyLegacy(
        @Body request: GsmVerifyLegacyRequest
    ): Response<GsmVerifyLegacyResponse>
}
