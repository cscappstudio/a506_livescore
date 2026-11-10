package com.football.livescores.matchlive.data.remote.gsm

import com.google.gson.annotations.SerializedName

data class GsmLoginRequest(
    @SerializedName("appId") val appId: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("pkName") val pkName: String,
    @SerializedName("os") val os: Int = 1, // 1 for Android
    @SerializedName("version") val version: String
)

data class GsmLoginResponse(
    @SerializedName("data") val data: GsmLoginData?,
    @SerializedName("accessToken") val accessTokenDirect: String?,
    @SerializedName("status") val status: Int?,
    @SerializedName("message") val message: String?
) {
    val token: String?
        get() = data?.accessToken ?: accessTokenDirect
}

data class GsmLoginData(
    @SerializedName("accessToken") val accessToken: String?
)

data class GsmVerifyRequest(
    @SerializedName("product_id") val productId: String,
    @SerializedName("product_type") val productType: String
)

data class GsmVerifyResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("granted_gems") val grantedGems: Int?,
    @SerializedName("user") val user: GsmUserData?
)

data class GsmUserData(
    @SerializedName("gems") val gems: Int?
)

data class GsmVerifyLegacyRequest(
    @SerializedName("packageName") val packageName: String,
    @SerializedName("purchaseToken") val purchaseToken: String,
    @SerializedName("productId") val productId: String,
    @SerializedName("productName") val productName: String,
    @SerializedName("keyhash") val keyhash: String = "",
    @SerializedName("rechargePoint") val rechargePoint: String = "1",
    @SerializedName("adid") val adid: String,
    @SerializedName("userType") val userType: String = "0"
)

data class GsmVerifyLegacyResponse(
    @SerializedName("success") val success: Boolean?,
    @SerializedName("status") val status: Int?,
    @SerializedName("message") val message: String?
)
