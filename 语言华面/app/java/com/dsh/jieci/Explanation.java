package com.dsh.jieci;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Explanation {

    public static final class Part {
        public String part = "";
        public String kind = "";
        public String function = "";
    }

    public static final class Meaning {
        public String meaning = "";
        public String gloss = "";
        public String from = "";
    }

    public static final class Example {
        public String en = "";
        public String zh = "";
        public String tag = "";
    }

    public String word = "";
    public String type = "word";
    public String phraseType = "";
    public boolean decomposable = true;
    public String note = "";
    public String originalAction = "";
    public String rootImage = "";
    public String initialMeaning = "";
    public String initialGloss = "";
    public String shortImage = "";
    public String anchor = "";
    public String anchorMeaning = "";
    public String phoneticUk = "";
    public String phoneticUs = "";
    public String reading = "";
    public String evolution = "";
    public String originalBold = "";
    public String rootBold = "";
    public final List<Part> components = new ArrayList<>();
    public final List<Meaning> meanings = new ArrayList<>();
    public final List<Example> examples = new ArrayList<>();

    public boolean valid() {
        return !anchor.isEmpty() && (!rootImage.isEmpty() || !initialMeaning.isEmpty());
    }

    public static Explanation fromJson(JSONObject o) {
        Explanation e = new Explanation();
        e.word = str(o, "word");
        e.type = str(o, "type");
        if (e.type.isEmpty()) e.type = "word";
        e.phraseType = str(o, "phrase_type", "phraseType");
        e.decomposable = o.optBoolean("decomposable", true);
        e.note = str(o, "note");
        e.originalAction = str(o, "original_action", "originalAction");
        e.rootImage = str(o, "root_image", "rootImage");
        e.initialMeaning = str(o, "initial_meaning", "initialMeaning");
        e.initialGloss = str(o, "initial_gloss", "initialGloss");
        e.shortImage = str(o, "short_image", "shortImage");
        e.anchor = str(o, "anchor", "mnemonic_anchor", "mnemonicAnchor");
        e.anchorMeaning = str(o, "anchor_meaning", "anchorMeaning");
        e.phoneticUk = str(o, "phonetic_uk", "phoneticUk");
        e.phoneticUs = str(o, "phonetic_us", "phoneticUs");
        e.reading = str(o, "reading", "phonetic");
        e.evolution = str(o, "evolution_history", "evolution", "evolutionHistory");
        e.originalBold = str(o, "original_bold", "originalBold");
        e.rootBold = str(o, "root_bold", "rootBold");

        JSONArray comps = o.optJSONArray("components");
        if (comps != null) {
            for (int i = 0; i < comps.length(); i++) {
                JSONObject c = comps.optJSONObject(i);
                if (c == null) continue;
                Part p = new Part();
                p.part = str(c, "part", "component", "text");
                p.kind = str(c, "kind", "type");
                p.function = str(c, "function", "meaning", "gloss", "role");
                if (!p.part.isEmpty()) e.components.add(p);
            }
        }

        JSONArray ms = o.optJSONArray("abstract_meanings");
        if (ms == null) ms = o.optJSONArray("abstractMeanings");
        if (ms != null) {
            for (int i = 0; i < ms.length(); i++) {
                JSONObject m = ms.optJSONObject(i);
                if (m == null) continue;
                Meaning x = new Meaning();
                x.meaning = str(m, "meaning", "text");
                x.gloss = str(m, "gloss", "explanation", "note");
                x.from = str(m, "from", "origin", "from_action");
                if (!x.meaning.isEmpty()) e.meanings.add(x);
            }
        }

        JSONArray ex = o.optJSONArray("examples");
        if (ex != null) {
            for (int i = 0; i < ex.length(); i++) {
                JSONObject m = ex.optJSONObject(i);
                if (m == null) continue;
                Example x = new Example();
                x.en = str(m, "en", "english", "sentence");
                x.zh = str(m, "zh", "chinese", "translation");
                x.tag = str(m, "tag", "note", "label");
                if (!x.en.isEmpty() || !x.zh.isEmpty()) e.examples.add(x);
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
