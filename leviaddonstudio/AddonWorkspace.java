package org.levimc.launcher.addonstudio;

import android.app.Activity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class AddonWorkspace {
    private static final String PACK_NAME = "LeviAddonStudio";

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

        File external = activity.getExternalFilesDir(null);
        if (external == null) external = activity.getFilesDir();

        File mojang = new File(external, "games/com.mojang");
        File bp = new File(mojang, "development_behavior_packs/" + PACK_NAME + "_BP");
        File rp = new File(mojang, "development_resource_packs/" + PACK_NAME + "_RP");
        File entities = new File(bp, "entities");

        if (!entities.exists() && !entities.mkdirs()) {
            throw new IllegalStateException("Falha ao criar " + entities);
        }
        if (!rp.exists() && !rp.mkdirs()) {
            throw new IllegalStateException("Falha ao criar " + rp);
        }

        writeBehaviorManifest(bp, namespace);
        writeResourceManifest(rp, namespace);

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

    private static void writeBehaviorManifest(File bp, String namespace) throws Exception {
        JSONObject manifest = new JSONObject();
        manifest.put("format_version", 2);

        JSONObject header = new JSONObject();
        header.put("name", PACK_NAME + " BP");
        header.put("description", "Behavior Pack criado dentro do LeviLauncher");
        header.put("uuid", stableUuid(namespace + ":bp:header"));
        header.put("version", version());
        header.put("min_engine_version", new JSONArray().put(1).put(21).put(0));
        manifest.put("header", header);

        JSONObject module = new JSONObject();
        module.put("type", "data");
        module.put("uuid", stableUuid(namespace + ":bp:module"));
        module.put("version", version());

        manifest.put("modules", new JSONArray().put(module));
        writePretty(new File(bp, "manifest.json"), manifest);
    }

    private static void writeResourceManifest(File rp, String namespace) throws Exception {
        JSONObject manifest = new JSONObject();
        manifest.put("format_version", 2);

        JSONObject header = new JSONObject();
        header.put("name", PACK_NAME + " RP");
        header.put("description", "Resource Pack criado dentro do LeviLauncher");
        header.put("uuid", stableUuid(namespace + ":rp:header"));
        header.put("version", version());
        header.put("min_engine_version", new JSONArray().put(1).put(21).put(0));
        manifest.put("header", header);

        JSONObject module = new JSONObject();
        module.put("type", "resources");
        module.put("uuid", stableUuid(namespace + ":rp:module"));
        module.put("version", version());

        manifest.put("modules", new JSONArray().put(module));
        writePretty(new File(rp, "manifest.json"), manifest);
    }

    private static JSONArray version() {
        return new JSONArray().put(0).put(1).put(0);
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

    private static void writePretty(File file, JSONObject json) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

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
}
