package com.dsh.jieci;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Words {

    private Words() {}

    public static String clean(String s) {
        if (s == null) return "";
        String t = s.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
        return t;
    }

    public static String key(String s) {
        return clean(s).toLowerCase(Locale.ROOT);
    }

    public static List<String> splitQueries(String s) {
        List<String> out = new ArrayList<>();
        String text = clean(s);
        if (text.isEmpty()) return out;
        String[] parts = text.split("[^\\p{L}\\p{N}\\s'’\\-]+");
        Set<String> seen = new HashSet<>();
        for (String part : parts) {
            String t = clean(part);
            if (t.isEmpty()) continue;
            String k = key(t);
            if (seen.add(k)) out.add(t);
            if (out.size() >= 12) break;
        }
        return out;
    }
}
