package com.dsh.jieci;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Samples {

    private static List<Entry> cache;

    private Samples() {}

    public static synchronized List<Entry> all(Context c) {
        if (cache != null) return cache;
        List<Entry> list = new ArrayList<>();
        try {
            InputStream in = c.getAssets().open("samples.json");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.id = -(i + 1);
                e.word = o.optString("word");
                e.lang = o.optString("lang", "en");
                if (!Langs.valid(e.lang)) e.lang = "en";
                e.type = o.optString("type");
                e.anchor = o.optString("anchor");
                e.json = o.toString();
                e.updatedAt = 0;
                list.add(e);
            }
        } catch (Exception ignored) {
        }
        cache = list;
        return cache;
    }

    public static List<Entry> all(Context c, String lang) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : all(c)) {
            if (e.lang.equals(lang)) out.add(e);
        }
        return out;
    }

    public static Entry find(Context c, String lang, String word) {
        String key = Words.key(word);
        for (Entry e : all(c)) {
            if (e.lang.equals(lang) && Words.key(e.word).equals(key)) return e;
        }
        return null;
    }
}
