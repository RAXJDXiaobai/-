package com.dsh.jieci;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.List;

public class UsageActivity extends Activity {

    public static final String EXTRA_TOPIC = "topic";
    public static final String EXTRA_LANG = "lang";
    public static final String EXTRA_JSON = "json";

    private String topic;
    private String lang;
    private String renderedJson;
    private LinearLayout content;
    private TextView toolbarTitle;
    private ImageView copyButton;
    private ImageView refreshButton;
    private UsageExplanation current;
    private Db db;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable ticker;
    private TextView loadingStatus;
    private int statusIndex;
    private int token;

    private static final String[] PHRASES = {
            "正在理清母语者心里的动作…",
            "正在列这个用法的常见类型…",
            "正在把相关词放进画面里…",
            "正在找几个不一样的场景…",
            "正在写那一句速记锚点…"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyStatusBar(this);
        db = Db.get(this);
        topic = Words.clean(getIntent().getStringExtra(EXTRA_TOPIC));
        lang = getIntent().getStringExtra(EXTRA_LANG);
        if (!Langs.valid(lang)) lang = Prefs.lang(this);

        buildUi();
        String json = getIntent().getStringExtra(EXTRA_JSON);
        if (json != null) {
            showJson(json);
        } else {
            startGeneration();
        }
    }

    private void buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(getColor(R.color.bg));
        setContentView(root);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 12), 0);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56)));

        ImageView back = Ui.iconButton(this, R.drawable.ic_back, R.color.text, 24);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        toolbarTitle = Ui.text(this, topic, 16f, R.color.text, Ui.MEDIUM);
        toolbarTitle.setSingleLine(true);
        toolbarTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dp(this, 4);
        bar.addView(toolbarTitle, tlp);

        refreshButton = Ui.iconButton(this, R.drawable.ic_refresh, R.color.text_secondary, 24);
        refreshButton.setAlpha(0.35f);
        refreshButton.setEnabled(false);
        refreshButton.setVisibility(View.GONE);
        refreshButton.setOnClickListener(v -> confirmRegenerate());
        bar.addView(refreshButton, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        copyButton = Ui.iconButton(this, R.drawable.ic_copy, R.color.text_secondary, 24);
        copyButton.setAlpha(0.35f);
        copyButton.setEnabled(false);
        copyButton.setOnClickListener(v -> copyAll());
        bar.addView(copyButton, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 20), Ui.dp(this, 40));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void startGeneration() {
        final int my = ++token;
        final String key = Prefs.apiKey(this);
        if (key.isEmpty()) {
            showNoKey();
            return;
        }
        showLoading();
        new Thread(() -> {
            try {
                final DeepSeekClient.UsageResult r = DeepSeekClient.explainUsage(topic, lang, key,
                        Prefs.baseUrl(UsageActivity.this), Prefs.model(UsageActivity.this));
                UsageEntry ue = new UsageEntry(r.explanation.topic, lang, r.explanation.anchor,
                        r.json, System.currentTimeMillis());
                db.usageUpsert(ue);
                runOnUiThread(() -> {
                    if (my != token || isFinishing()) return;
                    stopTicker();
                    show(r.explanation, r.json);
                });
            } catch (final DeepSeekClient.ApiException e) {
                runOnUiThread(() -> {
                    if (my != token || isFinishing()) return;
                    stopTicker();
                    showError(e.getMessage());
                });
            }
        }).start();
    }

    private void showLoading() {
        stopTicker();
        content.removeAllViews();
        refreshButton.setVisibility(View.GONE);
        refreshButton.setEnabled(false);
        copyButton.setEnabled(false);
        copyButton.setAlpha(0.35f);

        TextView head = headline(topic);
        content.addView(head);

        LinearLayout card = Ui.row(this);
        card.setBackgroundResource(R.drawable.bg_card_small);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20));

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        pb.getIndeterminateDrawable().setColorFilter(getColor(R.color.accent), PorterDuff.Mode.SRC_IN);
        card.addView(pb, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));

        LinearLayout texts = Ui.column(this);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dp(this, 14);
        card.addView(texts, tlp);

        loadingStatus = Ui.text(this, PHRASES[0], 14f, R.color.text);
        texts.addView(loadingStatus);
        TextView hint = Ui.text(this, "这是一种新的解释，通常要十几秒", 12f, R.color.text_tertiary);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.dp(this, 6);
        texts.addView(hint, hlp);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 24);
        content.addView(card, clp);

        statusIndex = 0;
        ticker = new Runnable() {
            @Override
            public void run() {
                if (loadingStatus == null) return;
                statusIndex = (statusIndex + 1) % PHRASES.length;
                loadingStatus.setText(PHRASES[statusIndex]);
                loadingStatus.setAlpha(0f);
                loadingStatus.animate().alpha(1f).setDuration(320).start();
                handler.postDelayed(this, 2800);
            }
        };
        handler.postDelayed(ticker, 2800);
    }

    private void stopTicker() {
        if (ticker != null) {
            handler.removeCallbacks(ticker);
            ticker = null;
        }
    }

    private void showJson(String json) {
        try {
            UsageExplanation ex = UsageExplanation.fromJson(new JSONObject(json));
            show(ex, json);
        } catch (Exception e) {
            showError("这段语言用法读不出来，请重新生成");
        }
    }

    private void show(UsageExplanation e, String json) {
        current = e;
        renderedJson = json;
        if (!e.topic.isEmpty()) {
            topic = e.topic;
            toolbarTitle.setText(topic);
        }
        copyButton.setEnabled(true);
        copyButton.setAlpha(1f);
        refreshButton.setVisibility(View.VISIBLE);
        refreshButton.setEnabled(true);
        refreshButton.setAlpha(1f);
        render(e);
    }

    private void render(UsageExplanation e) {
        stopTicker();
        content.removeAllViews();

        TextView head = headline(topic);
        content.addView(head);

        LinearLayout meta = Ui.row(this);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.dp(this, 12);
        content.addView(meta, mlp);
        meta.addView(chip(Langs.name(lang)));
        meta.addView(chip("语言用法"));

        LinearLayout nativeCard = Ui.column(this);
        nativeCard.setBackgroundResource(R.drawable.bg_anchor);
        nativeCard.setPadding(Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 15));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.dp(this, 18);
        content.addView(nativeCard, nlp);
        TextView nl = Ui.label(this, "母语者心里");
        nl.setTextColor(getColor(R.color.accent));
        nativeCard.addView(nl);
        TextView nf = Ui.text(this, e.nativeFrame, 15.5f, R.color.text);
        nf.setLineSpacing(Ui.dp(this, 5), 1f);
        LinearLayout.LayoutParams nflp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nflp.topMargin = Ui.dp(this, 7);
        nativeCard.addView(nf, nflp);

        if (!e.types.isEmpty()) {
            LinearLayout box = Ui.column(this);
            for (UsageExplanation.Type t : e.types) {
                LinearLayout row = Ui.column(this);
                row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
                TextView name = Ui.text(this, t.name, 15.5f, R.color.text, Ui.MEDIUM);
                row.addView(name);
                if (!t.use.isEmpty()) {
                    TextView use = Ui.text(this, t.use, 13f, R.color.text_note);
                    use.setLineSpacing(Ui.dp(this, 4), 1f);
                    LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    ulp.topMargin = Ui.dp(this, 4);
                    row.addView(use, ulp);
                }
                box.addView(row);
            }
            section("常见类型", box);
        }

        if (!e.words.isEmpty()) {
            LinearLayout box = Ui.column(this);
            for (UsageExplanation.Word w : e.words) {
                LinearLayout row = Ui.row(this);
                row.setGravity(Gravity.TOP);
                row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
                TextView word = Ui.text(this, w.word, 15f, R.color.text, Ui.MEDIUM);
                row.addView(word);
                TextView gloss = Ui.text(this, w.gloss, 13f, R.color.text_note);
                gloss.setLineSpacing(Ui.dp(this, 4), 1f);
                LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                glp.leftMargin = Ui.dp(this, 12);
                row.addView(gloss, glp);
                box.addView(row);
            }
            section("相关词汇", box);
        }

        if (!e.scenes.isEmpty()) {
            LinearLayout box = Ui.column(this);
            for (int i = 0; i < e.scenes.size(); i++) {
                UsageExplanation.Scene s = e.scenes.get(i);
                LinearLayout item = Ui.column(this);
                item.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 16));

                LinearLayout titleRow = Ui.row(this);
                TextView idx = Ui.text(this, String.format("%02d", i + 1), 11f,
                        R.color.accent, Ui.MEDIUM);
                titleRow.addView(idx, new LinearLayout.LayoutParams(Ui.dp(this, 26),
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView scene = Ui.text(this, s.scene, 14f, R.color.text, Ui.MEDIUM);
                titleRow.addView(scene);
                item.addView(titleRow);

                if (!s.utterance.isEmpty()) {
                    TextView ut = Ui.text(this, s.utterance, 15.5f, R.color.text);
                    ut.setLineSpacing(Ui.dp(this, 4), 1f);
                    LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    ulp.topMargin = Ui.dp(this, 7);
                    item.addView(ut, ulp);
                }
                if (!s.why.isEmpty()) {
                    item.addView(labeled("为什么这样说：", s.why));
                }
                if (!s.effect.isEmpty()) {
                    item.addView(labeled("听者接到：", s.effect));
                }
                box.addView(item);
            }
            section("场景", box);
        }

        if (!e.anchor.isEmpty()) {
            LinearLayout anchorCard = Ui.column(this);
            anchorCard.setBackgroundResource(R.drawable.bg_anchor);
            anchorCard.setPadding(Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 15));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.topMargin = Ui.dp(this, 24);
            content.addView(anchorCard, alp);
            TextView al = Ui.label(this, "速记锚点");
            al.setTextColor(getColor(R.color.accent));
            anchorCard.addView(al);
            TextView at = Ui.text(this, e.anchor, 15.5f, R.color.text, Ui.MEDIUM);
            at.setLineSpacing(Ui.dp(this, 4), 1f);
            LinearLayout.LayoutParams atlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            atlp.topMargin = Ui.dp(this, 7);
            anchorCard.addView(at, atlp);
        }

        if (!e.closing.isEmpty()) {
            TextView close = Ui.text(this, e.closing, 14f, R.color.text_secondary);
            close.setLineSpacing(Ui.dp(this, 5), 1f);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.topMargin = Ui.dp(this, 22);
            content.addView(close, clp);
        }

        TextView foot = Ui.text(this, "由 DeepSeek 生成 · 仅供学习参考", 11f, R.color.text_tertiary);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dp(this, 30);
        content.addView(foot, flp);

        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(220).start();
    }

    private TextView labeled(String label, String body) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        int s = sb.length();
        sb.append(label);
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, sb.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(body);
        TextView t = Ui.text(this, sb, 13f, R.color.text_note);
        t.setLineSpacing(Ui.dp(this, 4), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        t.setLayoutParams(lp);
        return t;
    }

    private void section(String name, View body) {
        TextView label = Ui.text(this, name, 11f, R.color.text_note, Ui.MEDIUM);
        label.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(this, 26);
        content.addView(label, llp);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 8);
        content.addView(body, blp);
    }

    private TextView headline(String w) {
        if (w == null) w = "";
        float sp = w.length() > 22 ? 22f : w.length() > 14 ? 26f : 30f;
        TextView t = Ui.text(this, w, sp, R.color.text, Ui.BOLD);
        t.setLetterSpacing(-0.02f);
        return t;
    }

    private TextView chip(String text) {
        TextView chip = Ui.text(this, text, 11.5f, R.color.text_secondary);
        chip.setBackgroundResource(R.drawable.bg_chip);
        chip.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 6);
        chip.setLayoutParams(lp);
        return chip;
    }

    private void showError(String message) {
        stopTicker();
        content.removeAllViews();
        refreshButton.setVisibility(View.GONE);
        refreshButton.setEnabled(false);

        content.addView(headline(topic));
        LinearLayout box = Ui.column(this);
        box.setBackgroundResource(R.drawable.bg_card_small);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 20);
        content.addView(box, blp);

        TextView title = Ui.text(this, "这次没有生成出来", 16f, R.color.text, Ui.BOLD);
        box.addView(title);
        TextView msg = Ui.text(this, message, 14f, R.color.text_secondary);
        msg.setLineSpacing(Ui.dp(this, 4), 1f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.dp(this, 8);
        box.addView(msg, mlp);

        LinearLayout buttons = Ui.row(this);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp2.topMargin = Ui.dp(this, 18);
        box.addView(buttons, blp2);

        TextView retry = Ui.text(this, "重新试一次", 14.5f, R.color.white, Ui.MEDIUM);
        retry.setGravity(Gravity.CENTER);
        retry.setBackgroundResource(R.drawable.bg_button);
        retry.setOnClickListener(v -> startGeneration());
        buttons.addView(retry, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));

        TextView settings = Ui.text(this, "去设置", 14.5f, R.color.text, Ui.MEDIUM);
        settings.setGravity(Gravity.CENTER);
        settings.setBackgroundResource(R.drawable.bg_card_small);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f);
        slp.leftMargin = Ui.dp(this, 10);
        buttons.addView(settings, slp);
    }

    private void showNoKey() {
        stopTicker();
        content.removeAllViews();
        refreshButton.setVisibility(View.GONE);
        refreshButton.setEnabled(false);

        content.addView(headline(topic));
        LinearLayout box = Ui.column(this);
        box.setBackgroundResource(R.drawable.bg_card_small);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 20);
        content.addView(box, blp);

        TextView title = Ui.text(this, "还没填写 DeepSeek 钥匙", 16f, R.color.text, Ui.BOLD);
        box.addView(title);
        TextView msg = Ui.text(this,
                "语言用法解释需要连上 DeepSeek。钥匙只存在这台手机里，不会发到别处。",
                14f, R.color.text_secondary);
        msg.setLineSpacing(Ui.dp(this, 4), 1f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.dp(this, 8);
        box.addView(msg, mlp);

        TextView go = Ui.text(this, "去填写钥匙", 14.5f, R.color.white, Ui.MEDIUM);
        go.setGravity(Gravity.CENTER);
        go.setBackgroundResource(R.drawable.bg_button);
        go.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        glp.topMargin = Ui.dp(this, 18);
        box.addView(go, glp);
    }

    private void confirmRegenerate() {
        if (Prefs.apiKey(this).isEmpty()) {
            Toast.makeText(this, "还没填写 DeepSeek 钥匙", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("重新生成？")
                .setMessage("会重新解释「" + topic + "」。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重新生成", (d, which) -> startGeneration())
                .create();
        dialog.show();
    }

    private void copyAll() {
        if (current == null) return;
        StringBuilder b = new StringBuilder();
        b.append(current.topic).append("\n\n");
        b.append("母语者心里：").append(current.nativeFrame).append("\n\n");
        if (!current.types.isEmpty()) {
            b.append("常见类型：\n");
            for (UsageExplanation.Type t : current.types) {
                b.append("- ").append(t.name).append("：").append(t.use).append("\n");
            }
            b.append("\n");
        }
        if (!current.words.isEmpty()) {
            b.append("相关词汇：\n");
            for (UsageExplanation.Word w : current.words) {
                b.append("- ").append(w.word).append("：").append(w.gloss).append("\n");
            }
            b.append("\n");
        }
        if (!current.scenes.isEmpty()) {
            b.append("场景：\n");
            for (int i = 0; i < current.scenes.size(); i++) {
                UsageExplanation.Scene s = current.scenes.get(i);
                b.append(i + 1).append(". ").append(s.scene).append("\n");
                if (!s.utterance.isEmpty()) b.append("   ").append(s.utterance).append("\n");
                if (!s.why.isEmpty()) b.append("   为什么这样说：").append(s.why).append("\n");
                if (!s.effect.isEmpty()) b.append("   听者接到：").append(s.effect).append("\n");
            }
            b.append("\n");
        }
        if (!current.anchor.isEmpty()) b.append("速记锚点：").append(current.anchor).append("\n");
        if (!current.closing.isEmpty()) b.append(current.closing).append("\n");

        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), b.toString()));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        token++;
        stopTicker();
    }
}
