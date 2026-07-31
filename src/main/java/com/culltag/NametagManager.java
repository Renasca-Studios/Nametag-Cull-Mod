package com.culltag;

import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Pushes the per-viewer nametag override that hides a target through walls.
 *
 * <p>On hide: sends a metadata packet that forces the sneaking bit on the target. Vanilla
 * clients never draw a sneaking player's nametag through blocks, so this borrows that rule
 * for anyone whose line of sight is obstructed, whatever their real pose. While the target is
 * in the viewer's hidden set,
 * {@link com.culltag.mixin.ServerCommonPacketListenerImplMixin} re-applies the bit to every
 * later metadata packet, so a vanilla delta-sync cannot clear the override.
 *
 * <p>On reveal: sends the target's real shared-flags byte so the client corrects the pose
 * immediately.
 */
public final class NametagManager {

    /**
     * Index of {@link Entity#DATA_SHARED_FLAGS_ID} in the synched-data table. It is 0 today
     * and has been for years, but it is read from the accessor rather than written as a
     * literal: a vanilla field defined ahead of it would move it silently, and the mod would
     * then rewrite some unrelated value as a byte on every packet. The access widener exists
     * to make this readable.
     */
    public static final int FLAGS_ID = Entity.DATA_SHARED_FLAGS_ID.id();

    /** Bit 1 of the shared-flags byte: sneaking. */
    private static final byte SNEAKING_BIT = 0x02;

    private NametagManager() {}

    /** Hides {@code target}'s nametag from {@code viewer}. The caller owns the hidden set and
     *  has already recorded the entity ID in it. */
    public static void hide(ServerPlayer viewer, ServerPlayer target) {
        controller(viewer).culltag_sendDirect(
                buildFlagsPacket(target, addSneaking(getRealFlags(target))));
    }

    /** Restores {@code target}'s real pose for {@code viewer}, which brings the nametag back. */
    public static void reveal(ServerPlayer viewer, ServerPlayer target) {
        controller(viewer).culltag_sendDirect(
                buildFlagsPacket(target, getRealFlags(target)));
    }

    /**
     * Force-restores nametag state for every (viewer, target) pair, whether or not the server
     * thinks the target was being hidden. That matters after a hot jar swap: the server-side
     * hidden sets are empty on the new binary, but clients may still be holding force-sneak
     * flags pushed by the old one, and clearing only the tracked set would miss those.
     *
     * <p>Returns the number of restoration packets sent.
     */
    public static int restoreAll(List<ServerPlayer> players) {
        int totalSent = 0;
        for (ServerPlayer viewer : players) {
            controller(viewer).culltag_getHiddenEntityIds().clear();
            int sentForViewer = 0;
            for (ServerPlayer target : players) {
                if (target == viewer) continue;
                // Force-clear the sneaking bit. If the target really is sneaking, vanilla
                // reasserts it on the next metadata tick; for a kill switch we want a
                // guaranteed restore now even where no override existed.
                byte restored = clearSneaking(getRealFlags(target));
                controller(viewer).culltag_sendDirect(buildFlagsPacket(target, restored));
                sentForViewer++;
            }
            if (sentForViewer > 0) {
                CullTagMod.LOGGER.info("[CullTag] Restored {} nametag(s) for viewer {}",
                        sentForViewer, viewer.getScoreboardName());
                totalSent += sentForViewer;
            }
        }
        CullTagMod.LOGGER.info("[CullTag] restoreAll complete: {} packet(s) across {} viewer(s)",
                totalSent, players.size());
        return totalSent;
    }

    /** Total entities currently hidden by line of sight across all viewers, for /culltag stats. */
    public static int countHidden(List<ServerPlayer> players) {
        int total = 0;
        for (ServerPlayer viewer : players) {
            total += controller(viewer).culltag_getHiddenEntityIds().size();
        }
        return total;
    }

    public static byte getRealFlags(Entity entity) {
        return entity.getEntityData().get(Entity.DATA_SHARED_FLAGS_ID);
    }

    public static byte addSneaking(byte flags) {
        return (byte) (flags | SNEAKING_BIT);
    }

    public static byte clearSneaking(byte flags) {
        return (byte) (flags & ~SNEAKING_BIT);
    }

    public static ClientboundSetEntityDataPacket buildFlagsPacket(Entity entity, byte flagsValue) {
        List<SynchedEntityData.DataValue<?>> entries = List.of(
                new SynchedEntityData.DataValue<>(FLAGS_ID, EntityDataSerializers.BYTE, flagsValue));
        return new ClientboundSetEntityDataPacket(entity.getId(), entries);
    }

    static NametagController controller(ServerPlayer viewer) {
        return (NametagController) viewer.connection;
    }
}
