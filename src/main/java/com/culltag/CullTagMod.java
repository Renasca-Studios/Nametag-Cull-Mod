package com.culltag;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CullTag: server-side nametag culling. Vanilla renders player nametags through walls; this
 * mod hides them when the line of sight is blocked, without anything installed client-side.
 */
public final class CullTagMod implements DedicatedServerModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("culltag");

    @Override
    public void onInitializeServer() {
        CullTagConfig.load(LOGGER);

        ServerTickEvents.END_SERVER_TICK.register(server ->
                LineOfSightEngine.tick(server.getPlayerList().getPlayers()));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CullTagConfig.reload(LOGGER);
            CrouchHider.onServerStarted(server);
        });

        // Both of these exist so an override never outlives the thing it was pointed at.
        // A player who leaves must not stay on anyone's hidden team, or they come back
        // already invisible; a player who respawns becomes a new entity with a new ID, and
        // the old one would sit in every viewer's hidden set for the rest of the session.
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
}
