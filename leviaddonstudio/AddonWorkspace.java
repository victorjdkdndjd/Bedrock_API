package org.levimc.launcher.addonstudio;

import android.app.Activity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class AddonWorkspace {
    private static final String PACK_NAME = "LeviAddonStudio";
    private static final String MANIFEST_SEED = "victor";

    private AddonWorkspace() {}

    public static Result writeEntity(
            Activity activity,
            String namespace,
            String entityName,
            double health,
            double movement,
            double damage
    ) throws Exception {
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

        JSONObject healthComponent = new JSONObject();
        healthComponent.put("value", health);
        healthComponent.put("max", health);
        components.put("minecraft:health", healthComponent);

        JSONObject movementComponent = new JSONObject();
        movementComponent.put("value", movement);
        components.put("minecraft:movement", movementComponent);

        JSONObject attack = new JSONObject();
        attack.put("damage", damage);
        components.put("minecraft:attack", attack);

        JSONObject collision = new JSONObject();
        collision.put("width", 0.6);
        collision.put("height", 1.8);
        components.put("minecraft:collision_box", collision);

        components.put("minecraft:movement.basic", new JSONObject());

        JSONObject navigation = new JSONObject();
        navigation.put("can_path_over_water", false);
        navigation.put("avoid_water", false);
        navigation.put("can_walk", true);
        components.put("minecraft:navigation.walk", navigation);

        components.put("minecraft:physics", new JSONObject());

        JSONObject pushable = new JSONObject();
        pushable.put("is_pushable", true);
        pushable.put("is_pushable_by_piston", true);
        components.put("minecraft:pushable", pushable);

        components.put("minecraft:nameable", new JSONObject());

        entity.put("components", components);
        root.put("minecraft:entity", entity);

        File entityFile = new File(entities, entityName + ".json");
        writePretty(entityFile, root);

        return new Result(identifier, bp, rp, entityFile);
    }

    public static List<EntityInfo> listEntities(Activity activity) {
        List<EntityInfo> result = new ArrayList<>();
        File dir = getEntitiesDirectory(activity);
        File[] files = dir.listFiles((parent, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null) return result;

        for (File file : files) {
            try {
                result.add(readEntity(file));
            } catch (Throwable ignored) {
            }
        }

        Collections.sort(result, Comparator.comparing(info -> info.identifier));
        return result;
    }

    public static EntityInfo readEntity(File file) throws Exception {
        JSONObject root = new JSONObject(readText(file));
        JSONObject entity = root.getJSONObject("minecraft:entity");
        JSONObject description = entity.getJSONObject("description");
        JSONObject components = entity.optJSONObject("components");
        if (components == null) components = new JSONObject();

        String identifier = description.optString("identifier", file.getName().replace(".json", ""));
        String namespace = "victor";
        String entityName = identifier;
        int colon = identifier.indexOf(':');
        if (colon > 0 && colon < identifier.length() - 1) {
            namespace = identifier.substring(0, colon);
            entityName = identifier.substring(colon + 1);
        }

        double health = componentNumber(components, "minecraft:health", "value", 20.0);
        double movement = componentNumber(components, "minecraft:movement", "value", 0.20);
        double damage = componentNumber(components, "minecraft:attack", "damage", 3.0);

        return new EntityInfo(namespace, entityName, identifier, health, movement, damage, file);
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
        header.put("uuid", stableUuid(MANIFEST_SEED + ":bp:header"));
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
        header.put("uuid", stableUuid(MANIFEST_SEED + ":rp:header"));
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

    private static JSONArray version() {
        return new JSONArray().put(0).put(2).put(0);
    }

    private static double componentNumber(
            JSONObject components,
            String componentName,
            String property,
            double fallback
    ) {
        JSONObject component = components.optJSONObject(componentName);
        if (component == null) return fallback;
        Object value = component.opt(property);
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String sanitize(String value, String fallback) {
        if (value == null) return fallback;
        String s = value.trim().toLowerCase()
                .replaceAll("[^a-z0-9_\\-.]", "_")
                .replaceAll("_+", "_");
        if (s.isEmpty()) return fallback;
        return s;
    }

    private static void ensureDirectory(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Falha ao criar " + dir);
        }
    }

    private static String readText(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int read = in.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return new String(bytes, 0, offset, StandardCharsets.UTF_8);
        }
    }

    private static void writePretty(File file, JSONObject json) throws Exception {
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

        Result(String identifier, File behaviorPack, File resourcePack, File entityFile) {
            this.identifier = identifier;
            this.behaviorPack = behaviorPack;
            this.resourcePack = resourcePack;
            this.entityFile = entityFile;
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

        EntityInfo(
                String namespace,
                String entityName,
                String identifier,
                double health,
                double movement,
                double damage,
                File file
        ) {
            this.namespace = namespace;
            this.entityName = entityName;
            this.identifier = identifier;
            this.health = health;
            this.movement = movement;
            this.damage = damage;
            this.file = file;
        }
    }
}
