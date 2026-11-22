package com.dsh.jieci;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private static final String NAME = "jieci";
    private static final String KEY_API = "api_key";
    private static final String KEY_BASE = "base_url";
    private static final String KEY_MODEL = "model";
    private static final String KEY_LANG = "lang";
    private static final String KEY_MODE = "mode";
    private static final String KEY_DRAFT = "draft";
    private static final String KEY_EDITORIAL = "editorial_shown";
    public static final String DEFAULT_BASE = "https://api.deepseek.com";
    public static final String DEFAULT_MODEL = "deepseek-chat";

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String apiKey(Context c) {
        return p(c).getString(KEY_API, "").trim();
    }

    public static void setApiKey(Context c, String v) {
        p(c).edit().putString(KEY_API, v == null ? "" : v.trim()).apply();
    }

    public static String baseUrl(Context c) {
        String v = p(c).getString(KEY_BASE, DEFAULT_BASE).trim();
        return v.isEmpty() ? DEFAULT_BASE : v;
    }

    public static void setBaseUrl(Context c, String v) {
        p(c).edit().putString(KEY_BASE, v == null ? "" : v.trim()).apply();
    }

    public static String model(Context c) {
        String v = p(c).getString(KEY_MODEL, DEFAULT_MODEL).trim();
        return v.isEmpty() ? DEFAULT_MODEL : v;
    }

    public static void setModel(Context c, String v) {
        p(c).edit().putString(KEY_MODEL, v == null ? "" : v.trim()).apply();
    }

    public static String lang(Context c) {
        String v = p(c).getString(KEY_LANG, "en");
        return Langs.valid(v) ? v : "en";
    }

    public static void setLang(Context c, String v) {
        p(c).edit().putString(KEY_LANG, Langs.valid(v) ? v : "en").apply();
    }

    public static String mode(Context c) {
        return "usage".equals(p(c).getString(KEY_MODE, "word")) ? "usage" : "word";
    }

    public static void setMode(Context c, String v) {
        p(c).edit().putString(KEY_MODE, "usage".equals(v) ? "usage" : "word").apply();
    }

    public static String draft(Context c) {
        return p(c).getString(KEY_DRAFT, "");
    }

    public static void setDraft(Context c, String v) {
        p(c).edit().putString(KEY_DRAFT, v == null ? "" : v).apply();
    }

    public static boolean editorialShown(Context c) {
        return p(c).getBoolean(KEY_EDITORIAL, false);
    }

    public static void setEditorialShown(Context c) {
        setEditorialShown(c, true);
    }

    public static void setEditorialShown(Context c, boolean shown) {
        p(c).edit().putBoolean(KEY_EDITORIAL, shown).apply();
    }
}
