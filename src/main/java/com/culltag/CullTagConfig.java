package com.culltag;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class CullTagConfig {

    /** Vanilla stops drawing any entity nametag past 64 blocks, so checking further than that
     *  is work that can never change what a player sees. It is the ceiling on max_distance
     *  rather than a separate setting. */
    public static final int VANILLA_NAMETAG_RANGE = 64;

    public static volatile boolean enabled            = true;
    public static volatile int maxDistance            = 32;
    public static volatile int checkIntervalTicks     = 10;
    public static volatile boolean cullEntityNametags = true;

    private static final Path CONFIG_PATH = Path.of("config", "culltag.properties");

    private CullTagConfig() {}

    public static void load(Logger logger) {
        Properties props = new Properties();

        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException e) {
                logger.error("[CullTag] Failed to read config, using defaults", e);
            }
        }

        enabled            = parseBoolean(props, "enabled",               true, logger);
        maxDistance        = parseInt(props, "max_distance", 32, 1, VANILLA_NAMETAG_RANGE, logger);
        checkIntervalTicks = parseInt(props, "check_interval_ticks", 10, 1, 40, logger);
        cullEntityNametags = parseBoolean(props, "cull_entity_nametags", true, logger);

        save(logger);
        logger.info("[CullTag] Config loaded: {}", summary());
    }

    public static void reload(Logger logger) {
        load(logger);
    }

    /** Operator diagnostics for /culltag reload. Every label here is the config file's own
     *  key name rather than prose, so {@link CullTagText} passes the whole string through as
     *  one opaque argument instead of breaking it into translatable pieces. */
    public static String summary() {
        return "enabled=" + enabled
                + " max_distance=" + maxDistance
                + " check_interval_ticks=" + checkIntervalTicks
                + " cull_entity_nametags=" + cullEntityNametags;
    }

    private static boolean parseBoolean(Properties props, String key, boolean def, Logger logger) {
        String raw = props.getProperty(key);
        if (raw == null) return def;
        String s = raw.trim().toLowerCase();
        if (s.equals("true")) return true;
        if (s.equals("false")) return false;
        logger.warn("[CullTag] '{}' is not a valid boolean ('{}'), using default {}", key, raw, def);
        return def;
    }

    private static int parseInt(Properties props, String key, int def, int min, int max, Logger logger) {
        String raw = props.getProperty(key);
        if (raw == null) return def;
        try {
            int val = Integer.parseInt(raw.trim());
            if (val < min || val > max) {
                logger.warn("[CullTag] '{}' value {} out of range [{}, {}], clamping", key, val, min, max);
                return Math.max(min, Math.min(max, val));
            }
            return val;
        } catch (NumberFormatException e) {
            logger.warn("[CullTag] '{}' is not a valid integer ('{}'), using default {}", key, raw, def);
            return def;
        }
    }

    public static void save(Logger logger) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, toPropertiesString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("[CullTag] Failed to save config", e);
        }
    }

    private static String toPropertiesString() {
        return """
                # CullTag configuration
                # Changes take effect after /culltag reload or server restart.

                # Enable or disable nametag culling entirely.
                enabled=%b

                # Maximum distance in blocks at which line-of-sight checks are performed.
                # Anything further away is left exactly as vanilla draws it. Capped at 64
                # because vanilla stops drawing nametags there anyway.
                max_distance=%d

                # How many server ticks between sweeps.
                # Lower values are more responsive but use more CPU.
                # 10 = ~2 checks per second, 4 = ~5 checks per second.
                check_interval_ticks=%d

                # Also cull the nametags of named mobs and armour stands, which vanilla draws
                # through walls just like a player's. Invisible ones are always left alone, so
                # hologram armour stands stay readable.
                cull_entity_nametags=%b

                # Blocks that do not hide a nametag are decided by the #culltag:transparent
                # block tag, not by this file. Override it with a datapack to add or remove
                # blocks; by default it covers glass, panes, bars, chains and ladders.
                """.formatted(enabled, maxDistance, checkIntervalTicks, cullEntityNametags);
    }
}
