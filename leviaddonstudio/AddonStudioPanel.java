package org.levimc.launcher.addonstudio;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class AddonStudioPanel {
    private AddonStudioPanel() {}

    public static void show(Activity activity) {
        activity.runOnUiThread(() -> open(activity));
    }

    private static void open(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 18));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(28, 28, 30));
        bg.setCornerRadius(dp(activity, 18));
        root.setBackground(bg);

        TextView title = text(activity, "LeviAddon Studio v0.2", 22);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        TextView subtitle = text(activity,
                "Criar e editar entidades sem fechar o Minecraft.", 14);
        subtitle.setTextColor(Color.LTGRAY);
        root.addView(subtitle);

        EditText namespace = field(activity, "Namespace", "victor", false);
        EditText identifier = field(activity, "ID da entidade", "custom_entity", false);
        EditText health = field(activity, "Vida", "20", true);
        EditText movement = field(activity, "Velocidade", "0.20", true);
        EditText damage = field(activity, "Dano", "3", true);

        root.addView(namespace);
        root.addView(identifier);
        root.addView(health);
        root.addView(movement);
        root.addView(damage);

        TextView status = text(activity,
                "v0.2: preparando lista de entidades, edicao e testes no mundo.",
                13);
        status.setTextColor(Color.LTGRAY);
        status.setPadding(0, dp(activity, 10), 0, dp(activity, 10));
        root.addView(status);

        Button create = new Button(activity);
        create.setText("CRIAR / SALVAR");
        create.setOnClickListener(v -> {
            try {
                double h = parse(health.getText().toString(), 20);
                double m = parse(movement.getText().toString(), 0.20);
                double d = parse(damage.getText().toString(), 3);

                AddonWorkspace.Result result = AddonWorkspace.writeEntity(
                        activity,
                        namespace.getText().toString(),
                        identifier.getText().toString(),
                        h, m, d
                );

                status.setText(
                        "Salvo: " + result.identifier +
                        "\n\n" + result.entityFile.getAbsolutePath()
                );
                status.setTextColor(Color.rgb(120, 235, 140));
            } catch (Throwable throwable) {
                status.setText("Erro: " + throwable.getClass().getSimpleName()
                        + "\n" + String.valueOf(throwable.getMessage()));
                status.setTextColor(Color.rgb(255, 120, 120));
            }
        });
        root.addView(create);

        Button close = new Button(activity);
        close.setText("FECHAR");
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(close);

        scroll.addView(root);
        dialog.setContentView(scroll);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.45f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f);
            params.height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.82f);
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }

        dialog.show();
    }

    private static EditText field(Activity activity, String hint, String value, boolean number) {
        EditText edit = new EditText(activity);
        edit.setHint(hint);
        edit.setHintTextColor(Color.GRAY);
        edit.setText(value);
        edit.setTextColor(Color.WHITE);
        edit.setSingleLine(true);
        if (number) {
            edit.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dp(activity, 8);
        edit.setLayoutParams(lp);
        return edit;
    }

    private static TextView text(Activity activity, String value, float size) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    private static double parse(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
