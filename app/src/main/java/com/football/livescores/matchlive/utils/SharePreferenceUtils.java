package com.football.livescores.matchlive.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;


public class SharePreferenceUtils {
    private static final String LIVE_SCORE = "LIVE_SCORE";
    private static final String PREFS_NAME = "CurrencyPrefs";

    private static final String TRANSACTION_LIST_KEY = "transactionList";

    private static final String KEY_SELECTED_CURRENCY = "SelectedCurrency";

    private static final String PREF_NAME = "chat_prefs";
    private static final String KEY_MESSAGES = "messages";

    private static final String COUNTER_KEY = "counter_value";

    private static final String TRANSACTION_KEY = "transaction_data";
    private SharedPreferences.Editor editor;


    private static volatile SharePreferenceUtils instance;
    private SharedPreferences sharePreference;
    private final Gson gson;

    public SharePreferenceUtils(Context context) {
        sharePreference = context.getSharedPreferences(LIVE_SCORE, Context.MODE_PRIVATE);
        gson = new Gson();
        editor = sharePreference.edit();

    }

    public static SharePreferenceUtils getInstance(Context context) {
        if (instance == null) {
            synchronized (SharePreferenceUtils.class) {
                if (instance == null) {
                    instance = new SharePreferenceUtils(context);
                }
            }
        }
        return instance;
    }



    public int getCurrentValue() {
        return sharePreference.getInt(COUNTER_KEY, 0);
    }

    public void incrementCounter() {
        sharePreference.edit()
                .putInt(COUNTER_KEY, getCurrentValue() + 1)
                .apply();
    }

    public static void setOrganic(Context context, boolean value) {
        SharedPreferences pre = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pre.edit();
        editor.putBoolean("organic_noti", value);
        editor.apply();
    }


    public static boolean isOrganic(Context context) {
        if (com.cscmobi.libraryads.commons.sharepreference.CSCSPFHolder.INSTANCE.get().is_organic()) {
            setOrganic(context, true);
            return true;
        }
        if (context == null) return false;
        SharedPreferences pre = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        return pre.getBoolean("organic_noti", false);
    }


    // Phương thức lưu object bất kỳ
    public <T> void saveObject(String key, T object) {
        String json = gson.toJson(object);
        sharePreference.edit().putString(key, json).apply();
    }

    // Phương thức đọc object bất kỳ
    public <T> T getObject(String key, Class<T> classType) {
        String json = sharePreference.getString(key, null);
        if (json == null) {
            return null;
        }
        return gson.fromJson(json, classType);
    }

    // Phương thức đọc List object bất kỳ
    public <T> List<T> getListObject(String key, Class<T> classType) {
        String json = sharePreference.getString(key, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = TypeToken.getParameterized(List.class, classType).getType();
        return gson.fromJson(json, type);
    }

    // Các phương thức tiện ích
    public void saveBoolean(String key, boolean value) {
        sharePreference.edit().putBoolean(key, value).apply();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return sharePreference.getBoolean(key, defaultValue);
    }

    public void saveString(String key, String value) {
        sharePreference.edit().putString(key, value).apply();
    }

    public String getString(String key, String defaultValue) {
        return sharePreference.getString(key, defaultValue);
    }

    public void saveInt(String key, int value) {
        sharePreference.edit().putInt(key, value).apply();
    }

    public int getInt(String key, int defaultValue) {
        return sharePreference.getInt(key, defaultValue);
    }

    public void saveLong(String key, long value) {
        sharePreference.edit().putLong(key, value).apply();
    }

    public long getLong(String key, long defaultValue) {
        return sharePreference.getLong(key, defaultValue);
    }

    // Xóa dữ liệu
    public void remove(String key) {
        sharePreference.edit().remove(key).apply();
    }

    public void clearAll() {
        sharePreference.edit().clear().apply();
    }
}
