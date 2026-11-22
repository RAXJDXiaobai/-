package com.dsh.jieci;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {

    public static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static int px(Context c, float v) {
        return Math.round(v);
    }

    public static TextView text(Context c, CharSequence s, float sp, int colorRes) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(c.getColor(colorRes));
        t.setTypeface(REGULAR);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView text(Context c, CharSequence s, float sp, int colorRes, Typeface tf) {
        TextView t = text(c, s, sp, colorRes);
        t.setTypeface(tf);
        return t;
    }

    public static TextView label(Context c, CharSequence s) {
        TextView t = text(c, s, 11f, R.color.text_tertiary, MEDIUM);
        t.setLetterSpacing(0.08f);
        return t;
    }

    public static ImageView icon(Context c, int drawable, int colorRes, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(drawable);
        v.setColorFilter(c.getColor(colorRes));
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return v;
    }

    public static ImageView iconButton(Context c, int drawable, int colorRes, int sizeDp) {
        ImageView v = icon(c, drawable, colorRes, sizeDp);
        int p = dp(c, 10);
        v.setPadding(p, p, p, p);
        v.setBackgroundResource(R.drawable.bg_ripple);
        v.setClickable(true);
        v.setFocusable(false);
        return v;
    }

    public static Drawable roundRect(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static Drawable circle(Context c, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(c.getColor(R.color.divider));
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    public static int statusBarHeight(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? c.getResources().getDimensionPixelSize(id) : dp(c, 26);
    }

    public static void applyStatusBar(Activity a) {
        Window w = a.getWindow();
        w.setStatusBarColor(a.getColor(R.color.bg));
        w.setNavigationBarColor(a.getColor(R.color.bg));
        boolean darkTheme = (a.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int flags = w.getDecorView().getSystemUiVisibility();
        if (!darkTheme) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        w.getDecorView().setSystemUiVisibility(flags);
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }
}
