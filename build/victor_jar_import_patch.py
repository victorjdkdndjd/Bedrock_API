#!/usr/bin/env python3
from pathlib import Path

ROOT = Path.cwd()


def patch_file(rel, fn):
    p = ROOT / rel
    if not p.exists():
        raise SystemExit(f"missing: {rel}")
    text = p.read_text(encoding="utf-8")
    new = fn(text)
    if new == text:
        raise SystemExit(f"no change made: {rel}")
    p.write_text(new, encoding="utf-8")
    print(f"[OK] {rel}")


def patch_launcher(text):
    uri_import = "import android.net.Uri;\n"
    if uri_import not in text:
        anchor = "import android.database.Cursor;\n"
        if anchor not in text:
            raise SystemExit("LauncherActivity Uri import anchor missing")
        text = text.replace(anchor, anchor + uri_import, 1)

    launcher_field = '''    public final ActivityResultLauncher<String[]> modImportLauncher =\n            registerForActivityResult(new ActivityResultContracts.OpenDocument(), (data)-> {\n                if(data != null) importModJar(data);\n            });\n'''
    anchor = "    public final ActivityResultLauncher<Object> modInstallerLauncher =\n"
    if launcher_field not in text:
        if anchor not in text:
            raise SystemExit("modInstallerLauncher anchor missing")
        text = text.replace(anchor, launcher_field + anchor, 1)

    method = r'''    private void importModJar(Uri data) {
        LauncherProfiles.load();
        MinecraftProfile profile = LauncherProfiles.getCurrentProfile();
        if (profile == null) {
            Toast.makeText(this, "Nenhuma instancia selecionada", Toast.LENGTH_LONG).show();
            return;
        }

        final File modsDir = new File(Tools.getGameDirPath(profile), "mods");
        PojavApplication.sExecutorService.execute(() -> {
            String fileName = "mod.jar";
            try (Cursor cursor = getContentResolver().query(
                    data,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (column >= 0) fileName = cursor.getString(column);
                }
            } catch (Exception ignored) {}

            if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Selecione um arquivo .jar",
                        Toast.LENGTH_LONG
                ).show());
                return;
            }

            fileName = fileName.replace("/", "_").replace("\\", "_");
            if (!modsDir.exists() && !modsDir.mkdirs()) {
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Nao foi possivel criar a pasta mods",
                        Toast.LENGTH_LONG
                ).show());
                return;
            }

            File destination = new File(modsDir, fileName);
            if (destination.exists()) {
                String base = fileName.substring(0, fileName.length() - 4);
                int i = 1;
                do {
                    destination = new File(modsDir, base + " (" + i + ").jar");
                    i++;
                } while (destination.exists() && i < 1000);
            }

            try (InputStream input = getContentResolver().openInputStream(data);
                 FileOutputStream output = new FileOutputStream(destination)) {
                if (input == null) throw new IOException("Could not open selected jar");
                byte[] buffer = new byte[262144];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.flush();
                final String importedName = destination.getName();
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Mod importado: " + importedName,
                        Toast.LENGTH_LONG
                ).show());
            } catch (IOException e) {
                Log.e("VictorModImport", "Failed to import mod jar", e);
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Falha ao importar o mod .jar",
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }

'''
    method_anchor = "    private mcAccountSpinner mAccountSpinner;\n"
    if method not in text:
        if method_anchor not in text:
            raise SystemExit("LauncherActivity method anchor missing")
        text = text.replace(method_anchor, method + method_anchor, 1)
    return text


patch_file(
    "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java",
    patch_launcher,
)


def patch_main_menu(text):
    import_line = "import net.kdt.pojavlaunch.LauncherActivity;\n"
    if import_line not in text:
        anchor = "import net.kdt.pojavlaunch.CustomControlsActivity;\n"
        if anchor not in text:
            raise SystemExit("MainMenu import anchor missing")
        text = text.replace(anchor, anchor + import_line, 1)

    old = '''        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
        mInstallJarButton.setOnLongClickListener(v -> {
            runInstallerWithConfirmation(true);
            return true;
        });
'''
    new = '''        mInstallJarButton.setText("Importar mod .jar");
        mInstallJarButton.setOnClickListener(v -> {
            if (requireActivity() instanceof LauncherActivity) {
                ((LauncherActivity) requireActivity()).modImportLauncher.launch(new String[]{"*/*"});
            }
        });
        mInstallJarButton.setOnLongClickListener(v -> {
            runInstallerWithConfirmation(false);
            return true;
        });
'''
    if new in text:
        return text
    if old not in text:
        raise SystemExit("MainMenu v0.4 jar button block missing")
    return text.replace(old, new, 1)


patch_file(
    "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java",
    patch_main_menu,
)

print("Victor Java Launcher Offline v0.4.1 jar import fix applied")
