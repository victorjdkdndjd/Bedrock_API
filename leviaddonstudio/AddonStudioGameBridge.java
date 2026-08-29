package org.levimc.launcher.addonstudio;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;

import org.levimc.launcher.preloader.PreloaderInput;

public final class AddonStudioGameBridge {
    private AddonStudioGameBridge() {}

    public static void spawn(Activity activity, String identifier) {
        String id = identifier == null ? "" : identifier.trim();
        if (id.isEmpty()) {
            Toast.makeText(activity, "ID vazio", Toast.LENGTH_SHORT).show();
            return;
        }
        sendCommand(activity, "/summon " + id + " ~ ~ ~");
    }

    public static void reloadAll(Activity activity) {
        sendCommand(activity, "/reload all");
    }

    public static void sendCommand(Activity activity, String command) {
        if (activity == null || command == null) return;
        String cmd = command.trim();
        if (cmd.isEmpty()) return;
        if (!cmd.startsWith("/")) cmd = "/" + cmd;
        final String finalCommand = cmd;

        activity.runOnUiThread(() -> {
            Toast.makeText(activity, "Executando: " + finalCommand, Toast.LENGTH_SHORT).show();
            press(activity, KeyEvent.KEYCODE_T);
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> PreloaderInput.onTextInput(finalCommand), 180L);
            handler.postDelayed(() -> press(activity, KeyEvent.KEYCODE_ENTER), 420L);
        });
    }

    private static void press(Activity activity, int keyCode) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up = new KeyEvent(now, now + 10L, KeyEvent.ACTION_UP, keyCode, 0);
        activity.dispatchKeyEvent(down);
        activity.dispatchKeyEvent(up);
    }
}
