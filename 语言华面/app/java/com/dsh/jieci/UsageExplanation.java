package com.dsh.jieci;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class UsageExplanation {

    public static final class Type {
        public String name = "";
        public String use = "";
    }

    public static final class Word {
        public String word = "";
        public String gloss = "";
    }

    public static final class Scene {
        public String scene = "";
        public String utterance = "";
        public String why = "";
        public String effect = "";
    }

    public String topic = "";
    public String language = "";
    public String nativeFrame = "";
    public String anchor = "";
    public String closing = "";
    public final List<Type> types = new ArrayList<>();
    public final List<Word> words = new ArrayList<>();
    public final List<Scene> scenes = new ArrayList<>();

    public boolean valid() {
        return !nativeFrame.isEmpty() && !scenes.isEmpty();
    }

    public static UsageExplanation fromJson(JSONObject o) {
        UsageExplanation e = new UsageExplanation();
        e.topic = str(o, "topic", "word");
        e.language = str(o, "language");
        e.nativeFrame = str(o, "native_frame", "nativeFrame");
        e.anchor = str(o, "anchor", "mnemonic_anchor");
        e.closing = str(o, "closing", "summary");

        JSONArray ts = o.optJSONArray("types");
        if (ts != null) {
            for (int i = 0; i < ts.length(); i++) {
                JSONObject t = ts.optJSONObject(i);
                if (t == null) continue;
                Type x = new Type();
                x.name = str(t, "name", "type", "title");
                x.use = str(t, "use", "usage", "description");
                if (!x.name.isEmpty()) e.types.add(x);
            }
        }

        JSONArray ws = o.optJSONArray("related_words");
        if (ws == null) ws = o.optJSONArray("relatedWords");
        if (ws != null) {
            for (int i = 0; i < ws.length(); i++) {
                JSONObject w = ws.optJSONObject(i);
                if (w == null) continue;
                Word x = new Word();
                x.word = str(w, "word", "term");
                x.gloss = str(w, "gloss", "meaning", "note");
                if (!x.word.isEmpty()) e.words.add(x);
            }
        }

        JSONArray ss = o.optJSONArray("scenes");
        if (ss != null) {
            for (int i = 0; i < ss.length(); i++) {
                JSONObject s = ss.optJSONObject(i);
                if (s == null) continue;
                Scene x = new Scene();
                x.scene = str(s, "scene", "title", "name");
                x.utterance = str(s, "utterance", "sentence", "example");
                x.why = str(s, "why", "why_speaker_chooses", "reason");
                x.effect = str(s, "effect", "listener_effect", "listener");
                if (!x.utterance.isEmpty() || !x.scene.isEmpty()) e.scenes.add(x);
            }
        }
        return e;
    }

    private static String str(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (v != null && !v.trim().isEmpty() && !"null".equalsIgnoreCase(v.trim())) return v.trim();
        }
        return "";
    }
}
