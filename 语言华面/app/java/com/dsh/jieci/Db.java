package com.dsh.jieci;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class Db extends SQLiteOpenHelper {

    private static final String NAME = "jieci.db";
    private static final int VERSION = 3;
    private static Db instance;

    public static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "word TEXT NOT NULL," +
                "word_key TEXT NOT NULL," +
                "lang TEXT NOT NULL DEFAULT 'en'," +
                "type TEXT," +
                "anchor TEXT," +
                "json TEXT NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "UNIQUE(lang, word_key))");
        db.execSQL("CREATE INDEX idx_entries_updated ON entries(updated_at DESC)");
        createUsageTable(db);
    }

    private static void createUsageTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS usage_entries (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "topic TEXT NOT NULL," +
                "topic_key TEXT NOT NULL," +
                "lang TEXT NOT NULL DEFAULT 'en'," +
                "anchor TEXT," +
                "json TEXT NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "UNIQUE(lang, topic_key))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_updated ON usage_entries(updated_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE entries_v2 (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "word TEXT NOT NULL," +
                    "word_key TEXT NOT NULL," +
                    "lang TEXT NOT NULL DEFAULT 'en'," +
                    "type TEXT," +
                    "anchor TEXT," +
                    "json TEXT NOT NULL," +
                    "updated_at INTEGER NOT NULL," +
                    "UNIQUE(lang, word_key))");
            db.execSQL("INSERT INTO entries_v2 (_id, word, word_key, lang, type, anchor, json, updated_at) " +
                    "SELECT _id, word, word_key, 'en', type, anchor, json, updated_at FROM entries");
            db.execSQL("DROP TABLE entries");
            db.execSQL("ALTER TABLE entries_v2 RENAME TO entries");
            db.execSQL("CREATE INDEX idx_entries_updated ON entries(updated_at DESC)");
        }
        if (oldVersion < 3) {
            createUsageTable(db);
        }
    }

    private static Entry read(Cursor c) {
        Entry e = new Entry();
        e.id = c.getLong(0);
        e.word = c.getString(1);
        e.lang = c.getString(3);
        e.type = c.getString(4);
        e.anchor = c.getString(5);
        e.json = c.getString(6);
        e.updatedAt = c.getLong(7);
        return e;
    }

    private static UsageEntry readUsage(Cursor c) {
        UsageEntry u = new UsageEntry();
        u.id = c.getLong(0);
        u.topic = c.getString(1);
        u.lang = c.getString(3);
        u.anchor = c.getString(4);
        u.json = c.getString(5);
        u.updatedAt = c.getLong(6);
        return u;
    }

    private static final String[] COLS = {
            "_id", "word", "word_key", "lang", "type", "anchor", "json", "updated_at"
    };
    private static final String[] UCOLS = {
            "_id", "topic", "topic_key", "lang", "anchor", "json", "updated_at"
    };

    public Entry find(String word, String lang) {
        String key = Words.key(word);
        if (key.isEmpty()) return null;
        Cursor c = getReadableDatabase().query("entries", COLS, "lang=? AND word_key=?",
                new String[]{lang, key}, null, null, null, "1");
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    public List<Entry> recent(String lang, int limit) {
        Cursor c = getReadableDatabase().query("entries", COLS, "lang=?",
                new String[]{lang}, null, null, "updated_at DESC", String.valueOf(limit));
        return collect(c);
    }

    public List<Entry> search(String q, String lang, int limit) {
        String key = Words.key(q);
        if (key.isEmpty()) return recent(lang, limit);
        String like = "%" + escape(key) + "%";
        Cursor c = getReadableDatabase().query("entries", COLS, "lang=? AND word_key LIKE ? ESCAPE '\\'",
                new String[]{lang, like}, null, null, "updated_at DESC", String.valueOf(limit));
        return collect(c);
    }

    public List<Entry> all(String lang) {
        Cursor c = getReadableDatabase().query("entries", COLS, "lang=?",
                new String[]{lang}, null, null, "updated_at DESC");
        return collect(c);
    }

    private static List<Entry> collect(Cursor c) {
        List<Entry> list = new ArrayList<>();
        try {
            while (c.moveToNext()) list.add(read(c));
        } finally {
            c.close();
        }
        return list;
    }

    public List<UsageEntry> usageAll(String lang) {
        Cursor c = getReadableDatabase().query("usage_entries", UCOLS, "lang=?",
                new String[]{lang}, null, null, "updated_at DESC");
        List<UsageEntry> list = new ArrayList<>();
        try {
            while (c.moveToNext()) list.add(readUsage(c));
        } finally {
            c.close();
        }
        return list;
    }

    public UsageEntry usageFind(String topic, String lang) {
        String key = Words.key(topic);
        if (key.isEmpty()) return null;
        Cursor c = getReadableDatabase().query("usage_entries", UCOLS, "lang=? AND topic_key=?",
                new String[]{lang, key}, null, null, null, "1");
        try {
            return c.moveToFirst() ? readUsage(c) : null;
        } finally {
            c.close();
        }
    }

    public long upsert(Entry e) {
        SQLiteDatabase db = getWritableDatabase();
        String lang = Langs.valid(e.lang) ? e.lang : "en";
        ContentValues v = new ContentValues();
        v.put("word", e.word);
        v.put("word_key", Words.key(e.word));
        v.put("lang", lang);
        v.put("type", e.type);
        v.put("anchor", e.anchor);
        v.put("json", e.json);
        v.put("updated_at", System.currentTimeMillis());
        long id = idOf(db, lang, Words.key(e.word));
        if (id > 0) {
            db.update("entries", v, "_id=?", new String[]{String.valueOf(id)});
            return id;
        }
        return db.insert("entries", null, v);
    }

    public long usageUpsert(UsageEntry u) {
        SQLiteDatabase db = getWritableDatabase();
        String lang = Langs.valid(u.lang) ? u.lang : "en";
        ContentValues v = new ContentValues();
        v.put("topic", u.topic);
        v.put("topic_key", Words.key(u.topic));
        v.put("lang", lang);
        v.put("anchor", u.anchor);
        v.put("json", u.json);
        v.put("updated_at", System.currentTimeMillis());
        long id = usageIdOf(db, lang, Words.key(u.topic));
        if (id > 0) {
            db.update("usage_entries", v, "_id=?", new String[]{String.valueOf(id)});
            return id;
        }
        return db.insert("usage_entries", null, v);
    }

    private static long idOf(SQLiteDatabase db, String lang, String key) {
        Cursor c = db.query("entries", new String[]{"_id"}, "lang=? AND word_key=?",
                new String[]{lang, key}, null, null, null, "1");
        try {
            return c.moveToFirst() ? c.getLong(0) : -1;
        } finally {
            c.close();
        }
    }

    private static long usageIdOf(SQLiteDatabase db, String lang, String key) {
        Cursor c = db.query("usage_entries", new String[]{"_id"}, "lang=? AND topic_key=?",
                new String[]{lang, key}, null, null, null, "1");
        try {
            return c.moveToFirst() ? c.getLong(0) : -1;
        } finally {
            c.close();
        }
    }

    public void delete(long id) {
        getWritableDatabase().delete("entries", "_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteUsage(long id) {
        getWritableDatabase().delete("usage_entries", "_id=?", new String[]{String.valueOf(id)});
    }

    public int count(String lang) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM entries WHERE lang=?",
                new String[]{lang});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public int usageCount(String lang) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM usage_entries WHERE lang=?",
                new String[]{lang});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public int totalCount(String lang) {
        return count(lang) + usageCount(lang);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
