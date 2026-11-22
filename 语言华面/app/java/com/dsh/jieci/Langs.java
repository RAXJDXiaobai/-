package com.dsh.jieci;

public final class Langs {

    public static final String[] CODES = {"en", "ru", "es", "fr", "de", "ja", "wy"};
    public static final String[] NAMES = {"英语", "俄语", "西班牙语", "法语", "德语", "日语", "文言文"};
    public static final String[] HINTS = {
            "输入英语单词或短语",
            "输入俄语单词或短语",
            "输入西班牙语单词或短语",
            "输入法语单词或短语",
            "输入德语单词或短语",
            "输入日语单词或短语",
            "输入文言文字词"
    };

    private Langs() {}

    public static int index(String code) {
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equals(code)) return i;
        }
        return 0;
    }

    public static boolean valid(String code) {
        for (String c : CODES) {
            if (c.equals(code)) return true;
        }
        return false;
    }

    public static String name(String code) {
        return NAMES[index(code)];
    }

    public static String hint(String code) {
        return HINTS[index(code)];
    }

    public static String youdaoLe(String code) {
        if ("en".equals(code)) return "eng";
        if ("ja".equals(code)) return "ja";
        if ("ru".equals(code)) return "ru";
        if ("es".equals(code)) return "es";
        if ("fr".equals(code)) return "fr";
        if ("de".equals(code)) return "de";
        if ("wy".equals(code)) return "zh";
        return "eng";
    }
}
