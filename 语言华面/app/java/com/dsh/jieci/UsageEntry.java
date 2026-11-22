package com.dsh.jieci;

public final class UsageEntry {

    public long id;
    public String topic;
    public String lang = "en";
    public String anchor;
    public String json;
    public long updatedAt;

    public UsageEntry() {}

    public UsageEntry(String topic, String lang, String anchor, String json, long updatedAt) {
        this.topic = topic;
        this.lang = lang;
        this.anchor = anchor;
        this.json = json;
        this.updatedAt = updatedAt;
    }
}
