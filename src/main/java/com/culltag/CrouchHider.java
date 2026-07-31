package com.culltag;

import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.List;
import java.util.Set;

/**
 * Per-viewer nametag suppression via a scoreboard team with
 * {@code nametagVisibility=NEVER}.
 *
 * <p>The trick: scoreboard teams are global state, but team-membership packets are
 * clientbound, so the server can tell each client something different about who is on which
 * team. We create a real {@link PlayerTeam} ({@value #TEAM_NAME}) on the server scoreboard and
 * never add anyone to it server-side; instead, to stop viewer V seeing target T's nametag, we
 * send V and only V a packet putting T on that team. V's client then applies the team's
 * {@code NEVER} rule to T's nametag.
 *
 * <p>Used for the crouch-hide feature, where the sneak-flag mechanism in
 * {@link NametagManager} does not work: vanilla still draws a sneaking player's nametag at
 * close range with clear sight.
 *
 * <p>Which names a client has been told about is held on that client's connection rather than
 * in a static map, so it dies with the connection. A viewer who reconnects has a fresh client
 * that knows nothing about the team, and a server-side set that outlived them would make
 * {@link #setHidden} decide there was nothing to send.
 *
 * <p>Caveat: if T was already on a real team server-side, V's client sees T on
 * {@value #TEAM_NAME} instead, which loses that team's colour and prefix in V's view. For
 * setups without server-managed teams this is a non-issue.
 */
public final class CrouchHider {

    public static final String TEAM_NAME = "culltag_hidden";

    private static volatile PlayerTeam team;

    private CrouchHider() {}

    /** Create the hidden-nametags team on the server scoreboard if it is not already there,
     *  and force its visibility rule. Idempotent. Vanilla syncs every existing team to a
     *  joining client on its own, so there is nothing to push per player. */
    public static void onServerStarted(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        PlayerTeam existing = sb.getPlayerTeam(TEAM_NAME);
        team = (existing != null) ? existing : sb.addPlayerTeam(TEAM_NAME);
        team.setNameTagVisibility(Team.Visibility.NEVER);
    }

    /** Set whether {@code target}'s nametag is hidden from {@code viewer}, sending the
     *  add or remove packet only on a state change. */
    public static void setHidden(ServerPlayer viewer, ServerPlayer target, boolean hide) {
        PlayerTeam t = team;
        if (t == null) return;

        Set<String> names = NametagManager.controller(viewer).culltag_getCrouchHiddenNames();
        // getScoreboardName, not getName().getString(): team membership is keyed on the
        // scoreboard name, and a mod that changes a player's display name would otherwise
        // make us send packets naming somebody the client's team roster has never heard of,
        // leaving the feature silently doing nothing.
        String name = target.getScoreboardName();

        if (hide) {
            if (!names.add(name)) return;
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    t, name, ClientboundSetPlayerTeamPacket.Action.ADD));
        } else {
            if (!names.remove(name)) return;
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    t, name, ClientboundSetPlayerTeamPacket.Action.REMOVE));
        }
    }

    /** Restore every viewer's view by taking all targets back off the hidden team. Called by
     *  the disable and reload kill switch alongside {@link NametagManager#restoreAll}. */
    public static int restoreAll(List<ServerPlayer> players) {
        PlayerTeam t = team;
        if (t == null) return 0;
        int total = 0;
        for (ServerPlayer viewer : players) {
            Set<String> names = NametagManager.controller(viewer).culltag_getCrouchHiddenNames();
            for (String name : names) {
                viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                        t, name, ClientboundSetPlayerTeamPacket.Action.REMOVE));
                total++;
            }
            names.clear();
        }
        return total;
    }

    /** Total nametags currently hidden by crouch across all viewers, for /culltag stats. */
    public static int countHidden(List<ServerPlayer> players) {
        int total = 0;
        for (ServerPlayer viewer : players) {
            total += NametagManager.controller(viewer).culltag_getCrouchHiddenNames().size();
        }
        return total;
    }
}
