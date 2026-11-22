package com.dsh.jieci;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class BatchActivity extends Activity {

    public static final String EXTRA_WORDS = "words";
    public static final String EXTRA_LANG = "lang";

    private static final class Item {
        String word;
        String state = "pending";
        String json;
        String error;
        boolean sample;
    }

    private final List<Item> items = new ArrayList<>();
    private String lang;
    private LinearLayout listBox;
    private TextView progress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Db db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyStatusBar(this);
        db = Db.get(this);
        lang = getIntent().getStringExtra(EXTRA_LANG);
        if (!Langs.valid(lang)) lang = Prefs.lang(this);
        ArrayList<String> words = getIntent().getStringArrayListExtra(EXTRA_WORDS);
        if (words != null) {
            for (String w : words) {
                Item it = new Item();
                it.word = w;
                Entry cached = db.find(w, lang);
                if (cached != null) {
                    it.state = "done";
                    it.json = cached.json;
                }
                items.add(it);
            }
        }

        buildUi();
        renderRows();
        startWorker();
    }

    private void buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(getColor(R.color.bg));
        setContentView(root);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 20), 0);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56)));

        ImageView back = Ui.iconButton(this, R.drawable.ic_back, R.color.text, 24);
        back.setOnClickListener(v -> goQueryScreen());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        TextView title = Ui.text(this, "批量解释 · " + items.size() + " 个", 17f, R.color.text, Ui.MEDIUM);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.dp(this, 4);
        bar.addView(title, tlp);

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        progress = Ui.text(this, "准备中…", 12f, R.color.text_tertiary);
        bar.addView(progress);

        TextView hint = Ui.text(this, "输入里的多个词语已拆开，会依次生成并存入历史。点已完成的词查看。", 12f, R.color.text_tertiary);
        hint.setLineSpacing(Ui.dp(this, 4), 1f);
        hint.setPadding(Ui.dp(this, 20), Ui.dp(this, 2), Ui.dp(this, 20), Ui.dp(this, 12));
        root.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        listBox = Ui.column(this);
        listBox.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 28));
        scroll.addView(listBox, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void startWorker() {
        final String key = Prefs.apiKey(this);
        final String base = Prefs.baseUrl(this);
        final String model = Prefs.model(this);
        new Thread(() -> {
            int done = 0;
            for (int i = 0; i < items.size(); i++) {
                final Item it = items.get(i);
                if (it.json != null) {
                    done++;
                    continue;
                }
                setState(i, "working", null, null);
                if (key.isEmpty()) {
                    Entry s = Samples.find(BatchActivity.this, lang, it.word);
                    if (s != null) {
                        it.json = s.json;
                        it.sample = true;
                        setState(i, "sample", null, null);
                    } else {
                        setState(i, "nokey", null, null);
                    }
                } else {
                    try {
                        DeepSeekClient.Result r = DeepSeekClient.explain(it.word, lang, key, base, model);
                        Entry e = new Entry(r.explanation.word, r.explanation.type,
                                r.explanation.anchor, r.json, System.currentTimeMillis());
                        e.lang = lang;
                        db.upsert(e);
                        it.json = r.json;
                        setState(i, "done", null, null);
                    } catch (DeepSeekClient.ApiException e) {
                        setState(i, "failed", null, e.getMessage());
                    }
                }
                done++;
                final int d = done;
                handler.post(() -> progress.setText("已完成 " + d + " / " + items.size()));
            }
            handler.post(() -> progress.setText("完成 " + items.size() + " / " + items.size()));
        }).start();
    }

    private void setState(int index, String state, String json, String error) {
        Item it = items.get(index);
        it.state = state;
        if (json != null) it.json = json;
        it.error = error;
        handler.post(this::renderRows);
    }

    private void renderRows() {
        listBox.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            final Item it = items.get(i);
            LinearLayout row = Ui.column(this);
            row.setBackgroundResource(R.drawable.bg_card_small);
            row.setPadding(Ui.dp(this, 16), Ui.dp(this, 13), Ui.dp(this, 16), Ui.dp(this, 13));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = Ui.dp(this, 10);

            LinearLayout top = Ui.row(this);
            TextView word = Ui.text(this, it.word, 16f, R.color.text, Ui.MEDIUM);
            word.setSingleLine(true);
            word.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(word, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView status = Ui.text(this, statusText(it), 12f,
                    "failed".equals(it.state) ? R.color.danger : R.color.text_tertiary);
            top.addView(status);
            row.addView(top);

            TextView sub = Ui.text(this, subText(it), 12f, R.color.text_tertiary);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(this, 5);
            row.addView(sub, slp);

            row.setClickable(true);
            row.setBackgroundResource(R.drawable.bg_ripple);
            row.setOnClickListener(v -> open(it));
            listBox.addView(row, rlp);
        }
    }

    private String statusText(Item it) {
        switch (it.state) {
            case "working": return "正在解释…";
            case "done": return "完成";
            case "sample": return "示例";
            case "nokey": return "需要钥匙";
            case "failed": return "失败";
            default: return "等待";
        }
    }

    private String subText(Item it) {
        if ("failed".equals(it.state)) return it.error == null ? "重新查一次试试" : it.error;
        if ("nokey".equals(it.state)) return "还没有填写 DeepSeek 钥匙";
        if ("sample".equals(it.state)) return "内置示例，点开查看";
        if ("done".equals(it.state)) return "已存入查询历史，点开查看";
        if ("working".equals(it.state)) return "正在连 DeepSeek";
        return "等待前面的词";
    }

    private void open(Item it) {
        if (it.json != null) {
            Intent i = new Intent(this, ResultActivity.class);
            i.putExtra(ResultActivity.EXTRA_WORD, it.word);
            i.putExtra(ResultActivity.EXTRA_LANG, lang);
            i.putExtra(ResultActivity.EXTRA_JSON, it.json);
            i.putExtra(ResultActivity.EXTRA_SAMPLE, it.sample);
            i.putExtra(ResultActivity.EXTRA_FROM_BATCH, true);
            startActivity(i);
            return;
        }
        if ("nokey".equals(it.state)) {
            Intent i = new Intent(this, ResultActivity.class);
            i.putExtra(ResultActivity.EXTRA_WORD, it.word);
            i.putExtra(ResultActivity.EXTRA_LANG, lang);
            i.putExtra(ResultActivity.EXTRA_FROM_BATCH, true);
            startActivity(i);
            return;
        }
        Toast.makeText(this, "还在处理，等一下", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        goQueryScreen();
    }

    private void goQueryScreen() {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_QUERY_SCREEN, true);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
