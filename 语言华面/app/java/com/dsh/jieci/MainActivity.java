package com.dsh.jieci;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    public static final String EXTRA_QUERY_SCREEN = "query_screen";
    public static final String EXTRA_RESET_EDITORIAL = "reset_editorial";

    private static final int TAB_HOME = 0;
    private static final int TAB_MORE = 1;

    private FrameLayout root;
    private LinearLayout hero;
    private LinearLayout card;
    private LinearLayout examples;
    private LinearLayout langButton;
    private TextView langLabel;
    private LinearLayout samplesRow;
    private HorizontalScrollView samplesScroll;
    private LinearLayout modeRow;
    private LinearLayout modeButton;
    private TextView modeLabel;
    private TextView modeHint;
    private EditText input;
    private ImageButton send;
    private TextView curtainEmpty;
    private TextView recentLabel;
    private TextView generateRow;
    private FrameLayout curtain;
    private ImageView gear;
    private ImageView restart;
    private FrameLayout morePage;
    private LinearLayout nav;
    private ImageView homeTabIcon;
    private ImageView moreTabIcon;
    private TextView homeTabLabel;
    private TextView moreTabLabel;
    private GestureDetector gestureDetector;
    private boolean downInExamples;
    private int tab = TAB_HOME;
    private ListView list;
    private SuggestionAdapter adapter;
    private Db db;

    private String lang;
    private String mode;
    private boolean expanded;
    private boolean positionsDone;
    private int cardTop;
    private int targetTop;
    private int lastRootH;

    private final List<HistoryItem> data = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyStatusBar(this);
        db = Db.get(this);
        lang = Prefs.lang(this);
        mode = Prefs.mode(this);
        if (getIntent().getBooleanExtra(EXTRA_RESET_EDITORIAL, false)) {
            Prefs.setEditorialShown(this, false);
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.bg));
        setContentView(root);

        targetTop = Ui.dp(this, 10);

        buildCurtain();
        buildHero();
        buildCard();
        buildExamples();
        buildMode();
        buildGear();
        buildRestart();
        buildMorePage();
        buildNav();
        gestureDetector = new GestureDetector(this, new SwipeListener());

        root.getViewTreeObserver().addOnGlobalLayoutListener(this::onRootLayout);

        adapter = new SuggestionAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                openHistoryItem((HistoryItem) adapter.getItem(position)));

        if (savedInstanceState != null) {
            input.setText(savedInstanceState.getString("query", ""));
            input.setSelection(input.getText().length());
        } else {
            String draft = Prefs.draft(this);
            if (!draft.isEmpty()) {
                input.setText(draft);
                input.setSelection(input.getText().length());
            }
        }

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                Prefs.setDraft(MainActivity.this, s.toString());
                updateSend();
                refreshSuggestions();
            }
        });

        input.setOnClickListener(v -> expand());
        final float[] inputDownX = {0f};
        final float[] inputDownY = {0f};
        final long[] inputDownAt = {0L};
        input.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                inputDownX[0] = event.getX();
                inputDownY[0] = event.getY();
                inputDownAt[0] = SystemClock.uptimeMillis();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - inputDownX[0];
                float dy = event.getY() - inputDownY[0];
                long dt = SystemClock.uptimeMillis() - inputDownAt[0];
                if (Math.abs(dx) < Ui.dp(this, 8) && Math.abs(dy) < Ui.dp(this, 8) && dt < 400) {
                    input.setSelection(input.getText().length());
                    expand();
                }
            }
            return true;
        });
        card.setOnClickListener(v -> {
            input.requestFocus();
            expand();
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                submit();
                return true;
            }
            return false;
        });
        send.setOnClickListener(v -> submit());

        updateSend();
        refreshSuggestions();

        if (getIntent().getBooleanExtra(EXTRA_QUERY_SCREEN, false)) {
            root.post(this::showQueryScreen);
        }

        if (!Prefs.editorialShown(this)) {
            root.postDelayed(this::showEditorial, 400);
        }
    }

    private void showEditorial() {
        if (isFinishing()) return;
        EditorialDialog.show(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_RESET_EDITORIAL, false)) {
            Prefs.setEditorialShown(this, false);
            root.postDelayed(this::showEditorial, 400);
        }
        if (intent.getBooleanExtra(EXTRA_QUERY_SCREEN, false)) {
            root.post(this::showQueryScreen);
        }
    }

    private void showQueryScreen() {
        if (!positionsDone) {
            root.post(this::showQueryScreen);
            return;
        }
        if (tab == TAB_MORE) {
            tab = TAB_HOME;
            updateNav();
            morePage.setVisibility(View.GONE);
            setHomeVisible(true);
        }
        input.setText("");
        Prefs.setDraft(this, "");
        input.clearFocus();
        hideKeyboard();
        if (!expanded) {
            expand(false);
        } else {
            refreshSuggestions();
        }
    }

    // ---------------------------------------------------------------- views

    private void buildCurtain() {
        curtain = new FrameLayout(this);
        curtain.setBackgroundResource(R.drawable.bg_curtain);
        curtain.setVisibility(View.INVISIBLE);
        curtain.setAlpha(0f);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        root.addView(curtain, clp);

        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), 0);
        curtain.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        recentLabel = Ui.label(this, "最近查询");
        col.addView(recentLabel);

        generateRow = Ui.text(this, "", 14f, R.color.accent, Ui.MEDIUM);
        generateRow.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));
        generateRow.setBackgroundResource(R.drawable.bg_card_small);
        generateRow.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_spark, 0, 0, 0);
        generateRow.setCompoundDrawablePadding(Ui.dp(this, 9));
        generateRow.setVisibility(View.GONE);
        generateRow.setOnClickListener(v -> submit());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = Ui.dp(this, 10);
        col.addView(generateRow, glp);

        FrameLayout box = new FrameLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        blp.topMargin = Ui.dp(this, 4);
        col.addView(box, blp);

        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(this, 20));
        box.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        curtainEmpty = Ui.text(this, "", 13f, R.color.text_tertiary);
        curtainEmpty.setGravity(Gravity.CENTER);
        curtainEmpty.setLineSpacing(Ui.dp(this, 6), 1f);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        elp.leftMargin = Ui.dp(this, 12);
        elp.rightMargin = Ui.dp(this, 12);
        box.addView(curtainEmpty, elp);
    }

    private void buildHero() {
        hero = Ui.column(this);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);

        TextView logo = Ui.text(this, "解", 30f, R.color.white, Ui.BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.roundRect(this, getColor(R.color.accent), 20));
        logo.setElevation(Ui.dp(this, 8));
        hero.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));

        TextView title = Ui.text(this, getString(R.string.app_name), 27f, R.color.text, Ui.BOLD);
        title.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(this, 18);
        hero.addView(title, tlp);

        TextView tag = Ui.text(this, getString(R.string.tagline), 13f, R.color.text_secondary);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 8);
        hero.addView(tag, slp);

        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(hero, hlp);
    }

    private void buildCard() {
        card = Ui.column(this);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 10));
        card.setElevation(Ui.dp(this, 3));

        LinearLayout inputRow = Ui.row(this);
        input = new EditText(this);
        input.setBackground(null);
        input.setTextSize(17f);
        input.setTextColor(getColor(R.color.text));
        input.setHintTextColor(getColor(R.color.text_tertiary));
        input.setHint(currentHint());
        input.setSingleLine(true);
        input.setMaxLines(1);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setIncludeFontPadding(false);
        inputRow.addView(input, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));

        send = new ImageButton(this);
        send.setImageResource(R.drawable.ic_arrow_up);
        send.setBackgroundResource(R.drawable.bg_send);
        send.setPadding(Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9));
        send.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        send.setColorFilter(getColor(R.color.white));
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38));
        sendLp.leftMargin = Ui.dp(this, 8);
        inputRow.addView(send, sendLp);
        card.addView(inputRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, 20);
        lp.rightMargin = Ui.dp(this, 20);
        root.addView(card, lp);
    }

    private void buildExamples() {
        examples = Ui.row(this);
        examples.setPadding(Ui.dp(this, 20), 0, 0, 0);

        langButton = Ui.row(this);
        langButton.setBackgroundResource(R.drawable.bg_chip);
        langButton.setPadding(Ui.dp(this, 11), Ui.dp(this, 7), Ui.dp(this, 8), Ui.dp(this, 7));
        langButton.setClickable(true);
        langButton.setFocusable(false);
        langButton.setContentDescription("语言切换");
        langLabel = Ui.text(this, Langs.name(lang), 12f, R.color.text_secondary, Ui.MEDIUM);
        langButton.addView(langLabel);
        ImageView chevron = Ui.icon(this, R.drawable.ic_dropdown, R.color.text_secondary, 16);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16));
        clp.leftMargin = Ui.dp(this, 2);
        langButton.addView(chevron, clp);
        langButton.setOnClickListener(v -> showLangPopup());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.rightMargin = Ui.dp(this, 8);
        examples.addView(langButton, blp);

        samplesScroll = new HorizontalScrollView(this);
        samplesScroll.setHorizontalScrollBarEnabled(false);
        samplesScroll.setClipToPadding(false);
        samplesScroll.setHorizontalFadingEdgeEnabled(true);
        samplesScroll.setFadingEdgeLength(Ui.dp(this, 24));
        samplesScroll.setPadding(0, 0, Ui.dp(this, 20), 0);

        samplesRow = Ui.row(this);
        samplesScroll.addView(samplesRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        examples.addView(samplesScroll, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        refreshSamples();

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(examples, lp);
    }

    private void buildMode() {
        modeRow = Ui.row(this);
        modeRow.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);

        modeButton = Ui.row(this);
        modeButton.setBackgroundResource(R.drawable.bg_chip);
        modeButton.setPadding(Ui.dp(this, 11), Ui.dp(this, 7), Ui.dp(this, 8), Ui.dp(this, 7));
        modeButton.setClickable(true);
        modeButton.setFocusable(false);
        modeButton.setContentDescription("解释模式");
        modeLabel = Ui.text(this, "usage".equals(mode) ? "语言用法" : "词语",
                12f, R.color.text_secondary, Ui.MEDIUM);
        modeButton.addView(modeLabel);
        ImageView chevron = Ui.icon(this, R.drawable.ic_dropdown, R.color.text_secondary, 16);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16));
        clp.leftMargin = Ui.dp(this, 2);
        modeButton.addView(chevron, clp);
        modeButton.setOnClickListener(v -> showModePopup());
        modeRow.addView(modeButton);

        modeHint = Ui.text(this, modeHintText(), 11.5f, R.color.text_tertiary);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = Ui.dp(this, 10);
        modeRow.addView(modeHint, hlp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(modeRow, lp);
    }

    private String modeHintText() {
        return "usage".equals(mode) ? "解释语言用法，如：虚拟语气、完成时" : "解释单词或短语";
    }

    private String currentHint() {
        return "usage".equals(mode) ? "输入语言用法，如：虚拟语气" : Langs.hint(lang);
    }

    private void showModePopup() {
        LinearLayout box = Ui.row(this);
        box.setBackgroundResource(R.drawable.bg_card_small);
        box.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));

        TextView wordOpt = modeOption("单词短语", "word".equals(mode));
        TextView usageOpt = modeOption("语言用法", "usage".equals(mode));
        box.addView(wordOpt);
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.divider));
        box.addView(divider, new LinearLayout.LayoutParams(Ui.dp(this, 1), Ui.dp(this, 24)));
        box.addView(usageOpt);

        final PopupWindow popup = new PopupWindow(box,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        int[] loc = new int[2];
        modeButton.getLocationOnScreen(loc);
        popup.showAtLocation(root, Gravity.TOP | Gravity.START,
                loc[0] + modeButton.getWidth() + Ui.dp(this, 8), loc[1]);

        wordOpt.setOnClickListener(v -> {
            popup.dismiss();
            setMode("word");
        });
        usageOpt.setOnClickListener(v -> {
            popup.dismiss();
            setMode("usage");
        });
    }

    private TextView modeOption(String text, boolean selected) {
        TextView t = Ui.text(this, text, 13f,
                selected ? R.color.accent : R.color.text,
                selected ? Ui.MEDIUM : Ui.REGULAR);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        t.setBackgroundResource(R.drawable.bg_ripple);
        t.setClickable(true);
        return t;
    }

    private void setMode(String code) {
        if (("usage".equals(code) ? "usage" : "word").equals(mode)) return;
        mode = "usage".equals(code) ? "usage" : "word";
        Prefs.setMode(this, mode);
        modeLabel.setText("usage".equals(mode) ? "语言用法" : "词语");
        modeHint.setText(modeHintText());
        input.setHint(currentHint());
        input.setText("");
        refreshSamples();
        refreshSuggestions();
        input.clearFocus();
        hideKeyboard();
    }

    private void buildRestart() {
        restart = Ui.iconButton(this, R.drawable.ic_restart, R.color.text_secondary, 24);
        restart.setContentDescription("重启界面");
        restart.setOnClickListener(v -> confirmRestart());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dp(this, 44), Ui.dp(this, 44), Gravity.TOP | Gravity.END);
        lp.topMargin = Ui.dp(this, 10);
        lp.rightMargin = Ui.dp(this, 56);
        root.addView(restart, lp);
    }

    private void buildMorePage() {
        morePage = new FrameLayout(this);
        morePage.setBackgroundColor(getColor(R.color.bg));
        morePage.setVisibility(View.GONE);
        root.addView(morePage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        morePage.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = Ui.column(this);
        content.setPadding(Ui.dp(this, 24), Ui.dp(this, 72),
                Ui.dp(this, 24), Ui.dp(this, 110));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = Ui.text(this, "更多", 26f, R.color.text, Ui.BOLD);
        content.addView(title);

        TextView sub = Ui.text(this, "设置、说明和关于", 12f, R.color.text_tertiary);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 5);
        content.addView(sub, slp);

        LinearLayout box = Ui.column(this);
        box.setBackgroundResource(R.drawable.bg_card);
        box.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        addMoreRow(box, "设置", "钥匙、接口地址和模型",
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        addMoreDivider(box);
        addMoreRow(box, "前言", "第一次使用的说明和记忆思路",
                v -> EditorialDialog.show(this));
        addMoreDivider(box);
        addMoreRow(box, "关于", "版本 1.0 和版权说明", v -> showAbout());

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 16);
        content.addView(box, blp);

        TextView footer = Ui.text(this, "语言华面 1.0", 11f, R.color.text_tertiary);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dp(this, 24);
        content.addView(footer, flp);
    }

    private void addMoreRow(LinearLayout parent, String title, String sub, View.OnClickListener click) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        row.setBackgroundResource(R.drawable.bg_ripple);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(click);

        LinearLayout texts = Ui.column(this);
        TextView t = Ui.text(this, title, 15f, R.color.text, Ui.MEDIUM);
        texts.addView(t);
        TextView s = Ui.text(this, sub, 11.5f, R.color.text_tertiary);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 3);
        texts.addView(s, slp);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chevron = Ui.icon(this, R.drawable.ic_chevron, R.color.text_tertiary, 16);
        row.addView(chevron, new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addMoreDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.divider));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = Ui.dp(this, 16);
        parent.addView(divider, lp);
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("关于语言华面")
                .setMessage("语言华面 1.0\n把词语拆回最初的动作\n\n"
                        + "内容由 AI 生成，仅供学习参考。\n\n"
                        + "该软件著作权归“热爱星际的小白”所有。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void buildNav() {
        nav = Ui.row(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundResource(R.drawable.bg_card);
        nav.setElevation(Ui.dp(this, 8));
        nav.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        nav.setContentDescription("底部导航");

        LinearLayout homeTab = navItem(R.drawable.ic_home, "主页", true);
        LinearLayout moreTab = navItem(R.drawable.ic_more, "更多", false);
        nav.addView(homeTab, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = Ui.dp(this, 4);
        nav.addView(moreTab, mlp);

        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        nlp.bottomMargin = Ui.dp(this, 16);
        root.addView(nav, nlp);
        updateNav();
    }

    private LinearLayout navItem(int iconRes, String text, boolean home) {
        LinearLayout item = Ui.column(this);
        item.setGravity(Gravity.CENTER);
        item.setPadding(Ui.dp(this, 14), Ui.dp(this, 3), Ui.dp(this, 14), Ui.dp(this, 2));
        item.setBackgroundResource(R.drawable.bg_ripple);
        item.setClickable(true);
        item.setFocusable(true);

        ImageView icon = Ui.icon(this, iconRes, R.color.text_tertiary, 22);
        item.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));

        TextView label = Ui.text(this, text, 10.5f, R.color.text_tertiary, Ui.MEDIUM);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(this, 2);
        item.addView(label, llp);

        if (home) {
            homeTabIcon = icon;
            homeTabLabel = label;
            item.setContentDescription("主页");
            item.setOnClickListener(v -> showHome());
        } else {
            moreTabIcon = icon;
            moreTabLabel = label;
            item.setContentDescription("更多");
            item.setOnClickListener(v -> showMore());
        }
        return item;
    }

    private void updateNav() {
        int active = getColor(R.color.accent);
        int inactive = getColor(R.color.text_tertiary);
        if (homeTabIcon != null) {
            homeTabIcon.setColorFilter(tab == TAB_HOME ? active : inactive);
            homeTabLabel.setTextColor(tab == TAB_HOME ? active : inactive);
            moreTabIcon.setColorFilter(tab == TAB_MORE ? active : inactive);
            moreTabLabel.setTextColor(tab == TAB_MORE ? active : inactive);
        }
    }

    private void showMore() {
        if (tab == TAB_MORE || expanded) return;
        tab = TAB_MORE;
        updateNav();
        input.clearFocus();
        hideKeyboard();
        gear.bringToFront();
        restart.bringToFront();
        morePage.animate().cancel();
        morePage.setVisibility(View.VISIBLE);
        morePage.setAlpha(0f);
        morePage.setTranslationX(Ui.dp(this, 24));
        morePage.animate().alpha(1f).translationX(0f).setDuration(220)
                .withEndAction(() -> {
                    if (tab == TAB_MORE) setHomeVisible(false);
                }).start();
    }

    private void showHome() {
        if (tab == TAB_HOME) return;
        tab = TAB_HOME;
        updateNav();
        morePage.animate().cancel();
        setHomeVisible(true);
        morePage.animate().alpha(0f).setDuration(180)
                .withEndAction(() -> {
                    if (tab == TAB_HOME) morePage.setVisibility(View.GONE);
                }).start();
    }

    private void setHomeVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        hero.setVisibility(v);
        card.setVisibility(v);
        examples.setVisibility(v);
        modeRow.setVisibility(v);
        if (!visible) {
            curtain.setVisibility(View.GONE);
        }
        nav.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downInExamples = inExamples(ev);
            }
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean inExamples(MotionEvent ev) {
        if (examples == null || examples.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        examples.getLocationInWindow(loc);
        float y = ev.getY();
        return y >= loc[1] - Ui.dp(this, 12)
                && y <= loc[1] + examples.getHeight() + Ui.dp(this, 12);
    }

    private final class SwipeListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            if (e1 == null || e2 == null || expanded) return false;
            float dx = e2.getX() - e1.getX();
            float dy = e2.getY() - e1.getY();
            if (Math.abs(dx) < Ui.dp(MainActivity.this, 56)) return false;
            if (Math.abs(dx) < Math.abs(dy) * 1.25f) return false;
            if (Math.abs(vx) < Ui.dp(MainActivity.this, 120)) return false;
            if (dx < 0) {
                if (tab == TAB_HOME && !downInExamples) {
                    showMore();
                    return true;
                }
            } else if (tab == TAB_MORE) {
                showHome();
                return true;
            }
            return false;
        }
    }

    private void confirmRestart() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("重启界面？")
                .setMessage("会清空当前输入、语言和解释模式，回到初始状态。历史记录和钥匙不会删。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重启", (d, which) -> {
                    Prefs.setLang(this, "en");
                    Prefs.setMode(this, "word");
                    Prefs.setDraft(this, "");
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .create();
        dialog.show();
    }

    private void refreshSamples() {
        samplesRow.removeAllViews();
        List<Entry> samples = "usage".equals(mode) ? new ArrayList<>() : Samples.all(this, lang);
        int remain = Math.max(0, 10 - samples.size());
        List<HistoryItem> history = loadHistoryItems();
        int shown = Math.min(remain, history.size());

        if (samples.isEmpty() && shown == 0) {
            samplesScroll.setVisibility(View.GONE);
            return;
        }
        samplesScroll.setVisibility(View.VISIBLE);

        if (!samples.isEmpty()) {
            addChipLabel("示例");
            for (Entry s : samples) {
                TextView chip = Ui.text(this, s.word, 12.5f, R.color.text_secondary);
                chip.setBackgroundResource(R.drawable.bg_chip);
                chip.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
                chip.setClickable(true);
                chip.setOnClickListener(v -> openSample(s));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.rightMargin = Ui.dp(this, 8);
                samplesRow.addView(chip, clp);
            }
        }

        if (shown > 0) {
            addChipLabel("历史");
            for (int i = 0; i < shown; i++) {
                final HistoryItem item = history.get(i);
                String text = item.kind == HistoryItem.USAGE ? "用法 · " + item.title : item.title;
                TextView chip = Ui.text(this, text, 12.5f, R.color.text_history);
                chip.setBackgroundResource(R.drawable.bg_chip);
                chip.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
                chip.setClickable(true);
                chip.setOnClickListener(v -> openHistoryItem(item));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.rightMargin = Ui.dp(this, 8);
                samplesRow.addView(chip, clp);
            }
        }
    }

    private void addChipLabel(String text) {
        TextView label = Ui.text(this, text, 12f, R.color.text_tertiary);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.rightMargin = Ui.dp(this, 8);
        samplesRow.addView(label, llp);
    }

    private List<HistoryItem> loadHistoryItems() {
        List<HistoryItem> list = new ArrayList<>();
        for (Entry e : db.all(lang)) list.add(HistoryItem.fromWord(e));
        for (UsageEntry u : db.usageAll(lang)) list.add(HistoryItem.fromUsage(u));
        Collections.sort(list, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return list;
    }

    private void openHistoryItem(HistoryItem item) {
        resetInstant();
        if (item.kind == HistoryItem.USAGE) {
            Intent i = new Intent(this, UsageActivity.class);
            i.putExtra(UsageActivity.EXTRA_TOPIC, item.title);
            i.putExtra(UsageActivity.EXTRA_LANG, item.lang);
            i.putExtra(UsageActivity.EXTRA_JSON, item.json);
            startActivity(i);
        } else {
            Intent i = new Intent(this, ResultActivity.class);
            i.putExtra(ResultActivity.EXTRA_WORD, item.title);
            i.putExtra(ResultActivity.EXTRA_LANG, item.lang);
            i.putExtra(ResultActivity.EXTRA_JSON, item.json);
            startActivity(i);
        }
    }

    private void showLangPopup() {
        final ListPopupWindow popup = new ListPopupWindow(this);
        popup.setAnchorView(langButton);
        popup.setWidth(Ui.dp(this, 176));
        popup.setHeight(ListPopupWindow.WRAP_CONTENT);
        popup.setModal(true);
        popup.setVerticalOffset(Ui.dp(this, 4));
        popup.setBackgroundDrawable(Ui.roundRect(this, getColor(R.color.card), 16));

        ArrayAdapter<String> popupAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, Langs.NAMES) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = new TextView(MainActivity.this);
                t.setText(Langs.NAMES[position]);
                t.setTextSize(15f);
                t.setIncludeFontPadding(false);
                t.setPadding(Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 6),
                        Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 6));
                boolean selected = position == Langs.index(lang);
                t.setTextColor(getColor(selected ? R.color.accent : R.color.text));
                t.setTypeface(selected ? Ui.MEDIUM : Ui.REGULAR);
                t.setBackgroundResource(R.drawable.bg_ripple);
                return t;
            }
        };
        popup.setAdapter(popupAdapter);
        popup.setOnItemClickListener((parent, view, position, id) -> {
            popup.dismiss();
            setLanguage(Langs.CODES[position]);
        });
        popup.show();
    }

    private void setLanguage(String code) {
        if (!Langs.valid(code) || code.equals(lang)) return;
        lang = code;
        Prefs.setLang(this, code);
        langLabel.setText(Langs.name(code));
        input.setHint(currentHint());
        input.setText("");
        refreshSamples();
        refreshSuggestions();
        input.clearFocus();
        hideKeyboard();
    }

    private void buildGear() {
        gear = Ui.iconButton(this, R.drawable.ic_settings, R.color.text_secondary, 24);
        gear.setContentDescription("设置");
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dp(this, 44), Ui.dp(this, 44), Gravity.TOP | Gravity.END);
        lp.topMargin = Ui.dp(this, 10);
        lp.rightMargin = Ui.dp(this, 8);
        root.addView(gear, lp);
    }

    // ---------------------------------------------------------------- layout

    private void onRootLayout() {
        int h = root.getHeight();
        if (h <= 0) return;
        if (!positionsDone) {
            if (card.getHeight() == 0 || hero.getHeight() == 0) {
                root.post(this::onRootLayout);
                return;
            }
            layoutIdle(h);
            positionsDone = true;
        } else if (expanded && h != lastRootH) {
            updateCurtainHeight(h);
            if (lastRootH > 0 && h < lastRootH) {
                curtain.setTranslationY(0f);
            }
        }
        lastRootH = h;
    }

    private void layoutIdle(int h) {
        int cardH = card.getHeight();
        cardTop = Math.max(Ui.dp(this, 24), (h - cardH) / 2 + Ui.dp(this, 4));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
        lp.topMargin = cardTop;
        card.setLayoutParams(lp);

        int heroH = hero.getHeight();
        int heroTop = cardTop - heroH - Ui.dp(this, 26);
        heroTop = Math.max(Ui.dp(this, 4), heroTop);
        FrameLayout.LayoutParams hlp = (FrameLayout.LayoutParams) hero.getLayoutParams();
        hlp.topMargin = heroTop;
        hero.setLayoutParams(hlp);

        FrameLayout.LayoutParams elp = (FrameLayout.LayoutParams) examples.getLayoutParams();
        elp.topMargin = cardTop + cardH + Ui.dp(this, 14);
        examples.setLayoutParams(elp);

        int exH = Math.max(examples.getHeight(), Ui.dp(this, 32));
        FrameLayout.LayoutParams mlp = (FrameLayout.LayoutParams) modeRow.getLayoutParams();
        mlp.topMargin = cardTop + cardH + Ui.dp(this, 14) + exH + Ui.dp(this, 8);
        modeRow.setLayoutParams(mlp);

        updateCurtainHeight(h);
    }

    private void updateCurtainHeight(int h) {
        int top = targetTop + card.getHeight() + Ui.dp(this, 8);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) curtain.getLayoutParams();
        lp.topMargin = top;
        lp.height = Math.max(0, h - top - Ui.dp(this, 4));
        curtain.setLayoutParams(lp);
    }

    // ---------------------------------------------------------------- state

    private void expand() {
        expand(true);
    }

    private void expand(boolean withKeyboard) {
        if (expanded) {
            if (withKeyboard) {
                input.requestFocus();
                showKeyboard();
            }
            return;
        }
        expanded = true;
        nav.setVisibility(View.GONE);
        updateCurtainHeight(root.getHeight());
        refreshSuggestions();

        int curtainH = ((FrameLayout.LayoutParams) curtain.getLayoutParams()).height;
        float delta = targetTop - cardTop;

        card.animate().translationY(delta).setDuration(300)
                .setInterpolator(new DecelerateInterpolator(1.7f)).start();

        hero.animate().alpha(0f).translationY(-Ui.dp(this, 26)).setDuration(200)
                .withEndAction(() -> {
                    if (expanded) hero.setVisibility(View.GONE);
                }).start();

        examples.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            if (expanded) examples.setVisibility(View.GONE);
        }).start();

        modeRow.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            if (expanded) modeRow.setVisibility(View.GONE);
        }).start();

        gear.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            if (expanded) gear.setVisibility(View.GONE);
        }).start();

        restart.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            if (expanded) restart.setVisibility(View.GONE);
        }).start();

        curtain.setVisibility(View.VISIBLE);
        curtain.setAlpha(0f);
        curtain.setTranslationY(-(curtainH + card.getHeight() + Ui.dp(this, 60)));
        curtain.animate().translationY(0f).alpha(1f).setDuration(380)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();

        if (withKeyboard) {
            input.requestFocus();
            root.postDelayed(this::showKeyboard, 140);
        }
    }

    private void collapse() {
        if (!expanded) return;
        expanded = false;
        nav.setVisibility(View.VISIBLE);
        hideKeyboard();
        input.clearFocus();

        card.animate().translationY(0f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator(1.4f)).start();

        hero.setVisibility(View.VISIBLE);
        hero.setAlpha(0f);
        hero.setTranslationY(-Ui.dp(this, 20));
        hero.animate().alpha(1f).translationY(0f).setDuration(260).start();

        examples.setVisibility(View.VISIBLE);
        examples.setAlpha(0f);
        examples.animate().alpha(1f).setDuration(200).start();

        modeRow.setVisibility(View.VISIBLE);
        modeRow.setAlpha(0f);
        modeRow.animate().alpha(1f).setDuration(200).start();

        gear.setVisibility(View.VISIBLE);
        gear.setAlpha(0f);
        gear.animate().alpha(1f).setDuration(200).start();

        restart.setVisibility(View.VISIBLE);
        restart.setAlpha(0f);
        restart.animate().alpha(1f).setDuration(200).start();

        int curtainH = curtain.getHeight();
        curtain.animate().translationY(-(curtainH + card.getHeight() + Ui.dp(this, 60)))
                .alpha(0f).setDuration(240)
                .withEndAction(() -> {
                    if (!expanded) curtain.setVisibility(View.INVISIBLE);
                }).start();
    }

    private void resetInstant() {
        expanded = false;
        hideKeyboard();
        input.clearFocus();
        card.setTranslationY(0f);
        hero.setVisibility(View.VISIBLE);
        hero.setAlpha(1f);
        hero.setTranslationY(0f);
        examples.setVisibility(View.VISIBLE);
        examples.setAlpha(1f);
        modeRow.setVisibility(View.VISIBLE);
        modeRow.setAlpha(1f);
        gear.setVisibility(View.VISIBLE);
        gear.setAlpha(1f);
        restart.setVisibility(View.VISIBLE);
        restart.setAlpha(1f);
        curtain.setVisibility(View.INVISIBLE);
        curtain.setAlpha(0f);
        nav.setVisibility(View.VISIBLE);
    }

    private void submit() {
        String text = Words.clean(input.getText().toString());
        if (text.isEmpty()) return;

        if ("usage".equals(mode)) {
            resetInstant();
            Intent i = new Intent(this, UsageActivity.class);
            i.putExtra(UsageActivity.EXTRA_TOPIC, text);
            i.putExtra(UsageActivity.EXTRA_LANG, lang);
            startActivity(i);
            return;
        }

        List<String> parts = Words.splitQueries(text);
        if (parts.isEmpty()) return;
        if (parts.size() > 1) {
            resetInstant();
            Intent i = new Intent(this, BatchActivity.class);
            i.putStringArrayListExtra(BatchActivity.EXTRA_WORDS, new ArrayList<>(parts));
            i.putExtra(BatchActivity.EXTRA_LANG, lang);
            startActivity(i);
            return;
        }

        String word = parts.get(0);
        Entry hit = db.find(word, lang);
        if (hit != null) {
            openEntry(hit);
        } else {
            resetInstant();
            Intent i = new Intent(this, ResultActivity.class);
            i.putExtra(ResultActivity.EXTRA_WORD, word);
            i.putExtra(ResultActivity.EXTRA_LANG, lang);
            startActivity(i);
        }
    }

    private void openEntry(Entry e) {
        resetInstant();
        Intent i = new Intent(this, ResultActivity.class);
        i.putExtra(ResultActivity.EXTRA_WORD, e.word);
        i.putExtra(ResultActivity.EXTRA_LANG, e.lang);
        i.putExtra(ResultActivity.EXTRA_JSON, e.json);
        startActivity(i);
    }

    private void openSample(Entry e) {
        resetInstant();
        Intent i = new Intent(this, ResultActivity.class);
        i.putExtra(ResultActivity.EXTRA_WORD, e.word);
        i.putExtra(ResultActivity.EXTRA_LANG, e.lang);
        i.putExtra(ResultActivity.EXTRA_JSON, e.json);
        i.putExtra(ResultActivity.EXTRA_SAMPLE, true);
        startActivity(i);
    }

    // ---------------------------------------------------------------- data

    private void refreshSuggestions() {
        if (adapter == null) return;
        String q = Words.clean(input.getText().toString());
        List<HistoryItem> all = loadHistoryItems();
        data.clear();
        if (q.isEmpty()) {
            int n = Math.min(40, all.size());
            data.addAll(all.subList(0, n));
        } else {
            String k = Words.key(q);
            for (HistoryItem it : all) {
                if (Words.key(it.title).contains(k)) {
                    data.add(it);
                    if (data.size() >= 40) break;
                }
            }
        }
        adapter.setData(data);

        boolean hasQuery = !q.isEmpty();
        boolean exact = hasQuery && hasExact(q);
        recentLabel.setText(hasQuery ? "搜索结果" : "最近查询");
        if (hasQuery && !exact) {
            generateRow.setVisibility(View.VISIBLE);
            generateRow.setText("生成「" + q + "」的解释");
        } else {
            generateRow.setVisibility(View.GONE);
        }

        if (data.isEmpty()) {
            curtainEmpty.setVisibility(View.VISIBLE);
            curtainEmpty.setText(hasQuery
                    ? "没有匹配的记录"
                    : "还没有查询记录\n输入一个词，按搜索开始");
        } else {
            curtainEmpty.setVisibility(View.GONE);
        }
    }

    private boolean hasExact(String q) {
        String k = Words.key(q);
        for (HistoryItem it : loadHistoryItems()) {
            if (Words.key(it.title).equals(k)) return true;
        }
        return false;
    }

    private void confirmDelete(final HistoryItem item) {
        String what = item.kind == HistoryItem.USAGE
                ? "这条语言用法" : "「" + item.title + "」的解释";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("确认删除？")
                .setMessage(what + "会从这台手机删除，不能恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", (d, which) -> {
                    if (item.kind == HistoryItem.USAGE) {
                        db.deleteUsage(item.id);
                    } else {
                        db.delete(item.id);
                    }
                    refreshSuggestions();
                    refreshSamples();
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(getColor(R.color.danger)));
        dialog.show();
    }

    private void copyTitle(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    private void updateSend() {
        boolean has = input.getText().toString().trim().length() > 0;
        send.setEnabled(has);
        send.setAlpha(has ? 1f : 0.35f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSamples();
        if (expanded) {
            refreshSuggestions();
        } else {
            input.clearFocus();
            hideKeyboard();
        }
    }

    @Override
    public void onBackPressed() {
        if (expanded) {
            collapse();
        } else if (tab == TAB_MORE) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("query", input.getText().toString());
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && input.isFocused()) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && input != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    // ---------------------------------------------------------------- adapter

    private final class SuggestionAdapter extends BaseAdapter {

        private List<HistoryItem> items = new ArrayList<>();

        void setData(List<HistoryItem> list) {
            items = list;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }

        @Override public Object getItem(int position) { return items.get(position); }

        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(MainActivity.this, 2), Ui.dp(MainActivity.this, 8),
                    Ui.dp(MainActivity.this, 4), Ui.dp(MainActivity.this, 8));
            row.setBackgroundResource(R.drawable.bg_ripple);

            final HistoryItem item = items.get(position);

            ImageView trash = Ui.icon(MainActivity.this, R.drawable.ic_trash_outline,
                    R.color.danger, 20);
            trash.setPadding(Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 8),
                    Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 8));
            trash.setBackgroundResource(R.drawable.bg_ripple);
            trash.setClickable(true);
            trash.setFocusable(false);
            trash.setOnClickListener(v -> confirmDelete(item));
            trash.setContentDescription("删除 " + item.title);
            row.addView(trash, new LinearLayout.LayoutParams(
                    Ui.dp(MainActivity.this, 36), Ui.dp(MainActivity.this, 36)));

            ImageView copy = Ui.icon(MainActivity.this, R.drawable.ic_copy,
                    R.color.text_secondary, 18);
            copy.setPadding(Ui.dp(MainActivity.this, 7), Ui.dp(MainActivity.this, 7),
                    Ui.dp(MainActivity.this, 7), Ui.dp(MainActivity.this, 7));
            copy.setBackgroundResource(R.drawable.bg_ripple);
            copy.setClickable(true);
            copy.setFocusable(false);
            copy.setOnClickListener(v -> copyTitle(item.title));
            copy.setContentDescription("复制 " + item.title);
            row.addView(copy, new LinearLayout.LayoutParams(
                    Ui.dp(MainActivity.this, 32), Ui.dp(MainActivity.this, 32)));

            TextView word = Ui.text(MainActivity.this, item.title, 16f, R.color.text, Ui.MEDIUM);
            word.setSingleLine(true);
            word.setEllipsize(android.text.TextUtils.TruncateAt.END);
            word.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.40f));
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wlp.leftMargin = Ui.dp(MainActivity.this, 4);
            row.addView(word, wlp);

            if (item.kind == HistoryItem.USAGE) {
                TextView tag = Ui.text(MainActivity.this, "用法", 9.5f, R.color.accent, Ui.MEDIUM);
                tag.setBackgroundResource(R.drawable.bg_chip);
                tag.setPadding(Ui.dp(MainActivity.this, 6), Ui.dp(MainActivity.this, 2),
                        Ui.dp(MainActivity.this, 6), Ui.dp(MainActivity.this, 2));
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tlp.leftMargin = Ui.dp(MainActivity.this, 6);
                row.addView(tag, tlp);
            }

            TextView anchor = Ui.text(MainActivity.this, item.anchor, 12f, R.color.text_tertiary);
            anchor.setSingleLine(true);
            anchor.setEllipsize(android.text.TextUtils.TruncateAt.END);
            anchor.setGravity(Gravity.END);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            alp.leftMargin = Ui.dp(MainActivity.this, 12);
            row.addView(anchor, alp);
            return row;
        }
    }
}
