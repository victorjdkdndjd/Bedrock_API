from pathlib import Path

TARGET = Path("app/src/main/java/org/levimc/launcher/core/minecraft/MinecraftActivity.kt")
text = TARGET.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    if new in text:
        print(f"{label}: already applied")
        return
    if old not in text:
        raise SystemExit(f"Patch anchor not found: {label}")
    text = text.replace(old, new, 1)
    print(f"{label}: applied")


replace_once(
    "import org.levimc.launcher.core.mods.inbuilt.overlay.InbuiltOverlayManager\n",
    "import org.levimc.launcher.core.mods.inbuilt.overlay.InbuiltOverlayManager\n"
    "import org.levimc.launcher.addonstudio.AddonStudioController\n",
    "AddonStudio import",
)

replace_once(
    "    private var overlayManager: InbuiltOverlayManager? = null\n",
    "    private var overlayManager: InbuiltOverlayManager? = null\n"
    "    private var addonStudioController: AddonStudioController? = null\n",
    "AddonStudio field",
)

replace_once(
    "        if (overlayManager == null) {\n"
    "            startInbuiltModServices()\n"
    "        }\n"
    "    }\n\n"
    "    private fun isMouseSource(source: Int): Boolean {",
    "        if (overlayManager == null) {\n"
    "            startInbuiltModServices()\n"
    "        }\n\n"
    "        if (addonStudioController == null) {\n"
    "            addonStudioController = AddonStudioController(this)\n"
    "            addonStudioController?.show()\n"
    "        }\n"
    "    }\n\n"
    "    private fun isMouseSource(source: Int): Boolean {",
    "AddonStudio onResume",
)

replace_once(
    "    override fun onDestroy() {\n"
    "        ModManager.disableAndUnloadLoadedMods()",
    "    override fun onDestroy() {\n"
    "        addonStudioController?.hide()\n"
    "        addonStudioController = null\n\n"
    "        ModManager.disableAndUnloadLoadedMods()",
    "AddonStudio onDestroy",
)

TARGET.write_text(text, encoding="utf-8")
print(f"Patched {TARGET}")
