package com.dsh.jieci;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryActivity extends Activity {

    public static final String EXTRA_LANG = "lang";

    private Db db;
    private String lang;
    private ListView list;
    private TextView empty;
    private TextView count;
    private HistoryAdapter adapter;
    private final List<HistoryItem> data = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyStatusBar(this);
        db = Db.get(this);
        lang = getIntent().getStringExtra(EXTRA_LANG);
        if (!Langs.valid(lang)) lang = Prefs.lang(this);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(getColor(R.color.bg));
        setContentView(root);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 20), 0);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56)));

        ImageView back = Ui.iconButton(this, R.drawable.ic_back, R.color.text, 24);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        TextView title = Ui.text(this, "查询历史", 17f, R.color.text, Ui.MEDIUM);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.dp(this, 4);
        bar.addView(title, tlp);

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        count = Ui.text(this, "", 12f, R.color.text_tertiary);
        bar.addView(count);

        TextView hint = Ui.text(this, "单词和语言用法都保存在这里 · 点左边的垃圾桶可以删除", 12f, R.color.text_tertiary);
        hint.setPadding(Ui.dp(this, 20), Ui.dp(this, 2), Ui.dp(this, 20), Ui.dp(this, 12));
        root.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout box = new FrameLayout(this);
        root.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        list = new ListView(this);
        list.setDivider(new ColorDrawable(getColor(R.color.divider)));
        list.setDividerHeight(1);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(this, 24));
        box.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        empty = Ui.text(this, "还没有查询记录\n查过的词和用法会留在这里", 13f, R.color.text_tertiary);
        empty.setGravity(Gravity.CENTER);
        empty.setLineSpacing(Ui.dp(this, 6), 1f);
        box.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        adapter = new HistoryAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> open(data.get(position)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        data.clear();
        for (Entry e : db.all(lang)) data.add(HistoryItem.fromWord(e));
        for (UsageEntry u : db.usageAll(lang)) data.add(HistoryItem.fromUsage(u));
        Collections.sort(data, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        adapter.notifyDataSetChanged();
        count.setText(data.isEmpty() ? "" : data.size() + " 条");
        empty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void open(HistoryItem item) {
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
                    reload();
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(getColor(R.color.danger)));
        dialog.show();
    }

    private final class HistoryAdapter extends BaseAdapter {

        @Override public int getCount() { return data.size(); }

        @Override public Object getItem(int position) { return data.get(position); }

        @Override public long getItemId(int position) { return data.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final HistoryItem item = data.get(position);
            LinearLayout row = new LinearLayout(HistoryActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(HistoryActivity.this, 66));
            row.setPadding(Ui.dp(HistoryActivity.this, 10), 0, Ui.dp(HistoryActivity.this, 20), 0);
            row.setBackgroundResource(R.drawable.bg_ripple);

            ImageView trash = Ui.icon(HistoryActivity.this, R.drawable.ic_trash, R.color.danger, 20);
            trash.setPadding(Ui.dp(HistoryActivity.this, 10), Ui.dp(HistoryActivity.this, 10),
                    Ui.dp(HistoryActivity.this, 10), Ui.dp(HistoryActivity.this, 10));
            trash.setBackgroundResource(R.drawable.bg_ripple);
            trash.setOnClickListener(v -> confirmDelete(item));
            trash.setContentDescription("删除 " + item.title);
            row.addView(trash, new LinearLayout.LayoutParams(
                    Ui.dp(HistoryActivity.this, 40), Ui.dp(HistoryActivity.this, 40)));

            TextView word = Ui.text(HistoryActivity.this, item.title, 16f, R.color.text, Ui.MEDIUM);
            word.setSingleLine(true);
            word.setEllipsize(android.text.TextUtils.TruncateAt.END);
            word.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.44f));
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wlp.leftMargin = Ui.dp(HistoryActivity.this, 10);
            row.addView(word, wlp);

            if (item.kind == HistoryItem.USAGE) {
                TextView tag = Ui.text(HistoryActivity.this, "用法", 9.5f, R.color.accent, Ui.MEDIUM);
                tag.setBackgroundResource(R.drawable.bg_chip);
                tag.setPadding(Ui.dp(HistoryActivity.this, 6), Ui.dp(HistoryActivity.this, 2),
                        Ui.dp(HistoryActivity.this, 6), Ui.dp(HistoryActivity.this, 2));
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tlp.leftMargin = Ui.dp(HistoryActivity.this, 6);
                row.addView(tag, tlp);
            }

            TextView anchor = Ui.text(HistoryActivity.this, item.anchor, 12f, R.color.text_tertiary);
            anchor.setSingleLine(true);
            anchor.setEllipsize(android.text.TextUtils.TruncateAt.END);
            anchor.setGravity(Gravity.END);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            alp.leftMargin = Ui.dp(HistoryActivity.this, 12);
            row.addView(anchor, alp);
            return row;
        }
    }
}
