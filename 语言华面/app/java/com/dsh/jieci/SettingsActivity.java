package com.dsh.jieci;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private EditText key;
    private EditText base;
    private EditText model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyStatusBar(this);

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

        TextView title = Ui.text(this, "设置", 17f, R.color.text, Ui.MEDIUM);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.dp(this, 4);
        bar.addView(title, tlp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = Ui.column(this);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 36));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView section = Ui.label(this, "DEEPSEEK 钥匙");
        content.addView(section);

        key = new EditText(this);
        key.setBackgroundResource(R.drawable.bg_edit);
        key.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));
        key.setTextSize(15f);
        key.setTextColor(getColor(R.color.text));
        key.setHintTextColor(getColor(R.color.text_tertiary));
        key.setHint("sk-...");
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setTypeface(Ui.REGULAR);
        key.setText(Prefs.apiKey(this));
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        klp.topMargin = Ui.dp(this, 10);
        content.addView(key, klp);

        TextView help = Ui.text(this,
                "在 DeepSeek 开放平台申请的钥匙，只保存在这台手机里，不会发到别处。",
                12f, R.color.text_tertiary);
        help.setLineSpacing(Ui.dp(this, 4), 1f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.dp(this, 8);
        content.addView(help, hlp);

        TextView section2 = Ui.label(this, "高级");
        LinearLayout.LayoutParams s2lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        s2lp.topMargin = Ui.dp(this, 28);
        content.addView(section2, s2lp);

        TextView baseLabel = Ui.text(this, "接口地址", 12.5f, R.color.text_secondary);
        LinearLayout.LayoutParams bllp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bllp.topMargin = Ui.dp(this, 12);
        content.addView(baseLabel, bllp);

        base = new EditText(this);
        base.setBackgroundResource(R.drawable.bg_edit);
        base.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));
        base.setTextSize(14f);
        base.setTextColor(getColor(R.color.text));
        base.setHintTextColor(getColor(R.color.text_tertiary));
        base.setHint(Prefs.DEFAULT_BASE);
        base.setSingleLine(true);
        base.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        base.setTypeface(Ui.REGULAR);
        base.setText(Prefs.baseUrl(this));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 8);
        content.addView(base, blp);

        TextView modelLabel = Ui.text(this, "模型", 12.5f, R.color.text_secondary);
        LinearLayout.LayoutParams mllp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mllp.topMargin = Ui.dp(this, 14);
        content.addView(modelLabel, mllp);

        model = new EditText(this);
        model.setBackgroundResource(R.drawable.bg_edit);
        model.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));
        model.setTextSize(14f);
        model.setTextColor(getColor(R.color.text));
        model.setHintTextColor(getColor(R.color.text_tertiary));
        model.setHint(Prefs.DEFAULT_MODEL);
        model.setSingleLine(true);
        model.setTypeface(Ui.REGULAR);
        model.setText(Prefs.model(this));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.dp(this, 8);
        content.addView(model, mlp);

        TextView save = Ui.text(this, "保存", 15f, R.color.white, Ui.MEDIUM);
        save.setGravity(Gravity.CENTER);
        save.setBackgroundResource(R.drawable.bg_button);
        save.setOnClickListener(v -> {
            Prefs.setApiKey(this, key.getText().toString());
            Prefs.setBaseUrl(this, base.getText().toString());
            Prefs.setModel(this, model.getText().toString());
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(key.getWindowToken(), 0);
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            finish();
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        slp.topMargin = Ui.dp(this, 28);
        content.addView(save, slp);

        TextView foot = Ui.text(this, getString(R.string.app_name) + " 1.0 · 字体跟随系统", 11f, R.color.text_tertiary);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dp(this, 30);
        content.addView(foot, flp);
    }
}
