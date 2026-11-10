package com.football.livescores.matchlive.data.remote.gsm

import com.football.livescores.matchlive.BuildConfig

object GsmConfig {
    const val PROD_BASE_URL = "https://gsm.cscmobicorp.com/"
    const val DEV_BASE_URL = "https://gsmdev.cscmobicorp.com/"
    
    val CURRENT_BASE_URL: String
        get() = if (BuildConfig.DEBUG) DEV_BASE_URL else PROD_BASE_URL
    
    const val GSM_APP_ID = "6af1a4efc3a0fbea0a8d5859"
}
