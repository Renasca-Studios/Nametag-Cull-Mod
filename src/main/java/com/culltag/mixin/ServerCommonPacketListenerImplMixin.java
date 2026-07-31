package com.culltag.mixin;

import com.culltag.NametagController;
import com.culltag.NametagManager;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mixed into {@link ServerCommonPacketListenerImpl}, the class that owns both the
 * {@code connection} field and the {@code send(Packet, ChannelFutureListener)} method.
 * Targeting the parent rather than the subclass lets us shadow the field and inject into the
 * method without Mixin failing to locate them.
 *
 * <p>Because this mixin implements {@link NametagController} on the parent class, the
 * interface is available on all subclass instances (including
 * {@code ServerGamePacketListenerImpl}). {@link com.culltag.LineOfSightEngine} casts
 * {@code player.connection} to {@link NametagController} to reach the per-connection state
 * and the direct-send bypass.
 *
 * <p>Everything merged into the target class is {@code @Unique}. A {@code culltag_} prefix is
 * convention only; without the annotation a helper lands in
 * {@code ServerCommonPacketListenerImpl} under its bare name and collides with any other mod
 * that picks the same one.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin implements NametagController {

    // connection is declared on this class, so shadow works without an access widener.
    @Shadow protected Connection connection;

    // Concurrent because send() is reachable from netty threads while the LOS sweep mutates
    // these from the server thread.
    @Unique
    private final Set<Integer> culltag_hiddenEntityIds = ConcurrentHashMap.newKeySet();

    @Unique
    private final Set<String> culltag_crouchHiddenNames = ConcurrentHashMap.newKeySet();

    // NametagController ───────────────────────────────────────────────────────

    @Override
    public Set<Integer> culltag_getHiddenEntityIds() {
        return culltag_hiddenEntityIds;
    }

    @Override
    public Set<String> culltag_getCrouchHiddenNames() {
        return culltag_crouchHiddenNames;
    }

    /**
     * Sends directly via {@link Connection} to bypass the intercept below, preventing
     * infinite recursion when {@link NametagManager} pushes override packets.
     */
    @Override
    public void culltag_sendDirect(Packet<?> packet) {
        connection.send(packet, null);
    }

    // Packet intercept ────────────────────────────────────────────────────────

    /**
     * Intercepts metadata packets for entities in the viewer's hidden set and ORs the
     * sneaking bit into the shared-flags byte, so vanilla's own "do not draw a sneaking
     * player's nametag through anything" rule does the hiding. Only fires when the packet is
     * a {@link ClientboundSetEntityDataPacket}, the entity ID is hidden for this viewer, and
     * the flags value is actually present in this delta; a delta that does not carry flags is
     * left alone, because the client keeps the sneaking state it already has.
     */
    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void culltag_interceptMetadata(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if (!(packet instanceof ClientboundSetEntityDataPacket dataPacket)) return;
        if (!culltag_hiddenEntityIds.contains(dataPacket.id())) return;

        List<SynchedEntityData.DataValue<?>> original = dataPacket.packedItems();
        if (original == null) return;

        boolean hasFlags = false;
        for (SynchedEntityData.DataValue<?> v : original) {
            if (v.id() == NametagManager.FLAGS_ID) { hasFlags = true; break; }
        }
        if (!hasFlags) return;

        ci.cancel();
        connection.send(new ClientboundSetEntityDataPacket(
                dataPacket.id(), culltag_rewriteFlags(original)), listener);
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static List<SynchedEntityData.DataValue<?>> culltag_rewriteFlags(
            List<SynchedEntityData.DataValue<?>> entries) {

        List<SynchedEntityData.DataValue<?>> result = new ArrayList<>(entries.size());
        for (SynchedEntityData.DataValue<?> entry : entries) {
            if (entry.id() == NametagManager.FLAGS_ID) {
                byte flags = NametagManager.addSneaking(
                        ((SynchedEntityData.DataValue<Byte>) entry).value());
                result.add(new SynchedEntityData.DataValue<>(
                        NametagManager.FLAGS_ID, EntityDataSerializers.BYTE, flags));
            } else {
                result.add(entry);
            }
        }
        return result;
    }
}
