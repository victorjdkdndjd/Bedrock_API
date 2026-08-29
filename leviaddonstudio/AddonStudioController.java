package org.levimc.launcher.addonstudio;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

public final class AddonStudioController {
    private final Activity activity;
    private Button floatingButton;

    public AddonStudioController(Activity activity) {
        this.activity = activity;
    }

    public void show() {
        if (floatingButton != null) return;

        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;

        FrameLayout host;
        if (content instanceof FrameLayout) {
            host = (FrameLayout) content;
        } else {
            host = new FrameLayout(activity);
            ((ViewGroup) content).addView(
                    host,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
        }

        Button button = new Button(activity);
        button.setText("A");
        button.setTextColor(Color.WHITE);
        button.setTextSize(18f);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(225, 35, 35, 35));
        bg.setStroke(dp(2), Color.argb(220, 90, 210, 120));
        button.setBackground(bg);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(58), dp(58));
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = dp(14);
        lp.topMargin = dp(170);
        host.addView(button, lp);

        final float[] downRaw = new float[2];
        final float[] start = new float[2];
        final boolean[] moved = new boolean[1];

        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRaw[0] = event.getRawX();
                    downRaw[1] = event.getRawY();
                    start[0] = v.getX();
                    start[1] = v.getY();
                    moved[0] = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRaw[0];
                    float dy = event.getRawY() - downRaw[1];

                    if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) {
                        moved[0] = true;
                    }

                    float nx = Math.max(0, Math.min(start[0] + dx,
                            host.getWidth() - v.getWidth()));
                    float ny = Math.max(0, Math.min(start[1] + dy,
                            host.getHeight() - v.getHeight()));

                    v.setX(nx);
                    v.setY(ny);
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!moved[0]) {
                        AddonStudioPanel.show(activity);
                    }
                    return true;
            }
            return false;
        });

        floatingButton = button;
    }

    public void hide() {
        if (floatingButton == null) return;
        ViewGroup parent = (ViewGroup) floatingButton.getParent();
        if (parent != null) parent.removeView(floatingButton);
        floatingButton = null;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
