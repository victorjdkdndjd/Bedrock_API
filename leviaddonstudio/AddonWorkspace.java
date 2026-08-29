package org.levimc.launcher.addonstudio;

import android.app.Activity;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class AddonWorkspace {
    private static final String PACK_NAME = "LeviAddonStudio";
    private static final String MANIFEST_SEED = "victor";
    private static final String VANILLA_ASSET_DIR = "leviaddonstudio/vanilla_entities";

    private AddonWorkspace() {}

    public static Result writeEntity(Activity activity, String namespace, String entityName,
                                     double health, double movement, double damage) throws Exception {
        namespace = sanitize(namespace, "victor");
        entityName = sanitize(entityName, "custom_entity");
        File bp = getBehaviorPack(activity);
        File rp = getResourcePack(activity);
        File entities = getEntitiesDirectory(activity);
        ensureDirectory(entities);
        ensureDirectory(rp);
        writeBehaviorManifest(bp);
        writeResourceManifest(rp);

        String identifier = namespace + ":" + entityName;
        JSONObject root = new JSONObject();
        root.put("format_version", "1.21.50");
        JSONObject entity = new JSONObject();
        JSONObject description = new JSONObject();
        description.put("identifier", identifier);
        description.put("is_spawnable", true);
        description.put("is_summonable", true);
        description.put("is_experimental", false);
        entity.put("description", description);
        JSONObject components = new JSONObject();
        components.put("minecraft:health", new JSONObject().put("value", health).put("max", health));
        components.put("minecraft:movement", new JSONObject().put("value", movement));
        components.put("minecraft:attack", new JSONObject().put("damage", damage));
        components.put("minecraft:collision_box", new JSONObject().put("width", 0.6).put("height", 1.8));
        components.put("minecraft:movement.basic", new JSONObject());
        components.put("minecraft:navigation.walk", new JSONObject()
                .put("can_path_over_water", false).put("avoid_water", false).put("can_walk", true));
        components.put("minecraft:physics", new JSONObject());
        components.put("minecraft:pushable", new JSONObject()
                .put("is_pushable", true).put("is_pushable_by_piston", true));
        components.put("minecraft:nameable", new JSONObject());
        entity.put("components", components);
        root.put("minecraft:entity", entity);
        File entityFile = new File(entities, entityName + ".json");
        writePretty(entityFile, root);
        return new Result(identifier, bp, rp, entityFile, 1, 1, 1);
    }

    public static Result writeVanillaOverride(Activity activity, String entityName,
                                              double health, double movement, double damage) throws Exception {
        entityName = sanitizeVanillaName(entityName);
        String raw = readVanillaAsset(activity, entityName);
        JSONObject root = new JSONObject(stripJsonComments(raw));
        JSONObject entity = root.getJSONObject("minecraft:entity");
        JSONObject description = entity.getJSONObject("description");
        String identifier = description.optString("identifier", "minecraft:" + entityName);
        if (!identifier.startsWith("minecraft:")) {
            throw new IllegalStateException("Template nao e vanilla: " + identifier);
        }

        int[] counts = new int[3];
        patchComponents(root, health, movement, damage, counts);

        File bp = getBehaviorPack(activity);
        File rp = getResourcePack(activity);
        File entities = getEntitiesDirectory(activity);
        ensureDirectory(entities);
        ensureDirectory(rp);
        writeBehaviorManifest(bp);
        writeResourceManifest(rp);
        File entityFile = new File(entities, entityName + ".json");
        writePretty(entityFile, root);
        return new Result(identifier, bp, rp, entityFile, counts[0], counts[1], counts[2]);
    }

    private static void patchComponents(Object node, double health, double movement, double damage, int[] counts) throws Exception {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            List<String> keys = new ArrayList<>();
            Iterator<String> it = obj.keys();
            while (it.hasNext()) keys.add(it.next());
            for (String key : keys) {
                Object value = obj.opt(key);
                if ("minecraft:health".equals(key) && value instanceof JSONObject) {
                    ((JSONObject) value).put("value", health).put("max", health);
                    counts[0]++;
                } else if ("minecraft:movement".equals(key) && value instanceof JSONObject) {
                    ((JSONObject) value).put("value", movement);
                    counts[1]++;
                } else if ("minecraft:attack".equals(key) && value instanceof JSONObject) {
                    ((JSONObject) value).put("damage", damage);
                    counts[2]++;
                }
                patchComponents(value, health, movement, damage, counts);
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                patchComponents(array.opt(i), health, movement, damage, counts);
            }
        }
    }

    public static List<EntityInfo> listEntities(Activity activity) {
        List<EntityInfo> result = new ArrayList<>();
        File dir = getEntitiesDirectory(activity);
        File[] files = dir.listFiles((parent, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null) return result;
        for (File file : files) {
            try { result.add(readEntity(file)); } catch (Throwable ignored) {}
        }
        Collections.sort(result, Comparator.comparing(info -> info.identifier));
        return result;
    }

    public static List<EntityInfo> listCustomEntities(Activity activity) {
        List<EntityInfo> out = new ArrayList<>();
        for (EntityInfo info : listEntities(activity)) {
            if (!info.identifier.startsWith("minecraft:")) out.add(info);
        }
        return out;
    }

    public static List<EntityInfo> listVanillaTemplates(Activity activity) {
        List<EntityInfo> result = new ArrayList<>();
        try {
            AssetManager assets = activity.getApplicationContext().getAssets();
            String[] names = assets.list(VANILLA_ASSET_DIR);
            if (names == null) return result;
            for (String name : names) {
                if (!name.endsWith(".json")) continue;
                try {
                    String entityName = name.substring(0, name.length() - 5);
                    String raw = readVanillaAsset(activity, entityName);
                    JSONObject root = new JSONObject(stripJsonComments(raw));
                    JSONObject entity = root.getJSONObject("minecraft:entity");
                    String identifier = entity.getJSONObject("description")
                            .optString("identifier", "minecraft:" + entityName);
                    double h = firstComponentNumber(root, "minecraft:health", "value", 20.0);
                    double m = firstComponentNumber(root, "minecraft:movement", "value", 0.20);
                    double d = firstComponentNumber(root, "minecraft:attack", "damage", 0.0);
                    result.add(new EntityInfo("minecraft", entityName, identifier, h, m, d, null));
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        Collections.sort(result, Comparator.comparing(info -> info.identifier));
        return result;
    }

    public static EntityInfo readEntity(File file) throws Exception {
        JSONObject root = new JSONObject(stripJsonComments(readText(file)));
        JSONObject entity = root.getJSONObject("minecraft:entity");
        String identifier = entity.getJSONObject("description")
                .optString("identifier", file.getName().replace(".json", ""));
        String namespace = "victor";
        String entityName = identifier;
        int colon = identifier.indexOf(':');
        if (colon > 0 && colon < identifier.length() - 1) {
            namespace = identifier.substring(0, colon);
            entityName = identifier.substring(colon + 1);
        }
        double h = firstComponentNumber(root, "minecraft:health", "value", 20.0);
        double m = firstComponentNumber(root, "minecraft:movement", "value", 0.20);
        double d = firstComponentNumber(root, "minecraft:attack", "damage", 0.0);
        return new EntityInfo(namespace, entityName, identifier, h, m, d, file);
    }

    public static WorldInfo detectCurrentWorld(Activity activity) {
        File worlds = new File(getMojangDirectory(activity), "minecraftWorlds");
        File[] dirs = worlds.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) return null;
        File best = null;
        long bestStamp = Long.MIN_VALUE;
        for (File dir : dirs) {
            long stamp = newestStamp(dir);
            if (stamp > bestStamp) {
                bestStamp = stamp;
                best = dir;
            }
        }
        if (best == null) return null;
        String name = best.getName();
        File levelName = new File(best, "levelname.txt");
        try {
            if (levelName.isFile()) {
                String text = readText(levelName).trim();
                if (!text.isEmpty()) name = text;
            }
        } catch (Throwable ignored) {}
        return new WorldInfo(name, best, bestStamp);
    }

    public static WorldInfo activatePackForCurrentWorld(Activity activity) throws Exception {
        File bp = getBehaviorPack(activity);
        File rp = getResourcePack(activity);
        ensureDirectory(bp);
        ensureDirectory(rp);
        writeBehaviorManifest(bp);
        writeResourceManifest(rp);
        WorldInfo world = detectCurrentWorld(activity);
        if (world == null) throw new IllegalStateException("Nenhum mundo detectado");
        ensurePackRef(new File(world.directory, "world_behavior_packs.json"), behaviorPackUuid());
        ensurePackRef(new File(world.directory, "world_resource_packs.json"), resourcePackUuid());
        return world;
    }

    private static long newestStamp(File dir) {
        long stamp = dir.lastModified();
        String[] important = {"level.dat", "levelname.txt", "session.lock", "world_behavior_packs.json", "world_resource_packs.json"};
        for (String name : important) {
            File f = new File(dir, name);
            if (f.exists()) stamp = Math.max(stamp, f.lastModified());
        }
        File db = new File(dir, "db");
        File[] dbFiles = db.listFiles();
        if (dbFiles != null) {
            for (File f : dbFiles) stamp = Math.max(stamp, f.lastModified());
        }
        return stamp;
    }

    private static void ensurePackRef(File file, String uuid) throws Exception {
        JSONArray array;
        if (file.isFile()) {
            String text = readText(file).trim();
            array = text.isEmpty() ? new JSONArray() : new JSONArray(text);
        } else {
            array = new JSONArray();
        }
        boolean found = false;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && uuid.equals(item.optString("pack_id"))) {
                item.put("version", version());
                found = true;
            }
        }
        if (!found) array.put(new JSONObject().put("pack_id", uuid).put("version", version()));
        writeArrayPretty(file, array);
    }

    public static File getBehaviorPack(Activity activity) {
        return new File(getMojangDirectory(activity), "development_behavior_packs/" + PACK_NAME + "_BP");
    }

    public static File getResourcePack(Activity activity) {
        return new File(getMojangDirectory(activity), "development_resource_packs/" + PACK_NAME + "_RP");
    }

    public static File getEntitiesDirectory(Activity activity) {
        return new File(getBehaviorPack(activity), "entities");
    }

    public static String getWorkspacePath(Activity activity) {
        return getBehaviorPack(activity).getAbsolutePath();
    }

    private static File getMojangDirectory(Activity activity) {
        File external = activity.getExternalFilesDir(null);
        if (external == null) external = activity.getFilesDir();
        return new File(external, "games/com.mojang");
    }

    private static void writeBehaviorManifest(File bp) throws Exception {
        JSONObject manifest = new JSONObject();
        manifest.put("format_version", 2);
        JSONObject header = new JSONObject();
        header.put("name", PACK_NAME + " BP");
        header.put("description", "Behavior Pack criado dentro do LeviLauncher");
        header.put("uuid", behaviorPackUuid());
        header.put("version", version());
        header.put("min_engine_version", new JSONArray().put(1).put(21).put(0));
        manifest.put("header", header);
        JSONObject module = new JSONObject();
        module.put("type", "data");
        module.put("uuid", stableUuid(MANIFEST_SEED + ":bp:module"));
        module.put("version", version());
        manifest.put("modules", new JSONArray().put(module));
        writePretty(new File(bp, "manifest.json"), manifest);
    }

    private static void writeResourceManifest(File rp) throws Exception {
        JSONObject manifest = new JSONObject();
        manifest.put("format_version", 2);
        JSONObject header = new JSONObject();
        header.put("name", PACK_NAME + " RP");
        header.put("description", "Resource Pack criado dentro do LeviLauncher");
        header.put("uuid", resourcePackUuid());
        header.put("version", version());
        header.put("min_engine_version", new JSONArray().put(1).put(21).put(0));
        manifest.put("header", header);
        JSONObject module = new JSONObject();
        module.put("type", "resources");
        module.put("uuid", stableUuid(MANIFEST_SEED + ":rp:module"));
        module.put("version", version());
        manifest.put("modules", new JSONArray().put(module));
        writePretty(new File(rp, "manifest.json"), manifest);
    }

    private static String behaviorPackUuid() { return stableUuid(MANIFEST_SEED + ":bp:header"); }
    private static String resourcePackUuid() { return stableUuid(MANIFEST_SEED + ":rp:header"); }
    private static JSONArray version() { return new JSONArray().put(0).put(3).put(1); }

    private static String readVanillaAsset(Activity activity, String entityName) throws Exception {
        String path = VANILLA_ASSET_DIR + "/" + sanitizeVanillaName(entityName) + ".json";
        try (InputStream in = activity.getApplicationContext().getAssets().open(path)) {
            return readStream(in);
        }
    }

    private static String sanitizeVanillaName(String value) {
        if (value == null) return "zombie";
        String s = value.trim().toLowerCase();
        if (s.startsWith("minecraft:")) s = s.substring("minecraft:".length());
        s = s.replaceAll("[^a-z0-9_\\-.]", "_");
        return s.isEmpty() ? "zombie" : s;
    }

    private static double firstComponentNumber(Object node, String component, String property, double fallback) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            Object direct = obj.opt(component);
            if (direct instanceof JSONObject) {
                Object v = ((JSONObject) direct).opt(property);
                if (v instanceof Number) return ((Number) v).doubleValue();
                try { return Double.parseDouble(String.valueOf(v)); } catch (Throwable ignored) {}
            }
            Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                double found = firstComponentNumber(obj.opt(it.next()), component, property, Double.NaN);
                if (!Double.isNaN(found)) return found;
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                double found = firstComponentNumber(array.opt(i), component, property, Double.NaN);
                if (!Double.isNaN(found)) return found;
            }
        }
        return fallback;
    }

    private static String stripJsonComments(String input) {
        StringBuilder out = new StringBuilder(input.length());
        boolean inString = false, escape = false, line = false, block = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            char n = i + 1 < input.length() ? input.charAt(i + 1) : '\0';
            if (line) {
                if (c == '\n' || c == '\r') { line = false; out.append(c); }
                continue;
            }
            if (block) {
                if (c == '*' && n == '/') { block = false; i++; }
                continue;
            }
            if (inString) {
                out.append(c);
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; out.append(c); continue; }
            if (c == '/' && n == '/') { line = true; i++; continue; }
            if (c == '/' && n == '*') { block = true; i++; continue; }
            out.append(c);
        }
        return out.toString();
    }

    private static String stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String sanitize(String value, String fallback) {
        if (value == null) return fallback;
        String s = value.trim().toLowerCase().replaceAll("[^a-z0-9_\\-.]", "_").replaceAll("_+", "_");
        return s.isEmpty() ? fallback : s;
    }

    private static void ensureDirectory(File dir) {
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Falha ao criar " + dir);
    }

    private static String readText(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) { return readStream(in); }
    }

    private static String readStream(InputStream in) throws Exception {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static void writePretty(File file, JSONObject json) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) ensureDirectory(parent);
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeArrayPretty(File file, JSONArray json) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) ensureDirectory(parent);
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    public static final class Result {
        public final String identifier;
        public final File behaviorPack;
        public final File resourcePack;
        public final File entityFile;
        public final int healthEdits;
        public final int movementEdits;
        public final int damageEdits;
        Result(String identifier, File behaviorPack, File resourcePack, File entityFile,
               int healthEdits, int movementEdits, int damageEdits) {
            this.identifier = identifier;
            this.behaviorPack = behaviorPack;
            this.resourcePack = resourcePack;
            this.entityFile = entityFile;
            this.healthEdits = healthEdits;
            this.movementEdits = movementEdits;
            this.damageEdits = damageEdits;
        }
    }

    public static final class EntityInfo {
        public final String namespace;
        public final String entityName;
        public final String identifier;
        public final double health;
        public final double movement;
        public final double damage;
        public final File file;
        EntityInfo(String namespace, String entityName, String identifier,
                   double health, double movement, double damage, File file) {
            this.namespace = namespace;
            this.entityName = entityName;
            this.identifier = identifier;
            this.health = health;
            this.movement = movement;
            this.damage = damage;
            this.file = file;
        }
    }

    public static final class WorldInfo {
        public final String name;
        public final File directory;
        public final long stamp;
        WorldInfo(String name, File directory, long stamp) {
            this.name = name;
            this.directory = directory;
            this.stamp = stamp;
        }
    }
}
