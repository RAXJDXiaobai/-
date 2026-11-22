package com.dsh.jieci;

public final class HistoryItem {

    public static final int WORD = 0;
    public static final int USAGE = 1;

    public int kind;
    public long id;
    public String title;
    public String anchor;
    public String json;
    public String lang;
    public long updatedAt;

    public static HistoryItem fromWord(Entry e) {
        HistoryItem h = new HistoryItem();
        h.kind = WORD;
        h.id = e.id;
        h.title = e.word;
        h.anchor = e.anchor;
        h.json = e.json;
        h.lang = e.lang;
        h.updatedAt = e.updatedAt;
        return h;
    }

    public static HistoryItem fromUsage(UsageEntry u) {
        HistoryItem h = new HistoryItem();
        h.kind = USAGE;
        h.id = u.id;
        h.title = u.topic;
        h.anchor = u.anchor;
        h.json = u.json;
        h.lang = u.lang;
        h.updatedAt = u.updatedAt;
        return h;
    }
}
