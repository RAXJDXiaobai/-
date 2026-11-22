package com.dsh.jieci;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EditorialDialog {

    private static final Pattern URL = Pattern.compile("https?://[^\\s，。；、）)]+");

    private EditorialDialog() {}

    public static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = Ui.column(activity);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 18),
                Ui.dp(activity, 20), Ui.dp(activity, 14));

        TextView title = Ui.text(activity, "前言", 20f, R.color.text, Ui.BOLD);
        card.addView(title);

        TextView sub = Ui.text(activity, "第一次使用前，花两分钟看看", 12f, R.color.text_tertiary);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(activity, 5);
        card.addView(sub, slp);

        TextView hint = Ui.text(activity, "文中链接会用手机默认浏览器打开", 10.5f, R.color.text_tertiary);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.dp(activity, 3);
        card.addView(hint, hlp);

        TextView body = new TextView(activity);
        body.setText(buildContent(activity));
        body.setTextSize(16f);
        body.setTextColor(activity.getColor(R.color.text));
        body.setLineSpacing(Ui.dp(activity, 6), 1f);
        body.setMovementMethod(LinkMovementMethod.getInstance());
        body.setHighlightColor(Color.TRANSPARENT);
        body.setTextIsSelectable(false);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        wlp.topMargin = Ui.dp(activity, 10);
        card.addView(scroll, wlp);

        TextView start = Ui.text(activity, "开始使用", 15f, R.color.white, Ui.MEDIUM);
        start.setGravity(Gravity.CENTER);
        start.setBackgroundResource(R.drawable.bg_button);
        start.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 48));
        blp.topMargin = Ui.dp(activity, 12);
        card.addView(start, blp);

        dialog.setContentView(card);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(d -> Prefs.setEditorialShown(activity));

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
            window.setGravity(Gravity.CENTER);
        }
        dialog.show();
        if (window != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            window.setLayout((int) (dm.widthPixels * 0.92f), (int) (dm.heightPixels * 0.84f));
        }
    }

    private static CharSequence buildContent(Activity activity) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int textColor = activity.getColor(R.color.text);
        int linkColor = activity.getColor(R.color.accent);
        try {
            JSONArray arr = new JSONArray(readAsset(activity, "editorial.json"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                String style = item.optString("style");
                String text = item.optString("text");
                if (text.trim().isEmpty()) continue;
                if ("Heading 1".equals(style)) {
                    addHeading(sb, text, 1.35f, textColor);
                } else if ("Heading 2".equals(style)) {
                    addHeading(sb, text, 1.15f, textColor);
                } else {
                    text = text.replace("\n\n\n\nBug说明", "\n\n@@BUG@@");
                    String[] blocks = text.split("\n\n");
                    for (String raw : blocks) {
                        String block = raw.trim();
                        if (block.isEmpty()) continue;
                        if ("@@BUG@@".equals(block)) {
                            addHeading(sb, "Bug说明", 1.15f, textColor);
                        } else {
                            addBody(activity, sb, block, textColor, linkColor);
                        }
                    }
                }
            }
        } catch (Exception e) {
            sb.append("前言加载失败。");
        }
        return sb;
    }

    private static void addHeading(SpannableStringBuilder sb, String text, float scale, int color) {
        if (sb.length() > 0) sb.append("\n\n");
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new RelativeSizeSpan(scale), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void addBody(final Activity activity, SpannableStringBuilder sb, String text,
                                int textColor, int linkColor) {
        if (sb.length() > 0) sb.append("\n\n");
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new ForegroundColorSpan(textColor), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        Matcher m = URL.matcher(text);
        while (m.find()) {
            final String url = m.group();
            int s = start + m.start();
            int e = start + m.end();
            sb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    openBrowser(activity, url);
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    ds.setColor(linkColor);
                    ds.setUnderlineText(true);
                }
            }, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void openBrowser(Activity activity, String url) {
        if (url == null || url.isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "这台手机没有可用的浏览器，打不开网页", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity, "网页打不开，请稍后再试", Toast.LENGTH_SHORT).show();
        }
    }

    private static String readAsset(Activity activity, String name) {
        try (InputStream in = activity.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[]";
        }
    }
}
