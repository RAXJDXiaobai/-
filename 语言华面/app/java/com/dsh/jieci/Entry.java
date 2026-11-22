package com.dsh.jieci;

public final class Entry {

    public long id;
    public String word;
    public String lang = "en";
    public String type;
    public String anchor;
    public String json;
    public long updatedAt;

    public Entry() {}

    public Entry(String word, String type, String anchor, String json, long updatedAt) {
        this.word = word;
        this.type = type;
        this.anchor = anchor;
        this.json = json;
        this.updatedAt = updatedAt;
    }
}
