from pathlib import Path

root = Path('LeviLaunchroid')

# NetEase APK import compatibility
apk_utils = root / 'app/src/main/java/org/levimc/launcher/util/ApkUtils.java'
text = apk_utils.read_text()
old = '"com.mojang.minecraftpe".equals(packageName) && versionName != null && !versionName.isEmpty()'
new = '("com.mojang.minecraftpe".equals(packageName) || "com.netease.x19".equals(packageName)) && versionName != null && !versionName.isEmpty()'
if text.count(old) != 3:
    raise SystemExit(f'ApkUtils expected 3 package checks, found {text.count(old)}')
apk_utils.write_text(text.replace(old, new))

installer = root / 'app/src/main/java/org/levimc/launcher/util/ApkInstaller.java'
text = installer.read_text()
old = '"com.mojang.minecraftpe".equals(pkgName) && vName != null && !vName.isEmpty()'
new = '("com.mojang.minecraftpe".equals(pkgName) || "com.netease.x19".equals(pkgName)) && vName != null && !vName.isEmpty()'
if text.count(old) != 1:
    raise SystemExit(f'ApkInstaller expected 1 package check, found {text.count(old)}')
installer.write_text(text.replace(old, new))

manifest = root / 'app/src/main/AndroidManifest.xml'
text = manifest.read_text()
needle = '        <package android:name="com.mojang.minecraftpe" />\n'
if 'com.netease.x19' not in text:
    if needle not in text:
        raise SystemExit('Manifest Minecraft query anchor not found')
    text = text.replace(needle, needle + '        <package android:name="com.netease.x19" />\n', 1)
manifest.write_text(text)

# Android 14/15-compatible NetEase class loader.
bridge = root / 'app/src/main/java/org/levimc/launcher/core/minecraft/ChinaDexLoader.java'
bridge.write_text(r'''package org.levimc.launcher.core.minecraft;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.DexClassLoader;

/**
 * Minecraft China Java/JNI bridge.
 *
 * Android 14+ refuses dynamically loaded dex/APK files that are writable.
 * Imported LeviLauncher versions live under Android/media and are writable,
 * so we stage private read-only copies before creating DexClassLoader.
 */
public final class ChinaDexLoader {
    private static final String TAG = "ChinaDexLoader";
    private static final String ANCHOR = "com.netease.androidcrashhandler.NTCrashHunterKit";
    private static DexClassLoader loader;
    private static Class<?> anchorClass;
    private static Method runtimeLoad0;

    private ChinaDexLoader() {}

    private static File stageReadOnly(File source, File dir, int index) throws Exception {
        if (!source.isFile()) throw new IllegalStateException("Missing China APK: " + source);
        String name = index + "_" + source.length() + "_" + source.lastModified() + ".apk";
        File dest = new File(dir, name);
        if (!dest.isFile() || dest.length() != source.length()) {
            if (dest.exists()) {
                dest.setWritable(true, true);
                if (!dest.delete()) throw new IllegalStateException("Cannot replace staged APK: " + dest);
            }
            try (FileInputStream in = new FileInputStream(source);
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[1024 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
                out.getFD().sync();
            }
        }
        // DCL requirement on Android 14/15: source containing executable code must be read-only.
        Os.chmod(dest.getAbsolutePath(), 0444);
        Log.i(TAG, "Read-only staged APK: " + dest + " writable=" + dest.canWrite());
        return dest;
    }

    private static synchronized void ensure(Context context, ApplicationInfo appInfo, String nativeLibDir) throws Exception {
        if (loader != null && anchorClass != null && runtimeLoad0 != null) return;

        List<File> sources = new ArrayList<>();
        if (appInfo.sourceDir != null) sources.add(new File(appInfo.sourceDir));
        if (appInfo.splitSourceDirs != null) {
            for (String split : appInfo.splitSourceDirs) if (split != null) sources.add(new File(split));
        }
        if (sources.isEmpty()) throw new IllegalStateException("Minecraft China APK path list is empty");

        File stageDir = new File(context.getCodeCacheDir(), "netease_apks_ro");
        if (!stageDir.exists() && !stageDir.mkdirs()) {
            throw new IllegalStateException("Cannot create NetEase APK stage dir: " + stageDir);
        }

        List<String> staged = new ArrayList<>();
        int i = 0;
        for (File src : sources) staged.add(stageReadOnly(src, stageDir, i++).getAbsolutePath());

        String dexPath = String.join(File.pathSeparator, staged);
        File optimized = new File(context.getCodeCacheDir(), "netease_dex");
        if (!optimized.exists() && !optimized.mkdirs()) {
            throw new IllegalStateException("Cannot create NetEase dex cache: " + optimized);
        }

        loader = new DexClassLoader(dexPath, optimized.getAbsolutePath(), nativeLibDir, context.getClassLoader());
        anchorClass = Class.forName(ANCHOR, false, loader);
        runtimeLoad0 = Runtime.class.getDeclaredMethod("load0", Class.class, String.class);
        runtimeLoad0.setAccessible(true);
        Log.i(TAG, "NetEase DexClassLoader ready: " + anchorClass + " dexPath=" + dexPath);
    }

    public static void load(Context context, ApplicationInfo appInfo, String nativeLibDir, String absolutePath) throws Exception {
        ensure(context, appInfo, nativeLibDir);
        try {
            runtimeLoad0.invoke(Runtime.getRuntime(), anchorClass, absolutePath);
            Log.i(TAG, "Loaded with NetEase classloader: " + absolutePath);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        }
    }
}
''')

# Only libraries that actually cross into NetEase Java/JNI need the custom class loader.
gpm = root / 'app/src/main/java/org/levimc/launcher/core/minecraft/GamePackageManager.kt'
text = gpm.read_text()
old = '                System.load(libFile.absolutePath)'
new = '''                val chinaJavaBridgeLib = fileName == "libandroidmainruns.so" || fileName == "libminecraftpe.so" || fileName == "libgxcore.so"
                if (isChinaRuntime && chinaJavaBridgeLib) {
                    report("China Java bridge loading $fileName through read-only staged APK DexClassLoader")
                    ChinaDexLoader.load(context, applicationInfo, nativeLibDir, libFile.absolutePath)
                } else {
                    System.load(libFile.absolutePath)
                }'''
if text.count(old) != 1:
    raise SystemExit(f'Expected one System.load anchor, found {text.count(old)}')
gpm.write_text(text.replace(old, new, 1))

# Unique app id so it cannot conflict with v0.1-v0.3 installs.
gradle = root / 'app/build.gradle'
text = gradle.read_text()
old = 'applicationId "org.levimc.launcher"'
if old not in text:
    raise SystemExit('applicationId anchor not found')
gradle.write_text(text.replace(old, 'applicationId "org.levimc.launcher.china.v04"', 1))

strings = root / 'app/src/main/res/values/strings.xml'
text = strings.read_text()
text = text.replace('<string name="app_name">LeviLauncher</string>', '<string name="app_name">LeviLauncher China Test v0.4</string>', 1)
strings.write_text(text)

print('China v0.4 Android 15 read-only DexClassLoader patch applied')
