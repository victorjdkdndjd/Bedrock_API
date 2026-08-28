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

# Android 15-safe NetEase class loader. All China native libraries are loaded
# by one in-memory thunk so bionic keeps them in the same classloader namespace.
bridge = root / 'app/src/main/java/org/levimc/launcher/core/minecraft/ChinaDexLoader.java'
bridge.write_text(r'''package org.levimc.launcher.core.minecraft;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.system.Os;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.DexClassLoader;
import dalvik.system.InMemoryDexClassLoader;

public final class ChinaDexLoader {
    private static final String TAG = "ChinaDexLoader";
    private static final String ANCHOR = "com.netease.androidcrashhandler.NTCrashHunterKit";
    private static final String THUNK_CLASS = "org.levimc.chinabridge.NativeLoadThunk";
    private static final String THUNK_ASSET = "china_native_load.dex";

    private static DexClassLoader neteaseLoader;
    private static ClassLoader thunkLoader;
    private static Class<?> anchorClass;
    private static Method thunkLoad;

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
        Os.chmod(dest.getAbsolutePath(), 0444);
        Log.i(TAG, "Read-only staged APK: " + dest + " writable=" + dest.canWrite());
        return dest;
    }

    private static byte[] readAsset(Context context, String name) throws Exception {
        try (InputStream in = context.getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static synchronized void ensure(Context context, ApplicationInfo appInfo, String nativeLibDir) throws Exception {
        if (neteaseLoader != null && thunkLoader != null && anchorClass != null && thunkLoad != null) return;

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

        neteaseLoader = new DexClassLoader(dexPath, optimized.getAbsolutePath(), nativeLibDir, context.getClassLoader());
        anchorClass = Class.forName(ANCHOR, false, neteaseLoader);
        Log.i(TAG, "NetEase DexClassLoader ready: " + anchorClass + " loader=" + anchorClass.getClassLoader());

        byte[] thunkDex = readAsset(context, THUNK_ASSET);
        ByteBuffer buffer = ByteBuffer.allocateDirect(thunkDex.length);
        buffer.put(thunkDex);
        buffer.flip();
        thunkLoader = new InMemoryDexClassLoader(buffer, neteaseLoader);
        Class<?> thunkClass = Class.forName(THUNK_CLASS, true, thunkLoader);
        thunkLoad = thunkClass.getMethod("load", String.class);
        Log.i(TAG, "Shared China native namespace thunk ready: " + thunkClass + " loader=" + thunkClass.getClassLoader());
    }

    public static void load(Context context, ApplicationInfo appInfo, String nativeLibDir, String absolutePath) throws Exception {
        ensure(context, appInfo, nativeLibDir);
        try {
            thunkLoad.invoke(null, absolutePath);
            Log.i(TAG, "Loaded in shared China namespace: " + absolutePath);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        }
    }
}
''')

# IMPORTANT: every China .so must enter through the same thunk/ClassLoader.
# Otherwise libminecraftpe.so ends up in clns-N and cannot resolve PhysX loaded
# earlier in the launcher's default namespace.
gpm = root / 'app/src/main/java/org/levimc/launcher/core/minecraft/GamePackageManager.kt'
text = gpm.read_text()
old = '                System.load(libFile.absolutePath)'
new = '''                if (isChinaRuntime) {
                    report("China shared namespace loading $fileName")
                    ChinaDexLoader.load(context, applicationInfo, nativeLibDir, libFile.absolutePath)
                } else {
                    System.load(libFile.absolutePath)
                }'''
if text.count(old) != 1:
    raise SystemExit(f'Expected one System.load anchor, found {text.count(old)}')
gpm.write_text(text.replace(old, new, 1))

# Keep the v0.5 package ID from now on. v0.6+ updates install over each other.
gradle = root / 'app/build.gradle'
text = gradle.read_text()
old = 'applicationId "org.levimc.launcher"'
if old not in text:
    raise SystemExit('applicationId anchor not found')
text = text.replace(old, 'applicationId "org.levimc.launcher.china.v05"', 1)

# Stable diagnostic signing key. The key is intentionally a test-only key.
needle = '''    buildTypes {\n'''
signing = '''    signingConfigs {\n        chinaTest {\n            storeFile file("levichina-test.jks")\n            storePassword "levichina123"\n            keyAlias "levichina"\n            keyPassword "levichina123"\n        }\n    }\n    buildTypes {\n'''
if needle not in text:
    raise SystemExit('buildTypes anchor not found')
text = text.replace(needle, signing, 1)

needle = '''        debug {\n            minifyEnabled false\n'''
replace = '''        debug {\n            signingConfig signingConfigs.chinaTest\n            minifyEnabled false\n'''
if needle not in text:
    raise SystemExit('debug buildType anchor not found')
text = text.replace(needle, replace, 1)
gradle.write_text(text)

strings = root / 'app/src/main/res/values/strings.xml'
text = strings.read_text()
text = text.replace('<string name="app_name">LeviLauncher</string>', '<string name="app_name">LeviLauncher China Test</string>', 1)
strings.write_text(text)

print('China v0.6 shared native namespace + stable signing patch applied')
