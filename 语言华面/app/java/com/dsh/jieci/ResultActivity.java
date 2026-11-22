package com.dsh.jieci;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
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

public class ResultActivity extends Activity {

    public static final String EXTRA_WORD = "word";
    public static final String EXTRA_JSON = "json";
    public static final String EXTRA_SAMPLE = "sample";
    public static final String EXTRA_LANG = "lang";
    public static final String EXTRA_FROM_BATCH = "from_batch";

    private String word;
    private String lang;
    private boolean sample;
    private String renderedJson;

    private LinearLayout content;
    private TextView toolbarTitle;
    private ImageView copyButton;
    private ImageView refreshButton;
    private Explanation current;
    private Db db;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable ticker;
    private TextView loadingStatus;
    private int statusIndex;
    private int token;

    private static final String[] PHRASES = {
            "正在拆开可见的词根词缀…",
            "正在找最底层的原始动作…",
            "正在还原一个能站住的画面…",
            "正在把抽象义一条条接回动作…",
            "正在写那一个速记锚点…"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyStatusBar(this);
        db = Db.get(this);
        word = Words.clean(getIntent().getStringExtra(EXTRA_WORD));
        sample = getIntent().getBooleanExtra(EXTRA_SAMPLE, false);
        lang = getIntent().getStringExtra(EXTRA_LANG);
        if (!Langs.valid(lang)) lang = Prefs.lang(this);

        buildUi();

        String json = getIntent().getStringExtra(EXTRA_JSON);
        if (json == null && savedInstanceState != null) {
            json = savedInstanceState.getString("json");
            sample = savedInstanceState.getBoolean("sample", sample);
        }
        if (json == null) {
            Entry cached = db.find(word, lang);
            if (cached != null) {
                json = cached.json;
                sample = false;
            }
        }
        if (json != null) {
            showJson(json, sample);
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
        back.setOnClickListener(v -> goSearch());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        toolbarTitle = Ui.text(this, word, 16f, R.color.text, Ui.MEDIUM);
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

    // ------------------------------------------------------------ generation

    private void startGeneration() {
        final int my = ++token;
        final String key = Prefs.apiKey(this);
        if (key.isEmpty()) {
            Entry s = Samples.find(this, lang, word);
            if (s != null) {
                showJson(s.json, true);
                return;
            }
            showNoKey();
            return;
        }
        showLoading();

        new Thread(() -> {
            try {
                final DeepSeekClient.Result r = DeepSeekClient.explain(word, lang, key,
                        Prefs.baseUrl(ResultActivity.this), Prefs.model(ResultActivity.this));
                if (r.explanation.word == null || r.explanation.word.trim().isEmpty()) {
                    r.explanation.word = word;
                }
                Entry e = new Entry(r.explanation.word, r.explanation.type,
                        r.explanation.anchor, r.json, System.currentTimeMillis());
                e.lang = lang;
                db.upsert(e);
                runOnUiThread(() -> {
                    if (my != token || isFinishing()) return;
                    stopTicker();
                    showJson(r.json, false);
                });
            } catch (final DeepSeekClient.ApiException ex) {
                runOnUiThread(() -> {
                    if (my != token || isFinishing()) return;
                    stopTicker();
                    showError(ex.getMessage());
                });
            }
        }).start();
    }

    private void showLoading() {
        stopTicker();
        content.removeAllViews();
        content.setAlpha(1f);
        refreshButton.setVisibility(View.GONE);
        refreshButton.setEnabled(false);

        TextView head = headline(word);
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

        TextView hint = Ui.text(this, "通常需要十几秒，请稍等", 12f, R.color.text_tertiary);
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

    // ------------------------------------------------------------ render

    private void showJson(String json, boolean asSample) {
        try {
            JSONObject o = new JSONObject(json);
            Explanation ex = Explanation.fromJson(o);
            current = ex;
            sample = asSample;
            renderedJson = json;
            if (!ex.word.isEmpty()) {
                word = ex.word;
                toolbarTitle.setText(word);
            }
            copyButton.setEnabled(true);
            copyButton.setAlpha(1f);
            refreshButton.setVisibility(asSample ? View.GONE : View.VISIBLE);
            refreshButton.setEnabled(!asSample);
            refreshButton.setAlpha(asSample ? 0.35f : 1f);
            render(ex, asSample);
        } catch (Exception e) {
            showError("这段内容读不出来，请重新生成");
        }
    }

    private void render(Explanation e, boolean asSample) {
        stopTicker();
        content.removeAllViews();

        TextView head = headline(e.word.isEmpty() ? word : e.word);
        content.addView(head);
        addPronunciation(e);

        LinearLayout meta = Ui.row(this);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.dp(this, 12);
        content.addView(meta, mlp);

        meta.addView(chip(Langs.name(lang)));
        meta.addView(chip("phrase".equalsIgnoreCase(e.type) ? "短语" : "单词"));
        if (!e.phraseType.isEmpty()) meta.addView(chip(e.phraseType));
        meta.addView(chip(e.decomposable ? "可拆解" : "整体记忆"));

        if (asSample) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = Ui.dp(this, 14);
            content.addView(banner("内置示例 · 没有联网，也没有用你的钥匙"), blp);
        }

        LinearLayout anchorCard = Ui.column(this);
        anchorCard.setBackgroundResource(R.drawable.bg_anchor);
        anchorCard.setPadding(Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 15));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dp(this, 18);
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

        // 拆解
        LinearLayout comps = Ui.column(this);
        if (e.components.isEmpty()) {
            TextView t = paragraph(e.decomposable
                    ? "没有可见的词根词缀，整体记忆。"
                    : "整体记忆，不硬拆。");
            comps.addView(t);
        } else {
            for (Explanation.Part p : e.components) {
                LinearLayout row = Ui.row(this);
                row.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
                TextView part = Ui.text(this, p.part, 16f, R.color.text, Ui.MEDIUM);
                row.addView(part);
                TextView fn = Ui.text(this, p.function, 13f, R.color.text_secondary);
                LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                flp.leftMargin = Ui.dp(this, 14);
                row.addView(fn, flp);
                comps.addView(row);
            }
        }
        if (!e.note.isEmpty()) {
            TextView note = Ui.text(this, e.note, 13f, R.color.text_note);
            note.setLineSpacing(Ui.dp(this, 4), 1f);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = Ui.dp(this, 8);
            comps.addView(note, nlp);
        }
        section("拆解", comps);

        if (!e.originalAction.isEmpty()) {
            section("原始动作", paragraphBold(e.originalAction, e.originalBold));
        }
        if (!e.rootImage.isEmpty()) {
            section("根源画面", paragraphBold(e.rootImage, e.rootBold));
        }
        if (!e.initialMeaning.isEmpty()) section("初意", paragraph(e.initialMeaning));

        if (!e.meanings.isEmpty()) {
            LinearLayout box = Ui.column(this);
            for (int i = 0; i < e.meanings.size(); i++) {
                Explanation.Meaning m = e.meanings.get(i);
                LinearLayout row = Ui.row(this);
                row.setGravity(Gravity.TOP);
                row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));

                TextView idx = Ui.text(this, String.format("%02d", i + 1), 11f,
                        R.color.accent, Ui.MEDIUM);
                row.addView(idx, new LinearLayout.LayoutParams(Ui.dp(this, 26),
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout col = Ui.column(this);
                row.addView(col, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView meaning = Ui.text(this, m.meaning, 15.5f, R.color.text, Ui.MEDIUM);
                meaning.setLineSpacing(Ui.dp(this, 4), 1f);
                col.addView(meaning);

                if (!m.from.isEmpty()) {
                    TextView from = Ui.text(this, m.from, 13f, R.color.text_note);
                    from.setLineSpacing(Ui.dp(this, 4), 1f);
                    LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    flp.topMargin = Ui.dp(this, 4);
                    col.addView(from, flp);
                }
                box.addView(row);
            }
            section("抽象义", box);
        }

        if (!e.examples.isEmpty()) {
            LinearLayout box = Ui.column(this);
            for (Explanation.Example ex : e.examples) {
                box.addView(exampleView(ex));
            }
            section("例句", box);
        }

        if (!e.shortImage.isEmpty()) section("简短画面", paragraph(e.shortImage));
        if (!e.evolution.isEmpty()) section("演化史", paragraph(e.evolution));

        TextView foot = Ui.text(this, asSample
                ? "内置示例 · 未联网生成"
                : "由 DeepSeek 生成 · 仅供学习参考", 11f, R.color.text_tertiary);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dp(this, 34);
        content.addView(foot, flp);
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(220).start();
    }

    private TextView exampleView(Explanation.Example ex) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(ex.en);
        if (!ex.zh.isEmpty()) {
            int start = sb.length();
            sb.append("（").append(ex.zh).append("）");
            sb.setSpan(new ForegroundColorSpan(getColor(R.color.text_note)), start, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (!ex.tag.isEmpty()) {
            int start = sb.length();
            sb.append("——").append(ex.tag);
            if (!ex.tag.endsWith("。") && !ex.tag.endsWith(".")) sb.append("。");
            sb.setSpan(new ForegroundColorSpan(getColor(R.color.text_note)), start, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView t = Ui.text(this, sb, 15f, R.color.text);
        t.setLineSpacing(Ui.dp(this, 5), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 13);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView headline(String w) {
        if (w == null) w = "";
        float sp = w.length() > 26 ? 21f : w.length() > 18 ? 26f : 32f;
        TextView t = Ui.text(this, w, sp, R.color.text, Ui.BOLD);
        t.setLetterSpacing(-0.02f);
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

    private void addPronunciation(Explanation e) {
        if ("en".equals(lang)) {
            boolean hasUk = !e.phoneticUk.isEmpty();
            boolean hasUs = !e.phoneticUs.isEmpty();
            if (!hasUk && !hasUs) return;
            LinearLayout row = Ui.row(this);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Ui.dp(this, 9);
            content.addView(row, rlp);
            if (hasUk) addPhoneticGroup(row, "英", e.phoneticUk, false);
            if (hasUs) addPhoneticGroup(row, "美", e.phoneticUs, true);

            TextView src = Ui.text(this, "音标：DJ / KK · 发音：有道词典", 10.5f, R.color.text_tertiary);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(this, 4);
            content.addView(src, slp);
        } else if (!e.reading.isEmpty()) {
            LinearLayout row = Ui.row(this);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Ui.dp(this, 9);
            content.addView(row, rlp);
            TextView read = Ui.text(this, "读音：" + e.reading, 14f, R.color.text_secondary);
            row.addView(read);
            ImageView speaker = volumeButton();
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    Ui.dp(this, 24), Ui.dp(this, 24));
            ilp.leftMargin = Ui.dp(this, 4);
            row.addView(speaker, ilp);
            speaker.setOnClickListener(v -> Speaker.play(this, word, lang));

            TextView src = Ui.text(this, "发音：有道词典", 10.5f, R.color.text_tertiary);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(this, 4);
            content.addView(src, slp);
        }
    }

    private void addPhoneticGroup(LinearLayout row, String label, String value, boolean us) {
        LinearLayout group = Ui.row(this);
        TextView lab = Ui.text(this, label, 10.5f, R.color.text_tertiary, Ui.MEDIUM);
        group.addView(lab);
        TextView val = Ui.text(this, value, 14f, R.color.text_secondary);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = Ui.dp(this, 4);
        group.addView(val, vlp);
        ImageView speaker = volumeButton();
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                Ui.dp(this, 24), Ui.dp(this, 24));
        ilp.leftMargin = Ui.dp(this, 2);
        group.addView(speaker, ilp);
        speaker.setOnClickListener(v -> Speaker.playEnglish(this, word, us));

        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.rightMargin = Ui.dp(this, 12);
        row.addView(group, glp);
    }

    private ImageView volumeButton() {
        ImageView v = Ui.icon(this, R.drawable.ic_volume, R.color.accent, 18);
        v.setClickable(true);
        v.setFocusable(false);
        v.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
        v.setBackgroundResource(R.drawable.bg_ripple);
        return v;
    }

    private TextView paragraphBold(String text, String bold) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        if (bold != null && !bold.isEmpty()) {
            int i = text.indexOf(bold);
            if (i >= 0) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), i, i + bold.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        TextView t = Ui.text(this, sb, 15.5f, R.color.text);
        t.setLineSpacing(Ui.dp(this, 5), 1f);
        return t;
    }

    private TextView paragraph(String text) {
        TextView t = Ui.text(this, text, 15.5f, R.color.text);
        t.setLineSpacing(Ui.dp(this, 5), 1f);
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

    private TextView banner(String text) {
        TextView t = Ui.text(this, text, 12f, R.color.text_secondary);
        t.setBackgroundResource(R.drawable.bg_card_small);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        return t;
    }

    private void showError(String message) {
        stopTicker();
        content.removeAllViews();
        refreshButton.setVisibility(View.GONE);
        refreshButton.setEnabled(false);

        TextView head = headline(word);
        content.addView(head);

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
        retry.setOnClickListener(v -> {
            token++;
            startGeneration();
        });
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

        TextView head = headline(word);
        content.addView(head);

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
                "解释一个从没查过的词，需要连上 DeepSeek。钥匙只存在这台手机里，不会发到别处。",
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
                .setMessage("会用新格式重新解释「" + word + "」，覆盖这条记录。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重新生成", (d, which) -> startGeneration())
                .create();
        dialog.show();
    }

    private void copyAll() {
        if (current == null) return;
        StringBuilder b = new StringBuilder();
        b.append(current.word).append("\n\n");
        b.append("速记锚点：").append(current.anchor).append("\n\n");
        if (!current.components.isEmpty()) {
            b.append("拆解：");
            for (Explanation.Part p : current.components) {
                b.append(p.part).append("（").append(p.function).append("） ");
            }
            b.append("\n\n");
        }
        if (!current.originalAction.isEmpty()) b.append("原始动作：").append(current.originalAction).append("\n\n");
        if (!current.rootImage.isEmpty()) b.append("根源画面：").append(current.rootImage).append("\n\n");
        if (!current.initialMeaning.isEmpty()) b.append("初意：").append(current.initialMeaning).append("\n\n");
        if (!current.meanings.isEmpty()) {
            b.append("抽象义：\n");
            for (int i = 0; i < current.meanings.size(); i++) {
                Explanation.Meaning m = current.meanings.get(i);
                b.append(i + 1).append(". ").append(m.meaning);
                if (!m.from.isEmpty()) b.append("（").append(m.from).append("）");
                b.append("\n");
            }
            b.append("\n");
        }
        if (!current.examples.isEmpty()) {
            b.append("例句：\n");
            for (Explanation.Example e : current.examples) {
                b.append("- ").append(e.en);
                if (!e.zh.isEmpty()) b.append("（").append(e.zh).append("）");
                if (!e.tag.isEmpty()) b.append("——").append(e.tag);
                b.append("\n");
            }
            b.append("\n");
        }
        if (!current.shortImage.isEmpty()) b.append("简短画面：").append(current.shortImage).append("\n");

        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), b.toString()));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (renderedJson != null) {
            outState.putString("json", renderedJson);
            outState.putBoolean("sample", sample);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        token++;
        stopTicker();
    }

    private void goSearch() {
        if (getIntent().getBooleanExtra(EXTRA_FROM_BATCH, false)) {
            finish();
            return;
        }
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_QUERY_SCREEN, true);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    @Override
    public void onBackPressed() {
        goSearch();
    }
}
