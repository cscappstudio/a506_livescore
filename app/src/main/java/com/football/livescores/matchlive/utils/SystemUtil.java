package com.football.livescores.matchlive.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class SystemUtil {
    private static Locale myLocale;
    private static volatile String cachedLanguage = null;

    // Lưu ngôn ngữ đã cài đặt
    public static void saveLocale(Context context, String lang) {
        setPreLanguage(context, lang);
    }

    public static String normalizeLanguageCode(String lang) {
        if (lang == null) return "";
        String lower = lang.toLowerCase(Locale.ROOT).trim();
        if (lower.contains("-")) {
            lower = lower.split("-")[0];
        } else if (lower.contains("_")) {
            lower = lower.split("_")[0];
        }
        switch (lower) {
            case "in":
                return "id";
            case "iw":
                return "he";
            case "ji":
                return "yi";
            default:
                return lower;
        }
    }

    public static boolean isSameLanguage(String lang1, String lang2) {
        if (lang1 == null || lang2 == null) return false;
        if (lang1.equalsIgnoreCase(lang2)) return true;
        return normalizeLanguageCode(lang1).equalsIgnoreCase(normalizeLanguageCode(lang2));
    }

    private static Locale getCurrentLocale(Context context) {
        if (context == null || context.getResources() == null) return null;
        Configuration currentConfig = context.getResources().getConfiguration();
        if (currentConfig == null) return null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            if (currentConfig.getLocales().size() > 0) {
                return currentConfig.getLocales().get(0);
            }
        }
        return currentConfig.locale;
    }

    public static Context setLocale(Context context, String lang) {
        if (context == null) return null;
        if (lang == null || lang.isEmpty()) {
            lang = getPreLanguage(context);
        }
        String normalized = normalizeLanguageCode(lang);
        Locale newLocale = new Locale(normalized);
        myLocale = newLocale;
        Locale.setDefault(newLocale);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(newLocale);
            android.os.LocaleList localeList;
            if ("id".equalsIgnoreCase(normalized) || "in".equalsIgnoreCase(normalized)) {
                localeList = new android.os.LocaleList(new Locale("id"), new Locale("in"));
            } else {
                localeList = new android.os.LocaleList(newLocale);
            }
            android.os.LocaleList.setDefault(localeList);
            config.setLocales(localeList);
            return context.createConfigurationContext(config);
        } else {
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.locale = newLocale;
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
            return context;
        }
    }

    public static void setLocale(Context context) {
        if (context == null) return;
        String language = getPreLanguage(context);
        if (language == null || language.isEmpty()) {
            language = "en";
        }

        Locale currentLocale = getCurrentLocale(context);
        if (currentLocale != null && isSameLanguage(language, currentLocale.getLanguage())) {
            return;
        }

        changeLang(language, context);
    }

    public static void changeLang(String lang, Context context) {
        if (lang == null || lang.equalsIgnoreCase("") || context == null)
            return;
        
        String normalizedLang = normalizeLanguageCode(lang);
        Locale currentLocale = getCurrentLocale(context);
        if (currentLocale != null && isSameLanguage(normalizedLang, currentLocale.getLanguage())) {
            cachedLanguage = normalizedLang;
            return;
        }

        Locale newLocale = new Locale(normalizedLang);
        myLocale = newLocale;
        saveLocale(context, normalizedLang);
        Locale.setDefault(myLocale);
        
        try {
            Configuration config = new Configuration(context.getResources().getConfiguration());
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                config.setLocale(myLocale);
                android.os.LocaleList localeList;
                if ("id".equalsIgnoreCase(normalizedLang) || "in".equalsIgnoreCase(normalizedLang)) {
                    localeList = new android.os.LocaleList(new Locale("id"), new Locale("in"));
                } else {
                    localeList = new android.os.LocaleList(myLocale);
                }
                android.os.LocaleList.setDefault(localeList);
                config.setLocales(localeList);
            } else {
                config.locale = myLocale;
            }
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
            if (context.getApplicationContext() != null && context.getApplicationContext() != context) {
                context.getApplicationContext().getResources().updateConfiguration(config, context.getApplicationContext().getResources().getDisplayMetrics());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getPreLanguage(Context mContext) {
        if (cachedLanguage != null) {
            return cachedLanguage;
        }
        if (mContext == null) return "en";
        SharedPreferences preferences = mContext.getSharedPreferences("data", Context.MODE_PRIVATE);
        cachedLanguage = normalizeLanguageCode(preferences.getString("KEY_LANGUAGE", "en"));
        return cachedLanguage;
    }

    public static void setPreLanguage(Context context, String language) {
        if (language == null || language.isEmpty()) {
            return;
        }
        String normalized = normalizeLanguageCode(language);
        cachedLanguage = normalized;
        if (context != null) {
            SharedPreferences preferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("KEY_LANGUAGE", normalized);
            editor.apply();
        }
    }

    public static String getPath(Context context, Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
        if (cursor == null) return null;
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String s = cursor.getString(column_index);
        cursor.close();
        return s;
    }

    public static File fileFromContentUri(Context context, Uri contentUri) throws IOException {
        String abc = "";
        String fileName = "";
        try {
            String fileDevice = getPath(context, contentUri);
            File file = new File(fileDevice);
            abc = file.getName();
        } catch (Exception e) {
            String fileExtension = getFileExtension(context, contentUri);
            try {
                String listUri[] = contentUri.toString().split("/");
                String fileNameEx = listUri[listUri.length - 1];
                Log.e("fileNameEx", fileNameEx);
                String urlDecodedTitle = URLDecoder.decode(fileNameEx, StandardCharsets.UTF_8.toString());
                String listUri2[] = urlDecodedTitle.split("_");
                if (listUri2.length > 1) {
                    for (int i = 0; i < listUri2.length - 1; i++) {
                        abc += listUri2[i];
                        if (i < listUri2.length - 2) {
                            abc += "_";
                        }
                    }
                } else {
                    abc = urlDecodedTitle;
                }
                abc += "." + fileExtension;
            } catch (Exception x) {
                if (fileExtension != null) {
                    abc = abc + fileExtension;
                } else {
                    abc = "";
                }
            }
        }
        fileName = abc;
        File tempFile = new File(context.getCacheDir(), fileName);
        tempFile.createNewFile();
        try {
            FileOutputStream oStream = new FileOutputStream(tempFile);
            InputStream inputStream = context.getContentResolver().openInputStream(contentUri);
            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                oStream.write(buf, 0, len);
            }
            oStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
            return tempFile;
        }
        return tempFile;
    }

    private static String getFileExtension(Context context, Uri uri) {
        String fileType = context.getContentResolver().getType(uri);
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(fileType);
    }

    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }
}