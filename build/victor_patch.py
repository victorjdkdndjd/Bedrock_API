#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path.cwd()


def need(rel):
    p = ROOT / rel
    if not p.exists():
        raise SystemExit(f"missing: {rel}")
    return p


def change(rel, fn):
    p = need(rel)
    text = p.read_text(encoding="utf-8")
    new = fn(text)
    if new == text:
        print(f"[WARN] no change: {rel}")
    else:
        p.write_text(new, encoding="utf-8")
        print(f"[OK] {rel}")


def once(text, old, new, label):
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"pattern not found: {label}")
    return text.replace(old, new, 1)


# ARM64-only and branding.
def patch_gradle(text):
    if "abiFilters 'arm64-v8a'" not in text:
        anchor = "        multiDexEnabled true //important\n"
        if anchor not in text:
            raise SystemExit("build.gradle multiDexEnabled anchor missing")
        text = text.replace(anchor, anchor + "        ndk {\n            abiFilters 'arm64-v8a'\n        }\n", 1)
    replacements = {
        'resValue "string", "app_name", "Amethyst (Debug)"': 'resValue "string", "app_name", "Victor Java Launcher Offline (Debug)"',
        'resValue "string", "app_short_name", "Amethyst (Debug)"': 'resValue "string", "app_short_name", "Victor Java Offline (Debug)"',
        'resValue "string", "app_name", "Amethyst"': 'resValue "string", "app_name", "Victor Java Launcher Offline"',
        'resValue "string", "app_short_name", "Amethyst"': 'resValue "string", "app_short_name", "Victor Java Offline"',
    }
    for a, b in replacements.items():
        text = text.replace(a, b)
    return text

change("app_pojavlauncher/build.gradle", patch_gradle)


# Account button goes straight to local profile creation.
def patch_launcher(text):
    anchor = "import net.kdt.pojavlaunch.fragments.MainMenuFragment;\n"
    imp = "import net.kdt.pojavlaunch.fragments.LocalLoginFragment;\n"
    if imp not in text:
        if anchor not in text:
            raise SystemExit("LauncherActivity import anchor missing")
        text = text.replace(anchor, anchor + imp, 1)
    return once(
        text,
        "Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);",
        "Tools.swapFragment(this, LocalLoginFragment.class, LocalLoginFragment.TAG, null);",
        "local auth flow",
    )

change("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java", patch_launcher)


# Remove the upstream requirement for an existing online account before local account UI.
def patch_local_login(text):
    text = text.replace("import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;\n\n", "")
    guard = """        // This is overkill but meh
        if (!hasOnlineProfile()){
            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
        }
"""
    return text.replace(guard, "", 1)

change("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/LocalLoginFragment.java", patch_local_login)


# Local profiles may install jars and open their game directory.
def patch_main_menu(text):
    old = """        if (hasOnlineProfile()) {
            mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
            mInstallJarButton.setOnLongClickListener(v -> {
                runInstallerWithConfirmation(true);
                return true;
            });
        } else mInstallJarButton.setOnClickListener(v -> hasNoOnlineProfileDialog(requireActivity()));
"""
    new = """        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
        mInstallJarButton.setOnLongClickListener(v -> {
            runInstallerWithConfirmation(true);
            return true;
        });
"""
    text = once(text, old, new, "local jar installer")
    old = """            } else if (!hasOnlineProfile()) { // Otherwise display the generic pop-up to log in
                hasNoOnlineProfileDialog(requireActivity());
            } else openPath(v.getContext(), getCurrentProfileDirectory(), false);
"""
    new = """            } else {
                openPath(v.getContext(), getCurrentProfileDirectory(), false);
            }
"""
    return once(text, old, new, "local game folder")

change("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java", patch_main_menu)


# Fixed Minecraft 1.21.1 profiles.
def patch_profile_model(text):
    text = text.replace('TEMPLATE.lastVersionId = LATEST_RELEASE;', 'TEMPLATE.lastVersionId = "1.21.1";')
    text = text.replace('defaultProfile.lastVersionId = "1.7.10";', 'defaultProfile.lastVersionId = "1.21.1";')
    return text

change("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/value/launcherprofiles/MinecraftProfile.java", patch_profile_model)


# Profile editor: fixed version and Java 21 preference.
def patch_profile_editor(text):
    old = """    private View.OnClickListener getVersionSelectListener() {
        return v -> VersionSelectorDialog.open(v.getContext(), false, (id, snapshot)-> {
            mTempProfile.lastVersionId = id;
            mDefaultVersion.setText(id);
        });
    }
"""
    new = """    private View.OnClickListener getVersionSelectListener() {
        return v -> {
            mTempProfile.lastVersionId = "1.21.1";
            mDefaultVersion.setText("1.21.1");
        };
    }
"""
    text = once(text, old, new, "fixed version selector")

    old = """        mDefaultRuntime.setAdapter(new RTSpinnerAdapter(context, runtimes));
        if(jvmIndex == -1) jvmIndex = runtimes.size() - 1;
        mDefaultRuntime.setSelection(jvmIndex);

        // Renderer spinner
"""
    new = """        mDefaultRuntime.setAdapter(new RTSpinnerAdapter(context, runtimes));
        String java21Name = MultiRTUtils.getExactJreName(21);
        if (java21Name != null) {
            int java21Index = runtimes.indexOf(new Runtime(java21Name));
            if (java21Index != -1) jvmIndex = java21Index;
        }
        if(jvmIndex == -1) jvmIndex = runtimes.size() - 1;
        mDefaultRuntime.setSelection(jvmIndex);
        if (java21Name != null) mDefaultRuntime.setEnabled(false);

        // Renderer spinner
"""
    text = once(text, old, new, "Java 21 preference")
    text = text.replace(
        "        mDefaultVersion.setText(mTempProfile.lastVersionId);\n",
        '        mTempProfile.lastVersionId = "1.21.1";\n        mDefaultVersion.setText("1.21.1");\n',
        1,
    )
    text = text.replace(
        "        mTempProfile.lastVersionId = mDefaultVersion.getText().toString();\n",
        '        mTempProfile.lastVersionId = "1.21.1";\n',
        1,
    )
    anchor = "        mVersionSelectButton.setOnClickListener(versionSelectListener);\n        mDefaultVersion.setOnClickListener(versionSelectListener);\n"
    extra = anchor + "        mVersionSelectButton.setEnabled(false);\n        mDefaultVersion.setEnabled(false);\n"
    if extra not in text:
        if anchor not in text:
            raise SystemExit("version widget anchor missing")
        text = text.replace(anchor, extra, 1)
    return text

change("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/ProfileEditorFragment.java", patch_profile_editor)


# NeoForge list only for the 21.1 line used by Minecraft 1.21.1.
def patch_neoforge(text):
    old = """                    ForgeVersionListHandler handler = new ForgeVersionListHandler();
                    saxParser.parse(new InputSource(new StringReader(input)), handler);
                    return handler.getVersions();
"""
    new = """                    ForgeVersionListHandler handler = new ForgeVersionListHandler();
                    saxParser.parse(new InputSource(new StringReader(input)), handler);
                    List<String> allVersions = handler.getVersions();
                    allVersions.removeIf(version -> !version.startsWith("21.1."));
                    return allVersions;
"""
    return once(text, old, new, "NeoForge 21.1 filter")

change("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/NeoForgeInstallFragment.java", patch_neoforge)


# Mobile performance defaults.
PERF = r'''package net.victor.javalauncher;

import android.content.Context;
import android.util.DisplayMetrics;

import net.kdt.pojavlaunch.utils.MCOptionUtils;

public final class VictorPerformance {
    public static final String DEFAULT_JVM_ARGS =
            "-XX:+UseG1GC " +
            "-XX:+ParallelRefProcEnabled " +
            "-XX:+UseStringDeduplication " +
            "-XX:MaxGCPauseMillis=100";

    private VictorPerformance() {}

    public static int recommendedRamMb(int totalRamMb) {
        if (totalRamMb < 3072) return 896;
        if (totalRamMb < 4096) return 1152;
        if (totalRamMb < 6144) return 1536;
        if (totalRamMb < 8192) return 2048;
        if (totalRamMb < 12288) return 2560;
        return 3072;
    }

    public static int recommendedResolutionPercent(Context context, boolean powerful) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int minSide = Math.min(metrics.widthPixels, metrics.heightPixels);
        if (minSide <= 720) return 100;
        int targetSide = powerful ? 900 : 720;
        int percent = Math.round((targetSide * 100f) / minSide);
        if (percent < 50) percent = 50;
        if (percent > 100) percent = 100;
        return ((percent + 4) / 5) * 5;
    }

    public static void applyMinecraftDefaults(String gameDir, int totalRamMb) {
        MCOptionUtils.load(gameDir);
        if (MCOptionUtils.get("renderDistance") == null)
            MCOptionUtils.set("renderDistance", Integer.toString(renderDistance(totalRamMb)));
        if (MCOptionUtils.get("simulationDistance") == null)
            MCOptionUtils.set("simulationDistance", Integer.toString(simulationDistance(totalRamMb)));
        if (MCOptionUtils.get("entityDistanceScaling") == null)
            MCOptionUtils.set("entityDistanceScaling", entityDistanceScale(totalRamMb));
        if (MCOptionUtils.get("maxFps") == null)
            MCOptionUtils.set("maxFps", "60");
        if (MCOptionUtils.get("mipmapLevels") == null)
            MCOptionUtils.set("mipmapLevels", totalRamMb < 6144 ? "1" : "2");
        MCOptionUtils.save();
    }

    private static int renderDistance(int ram) {
        if (ram < 4096) return 4;
        if (ram < 6144) return 5;
        if (ram < 8192) return 6;
        return 8;
    }

    private static int simulationDistance(int ram) {
        if (ram < 4096) return 4;
        if (ram < 8192) return 5;
        return 6;
    }

    private static String entityDistanceScale(int ram) {
        if (ram < 4096) return "0.50";
        if (ram < 6144) return "0.65";
        if (ram < 8192) return "0.75";
        return "0.85";
    }
}
'''

TARGET = r'''package net.victor.javalauncher;

public final class VictorOfflineTarget {
    public static final String APP_NAME = "Victor Java Launcher Offline";
    public static final String MINECRAFT_VERSION = "1.21.1";
    public static final int JAVA_MAJOR = 21;
    public static final String ABI = "arm64-v8a";
    public static final String MOD_LOADER = "neoforge";
    public static final String NEOFORGE_VERSION_PREFIX = "21.1.";
    public static final String PERFORMANCE_PROFILE = "AUTO_MOBILE";
    private VictorOfflineTarget() {}
}
'''

victor_dir = ROOT / "app_pojavlauncher/src/main/java/net/victor/javalauncher"
victor_dir.mkdir(parents=True, exist_ok=True)
(victor_dir / "VictorPerformance.java").write_text(PERF, encoding="utf-8")
(victor_dir / "VictorOfflineTarget.java").write_text(TARGET, encoding="utf-8")
print("[OK] Victor helper classes")


def patch_preferences(text):
    anchor = "import net.kdt.pojavlaunch.utils.JREUtils;\n"
    imp = "import net.victor.javalauncher.VictorPerformance;\n"
    if imp not in text:
        if anchor not in text:
            raise SystemExit("LauncherPreferences import anchor missing")
        text = text.replace(anchor, anchor + imp, 1)
    text = text.replace(
        'PREF_RAM_ALLOCATION = DEFAULT_PREF.getInt("allocation", findBestRAMAllocation(ctx));',
        'PREF_RAM_ALLOCATION = DEFAULT_PREF.getInt("allocation", VictorPerformance.recommendedRamMb(Tools.getTotalDeviceMemory(ctx)));',
    )
    text = text.replace(
        'PREF_CUSTOM_JAVA_ARGS = DEFAULT_PREF.getString("javaArgs", "");',
        'PREF_CUSTOM_JAVA_ARGS = DEFAULT_PREF.getString("javaArgs", VictorPerformance.DEFAULT_JVM_ARGS);',
    )
    text = text.replace(
        'PREF_SCALE_FACTOR = DEFAULT_PREF.getInt("resolutionRatio", findBestResolution(ctx, isDevicePowerful))/100f;',
        'PREF_SCALE_FACTOR = DEFAULT_PREF.getInt("resolutionRatio", VictorPerformance.recommendedResolutionPercent(ctx, isDevicePowerful))/100f;',
    )
    return text

change("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java", patch_preferences)


def patch_main_activity(text):
    anchor = "import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;\n"
    imp = "import net.victor.javalauncher.VictorPerformance;\n"
    if imp not in text:
        if anchor not in text:
            raise SystemExit("MainActivity import anchor missing")
        text = text.replace(anchor, anchor + imp, 1)
    old = """        String gameDirPath = Tools.getGameDirPath(minecraftProfile).getAbsolutePath();
        MCOptionUtils.load(gameDirPath);
"""
    new = """        String gameDirPath = Tools.getGameDirPath(minecraftProfile).getAbsolutePath();
        MCOptionUtils.load(gameDirPath);
        VictorPerformance.applyMinecraftDefaults(gameDirPath, Tools.getTotalDeviceMemory(this));
        MCOptionUtils.load(gameDirPath);
"""
    return once(text, old, new, "mobile MC defaults")

change("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MainActivity.java", patch_main_activity)

print("Victor Java Launcher Offline v0.4 patch applied")
