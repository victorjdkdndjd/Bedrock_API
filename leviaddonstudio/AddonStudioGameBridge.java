package org.levimc.launcher.addonstudio;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;

import org.levimc.launcher.preloader.PreloaderInput;

public final class AddonStudioGameBridge {
    private static final long FOCUS_DELAY_MS = 650L;
    private static final long OPEN_DELAY_MS = 260L;
    private static final long ENTER_DELAY_MS = 320L;

    private AddonStudioGameBridge() {}

    public static void spawn(Activity activity, String identifier) {
        String id = identifier == null ? "" : identifier.trim();
        if (id.isEmpty()) {
            Toast.makeText(activity, "ID vazio", Toast.LENGTH_SHORT).show();
            return;
        }
        sendCommand(activity, "/summon " + id + " ^ ^ ^3");
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
        final String commandBody = cmd.substring(1);
        Handler handler = new Handler(Looper.getMainLooper());

        activity.runOnUiThread(() -> {
            Toast.makeText(activity, "Preparando comando...", Toast.LENGTH_SHORT).show();

            handler.postDelayed(() -> {
                activity.getWindow().getDecorView().requestFocus();
                boolean slashHandled = pressThroughPreloader(activity, KeyEvent.KEYCODE_SLASH, '/');

                handler.postDelayed(() -> {
                    boolean textHandled = false;
                    try {
                        textHandled = PreloaderInput.onTextInput(commandBody);
                    } catch (Throwable ignored) {
                    }
                    final boolean finalTextHandled = textHandled;

                    handler.postDelayed(() -> {
                        boolean enterHandled = pressThroughPreloader(activity, KeyEvent.KEYCODE_ENTER, 0);
                        String result = "Comando: " + finalCommand
                                + "\nbridge slash=" + bool(slashHandled)
                                + " text=" + bool(finalTextHandled)
                                + " enter=" + bool(enterHandled);
                        Toast.makeText(activity, result, Toast.LENGTH_LONG).show();
                    }, ENTER_DELAY_MS);
                }, OPEN_DELAY_MS);
            }, FOCUS_DELAY_MS);
        });
    }

    private static boolean pressThroughPreloader(Activity activity, int keyCode, int unicodeChar) {
        boolean handledDown = false;
        boolean handledUp = false;
        try {
            handledDown = PreloaderInput.onKeyEvent(keyCode, unicodeChar, true);
            handledUp = PreloaderInput.onKeyEvent(keyCode, unicodeChar, false);
        } catch (Throwable ignored) {
        }

        if (!(handledDown || handledUp)) {
            dispatchFallback(activity, keyCode);
        }
        return handledDown || handledUp;
    }

    private static void dispatchFallback(Activity activity, int keyCode) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up = new KeyEvent(now, now + 12L, KeyEvent.ACTION_UP, keyCode, 0);
        try {
            activity.dispatchKeyEvent(down);
            activity.dispatchKeyEvent(up);
        } catch (Throwable ignored) {
        }
    }

    private static String bool(boolean value) {
        return value ? "OK" : "fallback";
    }
}
