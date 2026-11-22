package com.dsh.jieci;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.widget.Toast;

import java.net.URLEncoder;

public final class Speaker {

    private static MediaPlayer player;

    private Speaker() {}

    public static void playEnglish(Context c, String word, boolean us) {
        playUrl(c, "https://dict.youdao.com/dictvoice?audio=" + enc(word) + "&type=" + (us ? 2 : 1));
    }

    public static void play(Context c, String word, String lang) {
        if ("en".equals(lang)) {
            playEnglish(c, word, false);
            return;
        }
        playUrl(c, "https://dict.youdao.com/dictvoice?audio=" + enc(word)
                + "&le=" + Langs.youdaoLe(lang));
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static void playUrl(Context c, String url) {
        release();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            player.setDataSource(c, Uri.parse(url));
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp -> release());
            player.setOnErrorListener((mp, what, extra) -> {
                release();
                Toast.makeText(c, "发音加载失败，请检查网络", Toast.LENGTH_SHORT).show();
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            release();
            Toast.makeText(c, "发音加载失败，请检查网络", Toast.LENGTH_SHORT).show();
        }
    }

    private static void release() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }
}
