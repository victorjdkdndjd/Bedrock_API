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
import android.widget.Toast;

import java.util.List;

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

        TextView title = text(activity, "LeviAddon Studio v0.3", 22);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        TextView subtitle = text(activity,
                "Editor de Addon + Editor Vanilla dentro do Minecraft.", 14);
        subtitle.setTextColor(Color.LTGRAY);
        root.addView(subtitle);

        AddonWorkspace.WorldInfo detected = AddonWorkspace.detectCurrentWorld(activity);
        TextView worldStatus = text(activity,
                detected == null ? "Mundo detectado: nenhum" : "Mundo detectado: " + detected.name,
                13);
        worldStatus.setTextColor(detected == null ? Color.rgb(255, 170, 100) : Color.rgb(120, 220, 160));
        root.addView(worldStatus);

        final boolean[] vanillaMode = {false};
        final int[] entityCursor = {0};
        final int[] vanillaCursor = {0};

        Button mode = new Button(activity);
        mode.setText("MODO: ADDON");
        root.addView(mode);

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
                "Entidades do Studio: " + AddonWorkspace.listCustomEntities(activity).size() +
                        "\nTemplates vanilla: " + AddonWorkspace.listVanillaTemplates(activity).size() +
                        "\nWorkspace: " + AddonWorkspace.getWorkspacePath(activity),
                13);
        status.setTextColor(Color.LTGRAY);
        status.setPadding(0, dp(activity, 10), 0, dp(activity, 10));
        root.addView(status);

        Button nextEntity = new Button(activity);
        nextEntity.setText("CARREGAR PROXIMA ENTIDADE");
        root.addView(nextEntity);

        mode.setOnClickListener(v -> {
            vanillaMode[0] = !vanillaMode[0];
            entityCursor[0] = 0;
            vanillaCursor[0] = 0;
            if (vanillaMode[0]) {
                mode.setText("MODO: VANILLA");
                namespace.setText("minecraft");
                namespace.setEnabled(false);
                identifier.setText("zombie");
                health.setText("20");
                movement.setText("0.23");
                damage.setText("3");
                status.setText("Modo Vanilla: usa a definicao oficial completa como base e altera somente os componentes encontrados.");
                status.setTextColor(Color.rgb(255, 210, 110));
            } else {
                mode.setText("MODO: ADDON");
                namespace.setEnabled(true);
                namespace.setText("victor");
                identifier.setText("custom_entity");
                health.setText("20");
                movement.setText("0.20");
                damage.setText("3");
                status.setText("Modo Addon: cria ou edita entidades do LeviAddonStudio_BP.");
                status.setTextColor(Color.LTGRAY);
            }
        });

        nextEntity.setOnClickListener(v -> {
            if (vanillaMode[0]) {
                List<AddonWorkspace.EntityInfo> entities = AddonWorkspace.listVanillaTemplates(activity);
                if (entities.isEmpty()) {
                    status.setText("Nenhum template vanilla foi incluido no APK.");
                    status.setTextColor(Color.rgb(255, 120, 120));
                    return;
                }
                int index = vanillaCursor[0] % entities.size();
                vanillaCursor[0] = index + 1;
                AddonWorkspace.EntityInfo info = entities.get(index);
                namespace.setText("minecraft");
                identifier.setText(info.entityName);
                health.setText(formatNumber(info.health));
                movement.setText(formatNumber(info.movement));
                damage.setText(formatNumber(info.damage));
                status.setText("Vanilla: " + info.identifier + "\nTemplate oficial carregado.");
                status.setTextColor(Color.rgb(255, 210, 110));
                return;
            }

            List<AddonWorkspace.EntityInfo> entities = AddonWorkspace.listCustomEntities(activity);
            if (entities.isEmpty()) {
                status.setText("Nenhuma entidade custom criada ainda.");
                status.setTextColor(Color.GRAY);
                return;
            }
            int index = entityCursor[0] % entities.size();
            entityCursor[0] = index + 1;
            AddonWorkspace.EntityInfo info = entities.get(index);
            namespace.setText(info.namespace);
            identifier.setText(info.entityName);
            health.setText(formatNumber(info.health));
            movement.setText(formatNumber(info.movement));
            damage.setText(formatNumber(info.damage));
            status.setText("Editando: " + info.identifier + "\n" +
                    (info.file == null ? "" : info.file.getAbsolutePath()));
            status.setTextColor(Color.rgb(120, 190, 255));
        });

        Button save = new Button(activity);
        save.setText("CRIAR / SALVAR");
        save.setOnClickListener(v -> {
            try {
                double h = parse(health.getText().toString(), 20);
                double m = parse(movement.getText().toString(), 0.20);
                double d = parse(damage.getText().toString(), 3);
                AddonWorkspace.Result result;
                if (vanillaMode[0]) {
                    result = AddonWorkspace.writeVanillaOverride(
                            activity, identifier.getText().toString(), h, m, d);
                    status.setText(
                            "Vanilla salvo: " + result.identifier +
                            "\nhealth alterado em " + result.healthEdits + " ponto(s)" +
                            "\nmovement alterado em " + result.movementEdits + " ponto(s)" +
                            "\nattack alterado em " + result.damageEdits + " ponto(s)" +
                            "\n" + result.entityFile.getAbsolutePath()
                    );
                } else {
                    result = AddonWorkspace.writeEntity(
                            activity,
                            namespace.getText().toString(),
                            identifier.getText().toString(),
                            h, m, d
                    );
                    status.setText(
                            "Salvo: " + result.identifier +
                            "\nEntidades do Studio: " + AddonWorkspace.listCustomEntities(activity).size() +
                            "\n" + result.entityFile.getAbsolutePath()
                    );
                }
                status.setTextColor(Color.rgb(120, 235, 140));
            } catch (Throwable throwable) {
                status.setText("Erro: " + throwable.getClass().getSimpleName()
                        + "\n" + String.valueOf(throwable.getMessage()));
                status.setTextColor(Color.rgb(255, 120, 120));
            }
        });
        root.addView(save);

        Button spawn = new Button(activity);
        spawn.setText("SPAWN / TESTAR");
        spawn.setOnClickListener(v -> {
            String ns = vanillaMode[0] ? "minecraft" : namespace.getText().toString().trim();
            String id = identifier.getText().toString().trim();
            if (id.contains(":")) {
                String[] parts = id.split(":", 2);
                ns = parts[0];
                id = parts[1];
            }
            String fullId = ns + ":" + id;
            dialog.dismiss();
            AddonStudioGameBridge.spawn(activity, fullId);
        });
        root.addView(spawn);

        Button apply = new Button(activity);
        apply.setText("APLICAR NO MUNDO + RELOAD ALL");
        apply.setOnClickListener(v -> {
            try {
                AddonWorkspace.WorldInfo world = AddonWorkspace.activatePackForCurrentWorld(activity);
                Toast.makeText(activity,
                        "Pack vinculado a " + world.name + ". Recarregando...",
                        Toast.LENGTH_LONG).show();
                dialog.dismiss();
                AddonStudioGameBridge.reloadAll(activity);
            } catch (Throwable throwable) {
                status.setText("Falha ao aplicar: " + throwable.getClass().getSimpleName()
                        + "\n" + String.valueOf(throwable.getMessage()));
                status.setTextColor(Color.rgb(255, 120, 120));
            }
        });
        root.addView(apply);

        TextView reloadNote = text(activity,
                "Reload All requer cheats/permissao de administrador. O jogo pode reentrar automaticamente no mundo para recarregar os packs.",
                12);
        reloadNote.setTextColor(Color.GRAY);
        root.addView(reloadNote);

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
            params.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.88f);
            params.height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.86f);
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
        try { return Double.parseDouble(value.trim().replace(',', '.')); }
        catch (Throwable ignored) { return fallback; }
    }

    private static String formatNumber(double value) {
        if (Math.rint(value) == value) return String.valueOf((long) value);
        return String.valueOf(value);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
