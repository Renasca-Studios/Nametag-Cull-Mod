package com.culltag;

import net.minecraft.network.protocol.Packet;

import java.util.Set;

/**
 * Duck-typing interface mixed into {@link net.minecraft.server.network.ServerCommonPacketListenerImpl}
 * by {@link com.culltag.mixin.ServerCommonPacketListenerImplMixin}, and therefore present on
 * every {@code ServerGamePacketListenerImpl}.
 *
 * <p>This connection is the single source of truth for what CullTag has told one client.
 * Holding the same answer in a second map keyed by UUID is what 1.1.1 did, and every way the
 * two were allowed to disagree turned into a nametag stuck on or stuck off. The set below dies
 * with the connection, so a reconnecting player starts from the vanilla state that their fresh
 * client is actually in.
 */
public interface NametagController {

    /** Entity IDs whose outgoing metadata is currently being rewritten to force sneaking. */
    Set<Integer> culltag_getHiddenEntityIds();

    /** Sends {@code packet} to the client, bypassing the mixin's own intercept. */
    void culltag_sendDirect(Packet<?> packet);
}
