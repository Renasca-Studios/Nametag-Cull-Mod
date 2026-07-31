package com.culltag;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CullTag: server-side nametag culling. Vanilla renders nametags through walls; this mod hides
 * them when the line of sight is blocked, without anything installed client-side.
 */
public final class CullTagMod implements DedicatedServerModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("culltag");

    /** The team the removed crouch-hide feature created on the world scoreboard. It persisted
     *  in the save, so servers upgrading from 1.1.x still have it sitting in /team list. */
    private static final String LEGACY_TEAM = "culltag_hidden";

    @Override
    public void onInitializeServer() {
        CullTagConfig.load(LOGGER);

        ServerTickEvents.END_SERVER_TICK.register(server ->
                LineOfSightEngine.tick(server.getPlayerList().getPlayers()));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CullTagConfig.reload(LOGGER);
            removeLegacyTeam(server);
            logTransparentTag();
        });

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
                (server, resourceManager, success) -> logTransparentTag());

        // Both of these exist so an override never outlives the thing it was pointed at. A
        // player who leaves and a player who respawns both stop being the entity ID that
        // viewers recorded, and the old ID would otherwise sit in every viewer's hidden set
        // for the rest of the session.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LineOfSightEngine.forgetPlayer(server.getPlayerList().getPlayers(), handler.player));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            MinecraftServer server = newPlayer.level().getServer();
            if (server != null) {
                LineOfSightEngine.forgetEntity(server.getPlayerList().getPlayers(), oldPlayer.getId());
            }
        });

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> CullTagCommand.register(dispatcher));

        LOGGER.info("[CullTag] Initialised");
    }

    private static void logTransparentTag() {
        int count = SightTest.transparentBlockCount();
        if (count == 0) {
            LOGGER.warn("[CullTag] #culltag:transparent resolved to no blocks, so every block "
                    + "with collision will hide nametags, including glass. Check for a datapack "
                    + "overriding the tag with \"replace\": true.");
        } else {
            LOGGER.info("[CullTag] #culltag:transparent covers {} block(s) that never hide a nametag",
                    count);
        }
    }

    private static void removeLegacyTeam(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        PlayerTeam stale = sb.getPlayerTeam(LEGACY_TEAM);
        if (stale != null) {
            sb.removePlayerTeam(stale);
            LOGGER.info("[CullTag] Removed the leftover '{}' scoreboard team from a previous version",
                    LEGACY_TEAM);
        }
    }
}
