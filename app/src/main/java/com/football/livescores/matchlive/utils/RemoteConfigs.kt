package com.football.livescores.matchlive.utils

import com.cscmobi.libraryads.commons.remote.CSCKonfigModel
import com.cscmobi.libraryads.commons.remote.konfig

object RemoteConfigs : CSCKonfigModel {
    val native_permission by konfig("native_permission", true)
    val native_all by konfig("native_all", true)
    val native_home by konfig("native_home", true)
    val banner_splash by konfig("banner_splash", true)
    val inter_splash by konfig("inter_splash", true)
    val inter_splash_high by konfig("inter_splash_high", true)
    val inter_click by konfig("inter_click", true)
    val inter_click_enabled by konfig("inter_click_enabled", true)
    val native_language by konfig("native_language", true)
    val native_language_click by konfig("native_language_click", true)
    val native_onboarding_1 by konfig("native_onboarding_1", true)
    val native_onboarding_4 by konfig("native_onboarding_4", true)
    val native_banner_ob by konfig("native_banner_ob", true)
    val native_onboarding_full_1 by konfig("native_onboarding_full_1", true)
    val native_splash_full by konfig("native_splash_full", true)
    val native_splash_full_high by konfig("native_splash_full_high", true)
    val resume_open_app by konfig("resume_open_app", true)
}
